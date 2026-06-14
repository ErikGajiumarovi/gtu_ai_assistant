package com.gtu.aiassistant.infrastructure.ai

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.llms.RoundRobinRouter
import ai.koog.prompt.executor.llms.RoutingLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import com.gtu.aiassistant.domain.chat.model.Message as DomainMessage
import ai.koog.prompt.message.Message as KoogMessage
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import com.gtu.aiassistant.domain.model.InfrastructureError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class GenerateMessagePortImpl private constructor(
    private val executors: List<RoutingLLMPromptExecutor>,
    private val model: LLModel
) : GenerateMessagePort {

    override suspend fun invoke(command: GenerateMessageCommand): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                val validMessages = commonValidate(command.messages).bind()
                val generatedText = commonExecute(validMessages).bind()
                buildMessage(validMessages, generatedText)
            }
        }

    override suspend fun stream(
        command: GenerateMessageCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit
    ): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                val validMessages = commonValidate(command.messages).bind()
                onStatus(GenerateMessageStreamStatus("answering", "Writing answer..."))
                val generatedText = commonExecute(validMessages).bind()

                val words = generatedText.split(" ")
                for ((i, word) in words.withIndex()) {
                    onToken(if (i == 0) word else " $word")
                }

                buildMessage(validMessages, generatedText)
            }
        }

    private fun commonValidate(
        messages: List<DomainMessage>
    ): Either<InfrastructureError, List<DomainMessage>> = either {
        messages
            .validateForMessageGeneration()
            .mapLeft { cause ->
                InfrastructureError(cause = IllegalArgumentException("Invalid message history for AI generation: $cause"))
            }
            .bind()
    }

    private suspend fun commonExecute(
        validMessages: List<DomainMessage>
    ): Either<InfrastructureError, String> = either {
        val llmPrompt = prompt(
            id = "generate-chat-message",
            params = OpenAIChatParams(reasoningEffort = ReasoningEffort.MEDIUM)
        ) {
            system(SYSTEM_PROMPT)
            validMessages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
                when (message.senderType) {
                    MessageSenderType.USER -> user(message.originalText)
                    MessageSenderType.AI -> assistant(message.originalText)
                }
            }
        }

        val rawResponse = executeWithApiKeyFailover { executor ->
            executor.execute(llmPrompt, model)
        }.bind()

        val generatedText = Either.catch {
            rawResponse.assistantText()
        }.mapLeft(::InfrastructureError).bind().trim()

        ensure(generatedText.isNotBlank()) {
            InfrastructureError(cause = IllegalStateException("LLM response is blank"))
        }
        generatedText
    }

    private fun buildMessage(
        validMessages: List<DomainMessage>,
        generatedText: String
    ): DomainMessage {
        val lastMessageCreatedAt = validMessages.last().createdAt
        return DomainMessage(
            id = UUID.randomUUID(),
            originalText = generatedText,
            senderType = MessageSenderType.AI,
            createdAt = maxOf(Instant.now(), lastMessageCreatedAt.plusMillis(1))
        )
    }

    private suspend fun <T> executeWithApiKeyFailover(
        block: suspend (RoutingLLMPromptExecutor) -> T
    ): Either<InfrastructureError, T> {
        var lastError: Throwable? = null
        executors.forEachIndexed { index, executor ->
            when (val result = Either.catch { block(executor) }) {
                is Either.Right -> return result.value.right()
                is Either.Left -> {
                    lastError = result.value
                    if (!result.value.isAiKeyFailoverError() || index == executors.lastIndex) {
                        return InfrastructureError(result.value).left()
                    }
                }
            }
        }
        return InfrastructureError(lastError ?: IllegalStateException("No AI API keys configured")).left()
    }

    companion object {
        fun create(config: AiConfig): GenerateMessagePortImpl {
            val baseClient = HttpClient(CIO)
            val apiKeys = config.normalizedApiKeys()
            val executors = apiKeys.map { apiKey ->
                val client = OpenAILLMClient(
                    apiKey = apiKey,
                    settings = OpenAIClientSettings(baseUrl = config.baseUrl),
                    baseClient = baseClient
                )
                RoutingLLMPromptExecutor(RoundRobinRouter(listOf(client)))
            }

            return GenerateMessagePortImpl(
                executors = executors,
                model = LLModel(
                    provider = LLMProvider.OpenAI,
                    id = config.model,
                    capabilities = listOf(
                        LLMCapability.Completion,
                        LLMCapability.OpenAIEndpoint.Completions
                    ),
                    contextLength = DEFAULT_CONTEXT_LENGTH
                )
            )
        }

        private const val MAX_HISTORY_MESSAGES: Int = 20
        private const val DEFAULT_CONTEXT_LENGTH: Long = 128_000L

        private const val SYSTEM_PROMPT: String =
            """
            You are an AI assistant in a chat with a user.
            Use the provided conversation history.
            Answer the latest user message directly.
            You can describe the application capabilities: source-grounded chat, uploaded-material search, web context, and generated artifacts such as text/Markdown files, HTML pages, PNG charts, and DOCX documents.
            If the user asks what tools or capabilities you have, do not say you have no tools; describe these application-level capabilities.
            Keep the reply concise, relevant, and helpful.
            Return only the assistant reply text.
            """
    }
}

private fun List<KoogMessage.Response>.assistantText(): String =
    filterIsInstance<KoogMessage.Assistant>()
        .joinToString(separator = "\n") { it.content }
        .ifBlank { error("No assistant message in response: $this") }

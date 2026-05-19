package com.gtu.aiassistant.infrastructure.ai

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.model.Message as DomainMessage
import ai.koog.prompt.message.Message as KoogMessage
import com.gtu.aiassistant.domain.chat.model.MessageCitation
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.ai.tools.AgentSource
import com.gtu.aiassistant.infrastructure.ai.tools.GtuKnowledgeSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.GtuWebSearchTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType

import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

class AgentGenerateMessagePortImpl private constructor(
    private val executor: SingleLLMPromptExecutor,
    private val model: LLModel,
    private val knowledgeSearchTool: GtuKnowledgeSearchTool,
    private val webSearchTool: GtuWebSearchTool,
    private val config: AiConfig,
    private val httpClient: HttpClient
) : GenerateMessagePort {
    override suspend fun invoke(messages: List<DomainMessage>): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                val (validMessages, sources) = prepareGeneration(messages).bind()
                val generatedText = executeLlm(validMessages, sources).bind()
                buildDomainMessage(validMessages, sources, generatedText)
            }
        }

    override suspend fun stream(
        messages: List<DomainMessage>,
        onToken: suspend (String) -> Unit
    ): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                val (validMessages, sources) = prepareGeneration(messages).bind()
                val generatedText = executeLlmStream(validMessages, sources, onToken).bind()
                buildDomainMessage(validMessages, sources, generatedText)
            }
        }

    private suspend fun prepareGeneration(
        messages: List<DomainMessage>
    ): Either<InfrastructureError, Pair<List<DomainMessage>, List<AgentSource>>> = either {
        val validMessages = messages
            .validateForMessageGeneration()
            .mapLeft { cause ->
                InfrastructureError(cause = IllegalArgumentException("Invalid message history for AI generation: $cause"))
            }
            .bind()

        val latestUserText = validMessages.last().originalText
        val ragSources = knowledgeSearchTool.search(latestUserText)
            .fold(ifLeft = { emptyList() }, ifRight = { it })
        val shouldSearchWeb = ragSources.maxOfOrNull { it.score } == null ||
            ragSources.maxOfOrNull { it.score }!! < MIN_RAG_CONFIDENCE ||
            latestUserText.isTimeSensitive()
        val webSources = if (shouldSearchWeb) {
            webSearchTool.search(latestUserText)
                .fold(ifLeft = { emptyList() }, ifRight = { it })
        } else {
            emptyList()
        }
        val sources = (ragSources + webSources)
            .distinctBy { it.url to it.snippet }
            .take(MAX_SOURCES)

        validMessages to sources
    }

    private suspend fun executeLlm(
        validMessages: List<DomainMessage>,
        sources: List<AgentSource>
    ): Either<InfrastructureError, String> = either {
        val llmPrompt = prompt("generate-gtu-agent-message") {
            system(SYSTEM_PROMPT + "\n\n" + sources.toContextBlock())
            validMessages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
                when (message.senderType) {
                    MessageSenderType.USER -> user(message.originalText)
                    MessageSenderType.AI -> assistant(message.originalText)
                }
            }
        }

        val rawResponse = Either.catch {
            executor.execute(llmPrompt, model)
        }.mapLeft(::InfrastructureError).bind()

        val generatedText = Either.catch {
            rawResponse.assistantText()
        }.mapLeft(::InfrastructureError).bind().trim()

        ensure(generatedText.isNotBlank()) {
            InfrastructureError(cause = IllegalStateException("LLM response is blank"))
        }
        generatedText
    }

    private suspend fun executeLlmStream(
        validMessages: List<DomainMessage>,
        sources: List<AgentSource>,
        onToken: suspend (String) -> Unit
    ): Either<InfrastructureError, String> = either {
        val systemContent = SYSTEM_PROMPT + "\n\n" + sources.toContextBlock()
        val messagesJson = buildJsonArray {
            addJsonObject {
                put("role", "system")
                put("content", systemContent)
            }
            validMessages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
                addJsonObject {
                    put("role", when (message.senderType) {
                        MessageSenderType.USER -> "user"
                        MessageSenderType.AI -> "assistant"
                    })
                    put("content", message.originalText)
                }
            }
        }

        val requestBody = buildJsonObject {
            put("model", model.id)
            put("stream", true)
            put("messages", messagesJson)
        }

        val textBuilder = StringBuilder()

        httpClient.preparePost("${config.baseUrl}/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.apiKey}")
            setBody(requestBody.toString())
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = Json.parseToJsonElement(data).jsonObject
                        val content = json["choices"]
                            ?.jsonArray
                            ?.firstOrNull()
                            ?.jsonObject
                            ?.get("delta")
                            ?.jsonObject
                            ?.get("content")
                            ?.jsonPrimitive
                            ?.content ?: ""
                        if (content.isNotEmpty()) {
                            onToken(content)
                            textBuilder.append(content)
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        val fullText = textBuilder.toString().trim()
        ensure(fullText.isNotBlank()) {
            InfrastructureError(cause = IllegalStateException("LLM stream produced empty response"))
        }
        fullText
    }

    private fun buildDomainMessage(
        validMessages: List<DomainMessage>,
        sources: List<AgentSource>,
        generatedText: String
    ): DomainMessage {
        val lastMessageCreatedAt = validMessages.last().createdAt
        return DomainMessage(
            id = UUID.randomUUID(),
            originalText = generatedText,
            senderType = MessageSenderType.AI,
            createdAt = maxOf(Instant.now(), lastMessageCreatedAt.plusMillis(1)),
            citations = sources.toCitations()
        )
    }

    companion object {
        fun create(
            config: AiConfig,
            knowledgeSearchTool: GtuKnowledgeSearchTool,
            webSearchTool: GtuWebSearchTool
        ): AgentGenerateMessagePortImpl {
            val client = HttpClient(CIO)
            val openaiClient = OpenAILLMClient(
                apiKey = config.apiKey,
                settings = OpenAIClientSettings(baseUrl = config.baseUrl),
                baseClient = client
            )

            return AgentGenerateMessagePortImpl(
                executor = SingleLLMPromptExecutor(openaiClient),
                model = LLModel(
                    provider = LLMProvider.OpenAI,
                    id = config.model,
                    capabilities = listOf(
                        LLMCapability.Completion,
                        LLMCapability.OpenAIEndpoint.Completions
                    ),
                    contextLength = DEFAULT_CONTEXT_LENGTH
                ),
                knowledgeSearchTool = knowledgeSearchTool,
                webSearchTool = webSearchTool,
                config = config,
                httpClient = client
            )
        }

        private const val MAX_HISTORY_MESSAGES: Int = 20
        private const val DEFAULT_CONTEXT_LENGTH: Long = 128_000L
        private const val MAX_SOURCES: Int = 6
        private const val MIN_RAG_CONFIDENCE: Double = 0.32
        private const val STREAM_CHUNK_SIZE: Long = 4096L

        private const val SYSTEM_PROMPT: String =
            """
            You are the GTU AI Assistant for students of Georgian Technical University.
            Your main task is to help with university-related information: admissions, faculties, services, schedules, scholarships, exchange programs, rules, contacts, and public student resources.
            Use the provided GTU source context before relying on general knowledge.
            Answer in the user's language.
            For factual GTU claims, rely only on the provided source context. If the context does not confirm the answer, say that you could not confirm it from GTU sources and suggest checking the official GTU site or relevant office.
            Do not invent deadlines, prices, contacts, rules, or personal student data.
            Never claim access to private systems such as VICI, e-learning, testing, finances, or personal records.
            Keep the answer concise and practical.
            Return only the assistant reply text.
            """
    }
}

private fun List<AgentSource>.toContextBlock(): String =
    if (isEmpty()) {
        """
        GTU source context:
        No verified GTU source context was found for this message.
        If the user asks for factual university information, explicitly state that it could not be confirmed from GTU sources.
        """.trimIndent()
    } else {
        buildString {
            appendLine("GTU source context:")
            this@toContextBlock.forEachIndexed { index, source ->
                appendLine("[${index + 1}] ${source.title}")
                appendLine("URL: ${source.url}")
                appendLine("Excerpt: ${source.snippet}")
                appendLine()
            }
        }
    }

private fun List<AgentSource>.toCitations(): List<MessageCitation> =
    distinctBy { it.url }
        .take(6)
        .map { source ->
            MessageCitation(
                title = source.title,
                url = source.url,
                snippet = source.snippet,
                sourceType = source.sourceType
            )
        }

private fun String.isTimeSensitive(): Boolean {
    val normalized = lowercase()
    return listOf(
        "today", "latest", "current", "deadline", "news", "now",
        "сегодня", "сейчас", "последн", "дедлайн", "новост",
        "დღეს", "ახლა"
    ).any { it in normalized }
}

private fun List<KoogMessage.Response>.assistantText(): String =
    filterIsInstance<KoogMessage.Assistant>()
        .joinToString(separator = "\n") { it.content }
        .ifBlank { error("No assistant message in response: $this") }

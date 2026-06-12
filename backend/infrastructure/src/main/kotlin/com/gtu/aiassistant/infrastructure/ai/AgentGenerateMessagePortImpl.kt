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
import com.gtu.aiassistant.domain.chat.model.ChatSourceMode
import com.gtu.aiassistant.domain.chat.model.MessageCitation
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.ai.tools.AgentSource
import com.gtu.aiassistant.infrastructure.ai.tools.GtuKnowledgeSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.GtuWebSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.UserMaterialSearchTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class AgentGenerateMessagePortImpl private constructor(
    private val executor: SingleLLMPromptExecutor,
    private val model: LLModel,
    private val knowledgeSearchTool: GtuKnowledgeSearchTool,
    private val userMaterialSearchTool: UserMaterialSearchTool,
    private val webSearchTool: GtuWebSearchTool,
    private val config: AiConfig,
    private val httpClient: HttpClient
) : GenerateMessagePort {
    private val logger = LoggerFactory.getLogger(AgentGenerateMessagePortImpl::class.java)

    override suspend fun invoke(command: GenerateMessageCommand): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                logger.info(
                    "AI generation started model={} sourceMode={} messageCount={} collectionCount={} documentCount={}",
                    model.id,
                    command.sourceMode,
                    command.messages.size,
                    command.collectionIds.size,
                    command.documentIds.size
                )
                val preparedGeneration = prepareGeneration(command).bind()
                val generatedText = executeLlm(preparedGeneration).bind()
                logger.info(
                    "AI generation completed model={} outputLength={} sourceCount={}",
                    model.id,
                    generatedText.length,
                    preparedGeneration.sources.size
                )
                val validMessages = preparedGeneration.validMessages
                val sources = preparedGeneration.sources
                buildDomainMessage(validMessages, sources, generatedText)
            }
        }

    override suspend fun stream(
        command: GenerateMessageCommand,
        onToken: suspend (String) -> Unit
    ): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                logger.info(
                    "AI stream generation started model={} sourceMode={} messageCount={} collectionCount={} documentCount={}",
                    model.id,
                    command.sourceMode,
                    command.messages.size,
                    command.collectionIds.size,
                    command.documentIds.size
                )
                val preparedGeneration = prepareGeneration(command).bind()
                val generatedText = executeLlmStream(preparedGeneration, onToken).bind()
                logger.info(
                    "AI stream generation completed model={} outputLength={} sourceCount={}",
                    model.id,
                    generatedText.length,
                    preparedGeneration.sources.size
                )
                val validMessages = preparedGeneration.validMessages
                val sources = preparedGeneration.sources
                buildDomainMessage(validMessages, sources, generatedText)
            }
        }

    private suspend fun prepareGeneration(
        command: GenerateMessageCommand
    ): Either<InfrastructureError, PreparedGeneration> = either {
        val validMessages = command.messages
            .validateForMessageGeneration()
            .mapLeft { cause ->
                InfrastructureError(cause = IllegalArgumentException("Invalid message history for AI generation: $cause"))
            }
            .bind()

        val latestUserText = validMessages.last().originalText
        val gtuSources = if (command.sourceMode.usesGtuSources()) {
            knowledgeSearchTool.search(latestUserText)
                .fold(ifLeft = { emptyList() }, ifRight = { it })
        } else {
            emptyList()
        }
        val materialSources = if (command.sourceMode.usesUserMaterials()) {
            userMaterialSearchTool.search(
                ownerUserId = command.userId,
                query = latestUserText,
                collectionIds = command.collectionIds,
                documentIds = command.documentIds
            ).fold(ifLeft = { emptyList() }, ifRight = { it })
        } else {
            emptyList()
        }
        val bestLocalScore = (gtuSources + materialSources).maxOfOrNull { it.score }
        val shouldSearchWeb = command.sourceMode.usesWebSources() &&
            (bestLocalScore == null || bestLocalScore < MIN_RAG_CONFIDENCE || latestUserText.isTimeSensitive())
        val webSources = if (shouldSearchWeb) {
            webSearchTool.search(latestUserText)
                .fold(ifLeft = { emptyList() }, ifRight = { it })
        } else {
            emptyList()
        }
        val sources = command.sourceMode.combineSources(gtuSources, materialSources, webSources)
            .distinctBy { it.url to it.snippet }
            .take(MAX_SOURCES)

        logger.info(
            "AI context prepared sourceMode={} gtuSources={} materialSources={} webSources={} selectedSources={}",
            command.sourceMode,
            gtuSources.size,
            materialSources.size,
            webSources.size,
            sources.size
        )

        PreparedGeneration(
            validMessages = validMessages,
            sourceMode = command.sourceMode,
            sources = sources
        )
    }

    private suspend fun executeLlm(
        preparedGeneration: PreparedGeneration
    ): Either<InfrastructureError, String> = either {
        val llmPrompt = prompt("generate-gtu-agent-message") {
            system(preparedGeneration.systemPrompt(SYSTEM_PROMPT))
            preparedGeneration.validMessages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
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
        }.mapLeft { cause ->
            logger.warn("AI generation response could not be parsed model={} response={}", model.id, rawResponse, cause)
            InfrastructureError(cause)
        }.bind().trim()

        ensure(generatedText.isNotBlank()) {
            logger.warn("AI generation returned blank text model={} response={}", model.id, rawResponse)
            InfrastructureError(cause = IllegalStateException("LLM response is blank"))
        }
        generatedText
    }

    private suspend fun executeLlmStream(
        preparedGeneration: PreparedGeneration,
        onToken: suspend (String) -> Unit
    ): Either<InfrastructureError, String> = either {
        val systemContent = preparedGeneration.systemPrompt(SYSTEM_PROMPT)
        val messagesJson = buildJsonArray {
            addJsonObject {
                put("role", "system")
                put("content", systemContent)
            }
            preparedGeneration.validMessages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
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
        var streamedChunks = 0

        val streamUrl = config.openAiChatCompletionsUrl()
        Either.catch {
            httpClient.preparePost(streamUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${config.apiKey}")
                setBody(requestBody.toString())
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    error("LLM stream request failed: status=${response.status.value} url=$streamUrl")
                }
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
                                streamedChunks += 1
                                onToken(content)
                                textBuilder.append(content)
                            }
                        } catch (error: Exception) {
                            logger.warn("AI stream chunk parse failed model={} data={}", model.id, data, error)
                        }
                    }
                }
            }
        }.mapLeft { cause ->
            logger.warn("AI stream request failed model={} url={}", model.id, streamUrl, cause)
            InfrastructureError(cause)
        }.bind()

        val fullText = textBuilder.toString().trim()
        ensure(fullText.isNotBlank()) {
            logger.warn("AI stream returned blank text model={} streamedChunks={}", model.id, streamedChunks)
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
            userMaterialSearchTool: UserMaterialSearchTool,
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
                userMaterialSearchTool = userMaterialSearchTool,
                webSearchTool = webSearchTool,
                config = config,
                httpClient = client
            )
        }

        private const val MAX_HISTORY_MESSAGES: Int = 20
        private const val DEFAULT_CONTEXT_LENGTH: Long = 128_000L
        private const val MAX_SOURCES: Int = 6
        private const val MIN_RAG_CONFIDENCE: Double = 0.32

        private const val SYSTEM_PROMPT: String =
            """
            You are the GTU AI Assistant for students of Georgian Technical University.
            Your main task is to help with university-related information: admissions, faculties, services, schedules, scholarships, exchange programs, rules, contacts, and public student resources.
            Follow the source-selection rules for the current request.
            Answer in the user's language.
            For factual claims, rely only on the allowed source context. If the context does not confirm the answer, say that it could not be confirmed from the allowed sources.
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
        Source context:
        No allowed source context was found for this message.
        If the user asks for factual information that requires sources, explicitly state that it could not be confirmed from the allowed sources.
        """.trimIndent()
    } else {
        buildString {
            appendLine("Allowed source context:")
            this@toContextBlock.forEachIndexed { index, source ->
                appendLine("[${index + 1}] ${source.title}")
                appendLine("Type: ${source.sourceType}")
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
                sourceType = source.sourceType,
                documentId = source.documentId,
                pageStart = source.pageStart,
                pageEnd = source.pageEnd
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

private data class PreparedGeneration(
    val validMessages: List<DomainMessage>,
    val sourceMode: ChatSourceMode,
    val sources: List<AgentSource>
) {
    fun systemPrompt(basePrompt: String): String =
        basePrompt + "\n\n" + sourceMode.promptRules() + "\n\n" + sources.toContextBlock()
}

private fun AiConfig.openAiChatCompletionsUrl(): String {
    val normalizedBaseUrl = baseUrl.trimEnd('/')
    val openAiBaseUrl = if (normalizedBaseUrl.endsWith("/v1")) normalizedBaseUrl else "$normalizedBaseUrl/v1"
    return "$openAiBaseUrl/chat/completions"
}

private fun ChatSourceMode.usesGtuSources(): Boolean =
    this == ChatSourceMode.GTU_ONLY ||
        this == ChatSourceMode.GTU_AND_MY_MATERIALS ||
        this == ChatSourceMode.GTU_MY_MATERIALS_AND_WEB

private fun ChatSourceMode.usesUserMaterials(): Boolean =
    this == ChatSourceMode.MY_MATERIALS_ONLY ||
        this == ChatSourceMode.GTU_AND_MY_MATERIALS ||
        this == ChatSourceMode.GTU_MY_MATERIALS_AND_WEB

private fun ChatSourceMode.usesWebSources(): Boolean =
    this == ChatSourceMode.GTU_MY_MATERIALS_AND_WEB

private fun ChatSourceMode.promptRules(): String =
    when (this) {
        ChatSourceMode.GTU_ONLY ->
            "Use only verified GTU source context. Do not use private user materials or web search context."
        ChatSourceMode.MY_MATERIALS_ONLY ->
            "Use only uploaded user materials. Do not use GTU public sources, web search context, or general knowledge for factual claims. If uploaded materials do not contain enough information, say that the uploaded materials do not contain enough information to answer."
        ChatSourceMode.GTU_AND_MY_MATERIALS ->
            "Use only verified GTU source context and uploaded user materials. Do not use web search context. If these allowed sources are insufficient, say so."
        ChatSourceMode.GTU_MY_MATERIALS_AND_WEB ->
            "Use verified GTU source context and uploaded user materials first. Web search context may be used only as fallback when local sources are insufficient or the question is time-sensitive."
    }

private fun ChatSourceMode.combineSources(
    gtuSources: List<AgentSource>,
    materialSources: List<AgentSource>,
    webSources: List<AgentSource>
): List<AgentSource> =
    when (this) {
        ChatSourceMode.GTU_ONLY -> gtuSources
        ChatSourceMode.MY_MATERIALS_ONLY -> materialSources
        ChatSourceMode.GTU_AND_MY_MATERIALS ->
            materialSources.take(3) + gtuSources.take(3) + materialSources.drop(3) + gtuSources.drop(3)
        ChatSourceMode.GTU_MY_MATERIALS_AND_WEB ->
            materialSources.take(2) + gtuSources.take(2) + webSources.take(2) +
                materialSources.drop(2) + gtuSources.drop(2) + webSources.drop(2)
    }

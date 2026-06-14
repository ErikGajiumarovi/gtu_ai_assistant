package com.gtu.aiassistant.infrastructure.ai

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.dsl.extension.requestStreamingAndSendResultsImpl
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
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
import ai.koog.prompt.streaming.StreamFrame
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import com.gtu.aiassistant.domain.artifacts.model.MessageArtifact
import com.gtu.aiassistant.domain.chat.model.Message as DomainMessage
import ai.koog.prompt.message.Message as KoogMessage
import com.gtu.aiassistant.domain.chat.model.ChatSources
import com.gtu.aiassistant.domain.chat.model.MessageCitation
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.ai.tools.AgentSource
import com.gtu.aiassistant.infrastructure.ai.tools.GtuKnowledgeSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.GtuWebSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.UserMaterialContext
import com.gtu.aiassistant.infrastructure.ai.tools.UserMaterialSearchTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class AgentGenerateMessagePortImpl private constructor(
    private val executors: List<RoutingLLMPromptExecutor>,
    private val model: LLModel,
    private val knowledgeSearchTool: GtuKnowledgeSearchTool,
    private val userMaterialSearchTool: UserMaterialSearchTool,
    private val webSearchTool: GtuWebSearchTool,
    private val artifactService: AgentArtifactService?
) : GenerateMessagePort {
    private val logger = LoggerFactory.getLogger(AgentGenerateMessagePortImpl::class.java)

    override suspend fun invoke(command: GenerateMessageCommand): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                logger.info(
                    "AI generation started model={} sources={} messageCount={} collectionCount={} documentCount={}",
                    model.id,
                    command.sources,
                    command.messages.size,
                    command.collectionIds.size,
                    command.documentIds.size
                )
                val validMessages = validateCommand(command).bind()
                val agentResult = executeAgent(command, validMessages).bind()
                logger.info(
                    "AI generation completed model={} outputLength={} sourceCount={}",
                    model.id,
                    agentResult.generatedText.length,
                    agentResult.sources.size
                )
                buildDomainMessage(validMessages, agentResult.sources, agentResult.generatedText, agentResult.artifacts)
            }
        }

    override suspend fun stream(
        command: GenerateMessageCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit
    ): Either<InfrastructureError, DomainMessage> =
        withContext(Dispatchers.IO) {
            either {
                logger.info(
                    "AI stream generation started model={} sources={} messageCount={} collectionCount={} documentCount={}",
                    model.id,
                    command.sources,
                    command.messages.size,
                    command.collectionIds.size,
                    command.documentIds.size
                )
                val validMessages = validateCommand(command).bind()
                val agentResult = executeAgent(command, validMessages, onToken, onStatus).bind()
                logger.info(
                    "AI stream generation completed model={} outputLength={} sourceCount={}",
                    model.id,
                    agentResult.generatedText.length,
                    agentResult.sources.size
                )
                buildDomainMessage(validMessages, agentResult.sources, agentResult.generatedText, agentResult.artifacts)
            }
        }

    private fun validateCommand(command: GenerateMessageCommand): Either<InfrastructureError, List<DomainMessage>> = either {
        val validMessages = command.messages
            .validateForMessageGeneration()
            .mapLeft { cause ->
                InfrastructureError(cause = IllegalArgumentException("Invalid message history for AI generation: $cause"))
            }
            .bind()
        ensure(command.sources.hasAny()) {
            InfrastructureError(cause = IllegalArgumentException("At least one source must be selected"))
        }
        validMessages
    }

    private suspend fun executeAgent(
        command: GenerateMessageCommand,
        validMessages: List<DomainMessage>,
        onToken: suspend (String) -> Unit = {},
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit = {}
    ): Either<InfrastructureError, AgentRunResult> = either {
        onStatus(GenerateMessageStreamStatus("thinking", "Preparing agent tools..."))
        val conversation = buildAgentConversation(command, validMessages)
        executeWithApiKeyFailover { executor ->
            runAgentOnce(command, conversation, executor, onToken, onStatus)
        }.bind()
    }

    private suspend fun runAgentOnce(
        command: GenerateMessageCommand,
        conversation: AgentConversation,
        executor: RoutingLLMPromptExecutor,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit
    ): AgentRunResult {
        val runtime = AgentToolRuntime(
            command = command,
            knowledgeSearchTool = knowledgeSearchTool,
            userMaterialSearchTool = userMaterialSearchTool,
            webSearchTool = webSearchTool,
            artifactService = artifactService,
            maxSources = MAX_SOURCES
        )
        val agent = createToolCallingAgent(
            conversation = conversation,
            toolRegistry = runtime.toolRegistry(),
            executor = executor,
            onToken = onToken,
            onStatus = onStatus
        )

        val generatedText = try {
            agent.run(conversation.userMessage).trim()
        } finally {
            agent.close()
        }

        if (generatedText.isBlank()) {
            logger.warn("AI agent returned blank text model={}", model.id)
            throw IllegalStateException("LLM response is blank")
        }

        return AgentRunResult(
            generatedText = generatedText,
            sources = runtime.sourcesSnapshot(),
            artifacts = runtime.artifactsSnapshot()
        )
    }

    private fun buildAgentConversation(
        command: GenerateMessageCommand,
        validMessages: List<DomainMessage>
    ): AgentConversation {
        val windowedMessages = validMessages.takeLast(MAX_HISTORY_MESSAGES)
        val historyMessages = windowedMessages.dropLast(1)
        val userMessage = windowedMessages.last().originalText
        val historyPrompt = prompt(
            id = "generate-gtu-agent-message",
            params = OpenAIChatParams(
                parallelToolCalls = true,
                reasoningEffort = ReasoningEffort.MEDIUM
            )
        ) {
            system(command.sources.agentSystemPrompt())
            historyMessages.forEach { message ->
                when (message.senderType) {
                    MessageSenderType.USER -> user(message.originalText)
                    MessageSenderType.AI -> assistant(message.originalText)
                }
            }
        }

        return AgentConversation(historyPrompt = historyPrompt, userMessage = userMessage)
    }

    private fun createToolCallingAgent(
        conversation: AgentConversation,
        toolRegistry: ToolRegistry,
        executor: RoutingLLMPromptExecutor,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit
    ): AIAgent<String, String> = AIAgent(
        promptExecutor = executor,
        agentConfig = AIAgentConfig(
            prompt = conversation.historyPrompt,
            model = model,
            maxAgentIterations = MAX_AGENT_ITERATIONS
        ),
        strategy = streamingToolStrategy(),
        toolRegistry = toolRegistry,
        installFeatures = {
            handleEvents {
                onLLMStreamingFrameReceived { context ->
                    when (val frame = context.streamFrame) {
                        is StreamFrame.TextDelta -> {
                            if (frame.text.isNotEmpty()) {
                                onToken(frame.text)
                            }
                        }
                        else -> Unit
                    }
                }
                onToolCallStarting { context ->
                    onStatus(
                        GenerateMessageStreamStatus(
                            phase = "tool_call_started",
                            message = "Calling ${context.toolName.orEmpty().ifBlank { "tool" }}..."
                        )
                    )
                }
                onToolCallCompleted { context ->
                    onStatus(
                        GenerateMessageStreamStatus(
                            phase = "tool_call_completed",
                            message = "Completed ${context.toolName.orEmpty().ifBlank { "tool" }}."
                        )
                    )
                }
                onToolCallFailed { context ->
                    onStatus(
                        GenerateMessageStreamStatus(
                            phase = "tool_call_failed",
                            message = "Tool ${context.toolName.orEmpty().ifBlank { "call" }} failed."
                        )
                    )
                }
            }
        }
    )

    @OptIn(InternalAgentsApi::class)
    private fun streamingToolStrategy(): AIAgentGraphStrategy<String, String> = strategy("gtu_agent_tool_loop") {
        val nodeCallLLM by node<String, List<KoogMessage.Response>>("stream_initial_llm") { message ->
            llm.writeSession {
                appendPrompt {
                    user(message)
                }
            }
            requestStreamingAndSendResultsImpl(structureDefinition = null)
        }
        val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = true)
        val nodeSendToolResult by node<List<ReceivedToolResult>, List<KoogMessage.Response>>("stream_tool_results") { results ->
            llm.writeSession {
                appendPrompt {
                    tool {
                        results.forEach { result(it) }
                    }
                }
            }
            requestStreamingAndSendResultsImpl(structureDefinition = null)
        }

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
        edge(
            nodeCallLLM forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { it.joinToString(separator = "\n") { message -> message.content } }
        )
        edge(nodeExecuteTool forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
        edge(
            nodeSendToolResult forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { it.joinToString(separator = "\n") { message -> message.content } }
        )
    }

    private fun buildDomainMessage(
        validMessages: List<DomainMessage>,
        sources: List<AgentSource>,
        generatedText: String,
        artifacts: List<MessageArtifact>
    ): DomainMessage {
        val lastMessageCreatedAt = validMessages.last().createdAt
        return DomainMessage(
            id = UUID.randomUUID(),
            originalText = generatedText,
            senderType = MessageSenderType.AI,
            createdAt = maxOf(Instant.now(), lastMessageCreatedAt.plusMillis(1)),
            citations = sources.toCitations(),
            artifacts = artifacts
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
                        logger.warn("AI generation failed model={} apiKeyAttempt={}", model.id, index + 1, result.value)
                        return InfrastructureError(result.value).left()
                    }
                    logger.warn("AI API key failed with retryable auth/quota error model={} apiKeyAttempt={}, trying next key", model.id, index + 1)
                }
            }
        }
        return InfrastructureError(lastError ?: IllegalStateException("No AI API keys configured")).left()
    }

    companion object {
        fun create(
            config: AiConfig,
            knowledgeSearchTool: GtuKnowledgeSearchTool,
            userMaterialSearchTool: UserMaterialSearchTool,
            webSearchTool: GtuWebSearchTool,
            artifactService: AgentArtifactService? = null
        ): AgentGenerateMessagePortImpl {
            val baseClient = HttpClient(CIO)
            val apiKeys = config.normalizedApiKeys()
            val executors = apiKeys.map { apiKey ->
                val llmClient = OpenAILLMClient(
                    apiKey = apiKey,
                    settings = OpenAIClientSettings(baseUrl = config.baseUrl),
                    baseClient = baseClient
                )
                RoutingLLMPromptExecutor(RoundRobinRouter(listOf(llmClient)))
            }

            return AgentGenerateMessagePortImpl(
                executors = executors,
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
                artifactService = artifactService
            )
        }

        private const val MAX_HISTORY_MESSAGES: Int = 20
        private const val MAX_AGENT_ITERATIONS: Int = 100
        private const val DEFAULT_CONTEXT_LENGTH: Long = 128_000L
        private const val MAX_SOURCES: Int = 6
        const val SYSTEM_PROMPT: String =
            """
            You are the GTU AI Assistant for students of Georgian Technical University.
            In this application, GTU always means Georgian Technical University in Georgia. Never reinterpret GTU as Gujarat Technological University or any other institution.
            Your main task is to help with university-related information: admissions, faculties, services, schedules, scholarships, exchange programs, rules, contacts, and public student resources.
            Answer in the user's language.

            Core capabilities available in this application:
            - Answer questions using selected sources: GTU public knowledge, uploaded user materials, and optional web context.
            - Create downloadable artifacts when the user explicitly asks to create, generate, save, export, draw, plot, or prepare a file.
            - Supported generated artifacts include Markdown/text files, self-contained HTML pages, PNG charts, and DOCX Word documents.
            - HTML artifacts can be opened in a sandboxed page; other artifacts can be downloaded.
            - If the user asks what tools or capabilities you have, describe these capabilities directly. Do not say you have no tools.

            Source-grounding rules:
            For factual claims, rely only on the allowed source context. If the context does not confirm the answer, say that it could not be confirmed from the allowed sources.
            Do not invent deadlines, prices, contacts, rules, or personal student data.
            Never claim access to private systems such as VICI, e-learning, testing, finances, or personal records.

            Artifact rules:
            - If artifact_create returns artifact context, mention its download/open links and briefly describe what was created.
            - When Artifact context says the file was verified, tell the user that the returned file was checked and stored successfully.
            - Do not refuse artifact creation only because source context is empty. Artifact creation is a formatting/generation task, not a factual-source lookup.
            - For a user request like "create a docx report about GTU AI Assistant capabilities", use the capability list in this system prompt as valid self-context.
            - If artifact creation failed, explain the failure briefly and continue with a useful text answer.

            Keep the answer concise and practical.
            Return only the assistant reply text.
            """
    }
}

private data class AgentConversation(
    val historyPrompt: ai.koog.prompt.dsl.Prompt,
    val userMessage: String
)

private data class AgentRunResult(
    val generatedText: String,
    val sources: List<AgentSource>,
    val artifacts: List<MessageArtifact>
)

class AgentToolRuntime(
    private val command: GenerateMessageCommand,
    private val knowledgeSearchTool: GtuKnowledgeSearchTool,
    private val userMaterialSearchTool: UserMaterialSearchTool,
    private val webSearchTool: GtuWebSearchTool,
    private val artifactService: AgentArtifactService?,
    private val maxSources: Int
) {
    private val collectedSources = mutableListOf<AgentSource>()
    private val collectedArtifacts = mutableListOf<MessageArtifact>()
    private val latestUserText = command.messages.last().originalText

    fun toolRegistry(): ToolRegistry = ToolRegistry {
        tool(
            toolFunction = ::currentTime,
            name = "current_time",
            description = "Returns the current server time as an ISO-8601 instant string."
        )

        if (command.sources.gtu) {
            tool(
                toolFunction = ::gtuKnowledgeSearch,
                name = "gtu_knowledge_search",
                description = "Searches verified Georgian Technical University source excerpts. Use this before making factual claims about GTU when public GTU knowledge is needed."
            )
        }

        if (command.sources.materials) {
            tool(
                toolFunction = ::uploadedMaterialsSearch,
                name = "uploaded_materials_search",
                description = "Searches the current user's selected uploaded materials and returns document inventory plus matching excerpts. Use this before answering from uploaded files."
            )
        }

        if (command.sources.web) {
            tool(
                toolFunction = ::webSearch,
                name = "web_search",
                description = "Searches allowed public web pages for current or external context. Use this for latest, time-sensitive, or web-grounded claims."
            )
        }

        if (artifactService != null) {
            tool(
                toolFunction = ::artifactCreate,
                name = "artifact_create",
                description = "Creates one downloadable artifact through the backend-controlled artifact service. Use only when the user explicitly asks to create, save, export, download, prepare, draw, plot, or generate a file. Parameters: kind is text, html, docx, or chart; content is complete Markdown content for text/html/docx or a chart prompt for chart; fileName is optional."
            )
        }
    }

    fun currentTime(): String = Instant.now().toString()

    suspend fun gtuKnowledgeSearch(query: String, maxResults: Int = 6): String {
        val normalizedQuery = query.ifBlank { latestUserText }
        val result = knowledgeSearchTool.search(normalizedQuery, maxResults.coerceIn(1, 10))
        return result.fold(
            ifLeft = { error -> "Error: GTU knowledge search failed: ${error.cause.message ?: error.cause::class.simpleName ?: "unknown error"}" },
            ifRight = { sources ->
                rememberSources(sources)
                sources.formatAsToolResult("GTU knowledge search results")
            }
        )
    }

    suspend fun uploadedMaterialsSearch(query: String, maxResults: Int = 6): String {
        val normalizedQuery = query.ifBlank { latestUserText }
        val result = userMaterialSearchTool.resolve(
            ownerUserId = command.userId,
            query = normalizedQuery,
            collectionIds = command.collectionIds,
            documentIds = command.documentIds,
            maxResults = maxResults.coerceIn(1, 10)
        )
        return result.fold(
            ifLeft = { error -> "Error: uploaded material search failed: ${error.cause.message ?: error.cause::class.simpleName ?: "unknown error"}" },
            ifRight = { context ->
                rememberSources(context.sources)
                buildString {
                    appendLine("Uploaded material search results:")
                    appendLine(context.toContextBlock())
                    appendLine(context.sources.formatAsToolResult("Matching uploaded material excerpts"))
                }.trim()
            }
        )
    }

    suspend fun webSearch(query: String): String {
        val normalizedQuery = query.ifBlank { latestUserText }
        val result = webSearchTool.search(normalizedQuery)
        return result.fold(
            ifLeft = { error -> "Error: web search failed: ${error.cause.message ?: error.cause::class.simpleName ?: "unknown error"}" },
            ifRight = { sources ->
                rememberSources(sources)
                sources.formatAsToolResult("Web search results")
            }
        )
    }

    suspend fun artifactCreate(kind: String, content: String, fileName: String? = null): String {
        val service = artifactService ?: return "Error: artifact creation is not configured."
        val normalizedKind = kind.trim().lowercase()
        val artifactKind = when (normalizedKind) {
            "text", "markdown", "md", "txt" -> ArtifactKind.TEXT
            "html", "page" -> ArtifactKind.HTML
            "docx", "word", "document" -> ArtifactKind.DOCX
            "chart", "plot", "png" -> ArtifactKind.CHART
            else -> return "Error: unsupported artifact kind '$kind'. Use text, html, docx, or chart."
        }
        val artifactContent = content.ifBlank { latestUserText }
        val intent = artifactKind.toToolIntent(content = artifactContent, fileName = fileName)
        val draft = if (intent.needsDraft) {
            ArtifactDraft.fromMarkdown(artifactContent, fallbackTitle = intent.defaultTitle())
        } else {
            null
        }

        return service.createArtifact(
            userId = command.userId,
            intent = intent,
            draft = draft
        ).fold(
            ifLeft = { error -> "Error: artifact creation failed: ${error.cause.message ?: error.cause::class.simpleName ?: "unknown error"}" },
            ifRight = { result ->
                if (result == null) {
                    "Error: artifact service did not create an artifact."
                } else {
                    rememberArtifacts(result.artifacts)
                    result.context
                }
            }
        )
    }

    fun sourcesSnapshot(): List<AgentSource> = synchronized(collectedSources) {
        collectedSources
            .distinctBy { it.url to it.snippet }
            .take(maxSources)
    }

    fun artifactsSnapshot(): List<MessageArtifact> = synchronized(collectedArtifacts) {
        collectedArtifacts.distinctBy { it.id }
    }

    private fun rememberSources(sources: List<AgentSource>) {
        synchronized(collectedSources) {
            collectedSources += sources
        }
    }

    private fun rememberArtifacts(artifacts: List<MessageArtifact>) {
        synchronized(collectedArtifacts) {
            collectedArtifacts += artifacts
        }
    }
}

private fun List<AgentSource>.formatAsToolResult(title: String): String =
    if (isEmpty()) {
        "$title:\nNo relevant results were found."
    } else {
        buildString {
            appendLine("$title:")
            this@formatAsToolResult.forEachIndexed { index, source ->
                appendLine("[${index + 1}] ${source.title}")
                appendLine("Type: ${source.sourceType}")
                appendLine("URL: ${source.url}")
                appendLine("Score: ${source.score}")
                source.documentId?.let { appendLine("Document ID: ${it.value}") }
                source.pageStart?.let { start ->
                    val end = source.pageEnd?.takeIf { it != start }
                    appendLine(if (end == null) "Page: $start" else "Pages: $start-$end")
                }
                appendLine("Excerpt: ${source.snippet}")
                appendLine()
            }
        }.trim()
    }

private fun ArtifactKind.toToolIntent(content: String, fileName: String?): ArtifactIntent {
    val default = when (this) {
        ArtifactKind.TEXT -> "assistant-output.md"
        ArtifactKind.HTML -> "assistant-page.html"
        ArtifactKind.DOCX -> "assistant-document.docx"
        ArtifactKind.CHART -> "assistant-chart.png"
    }
    val extension = when (this) {
        ArtifactKind.TEXT -> ".md"
        ArtifactKind.HTML -> ".html"
        ArtifactKind.DOCX -> ".docx"
        ArtifactKind.CHART -> ".png"
    }
    val resolvedFileName = normalizeArtifactFileName(fileName, default, extension)
    return ArtifactIntent(
        kind = this,
        fileName = resolvedFileName,
        contentType = when (this) {
            ArtifactKind.TEXT -> "text/markdown; charset=utf-8"
            ArtifactKind.HTML -> "text/html; charset=utf-8"
            ArtifactKind.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ArtifactKind.CHART -> "image/png"
        },
        artifactPath = when (this) {
            ArtifactKind.DOCX -> "assistant-document.docx"
            ArtifactKind.CHART -> "assistant-chart.png"
            ArtifactKind.TEXT, ArtifactKind.HTML -> ""
        },
        originalPrompt = content
    )
}

private fun normalizeArtifactFileName(raw: String?, fallback: String, extension: String): String {
    val candidate = raw
        ?.trim()
        ?.take(120)
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { "/" !in it && "\\" !in it && ".." !in it && it.none(Char::isISOControl) }
        ?: fallback
    return if (candidate.endsWith(extension, ignoreCase = true)) candidate else candidate.substringBeforeLast('.') + extension
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

private fun UserMaterialContext?.toContextBlock(): String {
    if (this == null) return ""

    return buildString {
        appendLine()
        appendLine()
        appendLine("Uploaded material inventory:")
        if (documents.isEmpty()) {
            appendLine("No uploaded materials match the selected filters.")
        } else {
            documents.take(MAX_MATERIAL_CONTEXT_DOCUMENTS).forEachIndexed { index, document ->
                append("- [M${index + 1}] ${document.title}")
                append(" (file: ${document.originalFileName}; id: ${document.id.value}; status: ${document.ingestionStatus.name})")
                document.ingestionError?.takeIf(String::isNotBlank)?.let { error ->
                    append(" error: ${error.take(MAX_MATERIAL_ERROR_CONTEXT_LENGTH)}")
                }
                appendLine()
                if (document.outline.isNotEmpty()) {
                    appendLine("  Document outline:")
                    document.outline.take(MAX_MATERIAL_OUTLINE_CONTEXT_ENTRIES).forEach { entry ->
                        val indent = "  ".repeat((entry.level ?: 1).coerceIn(1, 4))
                        appendLine("$indent- ${entry.title}")
                    }
                    if (document.outline.size > MAX_MATERIAL_OUTLINE_CONTEXT_ENTRIES) {
                        appendLine("  - ${document.outline.size - MAX_MATERIAL_OUTLINE_CONTEXT_ENTRIES} more outline entries are not shown.")
                    }
                }
            }
            if (documents.size > MAX_MATERIAL_CONTEXT_DOCUMENTS) {
                appendLine("- ${documents.size - MAX_MATERIAL_CONTEXT_DOCUMENTS} more uploaded materials are not shown.")
            }
        }

        val readyCount = documents.count { it.ingestionStatus.name == "READY" }
        val notReadyCount = documents.size - readyCount
        appendLine("READY uploaded materials can be used for content answers. Non-READY materials are listed only as upload/status metadata.")
        if (notReadyCount > 0) {
            appendLine("$notReadyCount selected/uploaded material(s) are not READY and must not be used for factual content claims.")
        }
        if (searchError != null) {
            appendLine(searchError)
        } else if (documents.isNotEmpty() && sources.isEmpty()) {
            appendLine("No relevant excerpts were found in READY uploaded materials for the latest message. You may answer questions about which uploaded materials are available from this inventory, but do not make detailed claims about their contents without excerpts.")
        }
    }
}

private const val MAX_MATERIAL_CONTEXT_DOCUMENTS = 12
private const val MAX_MATERIAL_OUTLINE_CONTEXT_ENTRIES = 50
private const val MAX_MATERIAL_ERROR_CONTEXT_LENGTH = 240

private fun ArtifactIntent.defaultTitle(): String =
    when (kind) {
        ArtifactKind.DOCX -> "GTU Document"
        ArtifactKind.HTML -> "GTU Page"
        ArtifactKind.TEXT -> "GTU Assistant Output"
        ArtifactKind.CHART -> "Generated Chart"
    }

private fun ChatSources.agentSystemPrompt(): String =
    AgentGenerateMessagePortImpl.SYSTEM_PROMPT +
        "\n\nSelected source policy:\n" + promptRules() +
        """

        Tool-calling rules:
        - You have real function tools. Use them whenever the answer needs source-grounded factual information, uploaded-material content, current/web information, exact current time, or file creation.
        - Do not claim that you cannot use tools. If the user asks about capabilities, describe the available application tools naturally.
        - For GTU factual claims, call gtu_knowledge_search when it is available unless the answer is already fully supported by prior conversation.
        - For uploaded files, call uploaded_materials_search before answering from uploaded materials.
        - For latest/current/web-grounded claims, call web_search when it is available.
        - If a needed source tool is unavailable because the user did not select that source, say that the selected sources do not allow verifying that information.
        - If a tool returns no relevant evidence, state that the selected sources did not confirm the answer instead of inventing details.
        - Call artifact_create only when the user explicitly asks to create, save, export, download, prepare, draw, plot, or generate a file. Provide complete content/brief to the tool.
        - When independent tool calls are needed, request them in the same tool-calling step.
        - After tool results, answer the user directly and include relevant source URLs or artifact links returned by tools.
        """.trimIndent()

private fun ChatSources.promptRules(): String {
    val enabled = listOfNotNull(
        "verified GTU source context".takeIf { gtu },
        "uploaded user materials".takeIf { materials },
        "web search context".takeIf { web }
    ).joinToString(separator = ", ")
    val disabled = listOfNotNull(
        "GTU public sources".takeIf { !gtu },
        "uploaded user materials".takeIf { !materials },
        "web search context".takeIf { !web }
    ).joinToString(separator = ", ")
    val fallback = if (materials && !gtu && !web) {
        " If uploaded materials do not contain enough information, say that the uploaded materials do not contain enough information to answer."
    } else {
        " If the allowed sources are insufficient, say so."
    }

    val restriction = if (disabled.isBlank()) {
        "Do not use general knowledge for factual claims."
    } else {
        "Do not use $disabled or general knowledge for factual claims."
    }

    return "Use only these selected sources: $enabled. $restriction$fallback"
}

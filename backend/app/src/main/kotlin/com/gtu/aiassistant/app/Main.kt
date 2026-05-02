package com.gtu.aiassistant.app

import com.gtu.aiassistant.application.chat.ContinueChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.CreateChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.DeleteChatUseCaseImpl
import com.gtu.aiassistant.application.chat.ListChatsUseCaseImpl
import com.gtu.aiassistant.application.user.LoginInUseCaseImpl
import com.gtu.aiassistant.application.user.RegisterUserUseCaseImpl
import com.gtu.aiassistant.infrastructure.ai.AgentGenerateMessagePortImpl
import com.gtu.aiassistant.infrastructure.ai.AiConfig
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingConfig
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingMode
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPortFactory
import com.gtu.aiassistant.infrastructure.ai.tools.GtuKnowledgeSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.GtuWebSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.WebSearchConfig
import com.gtu.aiassistant.infrastructure.ai.tools.WebSearchMode
import com.gtu.aiassistant.app.memory.InMemoryDeleteChatPort
import com.gtu.aiassistant.app.memory.InMemoryExistsUserPort
import com.gtu.aiassistant.app.memory.InMemoryFindChatPort
import com.gtu.aiassistant.app.memory.InMemoryFindUserPort
import com.gtu.aiassistant.app.memory.InMemoryGenerateMessagePort
import com.gtu.aiassistant.app.memory.InMemorySaveChatPort
import com.gtu.aiassistant.app.memory.InMemorySaveUserPort
import com.gtu.aiassistant.app.memory.InMemoryState
import com.gtu.aiassistant.app.memory.InMemoryUpdateUserPort
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase
import com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase
import com.gtu.aiassistant.domain.chat.port.output.DeleteChatPort
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.knowledge.port.output.SearchKnowledgePort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.user.port.input.LoginInUseCase
import com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase
import com.gtu.aiassistant.domain.user.port.output.ExistsUserPort
import com.gtu.aiassistant.domain.user.port.output.FindUserPort
import com.gtu.aiassistant.domain.user.port.output.HashPasswordPort
import com.gtu.aiassistant.domain.user.port.output.IssueJwtPort
import com.gtu.aiassistant.domain.user.port.output.SaveUserPort
import com.gtu.aiassistant.domain.user.port.output.UpdateUserPort
import com.gtu.aiassistant.domain.user.port.output.VerifyPasswordPort
import com.gtu.aiassistant.infrastructure.persistence.chat.DeleteChatPortImpl
import com.gtu.aiassistant.infrastructure.persistence.chat.FindChatPortImpl
import com.gtu.aiassistant.infrastructure.persistence.chat.SaveChatPortImpl
import com.gtu.aiassistant.infrastructure.persistence.config.DatabaseFactory
import com.gtu.aiassistant.infrastructure.persistence.config.PersistenceConfig
import com.gtu.aiassistant.infrastructure.persistence.knowledge.SearchKnowledgePortImpl
import com.gtu.aiassistant.infrastructure.persistence.knowledge.UpsertKnowledgeDocumentPortImpl
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import com.gtu.aiassistant.infrastructure.persistence.user.ExistsUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.FindUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.SaveUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.UpdateUserPortImpl
import com.gtu.aiassistant.infrastructure.security.Argon2HashPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.Argon2VerifyPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.IssueJwtPortImpl
import com.gtu.aiassistant.infrastructure.security.JwtConfig
import com.gtu.aiassistant.infrastructure.knowledge.DisabledSearchKnowledgePort
import com.gtu.aiassistant.infrastructure.knowledge.DisabledUpsertKnowledgeDocumentPort
import com.gtu.aiassistant.infrastructure.knowledge.GtuPageFetcher
import com.gtu.aiassistant.infrastructure.knowledge.GtuUrlPolicy
import com.gtu.aiassistant.infrastructure.knowledge.KnowledgeDocumentBuilder
import com.gtu.aiassistant.infrastructure.knowledge.KnowledgeIngestionConfig
import com.gtu.aiassistant.infrastructure.knowledge.KnowledgeIngestionScheduler
import com.gtu.aiassistant.infrastructure.knowledge.KnowledgeIngestionService
import com.gtu.aiassistant.presentation.ApiDependencies
import com.gtu.aiassistant.presentation.configureApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.time.ZoneId

fun main() {
    val runtimeConfig = RuntimeConfig.fromEnvironment()
    val koin = startKoin {
        modules(appModule(runtimeConfig))
    }.koin

    koin.get<KnowledgeIngestionScheduler>().start()

    embeddedServer(
        factory = Netty,
        host = runtimeConfig.host,
        port = runtimeConfig.port
    ) {
        configureApi(
            dependencies = ApiDependencies(
                registerUserUseCase = koin.get(),
                loginInUseCase = koin.get(),
                createChatWithAgentUseCase = koin.get(),
                continueChatWithAgentUseCase = koin.get(),
                listChatsUseCase = koin.get(),
                deleteChatUseCase = koin.get(),
                jwtSecret = runtimeConfig.jwtSecret,
                jwtIssuer = runtimeConfig.jwtIssuer
            )
        )
    }.start(wait = true)
}

private fun appModule(
    runtimeConfig: RuntimeConfig
) = module {
    single<HttpClient> { HttpClient(CIO) }
    single {
        EmbeddingConfig(
            mode = runtimeConfig.embeddingMode,
            apiKey = runtimeConfig.embeddingApiKey,
            baseUrl = runtimeConfig.embeddingBaseUrl,
            model = runtimeConfig.embeddingModel,
            dimensions = runtimeConfig.embeddingDimensions
        )
    }
    single<EmbeddingPort> { EmbeddingPortFactory.create(get(), get()) }
    single {
        KnowledgeIngestionConfig(
            enabled = runtimeConfig.ragEnabled,
            schedulerEnabled = runtimeConfig.crawlerEnabled,
            ingestOnStartup = runtimeConfig.ragIngestOnStartup,
            sitemapUrl = runtimeConfig.ragSitemapUrl,
            robotsUrl = runtimeConfig.ragRobotsUrl,
            allowedDomains = runtimeConfig.ragAllowedDomains,
            maxPagesPerRun = runtimeConfig.ragMaxPagesPerRun,
            maxContentCharacters = runtimeConfig.ragMaxContentCharacters,
            refreshHour = runtimeConfig.ragRefreshHour,
            zoneId = runtimeConfig.ragZoneId
        )
    }
    single { GtuUrlPolicy(get<KnowledgeIngestionConfig>().allowedDomains) }
    single { GtuPageFetcher(get(), get()) }
    single { KnowledgeDocumentBuilder(get()) }
    single { KnowledgeIngestionService(get(), get(), get(), get(), get()) }
    single { KnowledgeIngestionScheduler(get(), get()) }
    single {
        WebSearchConfig(
            mode = runtimeConfig.webSearchMode,
            allowedDomains = runtimeConfig.ragAllowedDomains,
            maxResults = runtimeConfig.webSearchMaxResults
        )
    }
    single { GtuKnowledgeSearchTool(get()) }
    single { GtuWebSearchTool(get(), get(), get()) }

    when (runtimeConfig.aiMode) {
        AiMode.MEMORY -> {
            single<GenerateMessagePort> { InMemoryGenerateMessagePort() }
        }

        AiMode.OPENAI -> {
            single {
                AiConfig(
                    apiKey = runtimeConfig.aiApiKey
                        ?: error("APP_AI_API_KEY or OPENAI_API_KEY must be set when APP_AI_MODE=openai"),
                    baseUrl = runtimeConfig.aiBaseUrl,
                    model = runtimeConfig.aiModel
                )
            }
            single<GenerateMessagePort> { AgentGenerateMessagePortImpl.create(get(), get(), get()) }
        }
    }

    single {
        JwtConfig(
            secret = runtimeConfig.jwtSecret,
            issuer = runtimeConfig.jwtIssuer,
            ttlSeconds = runtimeConfig.jwtTtlSeconds
        )
    }
    single<HashPasswordPort> { Argon2HashPasswordPortImpl() }
    single<VerifyPasswordPort> { Argon2VerifyPasswordPortImpl() }
    single<IssueJwtPort> { IssueJwtPortImpl(get()) }

    when (runtimeConfig.persistenceMode) {
        PersistenceMode.MEMORY -> {
            single { InMemoryState() }

            single<FindUserPort> { InMemoryFindUserPort(get()) }
            single<ExistsUserPort> { InMemoryExistsUserPort(get()) }
            single<SaveUserPort> { InMemorySaveUserPort(get()) }
            single<UpdateUserPort> { InMemoryUpdateUserPort(get()) }

            single<FindChatPort> { InMemoryFindChatPort(get()) }
            single<SaveChatPort> { InMemorySaveChatPort(get()) }
            single<DeleteChatPort> { InMemoryDeleteChatPort(get()) }
            single<SearchKnowledgePort> { DisabledSearchKnowledgePort() }
            single<UpsertKnowledgeDocumentPort> { DisabledUpsertKnowledgeDocumentPort() }
        }

        PersistenceMode.POSTGRES -> {
            single {
                PersistenceConfig(
                    jdbcUrl = runtimeConfig.jdbcUrl,
                    username = runtimeConfig.jdbcUsername,
                    password = runtimeConfig.jdbcPassword
                )
            }
            single<JdbcPersistenceExecutor> { DatabaseFactory.createJdbcPersistenceExecutor(get()) }

            single<FindUserPort> { FindUserPortImpl(get()) }
            single<ExistsUserPort> { ExistsUserPortImpl(get()) }
            single<SaveUserPort> { SaveUserPortImpl(get()) }
            single<UpdateUserPort> { UpdateUserPortImpl(get()) }

            single<FindChatPort> { FindChatPortImpl(get()) }
            single<SaveChatPort> { SaveChatPortImpl(get()) }
            single<DeleteChatPort> { DeleteChatPortImpl(get()) }
            single<SearchKnowledgePort> {
                if (runtimeConfig.ragEnabled) {
                    SearchKnowledgePortImpl(get(), get())
                } else {
                    DisabledSearchKnowledgePort()
                }
            }
            single<UpsertKnowledgeDocumentPort> {
                if (runtimeConfig.ragEnabled) {
                    UpsertKnowledgeDocumentPortImpl(get())
                } else {
                    DisabledUpsertKnowledgeDocumentPort()
                }
            }
        }
    }

    single<RegisterUserUseCase> { RegisterUserUseCaseImpl(get(), get(), get()) }
    single<LoginInUseCase> { LoginInUseCaseImpl(get(), get(), get()) }
    single<CreateChatWithAgentUseCase> { CreateChatWithAgentUseCaseImpl(get(), get()) }
    single<ContinueChatWithAgentUseCase> { ContinueChatWithAgentUseCaseImpl(get(), get(), get()) }
    single<ListChatsUseCase> { ListChatsUseCaseImpl(get()) }
    single<DeleteChatUseCase> { DeleteChatUseCaseImpl(get(), get()) }
}

private data class RuntimeConfig(
    val host: String,
    val port: Int,
    val aiMode: AiMode,
    val aiApiKey: String?,
    val aiBaseUrl: String,
    val aiModel: String,
    val persistenceMode: PersistenceMode,
    val jdbcUrl: String,
    val jdbcUsername: String,
    val jdbcPassword: String,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtTtlSeconds: Long,
    val ragEnabled: Boolean,
    val crawlerEnabled: Boolean,
    val ragIngestOnStartup: Boolean,
    val ragSitemapUrl: String,
    val ragRobotsUrl: String,
    val ragAllowedDomains: Set<String>,
    val ragMaxPagesPerRun: Int,
    val ragMaxContentCharacters: Int,
    val ragRefreshHour: Int,
    val ragZoneId: ZoneId,
    val embeddingMode: EmbeddingMode,
    val embeddingApiKey: String?,
    val embeddingBaseUrl: String,
    val embeddingModel: String,
    val embeddingDimensions: Int,
    val webSearchMode: WebSearchMode,
    val webSearchMaxResults: Int
) {
    companion object {
        fun fromEnvironment(): RuntimeConfig {
            val aiBaseUrl = System.getenv("APP_AI_BASE_URL")
                ?: System.getenv("OPENAI_BASE_URL")
                ?: AiConfig.DEFAULT_OLLAMA_OPENAI_BASE_URL
            val aiApiKey = System.getenv("APP_AI_API_KEY")
                ?: System.getenv("OPENAI_API_KEY")
                ?: AiConfig.DEFAULT_OLLAMA_API_KEY
            val ragAllowedDomains = System.getenv("APP_RAG_ALLOWED_DOMAINS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: KnowledgeIngestionConfig.DEFAULT_ALLOWED_DOMAINS

            return RuntimeConfig(
                host = System.getenv("APP_HOST") ?: "0.0.0.0",
                port = (System.getenv("APP_PORT") ?: "8080").toInt(),
                aiMode = AiMode.from(System.getenv("APP_AI_MODE")),
                aiApiKey = aiApiKey,
                aiBaseUrl = aiBaseUrl,
                aiModel = System.getenv("APP_AI_MODEL") ?: AiConfig.GPT_OSS_20B,
                persistenceMode = PersistenceMode.from(System.getenv("APP_PERSISTENCE_MODE")),
                jdbcUrl = System.getenv("APP_DB_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/gtu_ai_assistant",
                jdbcUsername = System.getenv("APP_DB_USERNAME") ?: "postgres",
                jdbcPassword = System.getenv("APP_DB_PASSWORD") ?: "postgres",
                jwtSecret = System.getenv("APP_JWT_SECRET") ?: "local-dev-jwt-secret",
                jwtIssuer = System.getenv("APP_JWT_ISSUER") ?: "gtu-ai-assistant",
                jwtTtlSeconds = (System.getenv("APP_JWT_TTL_SECONDS") ?: "86400").toLong(),
                ragEnabled = System.getenv("APP_RAG_ENABLED").toBoolean(default = true),
                crawlerEnabled = System.getenv("APP_CRAWLER_ENABLED").toBoolean(default = false),
                ragIngestOnStartup = System.getenv("APP_RAG_INGEST_ON_STARTUP").toBoolean(default = false),
                ragSitemapUrl = System.getenv("APP_RAG_SITEMAP_URL") ?: "https://gtu.ge/sitemap.xml",
                ragRobotsUrl = System.getenv("APP_RAG_ROBOTS_URL") ?: "https://gtu.ge/robots.txt",
                ragAllowedDomains = ragAllowedDomains,
                ragMaxPagesPerRun = (System.getenv("APP_RAG_MAX_PAGES_PER_RUN") ?: "250").toInt(),
                ragMaxContentCharacters = (System.getenv("APP_RAG_MAX_CONTENT_CHARACTERS") ?: "250000").toInt(),
                ragRefreshHour = (System.getenv("APP_RAG_REFRESH_HOUR") ?: "3").toInt().coerceIn(0, 23),
                ragZoneId = ZoneId.of(System.getenv("APP_RAG_TIMEZONE") ?: "Asia/Tbilisi"),
                embeddingMode = EmbeddingMode.from(System.getenv("APP_EMBEDDING_MODE")),
                embeddingApiKey = System.getenv("APP_EMBEDDING_API_KEY") ?: System.getenv("OPENAI_API_KEY"),
                embeddingBaseUrl = System.getenv("APP_EMBEDDING_BASE_URL") ?: aiBaseUrl,
                embeddingModel = System.getenv("APP_EMBEDDING_MODEL") ?: "text-embedding-3-small",
                embeddingDimensions = (System.getenv("APP_EMBEDDING_DIMENSIONS") ?: "384").toInt(),
                webSearchMode = WebSearchMode.from(System.getenv("APP_WEB_SEARCH_MODE")),
                webSearchMaxResults = (System.getenv("APP_WEB_SEARCH_MAX_RESULTS") ?: "6").toInt()
            )
        }
    }
}

private fun String?.toBoolean(default: Boolean): Boolean =
    when (this?.lowercase()) {
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        else -> default
    }

private enum class AiMode {
    MEMORY,
    OPENAI;

    companion object {
        fun from(raw: String?): AiMode =
            when (raw?.lowercase()) {
                "memory" -> MEMORY
                "openai" -> OPENAI
                else -> OPENAI
            }
    }
}

private enum class PersistenceMode {
    MEMORY,
    POSTGRES;

    companion object {
        fun from(raw: String?): PersistenceMode =
            when (raw?.lowercase()) {
                "postgres" -> POSTGRES
                else -> MEMORY
            }
    }
}

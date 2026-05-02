package com.gtu.aiassistant.app

import com.gtu.aiassistant.application.chat.ContinueChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.CreateChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.DeleteChatUseCaseImpl
import com.gtu.aiassistant.application.chat.ListChatsUseCaseImpl
import com.gtu.aiassistant.application.user.LoginInUseCaseImpl
import com.gtu.aiassistant.application.user.RegisterUserUseCaseImpl
import com.gtu.aiassistant.infrastructure.ai.AiConfig
import com.gtu.aiassistant.infrastructure.ai.GenerateMessagePortImpl
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
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import com.gtu.aiassistant.infrastructure.persistence.user.ExistsUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.FindUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.SaveUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.UpdateUserPortImpl
import com.gtu.aiassistant.infrastructure.security.Argon2HashPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.Argon2VerifyPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.IssueJwtPortImpl
import com.gtu.aiassistant.infrastructure.security.JwtConfig
import com.gtu.aiassistant.presentation.ApiDependencies
import com.gtu.aiassistant.presentation.configureApi
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() {
    val runtimeConfig = RuntimeConfig.fromEnvironment()
    val koin = startKoin {
        modules(appModule(runtimeConfig))
    }.koin

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
            single<GenerateMessagePort> { GenerateMessagePortImpl.create(get()) }
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
    val jwtTtlSeconds: Long
) {
    companion object {
        fun fromEnvironment(): RuntimeConfig =
            RuntimeConfig(
                host = System.getenv("APP_HOST") ?: "0.0.0.0",
                port = (System.getenv("APP_PORT") ?: "8080").toInt(),
                aiMode = AiMode.from(System.getenv("APP_AI_MODE")),
                aiApiKey = System.getenv("APP_AI_API_KEY")
                    ?: System.getenv("OPENAI_API_KEY")
                    ?: AiConfig.DEFAULT_OLLAMA_API_KEY,
                aiBaseUrl = System.getenv("APP_AI_BASE_URL")
                    ?: System.getenv("OPENAI_BASE_URL")
                    ?: AiConfig.DEFAULT_OLLAMA_OPENAI_BASE_URL,
                aiModel = System.getenv("APP_AI_MODEL") ?: AiConfig.GPT_OSS_20B,
                persistenceMode = PersistenceMode.from(System.getenv("APP_PERSISTENCE_MODE")),
                jdbcUrl = System.getenv("APP_DB_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/gtu_ai_assistant",
                jdbcUsername = System.getenv("APP_DB_USERNAME") ?: "postgres",
                jdbcPassword = System.getenv("APP_DB_PASSWORD") ?: "postgres",
                jwtSecret = System.getenv("APP_JWT_SECRET") ?: "local-dev-jwt-secret",
                jwtIssuer = System.getenv("APP_JWT_ISSUER") ?: "gtu-ai-assistant",
                jwtTtlSeconds = (System.getenv("APP_JWT_TTL_SECONDS") ?: "86400").toLong()
            )
    }
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

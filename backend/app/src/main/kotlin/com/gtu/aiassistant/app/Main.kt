package com.gtu.aiassistant.app

import com.gtu.aiassistant.application.chat.ContinueChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.CreateChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.DeleteChatUseCaseImpl
import com.gtu.aiassistant.application.chat.ListChatsUseCaseImpl
import com.gtu.aiassistant.application.materials.DeleteMaterialUseCaseImpl
import com.gtu.aiassistant.application.materials.CreateMaterialCollectionUseCaseImpl
import com.gtu.aiassistant.application.materials.DeleteMaterialCollectionUseCaseImpl
import com.gtu.aiassistant.application.materials.DownloadMaterialUseCaseImpl
import com.gtu.aiassistant.application.materials.ListMaterialCollectionsUseCaseImpl
import com.gtu.aiassistant.application.materials.ListMaterialsUseCaseImpl
import com.gtu.aiassistant.application.materials.MaterialChunkBuilder
import com.gtu.aiassistant.application.materials.MaterialIngestionWorker
import com.gtu.aiassistant.application.materials.MaterialTextExtractionConfig
import com.gtu.aiassistant.application.materials.MaterialTextExtractionService
import com.gtu.aiassistant.application.materials.UploadMaterialUseCaseImpl
import com.gtu.aiassistant.application.user.LoginInUseCaseImpl
import com.gtu.aiassistant.application.user.RegisterUserUseCaseImpl
import com.gtu.aiassistant.app.materials.MaterialIngestionScheduler
import com.gtu.aiassistant.app.materials.MaterialIngestionSchedulerConfig
import com.gtu.aiassistant.app.memory.InMemoryDeleteMaterialChunksPort
import com.gtu.aiassistant.app.memory.InMemoryDeleteMaterialCollectionPort
import com.gtu.aiassistant.app.memory.InMemoryDeleteMaterialDocumentPort
import com.gtu.aiassistant.app.memory.InMemoryFindGeneratedArtifactPort
import com.gtu.aiassistant.app.memory.InMemoryFindMaterialCollectionPort
import com.gtu.aiassistant.app.memory.InMemoryFindMaterialDocumentPort
import com.gtu.aiassistant.app.memory.InMemoryGeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.app.memory.InMemoryReadGeneratedArtifactContentPort
import com.gtu.aiassistant.app.memory.InMemoryReplaceMaterialDocumentChunksPort
import com.gtu.aiassistant.app.memory.InMemorySaveMaterialChunksPort
import com.gtu.aiassistant.app.memory.InMemorySearchUserMaterialsPort
import com.gtu.aiassistant.app.memory.InMemoryStoreGeneratedArtifactPort
import com.gtu.aiassistant.infrastructure.ai.AgentGenerateMessagePortImpl
import com.gtu.aiassistant.infrastructure.ai.AgentArtifactService
import com.gtu.aiassistant.infrastructure.ai.AgentSpaceClient
import com.gtu.aiassistant.infrastructure.ai.AgentSpaceConfig
import com.gtu.aiassistant.infrastructure.ai.AiConfig
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingConfig
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingMode
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPortFactory
import com.gtu.aiassistant.infrastructure.ai.embedding.MaterialEmbeddingPortImpl
import com.gtu.aiassistant.infrastructure.ai.tools.GtuKnowledgeSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.GtuWebSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.UserMaterialSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.WebSearchConfig
import com.gtu.aiassistant.infrastructure.ai.tools.WebSearchMode
import com.gtu.aiassistant.app.memory.InMemoryDeleteChatPort
import com.gtu.aiassistant.app.memory.InMemoryExistsUserPort
import com.gtu.aiassistant.app.memory.InMemoryFindChatPort
import com.gtu.aiassistant.app.memory.InMemoryFindUserPort
import com.gtu.aiassistant.app.memory.InMemoryGenerateMessagePort
import com.gtu.aiassistant.app.memory.InMemorySaveMaterialCollectionPort
import com.gtu.aiassistant.app.memory.InMemorySaveMaterialDocumentPort
import com.gtu.aiassistant.app.memory.InMemorySaveChatPort
import com.gtu.aiassistant.app.memory.InMemorySaveUserPort
import com.gtu.aiassistant.app.memory.InMemoryState
import com.gtu.aiassistant.app.memory.InMemoryUpdateUserPort
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase
import com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase
import com.gtu.aiassistant.domain.artifacts.port.output.FindGeneratedArtifactPort
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.domain.artifacts.port.output.ReadGeneratedArtifactContentPort
import com.gtu.aiassistant.domain.artifacts.port.output.StoreGeneratedArtifactPort
import com.gtu.aiassistant.domain.chat.port.output.DeleteChatPort
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.knowledge.port.output.SaveKnowledgeIngestionRunPort
import com.gtu.aiassistant.domain.knowledge.port.output.SearchKnowledgePort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeSourcesPort
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialUseCase
import com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionUseCase
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionUseCase
import com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialUseCase
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsUseCase
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialsUseCase
import com.gtu.aiassistant.domain.materials.port.input.UploadMaterialUseCase
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlinePort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialEmbeddingPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialObjectStoragePort
import com.gtu.aiassistant.domain.materials.port.output.ReplaceMaterialDocumentChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.SearchUserMaterialsPort
import com.gtu.aiassistant.domain.user.port.input.LoginInUseCase
import com.gtu.aiassistant.domain.user.port.input.RegisterUserCommand
import com.gtu.aiassistant.domain.user.port.input.RegisterUserError
import com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPassword
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
import com.gtu.aiassistant.infrastructure.persistence.artifacts.FindGeneratedArtifactPortImpl
import com.gtu.aiassistant.infrastructure.persistence.artifacts.ReadGeneratedArtifactContentPortImpl
import com.gtu.aiassistant.infrastructure.persistence.artifacts.StoreGeneratedArtifactPortImpl
import com.gtu.aiassistant.infrastructure.persistence.config.DatabaseFactory
import com.gtu.aiassistant.infrastructure.persistence.config.PersistenceConfig
import com.gtu.aiassistant.infrastructure.persistence.embedding.DisabledEmbeddingProfileReindexService
import com.gtu.aiassistant.infrastructure.persistence.embedding.EmbeddingProfileReindexReport
import com.gtu.aiassistant.infrastructure.persistence.embedding.EmbeddingProfileReindexService
import com.gtu.aiassistant.infrastructure.persistence.embedding.PostgresEmbeddingProfileReindexService
import com.gtu.aiassistant.infrastructure.persistence.knowledge.SaveKnowledgeIngestionRunPortImpl
import com.gtu.aiassistant.infrastructure.persistence.knowledge.SearchKnowledgePortImpl
import com.gtu.aiassistant.infrastructure.persistence.knowledge.UpsertKnowledgeDocumentPortImpl
import com.gtu.aiassistant.infrastructure.persistence.knowledge.UpsertKnowledgeSourcesPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.DeleteMaterialCollectionPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.DeleteMaterialChunksPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.DeleteMaterialDocumentPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.FindMaterialCollectionPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.FindMaterialDocumentPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.FindMaterialDocumentOutlinePortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.FindMaterialDocumentSectionsPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.ReplaceMaterialDocumentChunksPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.SaveMaterialCollectionPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.SaveMaterialChunksPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.SaveMaterialDocumentPortImpl
import com.gtu.aiassistant.infrastructure.persistence.materials.SearchUserMaterialsPortImpl
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import com.gtu.aiassistant.infrastructure.persistence.user.ExistsUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.FindUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.SaveUserPortImpl
import com.gtu.aiassistant.infrastructure.persistence.user.UpdateUserPortImpl
import com.gtu.aiassistant.infrastructure.ocr.TesseractMaterialOcrPortImpl
import com.gtu.aiassistant.infrastructure.ocr.TesseractOcrConfig
import com.gtu.aiassistant.infrastructure.security.Argon2HashPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.Argon2VerifyPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.IssueJwtPortImpl
import com.gtu.aiassistant.infrastructure.security.JwtConfig
import com.gtu.aiassistant.infrastructure.storage.LocalMaterialObjectStoragePort
import com.gtu.aiassistant.infrastructure.storage.LocalGeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.infrastructure.storage.MinioGeneratedArtifactObjectStorageFactory
import com.gtu.aiassistant.infrastructure.storage.MinioMaterialObjectStorageConfig
import com.gtu.aiassistant.infrastructure.storage.MinioMaterialObjectStorageFactory
import com.gtu.aiassistant.infrastructure.knowledge.DisabledSaveKnowledgeIngestionRunPort
import com.gtu.aiassistant.infrastructure.knowledge.DisabledSearchKnowledgePort
import com.gtu.aiassistant.infrastructure.knowledge.DisabledUpsertKnowledgeDocumentPort
import com.gtu.aiassistant.infrastructure.knowledge.DisabledUpsertKnowledgeSourcesPort
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
import java.nio.file.Path
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() {
    val runtimeConfig = RuntimeConfig.fromEnvironment()
    val koin = startKoin {
        modules(appModule(runtimeConfig))
    }.koin

    seedDefaultUser(koin.get())
    val embeddingProfileReport = syncEmbeddingProfile(koin.get())
    if (embeddingProfileReport.changed && runtimeConfig.ragEnabled) {
        reindexKnowledgeProfile(koin.get())
    }

    koin.get<KnowledgeIngestionScheduler>().start()
    koin.get<MaterialIngestionScheduler>().start()

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
                uploadMaterialUseCase = koin.get(),
                listMaterialsUseCase = koin.get(),
                downloadMaterialUseCase = koin.get(),
                deleteMaterialUseCase = koin.get(),
                createMaterialCollectionUseCase = koin.get(),
                listMaterialCollectionsUseCase = koin.get(),
                deleteMaterialCollectionUseCase = koin.get(),
                readGeneratedArtifactContentPort = koin.get(),
                jwtSecret = runtimeConfig.jwtSecret,
                jwtIssuer = runtimeConfig.jwtIssuer
            )
        )
    }.start(wait = true)
}

private fun syncEmbeddingProfile(reindexService: EmbeddingProfileReindexService): EmbeddingProfileReindexReport =
    runBlocking {
        reindexService.sync().fold(
            ifLeft = { error ->
                error("Failed to synchronize embedding profile: $error")
            },
            ifRight = { report ->
                if (report.changed) {
                    println(
                        "Embedding profile changed from ${report.previousFingerprint ?: "<none>"} " +
                            "to ${report.currentFingerprint}; search indexes were reset for reindexing."
                    )
                }
                report
            }
        )
    }

private fun reindexKnowledgeProfile(ingestionService: KnowledgeIngestionService) {
    runBlocking {
        ingestionService.ingestOnce().fold(
            ifLeft = { error ->
                error("Failed to reindex knowledge after embedding profile change: $error")
            },
            ifRight = { report ->
                println(
                    "Knowledge reindexed after embedding profile change: " +
                        "fetched=${report.pagesFetched}, changed=${report.pagesChanged}, failed=${report.pagesFailed}."
                )
            }
        )
    }
}

private fun seedDefaultUser(registerUserUseCase: RegisterUserUseCase) {
    runBlocking {
        val command = RegisterUserCommand(
            name = UserName.create("Admin").getOrNull()!!,
            lastName = UserLastName.create("User").getOrNull()!!,
            email = UserEmail.create("admin@gmail.com").getOrNull()!!,
            password = UserPassword.create("admin123!").getOrNull()!!
        )

        registerUserUseCase(command).fold(
            ifLeft = { error ->
                if (error != RegisterUserError.EmailAlreadyTaken) {
                    error("Failed to seed default user: $error")
                }
            },
            ifRight = {}
        )
    }
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
    single<MaterialEmbeddingPort> { MaterialEmbeddingPortImpl(get()) }
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
    single { KnowledgeIngestionService(get(), get(), get(), get(), get(), get(), get()) }
    single { KnowledgeIngestionScheduler(get(), get()) }
    single {
        WebSearchConfig(
            mode = runtimeConfig.webSearchMode,
            allowedDomains = runtimeConfig.ragAllowedDomains,
            maxResults = runtimeConfig.webSearchMaxResults
        )
    }
    single { GtuKnowledgeSearchTool(get()) }
    single { UserMaterialSearchTool(get(), get(), get(), get()) }
    single { GtuWebSearchTool(get(), get(), get()) }

    when (runtimeConfig.aiMode) {
        AiMode.MEMORY -> {
            single<GenerateMessagePort> { InMemoryGenerateMessagePort() }
        }

        AiMode.OPENAI -> {
            single {
                AiConfig(
                    apiKey = runtimeConfig.aiApiKey
                        ?: error("APP_AI_API_KEYS, APP_AI_API_KEY, or OPENAI_API_KEY must be set when APP_AI_MODE=openai"),
                    apiKeys = runtimeConfig.aiApiKeys,
                    baseUrl = runtimeConfig.aiBaseUrl,
                    model = runtimeConfig.aiModel
                )
            }
            single<GenerateMessagePort> { AgentGenerateMessagePortImpl.create(get(), get(), get(), get(), get()) }
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
    single<MaterialObjectStoragePort> {
        when (runtimeConfig.fileStorageMode) {
            FileStorageMode.LOCAL -> LocalMaterialObjectStoragePort(Path.of(runtimeConfig.localStorageDir))
            FileStorageMode.MINIO -> MinioMaterialObjectStorageFactory.create(
                MinioMaterialObjectStorageConfig(
                    endpoint = runtimeConfig.minioEndpoint ?: error("APP_MINIO_ENDPOINT must be set when APP_FILE_STORAGE_MODE=minio"),
                    accessKey = runtimeConfig.minioAccessKey ?: error("APP_MINIO_ACCESS_KEY must be set when APP_FILE_STORAGE_MODE=minio"),
                    secretKey = runtimeConfig.minioSecretKey ?: error("APP_MINIO_SECRET_KEY must be set when APP_FILE_STORAGE_MODE=minio"),
                    bucket = runtimeConfig.minioBucket ?: error("APP_MINIO_BUCKET must be set when APP_FILE_STORAGE_MODE=minio"),
                    region = runtimeConfig.minioRegion
                )
            )
        }
    }
    single<GeneratedArtifactObjectStoragePort> {
        when (runtimeConfig.fileStorageMode) {
            FileStorageMode.LOCAL -> LocalGeneratedArtifactObjectStoragePort(Path.of(runtimeConfig.localStorageDir))
            FileStorageMode.MINIO -> MinioGeneratedArtifactObjectStorageFactory.create(
                MinioMaterialObjectStorageConfig(
                    endpoint = runtimeConfig.minioEndpoint ?: error("APP_MINIO_ENDPOINT must be set when APP_FILE_STORAGE_MODE=minio"),
                    accessKey = runtimeConfig.minioAccessKey ?: error("APP_MINIO_ACCESS_KEY must be set when APP_FILE_STORAGE_MODE=minio"),
                    secretKey = runtimeConfig.minioSecretKey ?: error("APP_MINIO_SECRET_KEY must be set when APP_FILE_STORAGE_MODE=minio"),
                    bucket = runtimeConfig.minioBucket ?: error("APP_MINIO_BUCKET must be set when APP_FILE_STORAGE_MODE=minio"),
                    region = runtimeConfig.minioRegion
                )
            )
        }
    }
    single {
        AgentSpaceConfig(
            baseUrl = runtimeConfig.agentSpaceBaseUrl,
            token = runtimeConfig.agentSpaceToken,
            defaultTimeoutSeconds = runtimeConfig.agentSpaceTimeoutSeconds,
            outputLimitChars = runtimeConfig.agentSpaceOutputLimitChars
        )
    }
    single { AgentSpaceClient(get(), get()) }
    single { AgentArtifactService(get(), get()) }
    single {
        MaterialTextExtractionConfig(
            ocrEnabled = runtimeConfig.materialOcrEnabled,
            pdfMinTextLayerCharacters = runtimeConfig.materialOcrMinTextChars,
            ocrRenderDpi = runtimeConfig.materialOcrRenderDpi
        )
    }
    single {
        val ocrPort: MaterialOcrPort? = if (runtimeConfig.materialOcrEnabled) {
            TesseractMaterialOcrPortImpl(
                TesseractOcrConfig(
                    command = runtimeConfig.tesseractCommand,
                    languages = runtimeConfig.tesseractLanguages,
                    timeout = Duration.ofSeconds(runtimeConfig.tesseractTimeoutSeconds)
                )
            )
        } else {
            null
        }
        MaterialTextExtractionService(ocrPort, get())
    }
    single { MaterialChunkBuilder() }
    single {
        MaterialIngestionSchedulerConfig(
            enabled = runtimeConfig.materialIngestionEnabled,
            interval = runtimeConfig.materialIngestionIntervalSeconds.seconds
        )
    }
    single { MaterialIngestionWorker(get(), get(), get(), get(), get(), get(), get()) }
    single { MaterialIngestionScheduler(get(), get()) }

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
            single<FindMaterialDocumentPort> { InMemoryFindMaterialDocumentPort(get()) }
            single<FindMaterialDocumentOutlinePort> { com.gtu.aiassistant.app.memory.InMemoryFindMaterialDocumentOutlinePort(get()) }
            single<FindMaterialDocumentSectionsPort> { com.gtu.aiassistant.app.memory.InMemoryFindMaterialDocumentSectionsPort(get()) }
            single<SaveMaterialDocumentPort> { InMemorySaveMaterialDocumentPort(get()) }
            single<DeleteMaterialDocumentPort> { InMemoryDeleteMaterialDocumentPort(get()) }
            single<SaveMaterialChunksPort> { InMemorySaveMaterialChunksPort(get()) }
            single<ReplaceMaterialDocumentChunksPort> { InMemoryReplaceMaterialDocumentChunksPort(get()) }
            single<DeleteMaterialChunksPort> { InMemoryDeleteMaterialChunksPort(get()) }
            single<SearchUserMaterialsPort> { InMemorySearchUserMaterialsPort(get()) }
            single<FindMaterialCollectionPort> { InMemoryFindMaterialCollectionPort(get()) }
            single<SaveMaterialCollectionPort> { InMemorySaveMaterialCollectionPort(get()) }
            single<DeleteMaterialCollectionPort> { InMemoryDeleteMaterialCollectionPort(get()) }
            single<StoreGeneratedArtifactPort> { InMemoryStoreGeneratedArtifactPort(get(), get()) }
            single<FindGeneratedArtifactPort> { InMemoryFindGeneratedArtifactPort(get()) }
            single<ReadGeneratedArtifactContentPort> { InMemoryReadGeneratedArtifactContentPort(get(), get()) }
            single<SearchKnowledgePort> { DisabledSearchKnowledgePort() }
            single<UpsertKnowledgeDocumentPort> { DisabledUpsertKnowledgeDocumentPort() }
            single<UpsertKnowledgeSourcesPort> { DisabledUpsertKnowledgeSourcesPort() }
            single<SaveKnowledgeIngestionRunPort> { DisabledSaveKnowledgeIngestionRunPort() }
            single<EmbeddingProfileReindexService> { DisabledEmbeddingProfileReindexService(get()) }
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
            single<FindMaterialDocumentPort> { FindMaterialDocumentPortImpl(get()) }
            single<FindMaterialDocumentOutlinePort> { FindMaterialDocumentOutlinePortImpl(get()) }
            single<FindMaterialDocumentSectionsPort> { FindMaterialDocumentSectionsPortImpl(get()) }
            single<SaveMaterialDocumentPort> { SaveMaterialDocumentPortImpl(get()) }
            single<DeleteMaterialDocumentPort> { DeleteMaterialDocumentPortImpl(get()) }
            single<SaveMaterialChunksPort> { SaveMaterialChunksPortImpl(get()) }
            single<ReplaceMaterialDocumentChunksPort> { ReplaceMaterialDocumentChunksPortImpl(get()) }
            single<DeleteMaterialChunksPort> { DeleteMaterialChunksPortImpl(get()) }
            single<SearchUserMaterialsPort> { SearchUserMaterialsPortImpl(get(), get()) }
            single<FindMaterialCollectionPort> { FindMaterialCollectionPortImpl(get()) }
            single<SaveMaterialCollectionPort> { SaveMaterialCollectionPortImpl(get()) }
            single<DeleteMaterialCollectionPort> { DeleteMaterialCollectionPortImpl(get()) }
            single<StoreGeneratedArtifactPort> { StoreGeneratedArtifactPortImpl(get(), get()) }
            single<FindGeneratedArtifactPort> { FindGeneratedArtifactPortImpl(get()) }
            single<ReadGeneratedArtifactContentPort> { ReadGeneratedArtifactContentPortImpl(get(), get()) }
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
            single<UpsertKnowledgeSourcesPort> {
                if (runtimeConfig.ragEnabled) {
                    UpsertKnowledgeSourcesPortImpl(get())
                } else {
                    DisabledUpsertKnowledgeSourcesPort()
                }
            }
            single<SaveKnowledgeIngestionRunPort> {
                if (runtimeConfig.ragEnabled) {
                    SaveKnowledgeIngestionRunPortImpl(get())
                } else {
                    DisabledSaveKnowledgeIngestionRunPort()
                }
            }
            single<EmbeddingProfileReindexService> { PostgresEmbeddingProfileReindexService(get(), get(), get()) }
        }
    }

    single<RegisterUserUseCase> { RegisterUserUseCaseImpl(get(), get(), get()) }
    single<LoginInUseCase> { LoginInUseCaseImpl(get(), get(), get()) }
    single<CreateChatWithAgentUseCase> { CreateChatWithAgentUseCaseImpl(get(), get(), get(), get()) }
    single<ContinueChatWithAgentUseCase> { ContinueChatWithAgentUseCaseImpl(get(), get(), get(), get(), get()) }
    single<ListChatsUseCase> { ListChatsUseCaseImpl(get()) }
    single<DeleteChatUseCase> { DeleteChatUseCaseImpl(get(), get()) }
    single<UploadMaterialUseCase> { UploadMaterialUseCaseImpl(get(), get(), get(), runtimeConfig.materialMaxFileSizeBytes) }
    single<ListMaterialsUseCase> { ListMaterialsUseCaseImpl(get()) }
    single<DownloadMaterialUseCase> { DownloadMaterialUseCaseImpl(get(), get()) }
    single<DeleteMaterialUseCase> { DeleteMaterialUseCaseImpl(get(), get(), get(), get()) }
    single<CreateMaterialCollectionUseCase> { CreateMaterialCollectionUseCaseImpl(get()) }
    single<ListMaterialCollectionsUseCase> { ListMaterialCollectionsUseCaseImpl(get()) }
    single<DeleteMaterialCollectionUseCase> { DeleteMaterialCollectionUseCaseImpl(get(), get()) }
}

private data class RuntimeConfig(
    val host: String,
    val port: Int,
    val aiMode: AiMode,
    val aiApiKey: String?,
    val aiApiKeys: List<String>,
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
    val webSearchMaxResults: Int,
    val fileStorageMode: FileStorageMode,
    val localStorageDir: String,
    val minioEndpoint: String?,
    val minioAccessKey: String?,
    val minioSecretKey: String?,
    val minioBucket: String?,
    val minioRegion: String?,
    val materialMaxFileSizeBytes: Long,
    val materialIngestionEnabled: Boolean,
    val materialIngestionIntervalSeconds: Int,
    val materialOcrEnabled: Boolean,
    val tesseractCommand: String,
    val tesseractLanguages: String,
    val tesseractTimeoutSeconds: Long,
    val materialOcrMinTextChars: Int,
    val materialOcrRenderDpi: Int,
    val agentSpaceBaseUrl: String?,
    val agentSpaceToken: String?,
    val agentSpaceTimeoutSeconds: Int,
    val agentSpaceOutputLimitChars: Int
) {
    companion object {
        fun fromEnvironment(): RuntimeConfig {
            val aiBaseUrl = System.getenv("APP_AI_BASE_URL")
                ?: System.getenv("OPENAI_BASE_URL")
                ?: AiConfig.DEFAULT_OLLAMA_OPENAI_BASE_URL
            val aiApiKey = System.getenv("APP_AI_API_KEY")
                ?: System.getenv("OPENAI_API_KEY")
                ?: AiConfig.DEFAULT_OLLAMA_API_KEY
            val aiApiKeys = System.getenv("APP_AI_API_KEYS")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(aiApiKey)
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
                aiApiKeys = aiApiKeys,
                aiBaseUrl = aiBaseUrl,
                aiModel = System.getenv("APP_AI_MODEL") ?: AiConfig.GEMMA4_31B,
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
                webSearchMaxResults = (System.getenv("APP_WEB_SEARCH_MAX_RESULTS") ?: "6").toInt(),
                fileStorageMode = FileStorageMode.from(System.getenv("APP_FILE_STORAGE_MODE")),
                localStorageDir = System.getenv("APP_LOCAL_STORAGE_DIR") ?: "./local-storage",
                minioEndpoint = System.getenv("APP_MINIO_ENDPOINT")?.takeIf(String::isNotBlank),
                minioAccessKey = System.getenv("APP_MINIO_ACCESS_KEY")?.takeIf(String::isNotBlank),
                minioSecretKey = System.getenv("APP_MINIO_SECRET_KEY")?.takeIf(String::isNotBlank),
                minioBucket = System.getenv("APP_MINIO_BUCKET")?.takeIf(String::isNotBlank),
                minioRegion = System.getenv("APP_MINIO_REGION")?.takeIf(String::isNotBlank) ?: "us-east-1",
                materialMaxFileSizeBytes = (System.getenv("APP_MATERIAL_MAX_FILE_SIZE_BYTES") ?: "52428800").toLong(),
                materialIngestionEnabled = System.getenv("APP_MATERIAL_INGESTION_ENABLED").toBoolean(default = true),
                materialIngestionIntervalSeconds = (System.getenv("APP_MATERIAL_INGESTION_INTERVAL_SECONDS") ?: "10")
                    .toInt()
                    .coerceAtLeast(1),
                materialOcrEnabled = System.getenv("APP_MATERIAL_OCR_ENABLED").toBoolean(default = false),
                tesseractCommand = System.getenv("APP_TESSERACT_COMMAND")?.takeIf(String::isNotBlank)
                    ?: TesseractOcrConfig.DEFAULT_COMMAND,
                tesseractLanguages = System.getenv("APP_TESSERACT_LANGUAGES")?.takeIf(String::isNotBlank)
                    ?: TesseractOcrConfig.DEFAULT_LANGUAGES,
                tesseractTimeoutSeconds = (System.getenv("APP_TESSERACT_TIMEOUT_SECONDS") ?: "60").toLong().coerceAtLeast(1L),
                materialOcrMinTextChars = (System.getenv("APP_MATERIAL_OCR_MIN_TEXT_CHARS") ?: MaterialTextExtractionConfig.DEFAULT_PDF_MIN_TEXT_LAYER_CHARACTERS.toString())
                    .toInt()
                    .coerceAtLeast(0),
                materialOcrRenderDpi = (System.getenv("APP_MATERIAL_OCR_RENDER_DPI") ?: MaterialTextExtractionConfig.DEFAULT_OCR_RENDER_DPI.toString())
                    .toInt()
                    .coerceIn(72, 600),
                agentSpaceBaseUrl = System.getenv("APP_AGENT_SPACE_BASE_URL")?.takeIf(String::isNotBlank),
                agentSpaceToken = System.getenv("APP_AGENT_SPACE_TOKEN")?.takeIf(String::isNotBlank),
                agentSpaceTimeoutSeconds = (System.getenv("APP_AGENT_SPACE_TIMEOUT_SECONDS") ?: "30").toInt().coerceIn(1, 120),
                agentSpaceOutputLimitChars = (System.getenv("APP_AGENT_SPACE_OUTPUT_LIMIT_CHARS") ?: "20000").toInt().coerceAtLeast(1000)
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

private enum class FileStorageMode {
    LOCAL,
    MINIO;

    companion object {
        fun from(raw: String?): FileStorageMode =
            when (raw?.lowercase()) {
                "minio" -> MINIO
                else -> LOCAL
            }
    }
}

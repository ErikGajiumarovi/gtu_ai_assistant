package com.gtu.aiassistant.app

import arrow.core.Either
import com.gtu.aiassistant.application.chat.ContinueChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.CreateChatWithAgentUseCaseImpl
import com.gtu.aiassistant.application.chat.DeleteChatUseCaseImpl
import com.gtu.aiassistant.application.chat.ListChatsUseCaseImpl
import com.gtu.aiassistant.application.materials.CreateMaterialCollectionUseCaseImpl
import com.gtu.aiassistant.application.materials.DeleteMaterialCollectionUseCaseImpl
import com.gtu.aiassistant.application.materials.DeleteMaterialUseCaseImpl
import com.gtu.aiassistant.application.materials.DownloadMaterialUseCaseImpl
import com.gtu.aiassistant.application.materials.ListMaterialCollectionsUseCaseImpl
import com.gtu.aiassistant.application.materials.ListMaterialsUseCaseImpl
import com.gtu.aiassistant.application.materials.UploadMaterialUseCaseImpl
import com.gtu.aiassistant.application.user.LoginInUseCaseImpl
import com.gtu.aiassistant.application.user.RegisterUserUseCaseImpl
import com.gtu.aiassistant.app.memory.InMemoryDeleteChatPort
import com.gtu.aiassistant.app.memory.InMemoryDeleteMaterialChunksPort
import com.gtu.aiassistant.app.memory.InMemoryDeleteMaterialCollectionPort
import com.gtu.aiassistant.app.memory.InMemoryDeleteMaterialDocumentPort
import com.gtu.aiassistant.app.memory.InMemoryExistsUserPort
import com.gtu.aiassistant.app.memory.InMemoryFindChatPort
import com.gtu.aiassistant.app.memory.InMemoryFindMaterialCollectionPort
import com.gtu.aiassistant.app.memory.InMemoryFindMaterialDocumentPort
import com.gtu.aiassistant.app.memory.InMemoryFindUserPort
import com.gtu.aiassistant.app.memory.InMemoryGenerateMessagePort
import com.gtu.aiassistant.app.memory.InMemorySaveChatPort
import com.gtu.aiassistant.app.memory.InMemorySaveMaterialCollectionPort
import com.gtu.aiassistant.app.memory.InMemorySaveMaterialDocumentPort
import com.gtu.aiassistant.app.memory.InMemorySaveUserPort
import com.gtu.aiassistant.app.memory.InMemorySearchUserMaterialsPort
import com.gtu.aiassistant.app.memory.InMemoryState
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.infrastructure.ai.AgentGenerateMessagePortImpl
import com.gtu.aiassistant.infrastructure.ai.AiConfig
import com.gtu.aiassistant.infrastructure.ai.tools.GtuKnowledgeSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.GtuWebSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.UserMaterialSearchTool
import com.gtu.aiassistant.infrastructure.ai.tools.WebSearchConfig
import com.gtu.aiassistant.infrastructure.ai.tools.WebSearchMode
import com.gtu.aiassistant.infrastructure.knowledge.DisabledSearchKnowledgePort
import com.gtu.aiassistant.infrastructure.knowledge.GtuPageFetcher
import com.gtu.aiassistant.infrastructure.knowledge.GtuUrlPolicy
import com.gtu.aiassistant.infrastructure.knowledge.KnowledgeIngestionConfig
import com.gtu.aiassistant.infrastructure.security.Argon2HashPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.Argon2VerifyPasswordPortImpl
import com.gtu.aiassistant.infrastructure.security.IssueJwtPortImpl
import com.gtu.aiassistant.infrastructure.security.JwtConfig
import com.gtu.aiassistant.infrastructure.storage.LocalMaterialObjectStoragePort
import com.gtu.aiassistant.presentation.ApiDependencies
import com.gtu.aiassistant.presentation.configureApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApiIntegrationTest {
    @Test
    fun `all endpoints work through real use cases and app dependencies`() = testApplication {
        installTestApi()

        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val jwt = registerAndLogin("full-stack-${System.nanoTime()}@example.com")

        val collectionId = createMaterialCollection(jwt)
        assertTrue(client.get("/api/material-collections") { bearer(jwt) }.bodyAsText().contains(collectionId))

        val materialId = uploadMaterial(jwt, collectionId)
        assertTrue(client.get("/api/materials") { bearer(jwt) }.bodyAsText().contains(materialId))
        assertTrue(client.get("/api/materials?collectionId=$collectionId") { bearer(jwt) }.bodyAsText().contains(materialId))
        assertEquals(HttpStatusCode.OK, client.get("/api/materials/$materialId") { bearer(jwt) }.status)
        assertEquals(
            "GTU integration material",
            client.get("/api/materials/$materialId/download") { bearer(jwt) }.bodyAsText()
        )

        val chatId = createChat(jwt)
        assertTrue(client.get("/api/chats") { bearer(jwt) }.bodyAsText().contains(chatId))
        assertEquals(HttpStatusCode.OK, client.post("/api/chats/$chatId/continue") {
            bearer(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"originalText":"Continue briefly","sources":{"gtu":false,"materials":true,"web":false}}""")
        }.status)

        val createStreamBody = client.post("/api/chats/with-agent/stream") {
            bearer(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"originalText":"Stream hello","sources":{"gtu":false,"materials":true,"web":false}}""")
        }.bodyAsText()
        assertNdjsonStreamCompleted(createStreamBody)

        val continueStreamBody = client.post("/api/chats/$chatId/continue/stream") {
            bearer(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"originalText":"Stream continue","sources":{"gtu":false,"materials":true,"web":false}}""")
        }.bodyAsText()
        assertNdjsonStreamCompleted(continueStreamBody)

        assertEquals(HttpStatusCode.OK, client.delete("/api/chats/$chatId") { bearer(jwt) }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/api/materials/$materialId") { bearer(jwt) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/materials/$materialId") { bearer(jwt) }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/api/material-collections/$collectionId") { bearer(jwt) }.status)
    }

    @Test
    fun `ai backed chat endpoints return assistant messages`() {
        if (System.getenv("RUN_AI_INTEGRATION_TESTS") != "true") return

        testApplication {
            installTestApi(generateMessagePort = realAgentGenerateMessagePort())

            val jwt = registerAndLogin("real-ai-${System.nanoTime()}@example.com")
            val chatId = createChat(jwt)

            val continued = client.post("/api/chats/$chatId/continue") {
                bearer(jwt)
                contentType(ContentType.Application.Json)
                setBody("""{"originalText":"Reply with exactly one short sentence.","sources":{"gtu":false,"materials":true,"web":false}}""")
            }
            assertEquals(HttpStatusCode.OK, continued.status)
            assertAssistantMessagePresent(continued.bodyAsText())

            val createStreamBody = client.post("/api/chats/with-agent/stream") {
                bearer(jwt)
                contentType(ContentType.Application.Json)
                setBody("""{"originalText":"Reply with exactly one short streamed sentence.","sources":{"gtu":false,"materials":true,"web":false}}""")
            }.bodyAsText()
            assertNdjsonStreamCompleted(createStreamBody)

            val continueStreamBody = client.post("/api/chats/$chatId/continue/stream") {
                bearer(jwt)
                contentType(ContentType.Application.Json)
                setBody("""{"originalText":"Reply with another short streamed sentence.","sources":{"gtu":false,"materials":true,"web":false}}""")
            }.bodyAsText()
            assertNdjsonStreamCompleted(continueStreamBody)
        }
    }

    private fun ApplicationTestBuilder.installTestApi(
        generateMessagePort: GenerateMessagePort = InMemoryGenerateMessagePort()
    ) {
        val dependencies = testApiDependencies(generateMessagePort)
        application { configureApi(dependencies) }
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(email: String): String {
        val register = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Agent","lastName":"User","email":"$email","password":"secret123"}""")
        }
        assertEquals(HttpStatusCode.Created, register.status)

        val login = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"secret123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)

        return login.bodyAsText().parseJsonObject()["jwt"]?.jsonPrimitive?.content
            ?: error("Login response does not contain jwt")
    }

    private suspend fun ApplicationTestBuilder.createMaterialCollection(jwt: String): String {
        val response = client.post("/api/material-collections") {
            bearer(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Algorithms"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.bodyAsText().parseJsonObject()["id"]?.jsonPrimitive?.content
            ?: error("Collection response does not contain id")
    }

    private suspend fun ApplicationTestBuilder.uploadMaterial(jwt: String, collectionId: String): String {
        val response = client.post("/api/materials") {
            bearer(jwt)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("collectionId", collectionId)
                        append(
                            "file",
                            "GTU integration material".encodeToByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"lecture.txt\"")
                            }
                        )
                    }
                )
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.bodyAsText().parseJsonObject()["id"]?.jsonPrimitive?.content
            ?: error("Material response does not contain id")
    }

    private suspend fun ApplicationTestBuilder.createChat(jwt: String): String {
        val response = client.post("/api/chats/with-agent") {
            bearer(jwt)
            contentType(ContentType.Application.Json)
            setBody("""{"originalText":"Hello, answer shortly","sources":{"gtu":false,"materials":true,"web":false}}""")
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.Created, response.status, body)
        assertAssistantMessagePresent(body)
        return body.parseJsonObject()["id"]?.jsonPrimitive?.content
            ?: error("Chat response does not contain id")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(jwt: String) {
        header(HttpHeaders.Authorization, "Bearer $jwt")
    }
}

private fun testApiDependencies(generateMessagePort: GenerateMessagePort): ApiDependencies {
    val state = InMemoryState()
    val jwtConfig = JwtConfig(
        secret = "integration-test-secret",
        issuer = "gtu-ai-assistant-test",
        ttlSeconds = 3600
    )

    val findUserPort = InMemoryFindUserPort(state)
    val existsUserPort = InMemoryExistsUserPort(state)
    val saveUserPort = InMemorySaveUserPort(state)
    val findChatPort = InMemoryFindChatPort(state)
    val saveChatPort = InMemorySaveChatPort(state)
    val deleteChatPort = InMemoryDeleteChatPort(state)
    val findMaterialDocumentPort = InMemoryFindMaterialDocumentPort(state)
    val saveMaterialDocumentPort = InMemorySaveMaterialDocumentPort(state)
    val deleteMaterialDocumentPort = InMemoryDeleteMaterialDocumentPort(state)
    val deleteMaterialChunksPort = InMemoryDeleteMaterialChunksPort(state)
    val findMaterialCollectionPort = InMemoryFindMaterialCollectionPort(state)
    val saveMaterialCollectionPort = InMemorySaveMaterialCollectionPort(state)
    val deleteMaterialCollectionPort = InMemoryDeleteMaterialCollectionPort(state)
    val objectStoragePort = LocalMaterialObjectStoragePort(Files.createTempDirectory("gtu-ai-assistant-api-test"))

    return ApiDependencies(
        registerUserUseCase = RegisterUserUseCaseImpl(
            existsUserPort = existsUserPort,
            hashPasswordPort = Argon2HashPasswordPortImpl(),
            saveUserPort = saveUserPort
        ),
        loginInUseCase = LoginInUseCaseImpl(
            findUserPort = findUserPort,
            verifyPasswordPort = Argon2VerifyPasswordPortImpl(),
            issueJwtPort = IssueJwtPortImpl(jwtConfig)
        ),
        createChatWithAgentUseCase = CreateChatWithAgentUseCaseImpl(
            generateMessagePort = generateMessagePort,
            saveChatPort = saveChatPort,
            findMaterialDocumentPort = findMaterialDocumentPort,
            findMaterialCollectionPort = findMaterialCollectionPort
        ),
        continueChatWithAgentUseCase = ContinueChatWithAgentUseCaseImpl(
            findChatPort = findChatPort,
            generateMessagePort = generateMessagePort,
            saveChatPort = saveChatPort,
            findMaterialDocumentPort = findMaterialDocumentPort,
            findMaterialCollectionPort = findMaterialCollectionPort
        ),
        listChatsUseCase = ListChatsUseCaseImpl(findChatPort),
        deleteChatUseCase = DeleteChatUseCaseImpl(findChatPort, deleteChatPort),
        uploadMaterialUseCase = UploadMaterialUseCaseImpl(
            objectStoragePort = objectStoragePort,
            saveMaterialDocumentPort = saveMaterialDocumentPort,
            findMaterialCollectionPort = findMaterialCollectionPort,
            maxFileSizeBytes = 1024 * 1024
        ),
        listMaterialsUseCase = ListMaterialsUseCaseImpl(findMaterialDocumentPort),
        downloadMaterialUseCase = DownloadMaterialUseCaseImpl(findMaterialDocumentPort, objectStoragePort),
        deleteMaterialUseCase = DeleteMaterialUseCaseImpl(
            findMaterialDocumentPort = findMaterialDocumentPort,
            deleteMaterialDocumentPort = deleteMaterialDocumentPort,
            deleteMaterialChunksPort = deleteMaterialChunksPort,
            objectStoragePort = objectStoragePort
        ),
        createMaterialCollectionUseCase = CreateMaterialCollectionUseCaseImpl(saveMaterialCollectionPort),
        listMaterialCollectionsUseCase = ListMaterialCollectionsUseCaseImpl(findMaterialCollectionPort),
        deleteMaterialCollectionUseCase = DeleteMaterialCollectionUseCaseImpl(
            findMaterialCollectionPort = findMaterialCollectionPort,
            deleteMaterialCollectionPort = deleteMaterialCollectionPort
        ),
        readGeneratedArtifactContentPort = { Either.Right(null) },
        jwtSecret = jwtConfig.secret,
        jwtIssuer = jwtConfig.issuer
    )
}

private fun realAgentGenerateMessagePort(): GenerateMessagePort {
    val state = InMemoryState()
    val httpClient = HttpClient(CIO)
    val knowledgeConfig = KnowledgeIngestionConfig(
        enabled = false,
        schedulerEnabled = false,
        ingestOnStartup = false,
        sitemapUrl = "https://gtu.ge/sitemap.xml",
        robotsUrl = "https://gtu.ge/robots.txt",
        allowedDomains = setOf("gtu.ge"),
        maxPagesPerRun = 1,
        maxContentCharacters = 20_000,
        refreshHour = 3,
        zoneId = java.time.ZoneId.of("Asia/Tbilisi")
    )
    val aiConfig = AiConfig(
        apiKey = System.getenv("APP_AI_API_KEY")
            ?: System.getenv("OPENAI_API_KEY")
            ?: AiConfig.DEFAULT_OLLAMA_API_KEY,
        apiKeys = System.getenv("APP_AI_API_KEYS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                System.getenv("APP_AI_API_KEY")
                    ?: System.getenv("OPENAI_API_KEY")
                    ?: AiConfig.DEFAULT_OLLAMA_API_KEY
            ),
        baseUrl = System.getenv("APP_AI_BASE_URL")
            ?: System.getenv("OPENAI_BASE_URL")
            ?: AiConfig.DEFAULT_OLLAMA_OPENAI_BASE_URL,
        model = System.getenv("APP_AI_MODEL") ?: AiConfig.GEMMA3_4B
    )

    return AgentGenerateMessagePortImpl.create(
        config = aiConfig,
        knowledgeSearchTool = GtuKnowledgeSearchTool(DisabledSearchKnowledgePort()),
        userMaterialSearchTool = UserMaterialSearchTool(
            searchUserMaterialsPort = InMemorySearchUserMaterialsPort(state),
            findMaterialDocumentPort = InMemoryFindMaterialDocumentPort(state),
            findMaterialDocumentOutlinePort = com.gtu.aiassistant.app.memory.InMemoryFindMaterialDocumentOutlinePort(state),
            findMaterialDocumentSectionsPort = com.gtu.aiassistant.app.memory.InMemoryFindMaterialDocumentSectionsPort(state)
        ),
        webSearchTool = GtuWebSearchTool(
            config = WebSearchConfig(mode = WebSearchMode.DISABLED, allowedDomains = setOf("gtu.ge"), maxResults = 1),
            urlPolicy = GtuUrlPolicy(setOf("gtu.ge")),
            fetcher = GtuPageFetcher(httpClient, knowledgeConfig)
        )
    )
}

private fun String.parseJsonObject(): kotlinx.serialization.json.JsonObject = Json.parseToJsonElement(this).jsonObject

private fun assertAssistantMessagePresent(body: String) {
    val messages = body.parseJsonObject()["messages"]?.jsonArray ?: error("Chat response does not contain messages")
    assertTrue(messages.any { message ->
        val json = message.jsonObject
        json["senderType"]?.jsonPrimitive?.content == "AI" &&
            !json["originalText"]?.jsonPrimitive?.content.isNullOrBlank()
    })
}

private fun assertNdjsonStreamCompleted(body: String) {
    val lines = body.lines().filter(String::isNotBlank)
    assertFalse(lines.isEmpty(), "Expected non-empty NDJSON stream")
    assertTrue(lines.any { it.startsWith("{\"t\":") }, "Expected at least one token line, got: $body")
    val finalLine = lines.last()
    assertTrue(finalLine.startsWith("{\"d\":"), "Expected final data line, got: $body")
    val finalJson = Json.parseToJsonElement(finalLine).jsonObject["d"]?.jsonObject
    assertNotNull(finalJson, "Expected final data object in stream")
    val messages = finalJson["messages"]?.jsonArray ?: error("Final stream data does not contain messages")
    assertTrue(messages.any { message ->
        val json = message.jsonObject
        json["senderType"]?.jsonPrimitive?.content == "AI" &&
            !json["originalText"]?.jsonPrimitive?.content.isNullOrBlank()
    })
}

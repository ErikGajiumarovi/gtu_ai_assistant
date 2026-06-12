package com.gtu.aiassistant.presentation

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatResult
import com.gtu.aiassistant.domain.chat.port.input.ListChatsResult
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialResult
import com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialError
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialsResult
import com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPasswordHash
import com.gtu.aiassistant.domain.user.port.input.LoginInError
import com.gtu.aiassistant.domain.user.port.input.LoginInResult
import com.gtu.aiassistant.domain.user.port.input.RegisterUserError
import com.gtu.aiassistant.domain.user.port.input.RegisterUserResult
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRoutesTest {
    private val jwtSecret = "test-secret"
    private val jwtIssuer = "test-issuer"

    @Test
    fun `register returns 201 and duplicate email returns 409`() = testApplication {
        application {
            configureApi(
                apiDependencies(
                    registerUserUseCase = { command ->
                        if (command.email.value == "taken@example.com") {
                            arrow.core.Either.Left(RegisterUserError.EmailAlreadyTaken)
                        } else {
                            arrow.core.Either.Right(
                                RegisterUserResult(
                                    user = sampleUser(email = command.email.value)
                                )
                            )
                        }
                    }
                )
            )
        }

        val success = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Agent","lastName":"User","email":"agent@example.com","password":"secret"}""")
        }
        val duplicate = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Agent","lastName":"User","email":"taken@example.com","password":"secret"}""")
        }

        assertEquals(HttpStatusCode.Created, success.status)
        assertTrue(success.bodyAsText().contains("agent@example.com"))
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
    }

    @Test
    fun `login returns 200 and invalid credentials returns 401`() = testApplication {
        application {
            configureApi(
                apiDependencies(
                    loginInUseCase = { command ->
                        if (command.email.value == "agent@example.com") {
                            arrow.core.Either.Right(LoginInResult(jwt = "jwt-token"))
                        } else {
                            arrow.core.Either.Left(LoginInError.InvalidCredentials)
                        }
                    }
                )
            )
        }

        val success = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"agent@example.com","password":"secret"}""")
        }
        val failure = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"missing@example.com","password":"secret"}""")
        }

        assertEquals(HttpStatusCode.OK, success.status)
        assertTrue(success.bodyAsText().contains("jwt-token"))
        assertEquals(HttpStatusCode.Unauthorized, failure.status)
    }

    @Test
    fun `protected chat routes reject missing or invalid jwt`() = testApplication {
        application {
            configureApi(apiDependencies())
        }

        val missing = client.get("/api/chats")
        val invalid = client.get("/api/chats") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }

    @Test
    fun `protected chat routes accept valid jwt and use token subject as effective user id`() = testApplication {
        val expectedUserId = UserId.fromTrusted(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        var receivedCommand: CreateChatWithAgentCommand? = null

        application {
            configureApi(
                apiDependencies(
                    createChatWithAgentUseCase = createChatUseCase { command ->
                        receivedCommand = command
                        arrow.core.Either.Right(
                            CreateChatWithAgentResult(
                                chat = sampleChat(userId = command.userId)
                            )
                        )
                    }
                )
            )
        }

        val response = client.post("/api/chats/with-agent") {
            header(HttpHeaders.Authorization, "Bearer ${issueJwt(expectedUserId.value.toString(), "agent@example.com")}")
            contentType(ContentType.Application.Json)
            setBody("""{"originalText":"Hello"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(expectedUserId, receivedCommand?.userId)
        assertTrue(response.bodyAsText().contains(expectedUserId.value.toString()))
    }

    @Test
    fun `material collection routes require jwt and use token subject as effective user id`() = testApplication {
        val expectedUserId = UserId.fromTrusted(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        var receivedOwnerId: UserId? = null

        application {
            configureApi(
                apiDependencies(
                    createMaterialCollectionUseCase = com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionUseCase { command ->
                        receivedOwnerId = command.ownerUserId
                        arrow.core.Either.Right(
                            com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionResult(
                                collection = sampleMaterialCollection(command.ownerUserId, command.name)
                            )
                        )
                    }
                )
            )
        }

        val missing = client.get("/api/material-collections")
        val created = client.post("/api/material-collections") {
            header(HttpHeaders.Authorization, "Bearer ${issueJwt(expectedUserId.value.toString(), "agent@example.com")}")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Algorithms"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(expectedUserId, receivedOwnerId)
        assertTrue(created.bodyAsText().contains("Algorithms"))
    }

    private fun apiDependencies(
        registerUserUseCase: com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase = com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase {
            arrow.core.Either.Right(RegisterUserResult(user = sampleUser(email = it.email.value)))
        },
        loginInUseCase: com.gtu.aiassistant.domain.user.port.input.LoginInUseCase = com.gtu.aiassistant.domain.user.port.input.LoginInUseCase {
            arrow.core.Either.Right(LoginInResult(jwt = "jwt-token"))
        },
        createChatWithAgentUseCase: CreateChatWithAgentUseCase = createChatUseCase { command ->
            arrow.core.Either.Right(CreateChatWithAgentResult(chat = sampleChat(userId = command.userId)))
        },
        continueChatWithAgentUseCase: ContinueChatWithAgentUseCase = continueChatUseCase { command ->
            arrow.core.Either.Right(ContinueChatWithAgentResult(chat = sampleChat(userId = command.userId)))
        },
        createMaterialCollectionUseCase: com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionUseCase = com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionUseCase {
            arrow.core.Either.Left(
                com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionError.InvalidDomainState(
                    com.gtu.aiassistant.domain.materials.model.MaterialCollectionError.BlankName
                )
            )
        },
        listMaterialCollectionsUseCase: com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsUseCase = com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsUseCase {
            arrow.core.Either.Right(
                com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsResult(collections = emptyList())
            )
        },
        deleteMaterialCollectionUseCase: com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionUseCase = com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionUseCase {
            arrow.core.Either.Right(
                com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionResult(deleted = false)
            )
        }
    ) = ApiDependencies(
        registerUserUseCase = registerUserUseCase,
        loginInUseCase = loginInUseCase,
        createChatWithAgentUseCase = createChatWithAgentUseCase,
        continueChatWithAgentUseCase = continueChatWithAgentUseCase,
        listChatsUseCase = com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase {
            arrow.core.Either.Right(ListChatsResult(chats = emptyList()))
        },
        deleteChatUseCase = com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase {
            arrow.core.Either.Right(DeleteChatResult)
        },
        uploadMaterialUseCase = com.gtu.aiassistant.domain.materials.port.input.UploadMaterialUseCase {
            arrow.core.Either.Left(UploadMaterialError.UnsupportedFileType)
        },
        listMaterialsUseCase = com.gtu.aiassistant.domain.materials.port.input.ListMaterialsUseCase {
            arrow.core.Either.Right(ListMaterialsResult(documents = emptyList()))
        },
        downloadMaterialUseCase = com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialUseCase {
            arrow.core.Either.Left(DownloadMaterialError.DocumentNotFound)
        },
        deleteMaterialUseCase = com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialUseCase {
            arrow.core.Either.Right(DeleteMaterialResult(deleted = false))
        },
        createMaterialCollectionUseCase = createMaterialCollectionUseCase,
        listMaterialCollectionsUseCase = listMaterialCollectionsUseCase,
        deleteMaterialCollectionUseCase = deleteMaterialCollectionUseCase,
        readGeneratedArtifactContentPort = { arrow.core.Either.Right(null) },
        jwtSecret = jwtSecret,
        jwtIssuer = jwtIssuer
    )

    private fun issueJwt(subject: String, email: String): String =
        JWT.create()
            .withIssuer(jwtIssuer)
            .withSubject(subject)
            .withClaim("email", email)
            .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
            .sign(Algorithm.HMAC256(jwtSecret))
}

private fun createChatUseCase(
    handler: suspend (CreateChatWithAgentCommand) -> arrow.core.Either<com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError, CreateChatWithAgentResult>
): CreateChatWithAgentUseCase = object : CreateChatWithAgentUseCase {
    override suspend fun invoke(command: CreateChatWithAgentCommand) = handler(command)

    override suspend fun stream(
        command: CreateChatWithAgentCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus) -> Unit
    ) = handler(command)
}

private fun continueChatUseCase(
    handler: suspend (ContinueChatWithAgentCommand) -> arrow.core.Either<com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError, ContinueChatWithAgentResult>
): ContinueChatWithAgentUseCase = object : ContinueChatWithAgentUseCase {
    override suspend fun invoke(command: ContinueChatWithAgentCommand) = handler(command)

    override suspend fun stream(
        command: ContinueChatWithAgentCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus) -> Unit
    ) = handler(command)
}

private fun sampleUser(email: String): com.gtu.aiassistant.domain.user.model.User =
    com.gtu.aiassistant.domain.user.model.User.fromTrusted(
        id = UserId.fromTrusted(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        version = 0L,
        name = UserName.fromTrusted("Agent"),
        lastName = UserLastName.fromTrusted("User"),
        email = UserEmail.fromTrusted(email),
        passwordHash = UserPasswordHash.fromTrusted("hashed-secret")
    )

private fun sampleChat(userId: UserId): Chat {
    val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    return Chat.fromTrusted(
        id = ChatId.fromTrusted(UUID.fromString("22222222-2222-2222-2222-222222222222")),
        version = 0L,
        createdAt = createdAt,
        updatedAt = createdAt.plusSeconds(5),
        ownedBy = userId,
        messages = listOf(
            Message(
                id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                originalText = "Hello",
                senderType = MessageSenderType.USER,
                createdAt = createdAt
            ),
            Message(
                id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                originalText = "Hi",
                senderType = MessageSenderType.AI,
                createdAt = createdAt.plusSeconds(5)
            )
        )
    )
}

private fun sampleMaterialCollection(
    ownerUserId: UserId,
    name: String
): com.gtu.aiassistant.domain.materials.model.MaterialCollection {
    val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    return com.gtu.aiassistant.domain.materials.model.MaterialCollection.fromTrusted(
        id = com.gtu.aiassistant.domain.materials.model.MaterialCollectionId.fromTrusted(
            UUID.fromString("55555555-5555-5555-5555-555555555555")
        ),
        version = 0L,
        ownerUserId = ownerUserId,
        name = name,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}

package com.gtu.aiassistant.presentation

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatResult
import com.gtu.aiassistant.domain.chat.port.input.ListChatsResult
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
                    createChatWithAgentUseCase = { command ->
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

    private fun apiDependencies(
        registerUserUseCase: com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase = com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase {
            arrow.core.Either.Right(RegisterUserResult(user = sampleUser(email = it.email.value)))
        },
        loginInUseCase: com.gtu.aiassistant.domain.user.port.input.LoginInUseCase = com.gtu.aiassistant.domain.user.port.input.LoginInUseCase {
            arrow.core.Either.Right(LoginInResult(jwt = "jwt-token"))
        },
        createChatWithAgentUseCase: com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase = com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase {
            arrow.core.Either.Right(CreateChatWithAgentResult(chat = sampleChat(userId = it.userId)))
        }
    ) = ApiDependencies(
        registerUserUseCase = registerUserUseCase,
        loginInUseCase = loginInUseCase,
        createChatWithAgentUseCase = createChatWithAgentUseCase,
        continueChatWithAgentUseCase = com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase {
            arrow.core.Either.Right(
                com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentResult(
                    chat = sampleChat(userId = it.userId)
                )
            )
        },
        listChatsUseCase = com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase {
            arrow.core.Either.Right(ListChatsResult(chats = emptyList()))
        },
        deleteChatUseCase = com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase {
            arrow.core.Either.Right(DeleteChatResult)
        },
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

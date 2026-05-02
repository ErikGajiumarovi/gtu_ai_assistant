package com.gtu.aiassistant.presentation

import arrow.core.raise.either
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPassword
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.Instant
import java.util.UUID

internal fun Application.configureRoutes(
    dependencies: ApiDependencies
) {
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        route("/api") {
            route("/auth") {
                post("/register") {
                    val request = call.receive<RegisterUserRequest>()

                    either {
                        com.gtu.aiassistant.domain.user.port.input.RegisterUserCommand(
                            name = UserName.create(request.name).bind(),
                            lastName = UserLastName.create(request.lastName).bind(),
                            email = UserEmail.create(request.email).bind(),
                            password = UserPassword.create(request.password).bind()
                        )
                    }.fold(
                        ifLeft = { domainError ->
                            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse.fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.registerUserUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), ApiErrorResponse.fromUseCaseError(error))
                                },
                                ifRight = { result ->
                                    call.respond(HttpStatusCode.Created, result.user.toResponse())
                                }
                            )
                        }
                    )
                }

                post("/login") {
                    val request = call.receive<LoginInRequest>()

                    either {
                        com.gtu.aiassistant.domain.user.port.input.LoginInCommand(
                            email = UserEmail.create(request.email).bind(),
                            password = UserPassword.create(request.password).bind()
                        )
                    }.fold(
                        ifLeft = { domainError ->
                            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse.fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.loginInUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), ApiErrorResponse.fromUseCaseError(error))
                                },
                                ifRight = { result ->
                                    call.respond(HttpStatusCode.OK, LoginInResponse(jwt = result.jwt))
                                }
                            )
                        }
                    )
                }
            }

            authenticate("auth-jwt") {
                post("/chats/with-agent") {
                    val request = call.receive<CreateChatWithAgentRequest>()
                    val principal = call.principal<AuthenticatedUserPrincipal>()

                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@post
                    }

                    dependencies.createChatWithAgentUseCase(
                        com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand(
                            userId = principal.userId,
                            message = request.toUserMessage()
                        )
                    ).fold(
                        ifLeft = { error ->
                            call.respond(error.statusCode(), ApiErrorResponse.fromUseCaseError(error))
                        },
                        ifRight = { result ->
                            call.respond(HttpStatusCode.Created, result.chat.toResponse())
                        }
                    )
                }

                post("/chats/{chatId}/continue") {
                    val chatIdRaw = call.parameters["chatId"]
                    val request = call.receive<ContinueChatWithAgentRequest>()
                    val principal = call.principal<AuthenticatedUserPrincipal>()

                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@post
                    }

                    if (chatIdRaw == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse(
                                code = "missing_chat_id",
                                message = "Path parameter 'chatId' is required"
                            )
                        )
                        return@post
                    }

                    either {
                        com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand(
                            chatId = ChatId.create(chatIdRaw).bind(),
                            userId = principal.userId,
                            message = request.toUserMessage()
                        )
                    }.fold(
                        ifLeft = { domainError ->
                            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse.fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.continueChatWithAgentUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), ApiErrorResponse.fromUseCaseError(error))
                                },
                                ifRight = { result ->
                                    call.respond(HttpStatusCode.OK, result.chat.toResponse())
                                }
                            )
                        }
                    )
                }

                get("/chats") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()

                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }

                    dependencies.listChatsUseCase(
                        com.gtu.aiassistant.domain.chat.port.input.ListChatsQuery(userId = principal.userId)
                    ).fold(
                        ifLeft = { error ->
                            call.respond(error.statusCode(), ApiErrorResponse.fromUseCaseError(error))
                        },
                        ifRight = { result ->
                            call.respond(
                                HttpStatusCode.OK,
                                ListChatsResponse(chats = result.chats.map { it.toResponse() })
                            )
                        }
                    )
                }

                delete("/chats/{chatId}") {
                    val chatIdRaw = call.parameters["chatId"]
                    val principal = call.principal<AuthenticatedUserPrincipal>()

                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@delete
                    }

                    if (chatIdRaw == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse(
                                code = "missing_chat_id",
                                message = "Path parameter 'chatId' is required"
                            )
                        )
                        return@delete
                    }

                    either {
                        com.gtu.aiassistant.domain.chat.port.input.DeleteChatCommand(
                            userId = principal.userId,
                            chatId = ChatId.create(chatIdRaw).bind()
                        )
                    }.fold(
                        ifLeft = { domainError ->
                            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse.fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.deleteChatUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), ApiErrorResponse.fromUseCaseError(error))
                                },
                                ifRight = {
                                    call.respond(HttpStatusCode.OK, DeleteChatResponse(deleted = true))
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun CreateChatWithAgentRequest.toUserMessage(): Message =
    Message(
        id = UUID.randomUUID(),
        originalText = originalText,
        senderType = MessageSenderType.USER,
        createdAt = Instant.now()
    )

private fun ContinueChatWithAgentRequest.toUserMessage(): Message =
    Message(
        id = UUID.randomUUID(),
        originalText = originalText,
        senderType = MessageSenderType.USER,
        createdAt = Instant.now()
    )

private fun com.gtu.aiassistant.domain.user.model.User.toResponse(): UserResponse =
    UserResponse(
        id = id.value.toString(),
        version = version,
        name = name.value,
        lastName = lastName.value,
        email = email.value
    )

private fun com.gtu.aiassistant.domain.chat.model.Chat.toResponse(): ChatResponse =
    ChatResponse(
        id = id.value.toString(),
        version = version,
        ownedBy = ownedBy.value.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        messages = messages.map { message ->
            MessageResponse(
                id = message.id.toString(),
                originalText = message.originalText,
                senderType = message.senderType.name,
                createdAt = message.createdAt.toString(),
                citations = message.citations.map { citation ->
                    CitationResponse(
                        title = citation.title,
                        url = citation.url,
                        snippet = citation.snippet,
                        sourceType = citation.sourceType.name
                    )
                }
            )
        }
    )

private fun com.gtu.aiassistant.domain.user.port.input.RegisterUserError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.user.port.input.RegisterUserError.EmailAlreadyTaken -> HttpStatusCode.Conflict
        is com.gtu.aiassistant.domain.user.port.input.RegisterUserError.InvalidDomainState -> HttpStatusCode.BadRequest
        is com.gtu.aiassistant.domain.user.port.input.RegisterUserError.DuplicateCheckFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.user.port.input.RegisterUserError.PasswordHashingFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.user.port.input.RegisterUserError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.user.port.input.LoginInError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.user.port.input.LoginInError.InvalidCredentials -> HttpStatusCode.Unauthorized
        is com.gtu.aiassistant.domain.user.port.input.LoginInError.FindFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.user.port.input.LoginInError.PasswordVerificationFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.user.port.input.LoginInError.JwtIssuingFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError.statusCode(): HttpStatusCode =
    when (this) {
        is com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError.InvalidDomainState -> HttpStatusCode.BadRequest
        is com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError.MessageGenerationFailed -> HttpStatusCode.BadGateway
        is com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError.ChatNotFound -> HttpStatusCode.NotFound
        com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError.AccessDenied -> HttpStatusCode.Forbidden
        is com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError.InvalidDomainState -> HttpStatusCode.BadRequest
        is com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError.MessageGenerationFailed -> HttpStatusCode.BadGateway
        is com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.chat.port.input.ListChatsError.statusCode(): HttpStatusCode =
    when (this) {
        is com.gtu.aiassistant.domain.chat.port.input.ListChatsError.FindFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.chat.port.input.DeleteChatError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.chat.port.input.DeleteChatError.ChatNotFound -> HttpStatusCode.NotFound
        com.gtu.aiassistant.domain.chat.port.input.DeleteChatError.AccessDenied -> HttpStatusCode.Forbidden
        is com.gtu.aiassistant.domain.chat.port.input.DeleteChatError.FindFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.chat.port.input.DeleteChatError.DeleteFailed -> HttpStatusCode.InternalServerError
    }

private fun ApiErrorResponse.Companion.fromUseCaseError(error: Any): ApiErrorResponse =
    ApiErrorResponse(
        code = error::class.simpleName ?: "use_case_error",
        message = error.toString()
    )

private fun unauthorizedResponse(): ApiErrorResponse =
    ApiErrorResponse(
        code = "unauthorized",
        message = "Missing or invalid bearer token"
    )

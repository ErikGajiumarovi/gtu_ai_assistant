package com.gtu.aiassistant.presentation

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
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
            call.respond(
                HealthResponse(status = "ok")
            )
        }

        route("/api") {
            post("/users") {
                val request = call.receive<CreateUserRequest>()

                either {
                    val command = com.gtu.aiassistant.domain.user.port.input.CreateUserCommand(
                        id = UserId.create(request.id).bind(),
                        name = UserName.create(request.name).bind(),
                        lastName = UserLastName.create(request.lastName).bind(),
                        email = UserEmail.create(request.email).bind()
                    )

                    command
                }.fold(
                    ifLeft = { domainError ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse.fromDomainError(domainError)
                        )
                    },
                    ifRight = { command ->
                        dependencies.createUserUseCase(command).fold(
                            ifLeft = { error ->
                                call.respond(
                                    error.statusCode(),
                                    ApiErrorResponse.fromUseCaseError(error)
                                )
                            },
                            ifRight = { result ->
                                call.respond(
                                    HttpStatusCode.Created,
                                    result.user.toResponse()
                                )
                            }
                        )
                    }
                )
            }

            post("/chats/with-agent") {
                val request = call.receive<CreateChatWithAgentRequest>()

                either {
                    val command = com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand(
                        userId = UserId.create(request.userId).bind(),
                        message = request.toUserMessage()
                    )

                    command
                }.fold(
                    ifLeft = { domainError ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse.fromDomainError(domainError)
                        )
                    },
                    ifRight = { command ->
                        dependencies.createChatWithAgentUseCase(command).fold(
                            ifLeft = { error ->
                                call.respond(
                                    error.statusCode(),
                                    ApiErrorResponse.fromUseCaseError(error)
                                )
                            },
                            ifRight = { result ->
                                call.respond(
                                    HttpStatusCode.Created,
                                    result.chat.toResponse()
                                )
                            }
                        )
                    }
                )
            }

            post("/chats/{chatId}/continue") {
                val chatIdRaw = call.parameters["chatId"]
                val request = call.receive<ContinueChatWithAgentRequest>()

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
                    val command = com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand(
                        chatId = ChatId.create(chatIdRaw).bind(),
                        userId = UserId.create(request.userId).bind(),
                        message = request.toUserMessage()
                    )

                    command
                }.fold(
                    ifLeft = { domainError ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse.fromDomainError(domainError)
                        )
                    },
                    ifRight = { command ->
                        dependencies.continueChatWithAgentUseCase(command).fold(
                            ifLeft = { error ->
                                call.respond(
                                    error.statusCode(),
                                    ApiErrorResponse.fromUseCaseError(error)
                                )
                            },
                            ifRight = { result ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    result.chat.toResponse()
                                )
                            }
                        )
                    }
                )
            }

            get("/users/{userId}/chats") {
                val userIdRaw = call.parameters["userId"]

                if (userIdRaw == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse(
                            code = "missing_user_id",
                            message = "Path parameter 'userId' is required"
                        )
                    )
                    return@get
                }

                UserId.create(userIdRaw).fold(
                    ifLeft = { domainError ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse.fromDomainError(domainError)
                        )
                    },
                    ifRight = { userId ->
                        dependencies.listChatsUseCase(
                            com.gtu.aiassistant.domain.chat.port.input.ListChatsQuery(
                                userId = userId
                            )
                        ).fold(
                            ifLeft = { error ->
                                call.respond(
                                    error.statusCode(),
                                    ApiErrorResponse.fromUseCaseError(error)
                                )
                            },
                            ifRight = { result ->
                                call.respond(
                                    HttpStatusCode.OK,
                                    ListChatsResponse(
                                        chats = result.chats.map { it.toResponse() }
                                    )
                                )
                            }
                        )
                    }
                )
            }

            delete("/users/{userId}/chats/{chatId}") {
                val userIdRaw = call.parameters["userId"]
                val chatIdRaw = call.parameters["chatId"]

                if (userIdRaw == null || chatIdRaw == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse(
                            code = "missing_path_parameter",
                            message = "Path parameters 'userId' and 'chatId' are required"
                        )
                    )
                    return@delete
                }

                either {
                    val command = com.gtu.aiassistant.domain.chat.port.input.DeleteChatCommand(
                        userId = UserId.create(userIdRaw).bind(),
                        chatId = ChatId.create(chatIdRaw).bind()
                    )

                    command
                }.fold(
                    ifLeft = { domainError ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse.fromDomainError(domainError)
                        )
                    },
                    ifRight = { command ->
                        dependencies.deleteChatUseCase(command).fold(
                            ifLeft = { error ->
                                call.respond(
                                    error.statusCode(),
                                    ApiErrorResponse.fromUseCaseError(error)
                                )
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
                createdAt = message.createdAt.toString()
            )
        }
    )

private fun com.gtu.aiassistant.domain.user.port.input.CreateUserError.statusCode(): HttpStatusCode =
    when (this) {
        is com.gtu.aiassistant.domain.user.port.input.CreateUserError.InvalidDomainState -> HttpStatusCode.BadRequest
        is com.gtu.aiassistant.domain.user.port.input.CreateUserError.PersistenceFailed -> HttpStatusCode.InternalServerError
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

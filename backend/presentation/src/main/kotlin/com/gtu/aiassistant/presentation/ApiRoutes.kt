package com.gtu.aiassistant.presentation

import arrow.core.raise.either
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.model.isViewableHtmlArtifact
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.ChatSources
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPassword
import com.gtu.aiassistant.shared.*
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.header
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ClosedWriteChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Writer
import java.time.Instant
import java.util.UUID
import io.ktor.utils.io.toByteArray
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val ndjsonJson = Json { encodeDefaults = false }
private val routesLogger = LoggerFactory.getLogger("com.gtu.aiassistant.presentation.ApiRoutes")
private const val STREAM_HEARTBEAT_PACKET = "{\"h\":true}\n"
private const val STREAM_HEARTBEAT_INTERVAL_MS = 5_000L

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
                            call.respond(HttpStatusCode.BadRequest, fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.registerUserUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), fromUseCaseError(error))
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
                            call.respond(HttpStatusCode.BadRequest, fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.loginInUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), fromUseCaseError(error))
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

                    routesLogger.info(
                        "create chat requested userId={} sources={} textLength={} collectionCount={} documentCount={}",
                        principal.userId.value,
                        request.sources,
                        request.originalText.length,
                        request.collectionIds.size,
                        request.documentIds.size
                    )

                    request.toCommand(principal.userId).fold(
                        ifLeft = { domainError ->
                            call.respond(HttpStatusCode.BadRequest, fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.createChatWithAgentUseCase(command).fold(
                                ifLeft = { error ->
                                    routesLogger.warn("create chat failed userId={} error={}", principal.userId.value, error)
                                    call.respond(error.statusCode(), fromUseCaseError(error))
                                },
                                ifRight = { result ->
                                    routesLogger.info(
                                        "create chat succeeded userId={} chatId={} messageCount={}",
                                        principal.userId.value,
                                        result.chat.id.value,
                                        result.chat.messages.size
                                    )
                                    call.respond(HttpStatusCode.Created, result.chat.toResponse())
                                }
                            )
                        }
                    )
                }

                post("/chats/with-agent/stream") {
                    val request = call.receive<CreateChatWithAgentRequest>()
                    val principal = call.principal<AuthenticatedUserPrincipal>()

                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@post
                    }

                    routesLogger.info(
                        "create chat stream requested userId={} sources={} textLength={} collectionCount={} documentCount={}",
                        principal.userId.value,
                        request.sources,
                        request.originalText.length,
                        request.collectionIds.size,
                        request.documentIds.size
                    )

                    call.respondTextWriter(
                        contentType = ContentType("application", "x-ndjson"),
                        status = HttpStatusCode.OK
                    ) {
                        request.toCommand(principal.userId).fold(
                            ifLeft = { domainError ->
                                write("{\"e\":${Json.encodeToString(domainError.toString())}}\n")
                                flush()
                            },
                            ifRight = { command ->
                                var tokenCount = 0
                                var clientDisconnected = false
                                val writeLock = ReentrantLock()
                                fun writePacket(packet: String) = writeNdjsonPacket(packet, writeLock)

                                coroutineScope {
                                    val heartbeatJob = launch {
                                        while (true) {
                                            delay(STREAM_HEARTBEAT_INTERVAL_MS)
                                            try {
                                                writePacket(STREAM_HEARTBEAT_PACKET)
                                            } catch (error: ClosedWriteChannelException) {
                                                clientDisconnected = true
                                                throw error
                                            }
                                        }
                                    }
                                    try {
                                        writePacket(STREAM_HEARTBEAT_PACKET)
                                        dependencies.createChatWithAgentUseCase.stream(
                                            command = command,
                                            onToken = { token ->
                                                tokenCount += 1
                                                try {
                                                    writePacket("{\"t\":${Json.encodeToString(token)}}\n")
                                                } catch (error: ClosedWriteChannelException) {
                                                    clientDisconnected = true
                                                    throw error
                                                }
                                            },
                                            onStatus = { status ->
                                                try {
                                                    writePacket(buildStreamStatusJson(status))
                                                } catch (error: ClosedWriteChannelException) {
                                                    clientDisconnected = true
                                                    throw error
                                                }
                                            }
                                        ).fold(
                                            ifLeft = { error ->
                                                if (clientDisconnected) {
                                                    routesLogger.info(
                                                        "create chat stream cancelled userId={} tokens={} reason=client_disconnected",
                                                        principal.userId.value,
                                                        tokenCount
                                                    )
                                                } else {
                                                    routesLogger.warn(
                                                        "create chat stream failed userId={} tokens={} error={}",
                                                        principal.userId.value,
                                                        tokenCount,
                                                        error
                                                    )
                                                    writePacket("{\"e\":${Json.encodeToString(error.toString())}}\n")
                                                }
                                            },
                                            ifRight = { result ->
                                                routesLogger.info(
                                                    "create chat stream succeeded userId={} chatId={} tokens={} messageCount={}",
                                                    principal.userId.value,
                                                    result.chat.id.value,
                                                    tokenCount,
                                                    result.chat.messages.size
                                                )
                                                val finalJson = buildChatFinalJson(result.chat.toResponse())
                                                writePacket("{\"d\":$finalJson}\n")
                                            }
                                        )
                                    } catch (_: ClosedWriteChannelException) {
                                        routesLogger.info(
                                            "create chat stream cancelled userId={} tokens={} reason=client_disconnected",
                                            principal.userId.value,
                                            tokenCount
                                        )
                                    } finally {
                                        heartbeatJob.cancel()
                                    }
                                }
                            }
                        )
                    }
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

                    routesLogger.info(
                        "continue chat requested userId={} chatId={} sources={} textLength={} collectionCount={} documentCount={}",
                        principal.userId.value,
                        chatIdRaw,
                        request.sources,
                        request.originalText.length,
                        request.collectionIds.size,
                        request.documentIds.size
                    )

                    either {
                        com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand(
                            chatId = ChatId.create(chatIdRaw).bind(),
                            userId = principal.userId,
                            message = request.toUserMessage(),
                            sources = request.sources.toDomain(),
                            collectionIds = request.collectionIds.map { MaterialCollectionId.create(it).bind() },
                            documentIds = request.documentIds.map { MaterialDocumentId.create(it).bind() }
                        )
                    }.fold(
                        ifLeft = { domainError ->
                            call.respond(HttpStatusCode.BadRequest, fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.continueChatWithAgentUseCase(command).fold(
                                ifLeft = { error ->
                                    routesLogger.warn(
                                        "continue chat failed userId={} chatId={} error={}",
                                        principal.userId.value,
                                        chatIdRaw,
                                        error
                                    )
                                    call.respond(error.statusCode(), fromUseCaseError(error))
                                },
                                ifRight = { result ->
                                    routesLogger.info(
                                        "continue chat succeeded userId={} chatId={} messageCount={}",
                                        principal.userId.value,
                                        chatIdRaw,
                                        result.chat.messages.size
                                    )
                                    call.respond(HttpStatusCode.OK, result.chat.toResponse())
                                }
                            )
                        }
                    )
                }

                post("/chats/{chatId}/continue/stream") {
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

                    routesLogger.info(
                        "continue chat stream requested userId={} chatId={} sources={} textLength={} collectionCount={} documentCount={}",
                        principal.userId.value,
                        chatIdRaw,
                        request.sources,
                        request.originalText.length,
                        request.collectionIds.size,
                        request.documentIds.size
                    )

                    call.respondTextWriter(
                        contentType = ContentType("application", "x-ndjson"),
                        status = HttpStatusCode.OK
                    ) {
                        either {
                            com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand(
                                chatId = ChatId.create(chatIdRaw).bind(),
                                userId = principal.userId,
                                message = request.toUserMessage(),
                                sources = request.sources.toDomain(),
                                collectionIds = request.collectionIds.map { MaterialCollectionId.create(it).bind() },
                                documentIds = request.documentIds.map { MaterialDocumentId.create(it).bind() }
                            )
                        }.fold(
                            ifLeft = { domainError ->
                                write("{\"e\":${Json.encodeToString(domainError.toString())}}\n")
                                flush()
                            },
                            ifRight = { command ->
                                var tokenCount = 0
                                var clientDisconnected = false
                                val writeLock = ReentrantLock()
                                fun writePacket(packet: String) = writeNdjsonPacket(packet, writeLock)

                                coroutineScope {
                                    val heartbeatJob = launch {
                                        while (true) {
                                            delay(STREAM_HEARTBEAT_INTERVAL_MS)
                                            try {
                                                writePacket(STREAM_HEARTBEAT_PACKET)
                                            } catch (error: ClosedWriteChannelException) {
                                                clientDisconnected = true
                                                throw error
                                            }
                                        }
                                    }
                                    try {
                                        writePacket(STREAM_HEARTBEAT_PACKET)
                                        dependencies.continueChatWithAgentUseCase.stream(
                                            command = command,
                                            onToken = { token ->
                                                tokenCount += 1
                                                try {
                                                    writePacket("{\"t\":${Json.encodeToString(token)}}\n")
                                                } catch (error: ClosedWriteChannelException) {
                                                    clientDisconnected = true
                                                    throw error
                                                }
                                            },
                                            onStatus = { status ->
                                                try {
                                                    writePacket(buildStreamStatusJson(status))
                                                } catch (error: ClosedWriteChannelException) {
                                                    clientDisconnected = true
                                                    throw error
                                                }
                                            }
                                        ).fold(
                                            ifLeft = { error ->
                                                if (clientDisconnected) {
                                                    routesLogger.info(
                                                        "continue chat stream cancelled userId={} chatId={} tokens={} reason=client_disconnected",
                                                        principal.userId.value,
                                                        chatIdRaw,
                                                        tokenCount
                                                    )
                                                } else {
                                                    routesLogger.warn(
                                                        "continue chat stream failed userId={} chatId={} tokens={} error={}",
                                                        principal.userId.value,
                                                        chatIdRaw,
                                                        tokenCount,
                                                        error
                                                    )
                                                    writePacket("{\"e\":${Json.encodeToString(error.toString())}}\n")
                                                }
                                            },
                                            ifRight = { result ->
                                                routesLogger.info(
                                                    "continue chat stream succeeded userId={} chatId={} tokens={} messageCount={}",
                                                    principal.userId.value,
                                                    chatIdRaw,
                                                    tokenCount,
                                                    result.chat.messages.size
                                                )
                                                val finalJson = buildChatFinalJson(result.chat.toResponse())
                                                writePacket("{\"d\":$finalJson}\n")
                                            }
                                        )
                                    } catch (_: ClosedWriteChannelException) {
                                        routesLogger.info(
                                            "continue chat stream cancelled userId={} chatId={} tokens={} reason=client_disconnected",
                                            principal.userId.value,
                                            chatIdRaw,
                                            tokenCount
                                        )
                                    } finally {
                                        heartbeatJob.cancel()
                                    }
                                }
                            }
                        )
                    }
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
                            call.respond(error.statusCode(), fromUseCaseError(error))
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
                            call.respond(HttpStatusCode.BadRequest, fromDomainError(domainError))
                        },
                        ifRight = { command ->
                            dependencies.deleteChatUseCase(command).fold(
                                ifLeft = { error ->
                                    call.respond(error.statusCode(), fromUseCaseError(error))
                                },
                                ifRight = {
                                    call.respond(HttpStatusCode.OK, DeleteChatResponse(deleted = true))
                                }
                            )
                        }
                    )
                }

                post("/materials") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@post
                    }

                    val upload = call.receiveMaterialUpload() ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiErrorResponse(code = "missing_file", message = "Multipart file part is required")
                        )
                        return@post
                    }
                    val collectionId = upload.collectionIdRaw?.let { raw ->
                        MaterialCollectionId.create(raw).fold(
                            ifLeft = {
                                call.respond(HttpStatusCode.BadRequest, fromDomainError(it))
                                return@post
                            },
                            ifRight = { it }
                        )
                    }

                    dependencies.uploadMaterialUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.UploadMaterialCommand(
                            ownerUserId = principal.userId,
                            collectionId = collectionId,
                            originalFileName = upload.originalFileName,
                            contentType = upload.contentType,
                            sizeBytes = upload.bytes.size.toLong(),
                            bytes = upload.bytes
                        )
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result -> call.respond(HttpStatusCode.Created, result.document.toMaterialResponse()) }
                    )
                }

                get("/materials") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }
                    val collectionId = call.request.queryParameters["collectionId"]?.let { raw ->
                        MaterialCollectionId.create(raw).fold(
                            ifLeft = {
                                call.respond(HttpStatusCode.BadRequest, fromDomainError(it))
                                return@get
                            },
                            ifRight = { it }
                        )
                    }

                    dependencies.listMaterialsUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.ListMaterialsQuery(
                            ownerUserId = principal.userId,
                            collectionId = collectionId
                        )
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result ->
                            call.respond(HttpStatusCode.OK, ListMaterialsResponse(result.documents.map { it.toMaterialResponse() }))
                        }
                    )
                }

                get("/materials/{id}") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }
                    val documentId = call.materialDocumentIdOrRespond() ?: return@get

                    dependencies.downloadMaterialUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialQuery(principal.userId, documentId)
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result -> call.respond(HttpStatusCode.OK, result.document.toMaterialResponse()) }
                    )
                }

                get("/materials/{id}/download") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }
                    val documentId = call.materialDocumentIdOrRespond() ?: return@get

                    dependencies.downloadMaterialUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialQuery(principal.userId, documentId)
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result ->
                            call.response.headers.append(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment
                                    .withParameter(ContentDisposition.Parameters.FileName, result.document.originalFileName)
                                    .toString()
                            )
                            call.respondBytes(
                                bytes = result.bytes,
                                contentType = ContentType.parse(result.document.contentType),
                                status = HttpStatusCode.OK
                            )
                        }
                    )
                }

                delete("/materials/{id}") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@delete
                    }
                    val documentId = call.materialDocumentIdOrRespond() ?: return@delete

                    dependencies.deleteMaterialUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCommand(principal.userId, documentId)
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result -> call.respond(HttpStatusCode.OK, DeleteMaterialResponse(deleted = result.deleted)) }
                    )
                }

                post("/material-collections") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@post
                    }
                    val request = call.receive<CreateMaterialCollectionRequest>()

                    dependencies.createMaterialCollectionUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionCommand(
                            ownerUserId = principal.userId,
                            name = request.name
                        )
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result -> call.respond(HttpStatusCode.Created, result.collection.toMaterialCollectionResponse()) }
                    )
                }

                get("/material-collections") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }

                    dependencies.listMaterialCollectionsUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsQuery(principal.userId)
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result ->
                            call.respond(
                                HttpStatusCode.OK,
                                ListMaterialCollectionsResponse(result.collections.map { it.toMaterialCollectionResponse() })
                            )
                        }
                    )
                }

                delete("/material-collections/{id}") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@delete
                    }
                    val collectionId = call.materialCollectionIdOrRespond() ?: return@delete

                    dependencies.deleteMaterialCollectionUseCase(
                        com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionCommand(principal.userId, collectionId)
                    ).fold(
                        ifLeft = { error -> call.respond(error.statusCode(), fromUseCaseError(error)) },
                        ifRight = { result -> call.respond(HttpStatusCode.OK, DeleteMaterialCollectionResponse(deleted = result.deleted)) }
                    )
                }

                get("/artifacts/{artifactId}/download") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }

                    val artifactId = call.parameters["artifactId"]
                        ?.let { GeneratedArtifactId.create(it).getOrNull() }
                    if (artifactId == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("invalid_artifact_id", "Invalid artifact id"))
                        return@get
                    }

                    dependencies.readGeneratedArtifactContentPort(artifactId).fold(
                        ifLeft = { error ->
                            routesLogger.warn("artifact download failed userId={} artifactId={} error={}", principal.userId.value, artifactId.value, error)
                            call.respond(HttpStatusCode.InternalServerError, ApiErrorResponse("artifact_read_failed", "Failed to read artifact"))
                        },
                        ifRight = { content ->
                            if (content == null) {
                                call.respond(HttpStatusCode.NotFound, ApiErrorResponse("artifact_not_found", "Artifact not found"))
                                return@fold
                            }
                            if (content.artifact.ownerUserId != principal.userId) {
                                call.respond(HttpStatusCode.Forbidden, ApiErrorResponse("access_denied", "Access denied"))
                                return@fold
                            }
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    content.artifact.fileName
                                ).toString()
                            )
                            call.respondBytes(
                                bytes = content.bytes,
                                contentType = runCatching { ContentType.parse(content.artifact.contentType) }
                                    .getOrDefault(ContentType.Application.OctetStream)
                            )
                        }
                    )
                }

                get("/artifacts/{artifactId}/view") {
                    val principal = call.principal<AuthenticatedUserPrincipal>()
                    if (principal == null) {
                        call.respond(HttpStatusCode.Unauthorized, unauthorizedResponse())
                        return@get
                    }

                    val artifactId = call.parameters["artifactId"]
                        ?.let { GeneratedArtifactId.create(it).getOrNull() }
                    if (artifactId == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("invalid_artifact_id", "Invalid artifact id"))
                        return@get
                    }

                    dependencies.readGeneratedArtifactContentPort(artifactId).fold(
                        ifLeft = { error ->
                            routesLogger.warn("artifact view failed userId={} artifactId={} error={}", principal.userId.value, artifactId.value, error)
                            call.respond(HttpStatusCode.InternalServerError, ApiErrorResponse("artifact_read_failed", "Failed to read artifact"))
                        },
                        ifRight = { content ->
                            if (content == null) {
                                call.respond(HttpStatusCode.NotFound, ApiErrorResponse("artifact_not_found", "Artifact not found"))
                                return@fold
                            }
                            if (content.artifact.ownerUserId != principal.userId) {
                                call.respond(HttpStatusCode.Forbidden, ApiErrorResponse("access_denied", "Access denied"))
                                return@fold
                            }
                            if (!isViewableHtmlArtifact(content.artifact.fileName, content.artifact.contentType)) {
                                call.respond(HttpStatusCode.UnsupportedMediaType, ApiErrorResponse("unsupported_artifact_view", "Only HTML artifacts can be opened as pages"))
                                return@fold
                            }
                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Inline.withParameter(
                                    ContentDisposition.Parameters.FileName,
                                    content.artifact.fileName
                                ).toString()
                            )
                            call.response.header(
                                "Content-Security-Policy",
                                "sandbox allow-scripts allow-forms allow-popups allow-downloads; default-src 'self' data: blob: https:; script-src 'unsafe-inline' 'unsafe-eval' blob: https:; style-src 'unsafe-inline' https:; img-src data: blob: https:; font-src data: https:; connect-src https:; frame-ancestors 'none'; base-uri 'none'"
                            )
                            call.response.header("Referrer-Policy", "no-referrer")
                            call.response.header("X-Content-Type-Options", "nosniff")
                            call.respondBytes(
                                bytes = content.bytes,
                                contentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)
                            )
                        }
                    )
                }
            }
        }
    }
}

private data class ReceivedMaterialUpload(
    val originalFileName: String,
    val contentType: String,
    val collectionIdRaw: String?,
    val bytes: ByteArray
)

private suspend fun io.ktor.server.application.ApplicationCall.receiveMaterialUpload(): ReceivedMaterialUpload? {
    var fileName: String? = null
    var contentType: String? = null
    var collectionId: String? = null
    var bytes: ByteArray? = null

    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                if (bytes == null) {
                    fileName = part.originalFileName
                    contentType = part.contentType?.toString() ?: "application/octet-stream"
                    bytes = part.provider().toByteArray()
                }
            }

            is PartData.FormItem -> {
                if (part.name == "collectionId") {
                    collectionId = part.value.takeIf { it.isNotBlank() }
                }
            }

            else -> Unit
        }
        part.dispose()
    }

    val receivedFileName = fileName?.takeIf { it.isNotBlank() } ?: return null
    val receivedBytes = bytes ?: return null
    return ReceivedMaterialUpload(
        originalFileName = receivedFileName,
        contentType = contentType ?: "application/octet-stream",
        collectionIdRaw = collectionId,
        bytes = receivedBytes
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.materialDocumentIdOrRespond(): MaterialDocumentId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(
            HttpStatusCode.BadRequest,
            ApiErrorResponse(code = "missing_material_id", message = "Path parameter 'id' is required")
        )
        return null
    }
    return MaterialDocumentId.create(raw).fold(
        ifLeft = {
            respond(HttpStatusCode.BadRequest, fromDomainError(it))
            null
        },
        ifRight = { it }
    )
}

private suspend fun io.ktor.server.application.ApplicationCall.materialCollectionIdOrRespond(): MaterialCollectionId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(
            HttpStatusCode.BadRequest,
            ApiErrorResponse(code = "missing_collection_id", message = "Path parameter 'id' is required")
        )
        return null
    }
    return MaterialCollectionId.create(raw).fold(
        ifLeft = {
            respond(HttpStatusCode.BadRequest, fromDomainError(it))
            null
        },
        ifRight = { it }
    )
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

private fun CreateChatWithAgentRequest.toCommand(
    userId: com.gtu.aiassistant.domain.user.model.UserId
): arrow.core.Either<com.gtu.aiassistant.domain.model.DomainError, com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand> =
    either {
        com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand(
            userId = userId,
            message = toUserMessage(),
            sources = sources.toDomain(),
            collectionIds = collectionIds.map { MaterialCollectionId.create(it).bind() },
            documentIds = documentIds.map { MaterialDocumentId.create(it).bind() }
        )
    }

private fun AgentSources.toDomain(): ChatSources =
    ChatSources(gtu = gtu, materials = materials, web = web)

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
                        url = citation.toResponseUrl(),
                        snippet = citation.snippet,
                        sourceType = citation.sourceType.name,
                        documentId = citation.documentId?.value?.toString(),
                        pageStart = citation.pageStart,
                        pageEnd = citation.pageEnd
                    )
                },
                artifacts = message.artifacts.map { artifact ->
                    ArtifactResponse(
                        id = artifact.id.value.toString(),
                        fileName = artifact.fileName,
                        contentType = artifact.contentType,
                        sizeBytes = artifact.sizeBytes,
                        downloadUrl = artifact.downloadUrl,
                        viewUrl = artifact.viewUrl
                    )
                }
            )
        }
    )

private fun MaterialDocument.toMaterialResponse(): MaterialResponse =
    MaterialResponse(
        id = id.value.toString(),
        version = version,
        ownerUserId = ownerUserId.value.toString(),
        collectionId = collectionId?.value?.toString(),
        title = title,
        originalFileName = originalFileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        ingestionStatus = ingestionStatus.name,
        ingestionError = ingestionError,
        ocrMetadata = ocrMetadata,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

private fun MaterialCollection.toMaterialCollectionResponse(): MaterialCollectionResponse =
    MaterialCollectionResponse(
        id = id.value.toString(),
        version = version,
        ownerUserId = ownerUserId.value.toString(),
        name = name,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )

private fun com.gtu.aiassistant.domain.chat.model.MessageCitation.toResponseUrl(): String =
    documentId
        ?.takeIf { sourceType == MessageCitationSourceType.USER_MATERIAL }
        ?.let { materialDocumentId ->
            "/api/materials/${materialDocumentId.value}/download"
        }
        ?: url

private fun buildChatFinalJson(response: ChatResponse): String =
    ndjsonJson.encodeToString(response)

private fun buildStreamStatusJson(status: GenerateMessageStreamStatus): String =
    "{\"s\":{\"phase\":${Json.encodeToString(status.phase)},\"message\":${Json.encodeToString(status.message)}}}\n"

private fun Writer.writeNdjsonPacket(packet: String, lock: ReentrantLock) {
    lock.withLock {
        write(packet)
        flush()
    }
}

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

private fun com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.UnsupportedFileType -> HttpStatusCode.BadRequest
        com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.FileIsEmpty -> HttpStatusCode.BadRequest
        com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.FileTooLarge -> HttpStatusCode.BadRequest
        com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.CollectionNotFound -> HttpStatusCode.NotFound
        is com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.InvalidDomainState -> HttpStatusCode.BadRequest
        is com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.PersistenceFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError.StorageFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.materials.port.input.ListMaterialsError.statusCode(): HttpStatusCode =
    when (this) {
        is com.gtu.aiassistant.domain.materials.port.input.ListMaterialsError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialError.DocumentNotFound -> HttpStatusCode.NotFound
        is com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialError.PersistenceFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialError.StorageFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialError.DocumentNotFound -> HttpStatusCode.NotFound
        is com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialError.PersistenceFailed -> HttpStatusCode.InternalServerError
        is com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialError.StorageFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionError.statusCode(): HttpStatusCode =
    when (this) {
        is com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionError.InvalidDomainState -> HttpStatusCode.BadRequest
        is com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsError.statusCode(): HttpStatusCode =
    when (this) {
        is com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

private fun com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionError.statusCode(): HttpStatusCode =
    when (this) {
        com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionError.CollectionNotFound -> HttpStatusCode.NotFound
        com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionError.CollectionIsNotEmpty -> HttpStatusCode.Conflict
        is com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionError.PersistenceFailed -> HttpStatusCode.InternalServerError
    }

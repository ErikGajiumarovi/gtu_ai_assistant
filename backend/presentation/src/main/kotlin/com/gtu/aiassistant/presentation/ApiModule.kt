package com.gtu.aiassistant.presentation

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.shared.ApiErrorResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

data class ApiDependencies(
    val registerUserUseCase: com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase,
    val loginInUseCase: com.gtu.aiassistant.domain.user.port.input.LoginInUseCase,
    val createChatWithAgentUseCase: com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase,
    val continueChatWithAgentUseCase: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase,
    val listChatsUseCase: com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase,
    val deleteChatUseCase: com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase,
    val uploadMaterialUseCase: com.gtu.aiassistant.domain.materials.port.input.UploadMaterialUseCase,
    val listMaterialsUseCase: com.gtu.aiassistant.domain.materials.port.input.ListMaterialsUseCase,
    val downloadMaterialUseCase: com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialUseCase,
    val deleteMaterialUseCase: com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialUseCase,
    val createMaterialCollectionUseCase: com.gtu.aiassistant.domain.materials.port.input.CreateMaterialCollectionUseCase,
    val listMaterialCollectionsUseCase: com.gtu.aiassistant.domain.materials.port.input.ListMaterialCollectionsUseCase,
    val deleteMaterialCollectionUseCase: com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCollectionUseCase,
    val readGeneratedArtifactContentPort: com.gtu.aiassistant.domain.artifacts.port.output.ReadGeneratedArtifactContentPort,
    val jwtSecret: String,
    val jwtIssuer: String
)

data class AuthenticatedUserPrincipal(
    val userId: UserId,
    val email: UserEmail
)

fun Application.configureApi(
    dependencies: ApiDependencies
) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
        )
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "gtu-ai-assistant"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(dependencies.jwtSecret))
                    .withIssuer(dependencies.jwtIssuer)
                    .build()
            )
            validate { credential ->
                val subject = credential.payload.subject ?: return@validate null
                val emailClaim = credential.payload.getClaim("email").asString() ?: return@validate null

                val userId = UserId.create(subject).getOrNull() ?: return@validate null
                val email = UserEmail.create(emailClaim).getOrNull() ?: return@validate null

                AuthenticatedUserPrincipal(
                    userId = userId,
                    email = email
                )
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse(
                        code = "unauthorized",
                        message = "Missing or invalid bearer token"
                    )
                )
            }
        }
    }

    configureRoutes(dependencies)
}

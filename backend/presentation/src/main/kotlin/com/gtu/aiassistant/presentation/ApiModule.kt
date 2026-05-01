package com.gtu.aiassistant.presentation

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

data class ApiDependencies(
    val createUserUseCase: com.gtu.aiassistant.domain.user.port.input.CreateUserUseCase,
    val createChatWithAgentUseCase: com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase,
    val continueChatWithAgentUseCase: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase,
    val listChatsUseCase: com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase,
    val deleteChatUseCase: com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase
)

fun Application.configureApi(
    dependencies: ApiDependencies
) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }
        )
    }

    configureRoutes(dependencies)
}

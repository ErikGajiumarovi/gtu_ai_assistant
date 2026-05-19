package com.gtu.aiassistant.frontend

import com.gtu.aiassistant.shared.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiClientError(
    message: String,
    val code: String,
    val status: Int
) : Exception(message)

class ApiClient(private val baseUrl: String = "") {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod,
        body: Any? = null
    ): T {
        val response = httpClient.request("$baseUrl$path") {
            this.method = method
            contentType(ContentType.Application.Json)
            if (authToken != null) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
            if (body != null) {
                setBody(body)
            }
        }

        if (!response.status.isSuccess()) {
            val errorBody = try {
                response.body<ApiErrorResponse>()
            } catch (_: Exception) {
                null
            }
            throw ApiClientError(
                message = errorBody?.message ?: "HTTP ${response.status.value}",
                code = errorBody?.code ?: "transport_error",
                status = response.status.value
            )
        }

        return response.body()
    }

    suspend fun checkHealth(): HealthResponse =
        request("/health", HttpMethod.Get)

    suspend fun registerUser(payload: RegisterUserRequest): UserResponse =
        request("/api/auth/register", HttpMethod.Post, payload)

    suspend fun login(payload: LoginInRequest): LoginInResponse =
        request("/api/auth/login", HttpMethod.Post, payload)

    suspend fun createChatWithAgent(payload: CreateChatWithAgentRequest): ChatResponse =
        request("/api/chats/with-agent", HttpMethod.Post, payload)

    suspend fun continueChatWithAgent(
        chatId: String,
        payload: ContinueChatWithAgentRequest
    ): ChatResponse =
        request("/api/chats/$chatId/continue", HttpMethod.Post, payload)

    suspend fun listChats(): List<ChatResponse> {
        val response: ListChatsResponse = request("/api/chats", HttpMethod.Get)
        return response.chats
    }

    suspend fun deleteChat(chatId: String): DeleteChatResponse =
        request("/api/chats/$chatId", HttpMethod.Delete)
}

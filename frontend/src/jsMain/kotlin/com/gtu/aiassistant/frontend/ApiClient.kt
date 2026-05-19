package com.gtu.aiassistant.frontend

import com.gtu.aiassistant.shared.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import kotlin.js.Promise

class ApiClientError(
    message: String,
    val code: String,
    val status: Int
) : Exception(message)

@JsName("ReadableStream")
private external class JsReadableStream {
    fun getReader(): JsReadableStreamDefaultReader
}

@JsName("ReadableStreamDefaultReader")
private external class JsReadableStreamDefaultReader {
    fun read(): Promise<JsReadResult>
}

@JsName("ReadableStreamReadResult")
private external class JsReadResult {
    val done: Boolean
    val value: dynamic
}

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

    suspend fun createChatWithAgentStream(
        payload: CreateChatWithAgentRequest,
        onToken: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        streamFromEndpoint(
            path = "/api/chats/with-agent/stream",
            body = json.encodeToString(payload),
            onToken = onToken,
            onDone = onDone,
            onError = onError
        )
    }

    suspend fun continueChatWithAgentStream(
        chatId: String,
        payload: ContinueChatWithAgentRequest,
        onToken: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        streamFromEndpoint(
            path = "/api/chats/$chatId/continue/stream",
            body = json.encodeToString(payload),
            onToken = onToken,
            onDone = onDone,
            onError = onError
        )
    }

    private suspend fun streamFromEndpoint(
        path: String,
        body: String,
        onToken: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val headers = Headers().also {
                it.set("Content-Type", "application/json")
                authToken?.let { token -> it.set("Authorization", "Bearer $token") }
            }

            val response = window.fetch(
                "$baseUrl$path",
                RequestInit(method = "POST", body = body, headers = headers)
            ).await()

            if (!response.ok) {
                val errorText = response.text().await()
                onError(ApiClientError(errorText, "stream_error", response.status.toInt()))
                return
            }

            val stream: JsReadableStream = response.body.unsafeCast<JsReadableStream>()
            val reader = stream.getReader()
            val decoder = js("new TextDecoder()")
            var buffer = ""

            while (true) {
                val result = reader.read().await()
                if (result.done) break

                val chunk: String = decoder.decode(result.value).unsafeCast<String>()
                buffer += chunk

                val lines = buffer.split("\n")
                val hasTrailingNewline = buffer.endsWith("\n")
                val completeLines = if (hasTrailingNewline) lines else lines.dropLast(1)
                buffer = if (hasTrailingNewline) "" else lines.last()

                for (line in completeLines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    processLine(trimmed, onToken, onDone, onError)
                }
            }

            if (buffer.isNotBlank()) {
                processLine(buffer.trim(), onToken, onDone, onError)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun processLine(
        line: String,
        onToken: (String) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val obj = json.parseToJsonElement(line).jsonObject
            val token = obj["t"]?.jsonPrimitive?.content
            if (token != null) {
                onToken(token)
                return
            }
            val doneData = obj["d"]
            if (doneData != null) {
                val chat = json.decodeFromString<ChatResponse>(doneData.toString())
                onDone(chat)
                return
            }
            val errorMsg = obj["e"]?.jsonPrimitive?.content
            if (errorMsg != null) {
                onError(ApiClientError(errorMsg, "stream_error", 500))
                return
            }
        } catch (e: Exception) {
            onError(ApiClientError("Parse error: ${e.message}", "parse_error", 500))
        }
    }
}

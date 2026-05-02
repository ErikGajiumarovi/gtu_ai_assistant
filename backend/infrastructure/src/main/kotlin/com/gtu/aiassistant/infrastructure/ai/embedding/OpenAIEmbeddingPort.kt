package com.gtu.aiassistant.infrastructure.ai.embedding

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OpenAIEmbeddingPort(
    private val config: EmbeddingConfig,
    private val client: HttpClient
) : EmbeddingPort {
    override suspend fun invoke(text: String): Either<InfrastructureError, List<Float>> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val apiKey = requireNotNull(config.apiKey?.takeIf { it.isNotBlank() }) {
                    "Embedding API key is required when APP_EMBEDDING_MODE=openai"
                }
                val payload = buildJsonObject {
                    put("model", config.model)
                    put("input", text)
                    put("dimensions", config.dimensions)
                }

                val response = client
                    .post("${config.baseUrl.openAiRoot()}/embeddings") {
                        bearerAuth(apiKey)
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(payload.toString())
                    }
                    .bodyAsText()

                Json.parseToJsonElement(response)
                    .jsonObject
                    .getValue("data")
                    .jsonArray
                    .first()
                    .jsonObject
                    .getValue("embedding")
                    .jsonArray
                    .map { it.jsonPrimitive.content.toFloat() }
            }.mapLeft(::InfrastructureError)
        }

    private fun String.openAiRoot(): String {
        val normalized = trimEnd('/')
        return if (normalized.endsWith("/v1")) normalized else "$normalized/v1"
    }
}

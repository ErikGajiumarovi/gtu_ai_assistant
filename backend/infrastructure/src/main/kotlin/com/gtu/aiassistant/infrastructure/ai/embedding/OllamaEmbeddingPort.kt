package com.gtu.aiassistant.infrastructure.ai.embedding

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import io.ktor.client.HttpClient
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

class OllamaEmbeddingPort(
    private val config: EmbeddingConfig,
    private val client: HttpClient
) : EmbeddingPort {
    override suspend fun invoke(text: String): Either<InfrastructureError, List<Float>> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val payload = buildJsonObject {
                    put("model", config.model)
                    put("input", text)
                    put("dimensions", config.dimensions)
                }

                val response = client
                    .post("${config.baseUrl.trimEnd('/')}/api/embed") {
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(payload.toString())
                    }
                    .bodyAsText()

                parseOllamaEmbeddingResponse(response, config.dimensions)
            }.mapLeft(::InfrastructureError)
        }
}

internal fun parseOllamaEmbeddingResponse(response: String, expectedDimensions: Int): List<Float> {
    val embeddings = Json.parseToJsonElement(response)
        .jsonObject
        .getValue("embeddings")
        .jsonArray

    require(embeddings.isNotEmpty()) {
        "Ollama embedding response does not contain embeddings"
    }

    val embedding = embeddings
        .first()
        .jsonArray
        .map { it.jsonPrimitive.content.toFloat() }

    require(embedding.size == expectedDimensions) {
        "Ollama embedding dimensions mismatch: expected $expectedDimensions, got ${embedding.size}"
    }

    return embedding
}

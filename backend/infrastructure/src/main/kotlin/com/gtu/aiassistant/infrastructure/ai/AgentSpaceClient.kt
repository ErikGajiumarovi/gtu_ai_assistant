package com.gtu.aiassistant.infrastructure.ai

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AgentSpaceConfig(
    val baseUrl: String?,
    val token: String?,
    val defaultTimeoutSeconds: Int,
    val outputLimitChars: Int
)

class AgentSpaceClient(
    private val client: HttpClient,
    private val config: AgentSpaceConfig
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun capabilities(): Either<InfrastructureError, String> =
        requestConfigured {
            val response = client.get("${config.baseUrl}/capabilities") {
                header("X-Agent-Space-Token", config.token)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("agent_space capabilities failed with HTTP ${response.status.value}: ${body.truncateForTool()}")
            }
            body.truncateForTool()
        }

    suspend fun runForArtifact(
        mode: String,
        code: String,
        artifactPath: String,
        timeoutSeconds: Int? = null
    ): Either<InfrastructureError, AgentSpaceRunResponse> =
        run(mode = mode, code = code, artifactPaths = listOf(artifactPath), timeoutSeconds = timeoutSeconds)

    suspend fun run(
        mode: String,
        code: String,
        artifactPaths: List<String> = emptyList(),
        timeoutSeconds: Int? = null
    ): Either<InfrastructureError, AgentSpaceRunResponse> =
        requestConfigured {
            val normalizedMode = mode.trim().lowercase()
            require(normalizedMode in supportedModes) { "Unsupported mode '$mode'. Use one of: shell, python, node." }
            require(code.isNotBlank()) { "code must not be empty" }
            val effectiveTimeout = timeoutSeconds?.coerceIn(1, maxTimeoutSeconds)
                ?: config.defaultTimeoutSeconds.coerceIn(1, maxTimeoutSeconds)
            withTimeout((effectiveTimeout + 10).toLong() * 1000L) {
                val response = client.post("${config.baseUrl}/run") {
                    header("X-Agent-Space-Token", config.token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            AgentSpaceRunHttpRequest(
                                mode = normalizedMode,
                                code = code,
                                timeoutSeconds = effectiveTimeout,
                                artifactPaths = artifactPaths
                            )
                        )
                    )
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    error("agent_space run failed with HTTP ${response.status.value}: ${body.truncateForTool()}")
                }
                json.decodeFromString<AgentSpaceRunResponse>(body)
            }
        }

    private suspend fun <A> requestConfigured(block: suspend () -> A): Either<InfrastructureError, A> =
        Either.catch {
            require(!config.baseUrl.isNullOrBlank()) { "APP_AGENT_SPACE_BASE_URL is missing" }
            require(!config.token.isNullOrBlank()) { "APP_AGENT_SPACE_TOKEN is missing" }
            block()
        }.mapLeft(::InfrastructureError)

    private fun String.truncateForTool(): String =
        if (length <= config.outputLimitChars) this else take(config.outputLimitChars) + "\n[tool output truncated]"

    private companion object {
        val supportedModes = setOf("shell", "python", "node")
        const val maxTimeoutSeconds = 120
    }
}

@Serializable
private data class AgentSpaceRunHttpRequest(
    val mode: String,
    val code: String,
    val timeoutSeconds: Int,
    val artifactPaths: List<String> = emptyList()
)

@Serializable
data class AgentSpaceRunResponse(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val durationMs: Long,
    val artifacts: List<AgentSpaceArtifact> = emptyList()
) {
    fun summary(): String = buildString {
        appendLine("exitCode: $exitCode")
        appendLine("timedOut: $timedOut")
        appendLine("durationMs: $durationMs")
        if (stdout.isNotBlank()) {
            appendLine("stdout:")
            appendLine(stdout)
        }
        if (stderr.isNotBlank()) {
            appendLine("stderr:")
            appendLine(stderr)
        }
    }.trim()
}

@Serializable
data class AgentSpaceArtifact(
    val path: String,
    val sizeBytes: Long,
    val base64: String
)

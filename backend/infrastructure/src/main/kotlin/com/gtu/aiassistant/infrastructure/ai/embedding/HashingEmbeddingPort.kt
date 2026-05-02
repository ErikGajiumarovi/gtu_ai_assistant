package com.gtu.aiassistant.infrastructure.ai.embedding

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.sqrt

class HashingEmbeddingPort(
    private val dimensions: Int
) : EmbeddingPort {
    override suspend fun invoke(text: String): Either<InfrastructureError, List<Float>> =
        withContext(Dispatchers.Default) {
            Either.catch {
                require(dimensions > 0) { "Embedding dimensions must be positive" }

                val vector = FloatArray(dimensions)
                TOKEN_REGEX
                    .findAll(text.lowercase())
                    .map { it.value }
                    .forEach { token ->
                        val digest = SHA_256.get().digest(token.toByteArray())
                        val bucket = digest.take(4).fold(0) { acc, byte ->
                            (acc shl 8) or (byte.toInt() and 0xff)
                        }.let { Math.floorMod(it, dimensions) }
                        val sign = if ((digest[4].toInt() and 1) == 0) 1.0f else -1.0f
                        vector[bucket] += sign
                    }

                val norm = sqrt(vector.fold(0.0) { acc, value -> acc + value * value }).toFloat()
                if (norm == 0.0f) {
                    vector.toList()
                } else {
                    vector.map { it / norm }
                }
            }.mapLeft(::InfrastructureError)
        }

    companion object {
        private val TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")
        private val SHA_256 = ThreadLocal.withInitial {
            MessageDigest.getInstance("SHA-256")
        }
    }
}

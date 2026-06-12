package com.gtu.aiassistant.domain.artifacts.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.user.model.UserId
import java.time.Instant
import java.util.UUID

data class GeneratedArtifactId private constructor(
    val value: UUID
) {
    companion object {
        fun create(value: String): Either<DomainError, GeneratedArtifactId> = either {
            val uuid = Either.catch { UUID.fromString(value) }
                .mapLeft { InvalidGeneratedArtifactId }
                .bind()
            GeneratedArtifactId(uuid)
        }

        fun create(value: UUID): Either<DomainError, GeneratedArtifactId> = either {
            GeneratedArtifactId(value)
        }

        fun fromTrusted(value: UUID): GeneratedArtifactId = GeneratedArtifactId(value)
    }
}

data class GeneratedArtifact(
    val id: GeneratedArtifactId,
    val ownerUserId: UserId,
    val chatId: ChatId?,
    val messageId: UUID?,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val objectKey: String,
    val createdAt: Instant
)

data class MessageArtifact(
    val id: GeneratedArtifactId,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val viewUrl: String? = null
)

data class ArtifactContent(
    val artifact: GeneratedArtifact,
    val bytes: ByteArray
)

data class StoreGeneratedArtifactCommand(
    val ownerUserId: UserId,
    val chatId: ChatId?,
    val messageId: UUID?,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    val createdAt: Instant = Instant.now()
)

data object InvalidGeneratedArtifactId : DomainError

data object InvalidGeneratedArtifactFileName : DomainError

const val MAX_GENERATED_ARTIFACT_BYTES: Int = 25 * 1024 * 1024

fun normalizeGeneratedArtifactFileName(fileName: String): Either<DomainError, String> = either {
    val trimmed = fileName.trim()
    ensure(trimmed.isNotBlank()) { InvalidGeneratedArtifactFileName }
    ensure(trimmed.length <= 160) { InvalidGeneratedArtifactFileName }
    ensure(!trimmed.contains('/')) { InvalidGeneratedArtifactFileName }
    ensure(!trimmed.contains('\\')) { InvalidGeneratedArtifactFileName }
    ensure(trimmed.none { it.isISOControl() }) { InvalidGeneratedArtifactFileName }
    trimmed
}

fun isViewableHtmlArtifact(fileName: String, contentType: String): Boolean {
    val normalizedContentType = contentType.substringBefore(';').trim().lowercase()
    return normalizedContentType == "text/html" ||
        normalizedContentType == "application/xhtml+xml" ||
        fileName.trim().lowercase().endsWith(".html") ||
        fileName.trim().lowercase().endsWith(".htm")
}

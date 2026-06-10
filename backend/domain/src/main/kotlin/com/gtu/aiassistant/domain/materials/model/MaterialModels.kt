package com.gtu.aiassistant.domain.materials.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.AggregateRoot
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.user.model.UserId
import java.time.Instant
import java.util.UUID

class MaterialDocument private constructor(
    override val id: MaterialDocumentId,
    override val version: Long,
    val ownerUserId: UserId,
    val collectionId: MaterialCollectionId?,
    val title: String,
    val originalFileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val storageObjectKey: String,
    val ingestionStatus: MaterialIngestionStatus,
    val ingestionError: String?,
    val ocrMetadata: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) : AggregateRoot<MaterialDocumentId>(id, version) {
    companion object {
        fun create(
            id: MaterialDocumentId,
            version: Long,
            ownerUserId: UserId,
            collectionId: MaterialCollectionId?,
            title: String,
            originalFileName: String,
            contentType: String,
            sizeBytes: Long,
            storageObjectKey: String,
            ingestionStatus: MaterialIngestionStatus,
            ingestionError: String?,
            ocrMetadata: String?,
            createdAt: Instant,
            updatedAt: Instant
        ): Either<DomainError, MaterialDocument> =
            either {
                ensure(version >= 0L) { MaterialDocumentError.InvalidVersion }
                ensure(title.isNotBlank()) { MaterialDocumentError.BlankTitle }
                ensure(originalFileName.isNotBlank()) { MaterialDocumentError.BlankOriginalFileName }
                ensure(contentType.isNotBlank()) { MaterialDocumentError.BlankContentType }
                ensure(sizeBytes > 0L) { MaterialDocumentError.InvalidSize }
                ensure(storageObjectKey.isNotBlank()) { MaterialDocumentError.BlankStorageObjectKey }

                MaterialDocument(
                    id = id,
                    version = version,
                    ownerUserId = ownerUserId,
                    collectionId = collectionId,
                    title = title.trim(),
                    originalFileName = originalFileName.trim(),
                    contentType = contentType.trim(),
                    sizeBytes = sizeBytes,
                    storageObjectKey = storageObjectKey.trim(),
                    ingestionStatus = ingestionStatus,
                    ingestionError = ingestionError?.trim()?.takeIf(String::isNotBlank),
                    ocrMetadata = ocrMetadata?.trim()?.takeIf(String::isNotBlank),
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            }

        fun fromTrusted(
            id: MaterialDocumentId,
            version: Long,
            ownerUserId: UserId,
            collectionId: MaterialCollectionId?,
            title: String,
            originalFileName: String,
            contentType: String,
            sizeBytes: Long,
            storageObjectKey: String,
            ingestionStatus: MaterialIngestionStatus,
            ingestionError: String?,
            ocrMetadata: String?,
            createdAt: Instant,
            updatedAt: Instant
        ): MaterialDocument =
            MaterialDocument(
                id = id,
                version = version,
                ownerUserId = ownerUserId,
                collectionId = collectionId,
                title = title,
                originalFileName = originalFileName,
                contentType = contentType,
                sizeBytes = sizeBytes,
                storageObjectKey = storageObjectKey,
                ingestionStatus = ingestionStatus,
                ingestionError = ingestionError,
                ocrMetadata = ocrMetadata,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
    }
}

class MaterialCollection private constructor(
    override val id: MaterialCollectionId,
    override val version: Long,
    val ownerUserId: UserId,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant
) : AggregateRoot<MaterialCollectionId>(id, version) {
    companion object {
        fun create(
            id: MaterialCollectionId,
            version: Long,
            ownerUserId: UserId,
            name: String,
            createdAt: Instant,
            updatedAt: Instant
        ): Either<DomainError, MaterialCollection> =
            either {
                ensure(version >= 0L) { MaterialCollectionError.InvalidVersion }
                ensure(name.isNotBlank()) { MaterialCollectionError.BlankName }

                MaterialCollection(
                    id = id,
                    version = version,
                    ownerUserId = ownerUserId,
                    name = name.trim(),
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            }

        fun fromTrusted(
            id: MaterialCollectionId,
            version: Long,
            ownerUserId: UserId,
            name: String,
            createdAt: Instant,
            updatedAt: Instant
        ): MaterialCollection =
            MaterialCollection(
                id = id,
                version = version,
                ownerUserId = ownerUserId,
                name = name,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
    }
}

data class MaterialChunk(
    val id: UUID,
    val ownerUserId: UserId,
    val documentId: MaterialDocumentId,
    val collectionId: MaterialCollectionId?,
    val chunkIndex: Int,
    val text: String,
    val embedding: List<Float>,
    val headingPath: String?,
    val pageStart: Int?,
    val pageEnd: Int?
)

enum class MaterialIngestionStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}

data class MaterialSearchQuery(
    val ownerUserId: UserId,
    val text: String,
    val collectionIds: List<MaterialCollectionId> = emptyList(),
    val documentIds: List<MaterialDocumentId> = emptyList(),
    val maxResults: Int = 6,
    val minScore: Double = 0.2
)

data class MaterialSearchHit(
    val chunkId: UUID,
    val documentId: MaterialDocumentId,
    val collectionId: MaterialCollectionId?,
    val title: String,
    val snippet: String,
    val score: Double,
    val headingPath: String?,
    val pageStart: Int?,
    val pageEnd: Int?
)

sealed interface MaterialDocumentError : DomainError {
    data object InvalidVersion : MaterialDocumentError
    data object BlankTitle : MaterialDocumentError
    data object BlankOriginalFileName : MaterialDocumentError
    data object BlankContentType : MaterialDocumentError
    data object InvalidSize : MaterialDocumentError
    data object BlankStorageObjectKey : MaterialDocumentError
}

sealed interface MaterialCollectionError : DomainError {
    data object InvalidVersion : MaterialCollectionError
    data object BlankName : MaterialCollectionError
}

package com.gtu.aiassistant.application.materials

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialEmbeddingInput
import com.gtu.aiassistant.domain.materials.port.output.MaterialEmbeddingPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialObjectStoragePort
import com.gtu.aiassistant.domain.materials.port.output.ReplaceMaterialDocumentChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialDocumentPort
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import java.time.Instant
import java.util.UUID

class MaterialIngestionWorker(
    private val findMaterialDocumentPort: FindMaterialDocumentPort,
    private val saveMaterialDocumentPort: SaveMaterialDocumentPort,
    private val objectStoragePort: MaterialObjectStoragePort,
    private val textExtractionService: MaterialTextExtractionService,
    private val chunkBuilder: MaterialChunkBuilder,
    private val materialEmbeddingPort: MaterialEmbeddingPort,
    private val replaceMaterialDocumentChunksPort: ReplaceMaterialDocumentChunksPort,
    private val batchSize: Int = DEFAULT_BATCH_SIZE
) {
    suspend fun processOnce(): Either<MaterialIngestionWorkerError, MaterialIngestionWorkerReport> =
        either {
            val result = findMaterialDocumentPort(
                FindMaterialDocumentPort.Strategy.ByStatus(
                    status = MaterialIngestionStatus.UPLOADED,
                    limit = batchSize
                )
            ).mapLeft(MaterialIngestionWorkerError::PersistenceFailed).bind()
            val documents = (result as FindMaterialDocumentPort.Result.Multiple).documents

            var processed = 0
            var ready = 0
            var failed = 0
            var skipped = 0

            for (document in documents) {
                if (!requiresTextExtraction(document.originalFileName)) {
                    skipped += 1
                    continue
                }

                processed += 1
                if (processDocument(document)) {
                    ready += 1
                } else {
                    failed += 1
                }
            }

            MaterialIngestionWorkerReport(
                scanned = documents.size,
                processed = processed,
                ready = ready,
                failed = failed,
                skipped = skipped
            )
        }

    private suspend fun processDocument(document: MaterialDocument): Boolean {
        val processingDocument = document.withIngestionState(
            status = MaterialIngestionStatus.PROCESSING,
            error = null
        ).fold(
            ifLeft = { return markFailed(document, "Invalid material document state") },
            ifRight = { it }
        )

        val persistedProcessingDocument = saveMaterialDocumentPort(processingDocument).fold(
            ifLeft = { return false },
            ifRight = { it }
        )

        val materialObject = objectStoragePort.read(persistedProcessingDocument.storageObjectKey).fold(
            ifLeft = { return markFailed(persistedProcessingDocument, it.toIngestionError("Object storage read failed")) },
            ifRight = { it }
        )

        val extractionResult = textExtractionService.extract(
            fileName = persistedProcessingDocument.originalFileName,
            bytes = materialObject.bytes
        ).fold(
            ifLeft = { return markFailed(persistedProcessingDocument, it.toIngestionErrorMessage()) },
            ifRight = { it }
        )

        val candidates = chunkBuilder.build(persistedProcessingDocument, extractionResult)
        if (candidates.isEmpty()) {
            return markFailed(persistedProcessingDocument, "Chunking failed: extracted text produced no chunks")
        }

        val chunks = mutableListOf<MaterialChunk>()
        for (candidate in candidates) {
            val embedding = materialEmbeddingPort(MaterialEmbeddingInput(candidate.embeddingInput)).fold(
                ifLeft = { return markFailed(persistedProcessingDocument, it.toIngestionError("Embedding failed")) },
                ifRight = { it }
            )
            chunks += MaterialChunk(
                id = UUID.randomUUID(),
                ownerUserId = persistedProcessingDocument.ownerUserId,
                documentId = persistedProcessingDocument.id,
                collectionId = persistedProcessingDocument.collectionId,
                chunkIndex = candidate.chunkIndex,
                text = candidate.text,
                embedding = embedding,
                headingPath = candidate.headingPath,
                pageStart = candidate.pageStart,
                pageEnd = candidate.pageEnd
            )
        }

        replaceMaterialDocumentChunksPort(
            persistedProcessingDocument.ownerUserId,
            persistedProcessingDocument.id,
            chunks
        ).fold(
            ifLeft = { return markFailed(persistedProcessingDocument, it.toIngestionError("Chunk persistence failed")) },
            ifRight = {}
        )

        val readyDocument = persistedProcessingDocument.withIngestionState(
            status = MaterialIngestionStatus.READY,
            error = null,
            ocrMetadata = extractionResult.ocrMetadata?.toStorageString()
        ).fold(
            ifLeft = { return markFailed(persistedProcessingDocument, "Invalid material document state") },
            ifRight = { it }
        )

        return saveMaterialDocumentPort(readyDocument).fold(
            ifLeft = { false },
            ifRight = { true }
        )
    }

    private suspend fun markFailed(document: MaterialDocument, message: String): Boolean {
        val failedDocument = document.withIngestionState(
            status = MaterialIngestionStatus.FAILED,
            error = message.take(MAX_ERROR_LENGTH),
            ocrMetadata = null
        ).fold(
            ifLeft = { return false },
            ifRight = { it }
        )

        return saveMaterialDocumentPort(failedDocument).fold(
            ifLeft = { false },
            ifRight = { false }
        )
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 25
        private const val MAX_ERROR_LENGTH = 1_000
    }
}

data class MaterialIngestionWorkerReport(
    val scanned: Int,
    val processed: Int,
    val ready: Int,
    val failed: Int,
    val skipped: Int
)

sealed interface MaterialIngestionWorkerError {
    data class PersistenceFailed(val reason: InfrastructureError) : MaterialIngestionWorkerError
}

private fun MaterialDocument.withIngestionState(
    status: MaterialIngestionStatus,
    error: String?,
    ocrMetadata: String? = this.ocrMetadata
): Either<DomainError, MaterialDocument> =
    MaterialDocument.create(
        id = id,
        version = version + 1,
        ownerUserId = ownerUserId,
        collectionId = collectionId,
        title = title,
        originalFileName = originalFileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        storageObjectKey = storageObjectKey,
        ingestionStatus = status,
        ingestionError = error,
        ocrMetadata = ocrMetadata,
        createdAt = createdAt,
        updatedAt = Instant.now()
    )

private fun InfrastructureError.toIngestionError(prefix: String): String =
    cause.message
        ?.takeIf(String::isNotBlank)
        ?.let { "$prefix: $it" }
        ?: "$prefix: ${cause::class.simpleName ?: "unknown error"}"

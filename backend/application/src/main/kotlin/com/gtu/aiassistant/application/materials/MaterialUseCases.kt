package com.gtu.aiassistant.application.materials

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialCommand
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialError
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialResult
import com.gtu.aiassistant.domain.materials.port.input.DeleteMaterialUseCase
import com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialError
import com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialQuery
import com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialResult
import com.gtu.aiassistant.domain.materials.port.input.DownloadMaterialUseCase
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialsError
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialsQuery
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialsResult
import com.gtu.aiassistant.domain.materials.port.input.ListMaterialsUseCase
import com.gtu.aiassistant.domain.materials.port.input.UploadMaterialCommand
import com.gtu.aiassistant.domain.materials.port.input.UploadMaterialError
import com.gtu.aiassistant.domain.materials.port.input.UploadMaterialResult
import com.gtu.aiassistant.domain.materials.port.input.UploadMaterialUseCase
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialObjectStoragePort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialObjectCommand
import java.time.Instant
import java.util.Locale
import java.util.UUID

class UploadMaterialUseCaseImpl(
    private val objectStoragePort: MaterialObjectStoragePort,
    private val saveMaterialDocumentPort: SaveMaterialDocumentPort,
    private val findMaterialCollectionPort: FindMaterialCollectionPort,
    private val maxFileSizeBytes: Long
) : UploadMaterialUseCase {
    override suspend fun invoke(command: UploadMaterialCommand): Either<UploadMaterialError, UploadMaterialResult> =
        either {
            ensure(command.bytes.isNotEmpty() && command.sizeBytes > 0L) { UploadMaterialError.FileIsEmpty }
            ensure(command.sizeBytes <= maxFileSizeBytes && command.bytes.size.toLong() <= maxFileSizeBytes) {
                UploadMaterialError.FileTooLarge
            }
            ensure(isSupported(command.originalFileName, command.contentType)) { UploadMaterialError.UnsupportedFileType }

            val collectionId = command.collectionId
            if (collectionId != null) {
                val collectionResult = findMaterialCollectionPort(
                    FindMaterialCollectionPort.Strategy.ById(command.ownerUserId, collectionId)
                ).mapLeft(UploadMaterialError::PersistenceFailed).bind()
                ensure((collectionResult as FindMaterialCollectionPort.Result.Single).collection != null) {
                    UploadMaterialError.CollectionNotFound
                }
            }

            val documentId = MaterialDocumentId
                .create(UUID.randomUUID())
                .mapLeft(UploadMaterialError::InvalidDomainState)
                .bind()
            val storageResult = objectStoragePort
                .save(
                    SaveMaterialObjectCommand(
                        ownerUserId = command.ownerUserId,
                        originalFileName = command.originalFileName,
                        contentType = command.contentType,
                        bytes = command.bytes
                    )
                )
                .mapLeft(UploadMaterialError::StorageFailed)
                .bind()
            val now = Instant.now()
            val document = MaterialDocument
                .create(
                    id = documentId,
                    version = 0L,
                    ownerUserId = command.ownerUserId,
                    collectionId = command.collectionId,
                    title = command.originalFileName.substringBeforeLast('.', command.originalFileName).ifBlank { command.originalFileName },
                    originalFileName = command.originalFileName,
                    contentType = command.contentType,
                    sizeBytes = command.sizeBytes,
                    storageObjectKey = storageResult.key,
                    ingestionStatus = MaterialIngestionStatus.UPLOADED,
                    ingestionError = null,
                    ocrMetadata = null,
                    createdAt = now,
                    updatedAt = now
                )
                .mapLeft(UploadMaterialError::InvalidDomainState)
                .bind()
            val persistedDocument = saveMaterialDocumentPort(document)
                .mapLeft(UploadMaterialError::PersistenceFailed)
                .bind()

            UploadMaterialResult(document = persistedDocument)
        }
}

class ListMaterialsUseCaseImpl(
    private val findMaterialDocumentPort: FindMaterialDocumentPort
) : ListMaterialsUseCase {
    override suspend fun invoke(query: ListMaterialsQuery): Either<ListMaterialsError, ListMaterialsResult> =
        either {
            val result = findMaterialDocumentPort(
                FindMaterialDocumentPort.Strategy.ByOwner(query.ownerUserId, query.collectionId)
            ).mapLeft(ListMaterialsError::PersistenceFailed).bind()

            ListMaterialsResult(documents = (result as FindMaterialDocumentPort.Result.Multiple).documents)
        }
}

class DownloadMaterialUseCaseImpl(
    private val findMaterialDocumentPort: FindMaterialDocumentPort,
    private val objectStoragePort: MaterialObjectStoragePort
) : DownloadMaterialUseCase {
    override suspend fun invoke(query: DownloadMaterialQuery): Either<DownloadMaterialError, DownloadMaterialResult> =
        either {
            val result = findMaterialDocumentPort(
                FindMaterialDocumentPort.Strategy.ById(query.ownerUserId, query.documentId)
            ).mapLeft(DownloadMaterialError::PersistenceFailed).bind()
            val document = (result as FindMaterialDocumentPort.Result.Single).document
            ensure(document != null) { DownloadMaterialError.DocumentNotFound }
            val materialObject = objectStoragePort.read(document.storageObjectKey)
                .mapLeft(DownloadMaterialError::StorageFailed)
                .bind()

            DownloadMaterialResult(document = document, bytes = materialObject.bytes)
        }
}

class DeleteMaterialUseCaseImpl(
    private val findMaterialDocumentPort: FindMaterialDocumentPort,
    private val deleteMaterialDocumentPort: DeleteMaterialDocumentPort,
    private val deleteMaterialChunksPort: DeleteMaterialChunksPort,
    private val objectStoragePort: MaterialObjectStoragePort
) : DeleteMaterialUseCase {
    override suspend fun invoke(command: DeleteMaterialCommand): Either<DeleteMaterialError, DeleteMaterialResult> =
        either {
            val result = findMaterialDocumentPort(
                FindMaterialDocumentPort.Strategy.ById(command.ownerUserId, command.documentId)
            ).mapLeft(DeleteMaterialError::PersistenceFailed).bind()
            val document = (result as FindMaterialDocumentPort.Result.Single).document
            ensure(document != null) { DeleteMaterialError.DocumentNotFound }

            objectStoragePort.delete(document.storageObjectKey)
                .mapLeft(DeleteMaterialError::StorageFailed)
                .bind()
            deleteMaterialChunksPort(command.ownerUserId, command.documentId)
                .mapLeft(DeleteMaterialError::PersistenceFailed)
                .bind()
            deleteMaterialDocumentPort(command.ownerUserId, command.documentId)
                .mapLeft(DeleteMaterialError::PersistenceFailed)
                .bind()

            DeleteMaterialResult(deleted = true)
        }
}

private fun isSupported(fileName: String, contentType: String): Boolean {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
    val normalizedContentType = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    return when (extension) {
        "md" -> normalizedContentType in setOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")
        "txt" -> normalizedContentType in setOf("text/plain", "application/octet-stream")
        "pdf" -> normalizedContentType in setOf("application/pdf", "application/octet-stream")
        "docx" -> normalizedContentType in setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream"
        )
        else -> false
    }
}

internal fun requiresTextExtraction(fileName: String): Boolean =
    fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT) in setOf("md", "txt", "pdf", "docx")

internal fun MaterialTextExtractionError.toIngestionErrorMessage(): String =
    when (this) {
        MaterialTextExtractionError.EmptyText -> "Text extraction failed: extracted text is empty"
        MaterialTextExtractionError.ExtractionFailed -> "Text extraction failed: parser could not read the file"
        MaterialTextExtractionError.InvalidUtf8 -> "Text extraction failed: file is not valid UTF-8"
        MaterialTextExtractionError.OcrFailed -> "Text extraction failed: OCR could not read the scanned PDF"
        MaterialTextExtractionError.UnsupportedFormat -> "Text extraction failed: unsupported text format"
    }

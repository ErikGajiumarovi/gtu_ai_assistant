package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface UploadMaterialUseCase {
    suspend operator fun invoke(command: UploadMaterialCommand): Either<UploadMaterialError, UploadMaterialResult>
}

data class UploadMaterialCommand(
    val ownerUserId: UserId,
    val collectionId: MaterialCollectionId?,
    val originalFileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val bytes: ByteArray
)

data class UploadMaterialResult(
    val document: MaterialDocument
)

sealed interface UploadMaterialError {
    data object UnsupportedFileType : UploadMaterialError
    data object FileIsEmpty : UploadMaterialError
    data object FileTooLarge : UploadMaterialError
    data object CollectionNotFound : UploadMaterialError

    data class InvalidDomainState(
        val reason: DomainError
    ) : UploadMaterialError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : UploadMaterialError

    data class StorageFailed(
        val reason: InfrastructureError
    ) : UploadMaterialError
}

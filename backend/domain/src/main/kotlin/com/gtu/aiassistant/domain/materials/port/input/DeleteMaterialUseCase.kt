package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface DeleteMaterialUseCase {
    suspend operator fun invoke(command: DeleteMaterialCommand): Either<DeleteMaterialError, DeleteMaterialResult>
}

data class DeleteMaterialCommand(
    val ownerUserId: UserId,
    val documentId: MaterialDocumentId
)

data class DeleteMaterialResult(
    val deleted: Boolean
)

sealed interface DeleteMaterialError {
    data object DocumentNotFound : DeleteMaterialError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : DeleteMaterialError

    data class StorageFailed(
        val reason: InfrastructureError
    ) : DeleteMaterialError
}

package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface DeleteMaterialCollectionUseCase {
    suspend operator fun invoke(command: DeleteMaterialCollectionCommand): Either<DeleteMaterialCollectionError, DeleteMaterialCollectionResult>
}

data class DeleteMaterialCollectionCommand(
    val ownerUserId: UserId,
    val collectionId: MaterialCollectionId
)

data class DeleteMaterialCollectionResult(
    val deleted: Boolean
)

sealed interface DeleteMaterialCollectionError {
    data object CollectionNotFound : DeleteMaterialCollectionError
    data object CollectionIsNotEmpty : DeleteMaterialCollectionError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : DeleteMaterialCollectionError
}

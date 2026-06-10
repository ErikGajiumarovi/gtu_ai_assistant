package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface ListMaterialsUseCase {
    suspend operator fun invoke(query: ListMaterialsQuery): Either<ListMaterialsError, ListMaterialsResult>
}

data class ListMaterialsQuery(
    val ownerUserId: UserId,
    val collectionId: MaterialCollectionId? = null
)

data class ListMaterialsResult(
    val documents: List<MaterialDocument>
)

sealed interface ListMaterialsError {
    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : ListMaterialsError
}

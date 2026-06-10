package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface ListMaterialCollectionsUseCase {
    suspend operator fun invoke(query: ListMaterialCollectionsQuery): Either<ListMaterialCollectionsError, ListMaterialCollectionsResult>
}

data class ListMaterialCollectionsQuery(
    val ownerUserId: UserId
)

data class ListMaterialCollectionsResult(
    val collections: List<MaterialCollection>
)

sealed interface ListMaterialCollectionsError {
    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : ListMaterialCollectionsError
}

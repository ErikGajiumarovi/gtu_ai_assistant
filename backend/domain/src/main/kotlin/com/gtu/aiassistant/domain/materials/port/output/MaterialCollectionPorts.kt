package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface SaveMaterialCollectionPort {
    suspend operator fun invoke(collection: MaterialCollection): Either<InfrastructureError, MaterialCollection>
}

fun interface FindMaterialCollectionPort {
    suspend operator fun invoke(strategy: Strategy): Either<InfrastructureError, Result>

    sealed interface Strategy {
        data class ById(
            val ownerUserId: UserId,
            val collectionId: MaterialCollectionId
        ) : Strategy

        data class ByOwner(
            val ownerUserId: UserId
        ) : Strategy
    }

    sealed interface Result {
        data class Single(
            val collection: MaterialCollection?
        ) : Result

        data class Multiple(
            val collections: List<MaterialCollection>
        ) : Result
    }
}

fun interface DeleteMaterialCollectionPort {
    suspend operator fun invoke(ownerUserId: UserId, collectionId: MaterialCollectionId): Either<InfrastructureError, Unit>
}

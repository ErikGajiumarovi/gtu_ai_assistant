package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface SaveMaterialDocumentPort {
    suspend operator fun invoke(document: MaterialDocument): Either<InfrastructureError, MaterialDocument>
}

fun interface FindMaterialDocumentPort {
    suspend operator fun invoke(strategy: Strategy): Either<InfrastructureError, Result>

    sealed interface Strategy {
        data class ById(
            val ownerUserId: UserId,
            val documentId: MaterialDocumentId
        ) : Strategy

        data class ByIds(
            val ownerUserId: UserId,
            val documentIds: List<MaterialDocumentId>
        ) : Strategy

        data class ByOwner(
            val ownerUserId: UserId,
            val collectionId: MaterialCollectionId? = null
        ) : Strategy

        data class ByStatus(
            val status: MaterialIngestionStatus,
            val limit: Int
        ) : Strategy
    }

    sealed interface Result {
        data class Single(
            val document: MaterialDocument?
        ) : Result

        data class Multiple(
            val documents: List<MaterialDocument>
        ) : Result
    }
}

fun interface DeleteMaterialDocumentPort {
    suspend operator fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId): Either<InfrastructureError, Unit>
}

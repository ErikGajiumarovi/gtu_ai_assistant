package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

suspend fun validateMaterialFilters(
    userId: UserId,
    collectionIds: List<MaterialCollectionId>,
    documentIds: List<MaterialDocumentId>,
    findMaterialDocumentPort: FindMaterialDocumentPort,
    findMaterialCollectionPort: FindMaterialCollectionPort
): Either<MaterialFilterValidationError, Unit> = either {
    collectionIds.distinct().forEach { collectionId ->
        val result = findMaterialCollectionPort(FindMaterialCollectionPort.Strategy.ById(userId, collectionId))
            .mapLeft(MaterialFilterValidationError::PersistenceFailed)
            .bind()
        ensure((result as FindMaterialCollectionPort.Result.Single).collection != null) {
            MaterialFilterValidationError.InvalidDomainState(MaterialFilterDomainError.CollectionNotFound)
        }
    }

    documentIds.distinct().forEach { documentId ->
        val result = findMaterialDocumentPort(FindMaterialDocumentPort.Strategy.ById(userId, documentId))
            .mapLeft(MaterialFilterValidationError::PersistenceFailed)
            .bind()
        ensure((result as FindMaterialDocumentPort.Result.Single).document != null) {
            MaterialFilterValidationError.InvalidDomainState(MaterialFilterDomainError.DocumentNotFound)
        }
    }
}

sealed interface MaterialFilterValidationError {
    data class InvalidDomainState(val reason: DomainError) : MaterialFilterValidationError
    data class PersistenceFailed(val reason: InfrastructureError) : MaterialFilterValidationError
}

sealed interface MaterialFilterDomainError : DomainError {
    data object CollectionNotFound : MaterialFilterDomainError
    data object DocumentNotFound : MaterialFilterDomainError
}

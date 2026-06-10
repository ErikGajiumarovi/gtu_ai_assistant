package com.gtu.aiassistant.domain.materials.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface CreateMaterialCollectionUseCase {
    suspend operator fun invoke(command: CreateMaterialCollectionCommand): Either<CreateMaterialCollectionError, CreateMaterialCollectionResult>
}

data class CreateMaterialCollectionCommand(
    val ownerUserId: UserId,
    val name: String
)

data class CreateMaterialCollectionResult(
    val collection: MaterialCollection
)

sealed interface CreateMaterialCollectionError {
    data class InvalidDomainState(
        val reason: DomainError
    ) : CreateMaterialCollectionError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : CreateMaterialCollectionError
}

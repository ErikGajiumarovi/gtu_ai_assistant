package com.gtu.aiassistant.domain.materials.model

import arrow.core.Either
import com.gtu.aiassistant.domain.model.DomainError
import java.util.UUID
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class MaterialCollectionId private constructor(
    val value: UUID
) {
    companion object {
        fun create(value: String): Either<DomainError, MaterialCollectionId> =
            Either
                .catch { UUID.fromString(value.trim()) }
                .map(::MaterialCollectionId)
                .mapLeft { MaterialCollectionIdError.InvalidFormat }

        fun create(value: UUID): Either<DomainError, MaterialCollectionId> =
            Either.Right(MaterialCollectionId(value))

        fun fromTrusted(value: UUID): MaterialCollectionId =
            MaterialCollectionId(value)
    }
}

sealed interface MaterialCollectionIdError : DomainError {
    data object InvalidFormat : MaterialCollectionIdError
}

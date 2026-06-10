package com.gtu.aiassistant.domain.materials.model

import arrow.core.Either
import com.gtu.aiassistant.domain.model.DomainError
import java.util.UUID
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class MaterialDocumentId private constructor(
    val value: UUID
) {
    companion object {
        fun create(value: String): Either<DomainError, MaterialDocumentId> =
            Either
                .catch { UUID.fromString(value.trim()) }
                .map(::MaterialDocumentId)
                .mapLeft { MaterialDocumentIdError.InvalidFormat }

        fun create(value: UUID): Either<DomainError, MaterialDocumentId> =
            Either.Right(MaterialDocumentId(value))

        fun fromTrusted(value: UUID): MaterialDocumentId =
            MaterialDocumentId(value)
    }
}

sealed interface MaterialDocumentIdError : DomainError {
    data object InvalidFormat : MaterialDocumentIdError
}

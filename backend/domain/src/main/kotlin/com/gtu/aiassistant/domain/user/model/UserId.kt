package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.model.DomainError
import java.util.UUID
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserId private constructor(
    val value: UUID
) {
    companion object {
        fun create(value: String): Either<DomainError, UserId> =
            Either
                .catch { UUID.fromString(value.trim()) }
                .map(::UserId)
                .mapLeft { UserIdError.InvalidFormat }

        fun create(value: UUID): Either<DomainError, UserId> =
            either { UserId(value) }

        fun fromTrusted(value: UUID): UserId =
            UserId(value)
    }
}

sealed interface UserIdError : DomainError {
    data object InvalidFormat : UserIdError
}

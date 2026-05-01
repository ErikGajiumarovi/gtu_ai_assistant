package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.gtu.aiassistant.domain.model.DomainError
import java.util.UUID
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserId private constructor(
    val value: UUID
) {
    companion object {
        fun create(value: String): Either<DomainError, UserId> =
            try {
                UserId(UUID.fromString(value)).right()
            } catch (_: IllegalArgumentException) {
                UserIdError.InvalidFormat.left()
            }

        fun create(value: UUID): Either<DomainError, UserId> =
            UserId(value).right()

        fun fromTrusted(value: UUID): UserId =
            UserId(value)
    }
}

sealed interface UserIdError : DomainError {
    data object InvalidFormat : UserIdError
}

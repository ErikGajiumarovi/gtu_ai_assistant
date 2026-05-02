package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.DomainError
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserPasswordHash private constructor(
    val value: String
) {
    companion object {
        private const val MAX_LENGTH = 512

        fun create(value: String): Either<DomainError, UserPasswordHash> =
            either {
                val normalizedValue = value.trim()

                ensure(normalizedValue.isNotBlank()) { UserPasswordHashError.Blank }
                ensure(normalizedValue.length <= MAX_LENGTH) { UserPasswordHashError.TooLong }

                UserPasswordHash(normalizedValue)
            }

        fun fromTrusted(value: String): UserPasswordHash =
            UserPasswordHash(value)
    }
}

sealed interface UserPasswordHashError : DomainError {
    data object Blank : UserPasswordHashError
    data object TooLong : UserPasswordHashError
}

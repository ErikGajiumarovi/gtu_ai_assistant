package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.DomainError
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserPassword private constructor(
    val value: String
) {
    companion object {
        private const val MAX_LENGTH = 255

        fun create(value: String): Either<DomainError, UserPassword> =
            either {
                ensure(value.isNotBlank()) { UserPasswordError.Blank }
                ensure(value.length <= MAX_LENGTH) { UserPasswordError.TooLong }

                UserPassword(value)
            }

        fun fromTrusted(value: String): UserPassword =
            UserPassword(value)
    }
}

sealed interface UserPasswordError : DomainError {
    data object Blank : UserPasswordError
    data object TooLong : UserPasswordError
}

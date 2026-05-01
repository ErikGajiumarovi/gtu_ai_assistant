package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.DomainError
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserLastName private constructor(
    val value: String
) {
    companion object {
        private const val MAX_LENGTH = 100

        fun create(value: String): Either<DomainError, UserLastName> =
            either {
                val normalizedValue = value.trim()

                ensure(normalizedValue.isNotBlank()) { UserLastNameError.Blank }
                ensure(normalizedValue.length <= MAX_LENGTH) { UserLastNameError.TooLong }

                UserLastName(normalizedValue)
            }

        fun fromTrusted(value: String): UserLastName =
            UserLastName(value)
    }
}

sealed interface UserLastNameError : DomainError {
    data object Blank : UserLastNameError
    data object TooLong : UserLastNameError
}

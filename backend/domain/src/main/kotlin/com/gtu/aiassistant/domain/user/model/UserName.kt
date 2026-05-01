package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.DomainError
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserName private constructor(
    val value: String
) {
    companion object {
        private const val MAX_LENGTH = 100

        fun create(value: String): Either<DomainError, UserName> =
            either {
                val normalizedValue = value.trim()

                ensure(normalizedValue.isNotBlank()) { UserNameError.Blank }
                ensure(normalizedValue.length <= MAX_LENGTH) { UserNameError.TooLong }

                UserName(normalizedValue)
            }

        fun fromTrusted(value: String): UserName =
            UserName(value)
    }
}

sealed interface UserNameError : DomainError {
    data object Blank : UserNameError
    data object TooLong : UserNameError
}

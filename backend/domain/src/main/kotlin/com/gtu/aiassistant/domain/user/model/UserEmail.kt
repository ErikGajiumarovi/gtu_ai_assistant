package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.DomainError
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserEmail private constructor(
    val value: String
) {
    companion object {
        private const val MAX_LENGTH = 320
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

        fun create(value: String): Either<DomainError, UserEmail> =
            either {
                val normalizedValue = value.trim().lowercase()

                ensure(normalizedValue.isNotBlank()) { UserEmailError.Blank }
                ensure(normalizedValue.length <= MAX_LENGTH) { UserEmailError.TooLong }
                ensure(EMAIL_REGEX.matches(normalizedValue)) { UserEmailError.InvalidFormat }

                UserEmail(normalizedValue)
            }

        fun fromTrusted(value: String): UserEmail =
            UserEmail(value)
    }
}

sealed interface UserEmailError : DomainError {
    data object Blank : UserEmailError
    data object TooLong : UserEmailError
    data object InvalidFormat : UserEmailError
}

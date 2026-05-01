package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.gtu.aiassistant.domain.model.DomainError
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class UserName private constructor(
    val value: String
) {
    companion object {
        private const val MAX_LENGTH = 100

        fun create(value: String): Either<DomainError, UserName> {
            val normalizedValue = value.trim()

            return when {
                normalizedValue.isBlank() -> UserNameError.Blank.left()
                normalizedValue.length > MAX_LENGTH -> UserNameError.TooLong.left()
                else -> UserName(normalizedValue).right()
            }
        }

        fun fromTrusted(value: String): UserName =
            UserName(value)
    }
}

sealed interface UserNameError : DomainError {
    data object Blank : UserNameError
    data object TooLong : UserNameError
}

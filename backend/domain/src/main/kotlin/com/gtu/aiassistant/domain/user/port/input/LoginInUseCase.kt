package com.gtu.aiassistant.domain.user.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserPassword

fun interface LoginInUseCase {
    suspend operator fun invoke(command: LoginInCommand): Either<LoginInError, LoginInResult>
}

data class LoginInCommand(
    val email: UserEmail,
    val password: UserPassword
)

data class LoginInResult(
    val jwt: String
)

sealed interface LoginInError {
    data object InvalidCredentials : LoginInError

    data class FindFailed(
        val reason: InfrastructureError
    ) : LoginInError

    data class PasswordVerificationFailed(
        val reason: InfrastructureError
    ) : LoginInError

    data class JwtIssuingFailed(
        val reason: InfrastructureError
    ) : LoginInError
}

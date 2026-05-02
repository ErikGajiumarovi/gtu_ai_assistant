package com.gtu.aiassistant.domain.user.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPassword

fun interface RegisterUserUseCase {
    suspend operator fun invoke(command: RegisterUserCommand): Either<RegisterUserError, RegisterUserResult>
}

data class RegisterUserCommand(
    val name: UserName,
    val lastName: UserLastName,
    val email: UserEmail,
    val password: UserPassword
)

data class RegisterUserResult(
    val user: User
)

sealed interface RegisterUserError {
    data object EmailAlreadyTaken : RegisterUserError

    data class InvalidDomainState(
        val reason: DomainError
    ) : RegisterUserError

    data class DuplicateCheckFailed(
        val reason: InfrastructureError
    ) : RegisterUserError

    data class PasswordHashingFailed(
        val reason: InfrastructureError
    ) : RegisterUserError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : RegisterUserError
}

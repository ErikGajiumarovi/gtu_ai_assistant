package com.gtu.aiassistant.domain.user.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName

fun interface CreateUserUseCase {
    suspend operator fun invoke(command: CreateUserCommand): Either<CreateUserError, CreateUserResult>
}

data class CreateUserCommand(
    val id: UserId,
    val name: UserName,
    val lastName: UserLastName,
    val email: UserEmail
)

data class CreateUserResult(
    val user: User
)

sealed interface CreateUserError {
    data class InvalidDomainState(
        val reason: DomainError
    ) : CreateUserError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : CreateUserError
}

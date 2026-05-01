package com.gtu.aiassistant.application.user

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.port.input.CreateUserError
import com.gtu.aiassistant.domain.user.port.input.CreateUserResult
import com.gtu.aiassistant.domain.user.port.input.CreateUserUseCase
import com.gtu.aiassistant.domain.user.port.output.SaveUserPort

class CreateUserUseCaseImpl(
    private val saveUserPort: SaveUserPort
) : CreateUserUseCase {
    override suspend fun invoke(
        command: com.gtu.aiassistant.domain.user.port.input.CreateUserCommand
    ): Either<CreateUserError, CreateUserResult> =
        either {
            val user = User
                .create(
                    id = command.id,
                    version = 0L,
                    name = command.name,
                    lastName = command.lastName,
                    email = command.email
                )
                .mapLeft(CreateUserError::InvalidDomainState)
                .bind()

            val persistedUser = saveUserPort
                .invoke(user)
                .mapLeft(CreateUserError::PersistenceFailed)
                .bind()

            CreateUserResult(
                user = persistedUser
            )
        }
}

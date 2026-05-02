package com.gtu.aiassistant.application.user

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.port.input.RegisterUserCommand
import com.gtu.aiassistant.domain.user.port.input.RegisterUserError
import com.gtu.aiassistant.domain.user.port.input.RegisterUserResult
import com.gtu.aiassistant.domain.user.port.input.RegisterUserUseCase
import com.gtu.aiassistant.domain.user.port.output.ExistsUserPort
import com.gtu.aiassistant.domain.user.port.output.HashPasswordPort
import com.gtu.aiassistant.domain.user.port.output.SaveUserPort
import java.util.UUID

class RegisterUserUseCaseImpl(
    private val existsUserPort: ExistsUserPort,
    private val hashPasswordPort: HashPasswordPort,
    private val saveUserPort: SaveUserPort
) : RegisterUserUseCase {
    override suspend fun invoke(command: RegisterUserCommand): Either<RegisterUserError, RegisterUserResult> =
        either {
            val emailExists = existsUserPort
                .invoke(ExistsUserPort.Strategy.ByEmail(command.email))
                .mapLeft(RegisterUserError::DuplicateCheckFailed)
                .bind()

            ensure(!emailExists) { RegisterUserError.EmailAlreadyTaken }

            val passwordHash = hashPasswordPort
                .invoke(command.password)
                .mapLeft(RegisterUserError::PasswordHashingFailed)
                .bind()

            val userId = UserId
                .create(UUID.randomUUID())
                .mapLeft(RegisterUserError::InvalidDomainState)
                .bind()

            val user = User
                .create(
                    id = userId,
                    version = 0L,
                    name = command.name,
                    lastName = command.lastName,
                    email = command.email,
                    passwordHash = passwordHash
                )
                .mapLeft(RegisterUserError::InvalidDomainState)
                .bind()

            val persistedUser = saveUserPort
                .invoke(user)
                .mapLeft(RegisterUserError::PersistenceFailed)
                .bind()

            RegisterUserResult(user = persistedUser)
        }
}

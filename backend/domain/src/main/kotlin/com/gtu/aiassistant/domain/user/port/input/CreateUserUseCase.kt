package com.gtu.aiassistant.domain.user.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.user.model.User

interface CreateUserUseCase {
    suspend operator fun invoke(command: CreateUserCommand): Either<CreateUserError, User>
}

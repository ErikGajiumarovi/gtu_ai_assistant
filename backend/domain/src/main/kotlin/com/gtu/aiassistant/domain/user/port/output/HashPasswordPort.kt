package com.gtu.aiassistant.domain.user.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserPassword
import com.gtu.aiassistant.domain.user.model.UserPasswordHash

fun interface HashPasswordPort {
    suspend operator fun invoke(password: UserPassword): Either<InfrastructureError, UserPasswordHash>
}

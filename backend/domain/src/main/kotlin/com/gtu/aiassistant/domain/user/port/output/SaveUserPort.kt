package com.gtu.aiassistant.domain.user.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User

fun interface SaveUserPort {
    suspend operator fun invoke(user: User): Either<InfrastructureError, User>
}

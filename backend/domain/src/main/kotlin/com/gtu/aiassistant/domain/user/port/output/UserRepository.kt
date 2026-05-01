package com.gtu.aiassistant.domain.user.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserId

interface UserRepository {
    suspend fun save(user: User): Either<InfrastructureError, User>

    suspend fun findById(id: UserId): Either<InfrastructureError, User?>
}

package com.gtu.aiassistant.domain.user.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId

fun interface ExistsUserPort {
    suspend operator fun invoke(strategy: Strategy): Either<InfrastructureError, Boolean>

    sealed interface Strategy {
        data class ById(
            val userId: UserId
        ) : Strategy

        data class ByEmail(
            val email: UserEmail
        ) : Strategy
    }
}

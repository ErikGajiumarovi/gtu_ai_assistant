package com.gtu.aiassistant.app.memory

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.port.output.ExistsUserPort
import com.gtu.aiassistant.domain.user.port.output.FindUserPort
import com.gtu.aiassistant.domain.user.port.output.SaveUserPort
import com.gtu.aiassistant.domain.user.port.output.UpdateUserPort

class InMemoryFindUserPort(
    private val state: InMemoryState
) : FindUserPort {
    override suspend fun invoke(strategy: FindUserPort.Strategy): Either<InfrastructureError, User?> =
        Either.Right(
            when (strategy) {
                is FindUserPort.Strategy.ById -> state.users[strategy.userId.value.toString()]
                is FindUserPort.Strategy.ByEmail -> state.users.values.firstOrNull { it.email == strategy.email }
            }
        )
}

class InMemoryExistsUserPort(
    private val state: InMemoryState
) : ExistsUserPort {
    override suspend fun invoke(strategy: ExistsUserPort.Strategy): Either<InfrastructureError, Boolean> =
        Either.Right(
            when (strategy) {
                is ExistsUserPort.Strategy.ById -> state.users.containsKey(strategy.userId.value.toString())
                is ExistsUserPort.Strategy.ByEmail -> state.users.values.any { it.email == strategy.email }
            }
        )
}

class InMemorySaveUserPort(
    private val state: InMemoryState
) : SaveUserPort {
    override suspend fun invoke(user: User): Either<InfrastructureError, User> {
        state.users[user.id.value.toString()] = user
        return Either.Right(user)
    }
}

class InMemoryUpdateUserPort(
    private val state: InMemoryState
) : UpdateUserPort {
    override suspend fun invoke(user: User): Either<InfrastructureError, User> {
        state.users[user.id.value.toString()] = user
        return Either.Right(user)
    }
}

package com.gtu.aiassistant.infrastructure.persistence.user

import com.gtu.aiassistant.domain.user.port.output.FindUserPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class FindUserPortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindUserPort {
    override suspend fun invoke(strategy: FindUserPort.Strategy) =
        executor.execute {
            when (strategy) {
                is FindUserPort.Strategy.ById -> UserRecords.table
                    .selectAll()
                    .where { UserRecords.id eq strategy.userId.value.toString() }
                is FindUserPort.Strategy.ByEmail -> UserRecords.table
                    .selectAll()
                    .where { UserRecords.email eq strategy.email.value }
            }
                .singleOrNull()
                ?.toDomainUser()
        }
}

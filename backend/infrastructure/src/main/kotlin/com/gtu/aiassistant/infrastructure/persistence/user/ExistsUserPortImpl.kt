package com.gtu.aiassistant.infrastructure.persistence.user

import com.gtu.aiassistant.domain.user.port.output.ExistsUserPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class ExistsUserPortImpl(
    private val executor: JdbcPersistenceExecutor
) : ExistsUserPort {
    override suspend fun invoke(strategy: ExistsUserPort.Strategy) =
        executor.execute {
            val query = when (strategy) {
                is ExistsUserPort.Strategy.ById -> UserRecords.table
                    .selectAll()
                    .where { UserRecords.id eq strategy.userId.value.toString() }
                is ExistsUserPort.Strategy.ByEmail -> UserRecords.table
                    .selectAll()
                    .where { UserRecords.email eq strategy.email.value }
            }

            query.any()
        }
}

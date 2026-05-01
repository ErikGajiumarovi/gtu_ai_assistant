package com.gtu.aiassistant.infrastructure.persistence.user

import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.port.output.SaveUserPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.insert

class SaveUserPortImpl(
    private val executor: JdbcPersistenceExecutor
) : SaveUserPort {
    override suspend fun invoke(user: User) =
        executor.execute {
            UserRecords.table.insert {
                it[UserRecords.id] = user.id.value.toString()
                it[UserRecords.version] = user.version
                it[UserRecords.name] = user.name.value
                it[UserRecords.lastName] = user.lastName.value
                it[UserRecords.email] = user.email.value
            }

            user
        }
}

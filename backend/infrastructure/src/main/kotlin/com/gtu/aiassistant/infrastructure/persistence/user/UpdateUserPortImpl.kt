package com.gtu.aiassistant.infrastructure.persistence.user

import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.port.output.UpdateUserPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

class UpdateUserPortImpl(
    private val executor: JdbcPersistenceExecutor
) : UpdateUserPort {
    override suspend fun invoke(user: User) =
        executor.execute {
            val updatedRows = UserRecords.table.update({
                (UserRecords.id eq user.id.value.toString()) and
                    (UserRecords.version eq (user.version - 1))
            }) {
                it[UserRecords.version] = user.version
                it[UserRecords.name] = user.name.value
                it[UserRecords.lastName] = user.lastName.value
                it[UserRecords.email] = user.email.value
                it[UserRecords.passwordHash] = user.passwordHash.value
            }

            check(updatedRows == 1) {
                "User optimistic lock failed for id=${user.id.value}"
            }

            user
        }
}

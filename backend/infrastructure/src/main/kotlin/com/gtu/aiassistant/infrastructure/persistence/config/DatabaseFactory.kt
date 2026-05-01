package com.gtu.aiassistant.infrastructure.persistence.config

import com.gtu.aiassistant.infrastructure.persistence.schema.PersistenceSchema
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    fun connect(config: PersistenceConfig): Database =
        Database.connect(
            url = config.jdbcUrl,
            user = config.username,
            password = config.password,
            driver = config.driverClassName
        )

    fun createJdbcPersistenceExecutor(config: PersistenceConfig): JdbcPersistenceExecutor {
        val database = connect(config)
        PersistenceSchema.create(database)

        return JdbcPersistenceExecutor(database)
    }
}

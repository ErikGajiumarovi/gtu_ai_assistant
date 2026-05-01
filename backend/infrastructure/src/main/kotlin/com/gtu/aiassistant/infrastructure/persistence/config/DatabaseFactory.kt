package com.gtu.aiassistant.infrastructure.persistence.config

import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    fun connect(config: PersistenceConfig): Database =
        Database.connect(
            url = config.jdbcUrl,
            user = config.username,
            password = config.password,
            driver = config.driverClassName
        )
}

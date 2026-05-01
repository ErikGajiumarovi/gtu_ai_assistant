package com.gtu.aiassistant.infrastructure.persistence.config

data class PersistenceConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val driverClassName: String = "org.postgresql.Driver"
)

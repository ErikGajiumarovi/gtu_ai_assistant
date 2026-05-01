package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table(name = "users") {
    val id = varchar("id", length = 36)
    val version = long("version")
    val name = varchar("name", length = 100)
    val lastName = varchar("last_name", length = 100)
    val email = varchar("email", length = 320).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

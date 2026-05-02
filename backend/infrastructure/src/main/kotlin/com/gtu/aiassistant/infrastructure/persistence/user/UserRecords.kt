package com.gtu.aiassistant.infrastructure.persistence.user

import com.gtu.aiassistant.infrastructure.persistence.schema.UsersTable

internal object UserRecords {
    val table = UsersTable
    val id = table.id
    val version = table.version
    val name = table.name
    val lastName = table.lastName
    val email = table.email
    val passwordHash = table.passwordHash
}

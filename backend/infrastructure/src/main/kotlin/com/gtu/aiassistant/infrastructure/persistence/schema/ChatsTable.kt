package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object ChatsTable : Table(name = "chats") {
    val id = varchar("id", length = 36)
    val version = long("version")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val ownedBy = reference("owned_by", UsersTable.id)

    override val primaryKey = PrimaryKey(id)
}

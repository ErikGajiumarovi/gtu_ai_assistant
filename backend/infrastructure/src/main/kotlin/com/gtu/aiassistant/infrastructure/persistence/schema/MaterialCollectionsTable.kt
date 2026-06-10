package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object MaterialCollectionsTable : Table(name = "material_collections") {
    val id = varchar("id", length = 36)
    val version = long("version")
    val ownerUserId = reference("owner_user_id", UsersTable.id)
    val name = text("name")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerUserId)
        uniqueIndex(ownerUserId, name)
    }
}

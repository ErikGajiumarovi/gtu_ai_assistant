package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object GeneratedArtifactsTable : Table(name = "generated_artifacts") {
    val id = varchar("id", length = 36)
    val ownerUserId = reference("owner_user_id", UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val chatId = reference("chat_id", ChatsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val messageId = varchar("message_id", length = 36).nullable()
    val fileName = varchar("file_name", length = 160)
    val contentType = varchar("content_type", length = 255)
    val sizeBytes = long("size_bytes")
    val objectKey = text("object_key")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerUserId, createdAt)
        index(false, chatId)
        index(false, messageId)
    }
}

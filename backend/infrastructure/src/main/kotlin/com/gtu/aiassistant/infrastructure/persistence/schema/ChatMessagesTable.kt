package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object ChatMessagesTable : Table(name = "chat_messages") {
    val id = varchar("id", length = 36)
    val chatId = reference("chat_id", ChatsTable.id, onDelete = ReferenceOption.CASCADE)
    val orderIndex = integer("order_index")
    val originalText = text("original_text")
    val senderType = varchar("sender_type", length = 16)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(chatId, orderIndex)
    }
}

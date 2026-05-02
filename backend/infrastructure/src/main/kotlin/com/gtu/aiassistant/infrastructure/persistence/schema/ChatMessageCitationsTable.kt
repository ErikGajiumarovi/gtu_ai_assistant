package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object ChatMessageCitationsTable : Table(name = "chat_message_citations") {
    val messageId = reference("message_id", ChatMessagesTable.id, onDelete = ReferenceOption.CASCADE)
    val orderIndex = integer("order_index")
    val title = text("title")
    val url = text("url")
    val snippet = text("snippet")
    val sourceType = varchar("source_type", length = 16)

    override val primaryKey = PrimaryKey(messageId, orderIndex)
}

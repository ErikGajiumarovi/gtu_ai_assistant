package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.infrastructure.persistence.schema.ChatMessagesTable

internal object ChatMessageRecords {
    val table = ChatMessagesTable
    val id = table.id
    val chatId = table.chatId
    val orderIndex = table.orderIndex
    val originalText = table.originalText
    val senderType = table.senderType
    val createdAt = table.createdAt
}

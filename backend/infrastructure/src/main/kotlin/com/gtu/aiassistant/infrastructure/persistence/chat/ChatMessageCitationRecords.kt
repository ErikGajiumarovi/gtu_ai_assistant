package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.infrastructure.persistence.schema.ChatMessageCitationsTable

internal object ChatMessageCitationRecords {
    val table = ChatMessageCitationsTable
    val messageId = table.messageId
    val orderIndex = table.orderIndex
    val title = table.title
    val url = table.url
    val snippet = table.snippet
    val sourceType = table.sourceType
}

package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.infrastructure.persistence.schema.ChatsTable

internal object ChatRecords {
    val table = ChatsTable
    val id = table.id
    val version = table.version
    val createdAt = table.createdAt
    val updatedAt = table.updatedAt
    val ownedBy = table.ownedBy
}

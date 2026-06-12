package com.gtu.aiassistant.infrastructure.persistence.artifacts

import com.gtu.aiassistant.infrastructure.persistence.schema.GeneratedArtifactsTable

internal object GeneratedArtifactRecords {
    val table = GeneratedArtifactsTable
    val id = table.id
    val ownerUserId = table.ownerUserId
    val chatId = table.chatId
    val messageId = table.messageId
    val fileName = table.fileName
    val contentType = table.contentType
    val sizeBytes = table.sizeBytes
    val objectKey = table.objectKey
    val createdAt = table.createdAt
}

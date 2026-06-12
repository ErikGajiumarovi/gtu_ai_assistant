package com.gtu.aiassistant.infrastructure.persistence.artifacts

import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifact
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.user.model.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import java.util.UUID

internal fun ResultRow.toGeneratedArtifact(): GeneratedArtifact =
    GeneratedArtifact(
        id = GeneratedArtifactId.fromTrusted(UUID.fromString(this[GeneratedArtifactRecords.id])),
        ownerUserId = UserId.fromTrusted(UUID.fromString(this[GeneratedArtifactRecords.ownerUserId])),
        chatId = this[GeneratedArtifactRecords.chatId]?.let { ChatId.fromTrusted(UUID.fromString(it)) },
        messageId = this[GeneratedArtifactRecords.messageId]?.let(UUID::fromString),
        fileName = this[GeneratedArtifactRecords.fileName],
        contentType = this[GeneratedArtifactRecords.contentType],
        sizeBytes = this[GeneratedArtifactRecords.sizeBytes],
        objectKey = this[GeneratedArtifactRecords.objectKey],
        createdAt = this[GeneratedArtifactRecords.createdAt]
    )

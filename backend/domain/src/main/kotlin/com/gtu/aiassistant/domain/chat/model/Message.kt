package com.gtu.aiassistant.domain.chat.model

import com.gtu.aiassistant.domain.artifacts.model.MessageArtifact
import java.time.Instant
import java.util.UUID

data class Message(
    val id: UUID,
    val originalText: String,
    val senderType: MessageSenderType,
    val createdAt: Instant,
    val citations: List<MessageCitation> = emptyList(),
    val artifacts: List<MessageArtifact> = emptyList()
)

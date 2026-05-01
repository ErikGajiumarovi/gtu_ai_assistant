package com.gtu.aiassistant.domain.chat.model

import java.time.Instant
import java.util.UUID

data class Message(
    val id: UUID,
    val originalText: String,
    val senderType: MessageSenderType,
    val createdAt: Instant
)

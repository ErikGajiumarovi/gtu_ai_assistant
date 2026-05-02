package com.gtu.aiassistant.infrastructure.ai.tools

import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType

data class AgentSource(
    val title: String,
    val url: String,
    val snippet: String,
    val score: Double,
    val sourceType: MessageCitationSourceType
)

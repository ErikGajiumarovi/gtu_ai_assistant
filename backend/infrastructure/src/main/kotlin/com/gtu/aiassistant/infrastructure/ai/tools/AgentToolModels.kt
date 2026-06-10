package com.gtu.aiassistant.infrastructure.ai.tools

import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId

data class AgentSource(
    val title: String,
    val url: String,
    val snippet: String,
    val score: Double,
    val sourceType: MessageCitationSourceType,
    val documentId: MaterialDocumentId? = null,
    val pageStart: Int? = null,
    val pageEnd: Int? = null
)

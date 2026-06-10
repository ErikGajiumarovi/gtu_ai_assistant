package com.gtu.aiassistant.domain.chat.model

import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId

data class MessageCitation(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceType: MessageCitationSourceType,
    val documentId: MaterialDocumentId? = null,
    val pageStart: Int? = null,
    val pageEnd: Int? = null
)

enum class MessageCitationSourceType {
    RAG,
    WEB,
    USER_MATERIAL
}

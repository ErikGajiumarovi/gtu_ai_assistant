package com.gtu.aiassistant.domain.chat.model

data class MessageCitation(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceType: MessageCitationSourceType
)

enum class MessageCitationSourceType {
    RAG,
    WEB
}

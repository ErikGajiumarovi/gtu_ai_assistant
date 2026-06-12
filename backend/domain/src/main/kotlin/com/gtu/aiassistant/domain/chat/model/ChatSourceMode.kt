package com.gtu.aiassistant.domain.chat.model

data class ChatSources(
    val gtu: Boolean = true,
    val materials: Boolean = true,
    val web: Boolean = false
) {
    fun hasAny(): Boolean = gtu || materials || web
}

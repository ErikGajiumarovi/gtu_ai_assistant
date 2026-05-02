package com.gtu.aiassistant.infrastructure.ai.tools

data class WebSearchConfig(
    val mode: WebSearchMode,
    val allowedDomains: Set<String>,
    val maxResults: Int
)

enum class WebSearchMode {
    DISABLED,
    DIRECT;

    companion object {
        fun from(raw: String?): WebSearchMode =
            when (raw?.lowercase()) {
                "disabled", "off", "false", "0" -> DISABLED
                else -> DIRECT
            }
    }
}

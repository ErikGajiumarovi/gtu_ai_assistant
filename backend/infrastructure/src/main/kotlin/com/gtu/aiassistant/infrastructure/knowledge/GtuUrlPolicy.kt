package com.gtu.aiassistant.infrastructure.knowledge

import java.net.URI

class GtuUrlPolicy(
    private val allowedDomains: Set<String>
) {
    fun canonicalize(rawUrl: String): String? =
        runCatching {
            val uri = URI(rawUrl.trim()).normalize()
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()?.removePrefix("www.")

            if (scheme !in setOf("http", "https")) return null
            if (host == null || host !in normalizedAllowedDomains) return null
            if (!isIndexablePath(uri.path.orEmpty())) return null

            URI(
                "https",
                uri.userInfo,
                host,
                -1,
                uri.path.ifBlank { "/" },
                null,
                null
            ).toString()
        }.getOrNull()

    fun isAllowed(rawUrl: String): Boolean =
        canonicalize(rawUrl) != null

    private val normalizedAllowedDomains = allowedDomains.map { it.lowercase().removePrefix("www.") }.toSet()

    private fun isIndexablePath(path: String): Boolean {
        val normalizedPath = path.lowercase()
        if (normalizedPath.isBlank()) return true
        if (normalizedPath.contains("/auth/")) return false
        if (normalizedPath.contains("/personal/")) return false
        if (normalizedPath.contains("/search/")) return false
        if (SKIPPED_EXTENSIONS.any { normalizedPath.endsWith(it) }) return false

        return true
    }

    companion object {
        private val SKIPPED_EXTENSIONS = setOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".gif",
            ".svg",
            ".webp",
            ".ico",
            ".css",
            ".js",
            ".zip",
            ".rar",
            ".mp4",
            ".mp3",
            ".avi"
        )
    }
}

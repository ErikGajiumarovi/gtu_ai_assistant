package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeChunk
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeDocument
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class KnowledgeDocumentBuilder(
    private val embeddingPort: EmbeddingPort
) {
    suspend fun build(
        page: FetchedPage,
        canonicalUrl: String,
        sitemapLastModifiedAt: Instant?
    ): Either<InfrastructureError, KnowledgeDocument?> =
        either {
            val parsed = Jsoup.parse(page.html, canonicalUrl)
            parsed.select("script, style, nav, header, footer, form, noscript, svg").remove()

            val title = parsed.title().ifBlank { canonicalUrl }
            val text = parsed.body()
                .text()
                .replace(Regex("\\s+"), " ")
                .trim()

            if (text.length < MIN_CONTENT_LENGTH) {
                return@either null
            }

            val documentId = stableUuid(canonicalUrl)
            val sourceLastModifiedAt = page.sourceLastModifiedAt ?: sitemapLastModifiedAt
            val chunks = text.toChunks().mapIndexed { index, chunkText ->
                KnowledgeChunk(
                    id = stableUuid("$canonicalUrl#$index"),
                    documentId = documentId,
                    chunkIndex = index,
                    title = title,
                    url = canonicalUrl,
                    text = chunkText,
                    embedding = embeddingPort("$title\n$chunkText").bind()
                )
            }

            KnowledgeDocument(
                id = documentId,
                sourceUrl = page.url,
                canonicalUrl = canonicalUrl,
                title = title,
                contentHash = sha256(text),
                fetchedAt = page.fetchedAt,
                sourceLastModifiedAt = sourceLastModifiedAt,
                chunks = chunks
            )
        }

    companion object {
        private const val MIN_CONTENT_LENGTH = 160
        private const val CHUNK_WORDS = 220
        private const val OVERLAP_WORDS = 40
    }

    private fun String.toChunks(): List<String> {
        val words = split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val end = minOf(start + CHUNK_WORDS, words.size)
            chunks += words.subList(start, end).joinToString(" ")
            if (end == words.size) break
            start = maxOf(0, end - OVERLAP_WORDS)
        }

        return chunks
    }
}

private fun stableUuid(value: String): UUID =
    UUID.nameUUIDFromBytes(value.toByteArray())

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

package com.gtu.aiassistant.infrastructure.ai.tools

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.knowledge.GtuPageFetcher
import com.gtu.aiassistant.infrastructure.knowledge.GtuUrlPolicy
import com.gtu.aiassistant.infrastructure.knowledge.RobotsRules
import com.gtu.aiassistant.infrastructure.knowledge.SitemapEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import kotlin.math.ln

class GtuWebSearchTool(
    private val config: WebSearchConfig,
    private val urlPolicy: GtuUrlPolicy,
    private val fetcher: GtuPageFetcher
) {
    suspend fun search(query: String): Either<InfrastructureError, List<AgentSource>> =
        when (config.mode) {
            WebSearchMode.DISABLED -> Either.Right(emptyList())
            WebSearchMode.DIRECT -> searchDirect(query)
        }

    private suspend fun searchDirect(query: String): Either<InfrastructureError, List<AgentSource>> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val queryTerms = query.queryTerms()
                if (queryTerms.isEmpty()) {
                    return@catch emptyList()
                }

                val robots = fetcher.fetchRobots().fold(
                    ifLeft = { RobotsRules.allowAll() },
                    ifRight = { it }
                )
                val sitemapEntries = fetcher.fetchSitemap().fold(
                    ifLeft = { return@catch emptyList() },
                    ifRight = { it }
                )

                val candidates = sitemapEntries
                    .asSequence()
                    .mapNotNull { entry -> entry.toCandidate(queryTerms, robots) }
                    .sortedByDescending { it.urlScore }
                    .take(MAX_FETCH_CANDIDATES)
                    .toList()

                val results = mutableListOf<AgentSource>()
                for (candidate in candidates) {
                    val source = candidate.fetchAndScore(queryTerms)
                    if (source != null) {
                        results += source
                    }
                }

                results
                    .sortedByDescending { it.score }
                    .take(config.maxResults.coerceIn(1, 10))
            }.mapLeft(::InfrastructureError)
        }

    private fun SitemapEntry.toCandidate(queryTerms: Set<String>, robots: RobotsRules): SearchCandidate? {
        val canonicalUrl = urlPolicy.canonicalize(url) ?: return null
        if (!robots.isAllowed(canonicalUrl)) return null

        val urlText = canonicalUrl
            .replace(Regex("""https?://"""), " ")
            .replace(Regex("""[^a-zA-Zა-ჰА-Яа-я0-9]+"""), " ")
            .lowercase()

        val matches = queryTerms.count { it in urlText }
        val priority = when {
            "/students/" in canonicalUrl || "/en/students/" in canonicalUrl -> 2.0
            "/apply/" in canonicalUrl || "/en/apply/" in canonicalUrl -> 1.8
            "/faculties/" in canonicalUrl || "/structure/faculties" in canonicalUrl -> 1.6
            "/news/" in canonicalUrl || "/en/news/" in canonicalUrl -> 1.4
            "/en/" in canonicalUrl -> 1.2
            else -> 1.0
        }

        val score = priority + matches * 2.0 + (lastModifiedAt?.epochSecond?.let { ln(it.toDouble()) / 100.0 } ?: 0.0)
        return SearchCandidate(url = canonicalUrl, urlScore = score)
    }

    private suspend fun SearchCandidate.fetchAndScore(queryTerms: Set<String>): AgentSource? {
        val page = fetcher.fetchPage(url).fold(
            ifLeft = { null },
            ifRight = { it }
        ) ?: return null

        val parsed = Jsoup.parse(page.html, url)
        parsed.select("script, style, nav, header, footer, form, noscript, svg").remove()
        val title = parsed.title().ifBlank { url }
        val text = parsed.body().text().replace(Regex("\\s+"), " ").trim()
        if (text.length < MIN_TEXT_LENGTH) return null

        val normalizedText = text.lowercase()
        val contentMatches = queryTerms.count { it in normalizedText }
        if (contentMatches == 0 && queryTerms.none { it in title.lowercase() || it in url.lowercase() }) {
            return null
        }

        return AgentSource(
            title = title,
            url = url,
            snippet = text.bestSnippet(queryTerms),
            score = urlScore + contentMatches * 3.0,
            sourceType = MessageCitationSourceType.WEB
        )
    }

    companion object {
        private const val MAX_FETCH_CANDIDATES = 18
        private const val MIN_TEXT_LENGTH = 120
    }
}

private data class SearchCandidate(
    val url: String,
    val urlScore: Double
)

private fun String.queryTerms(): Set<String> =
    lowercase()
        .split(Regex("""[^a-zA-Zა-ჰА-Яа-я0-9]+"""))
        .map { it.trim() }
        .filter { it.length >= 3 }
        .filterNot { it in STOP_WORDS }
        .toSet()

private fun String.bestSnippet(queryTerms: Set<String>): String {
    val normalized = lowercase()
    val firstMatch = queryTerms
        .mapNotNull { term -> normalized.indexOf(term).takeIf { it >= 0 } }
        .minOrNull()
        ?: 0
    val start = (firstMatch - 250).coerceAtLeast(0)
    val end = (start + 800).coerceAtMost(length)

    return substring(start, end)
        .trim()
        .let { snippet ->
            when {
                start > 0 && end < length -> "...$snippet..."
                start > 0 -> "...$snippet"
                end < length -> "$snippet..."
                else -> snippet
            }
        }
}

private val STOP_WORDS = setOf(
    "the",
    "and",
    "for",
    "with",
    "what",
    "when",
    "where",
    "how",
    "что",
    "как",
    "где",
    "когда",
    "для",
    "или",
    "это",
    "არის",
    "რომ",
    "როგორ",
    "სად"
)

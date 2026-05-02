package com.gtu.aiassistant.infrastructure.ai.tools

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.knowledge.GtuPageFetcher
import com.gtu.aiassistant.infrastructure.knowledge.GtuUrlPolicy
import org.jsoup.Jsoup

class GtuPageOpenTool(
    private val urlPolicy: GtuUrlPolicy,
    private val fetcher: GtuPageFetcher
) {
    suspend fun open(url: String): Either<InfrastructureError, AgentSource?> {
        val canonicalUrl = urlPolicy.canonicalize(url)
            ?: return Either.Right(null)

        return fetcher.fetchPage(canonicalUrl).map { page ->
            page?.let {
                val parsed = Jsoup.parse(it.html, canonicalUrl)
                parsed.select("script, style, nav, header, footer, form, noscript, svg").remove()
                AgentSource(
                    title = parsed.title().ifBlank { canonicalUrl },
                    url = canonicalUrl,
                    snippet = parsed.body().text().replace(Regex("\\s+"), " ").trim().take(900),
                    score = 0.7,
                    sourceType = com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType.WEB
                )
            }
        }
    }
}

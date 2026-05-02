package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.model.InfrastructureError

class KnowledgeIngestionService(
    private val config: KnowledgeIngestionConfig,
    private val urlPolicy: GtuUrlPolicy,
    private val fetcher: GtuPageFetcher,
    private val documentBuilder: KnowledgeDocumentBuilder,
    private val upsertKnowledgeDocumentPort: UpsertKnowledgeDocumentPort
) {
    suspend fun ingestOnce(): Either<InfrastructureError, KnowledgeIngestionReport> =
        either {
            if (!config.enabled) {
                return@either KnowledgeIngestionReport(0, 0, 0, 0)
            }

            val robots = fetcher.fetchRobots().fold(
                ifLeft = { RobotsRules.allowAll() },
                ifRight = { it }
            )
            val entries = fetcher.fetchSitemap().bind()
                .asSequence()
                .mapNotNull { entry ->
                    val canonical = urlPolicy.canonicalize(entry.url) ?: return@mapNotNull null
                    if (!robots.isAllowed(canonical)) return@mapNotNull null
                    entry.copy(url = canonical)
                }
                .distinctBy { it.url }
                .sortedWith(compareByDescending<SitemapEntry> { it.url.priorityScore() }.thenBy { it.url })
                .take(config.maxPagesPerRun)
                .toList()

            var fetched = 0
            var changed = 0
            var failed = 0

            for (entry in entries) {
                val fetchedPage = fetcher.fetchPage(entry.url).fold(
                    ifLeft = {
                        failed += 1
                        null
                    },
                    ifRight = { it }
                ) ?: continue

                fetched += 1

                val document = documentBuilder
                    .build(
                        page = fetchedPage,
                        canonicalUrl = entry.url,
                        sitemapLastModifiedAt = entry.lastModifiedAt
                    )
                    .fold(
                        ifLeft = {
                            failed += 1
                            null
                        },
                        ifRight = { it }
                    )
                    ?: continue

                upsertKnowledgeDocumentPort(document)
                    .fold(
                        ifLeft = {
                            failed += 1
                        },
                        ifRight = { result ->
                            if (result.changed) changed += 1
                        }
                    )
            }

            KnowledgeIngestionReport(
                pagesSeen = entries.size,
                pagesFetched = fetched,
                pagesChanged = changed,
                pagesFailed = failed
            )
        }
}

private fun String.priorityScore(): Int {
    val url = lowercase()

    return when {
        "/en/students/" in url || "/students/" in url -> 100
        "/en/apply/" in url || "/apply/" in url -> 90
        "/en/gtu/structure/faculties" in url || "/faculties/" in url -> 80
        "/en/gtu/about/" in url || "/about/" in url -> 70
        "/en/research/" in url || "/research/" in url -> 60
        "/en/" in url -> 50
        else -> 10
    }
}

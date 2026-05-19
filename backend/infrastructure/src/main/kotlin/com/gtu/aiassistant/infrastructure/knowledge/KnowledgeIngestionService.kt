package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeIngestionRun
import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeIngestionRunStatus
import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeSourceRegistration
import com.gtu.aiassistant.domain.knowledge.port.output.SaveKnowledgeIngestionRunPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeSourcesPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import java.net.URI
import java.time.Instant
import java.util.UUID

class KnowledgeIngestionService(
    private val config: KnowledgeIngestionConfig,
    private val urlPolicy: GtuUrlPolicy,
    private val fetcher: GtuPageFetcher,
    private val documentBuilder: KnowledgeDocumentBuilder,
    private val upsertKnowledgeDocumentPort: UpsertKnowledgeDocumentPort,
    private val upsertKnowledgeSourcesPort: UpsertKnowledgeSourcesPort,
    private val saveKnowledgeIngestionRunPort: SaveKnowledgeIngestionRunPort
) {
    suspend fun ingestOnce(): Either<InfrastructureError, KnowledgeIngestionReport> {
        if (!config.enabled) {
            return Either.Right(KnowledgeIngestionReport(0, 0, 0, 0))
        }

        val startedAt = Instant.now()
        val result = either {
            upsertKnowledgeSourcesPort(sourceRegistrations(startedAt)).bind()

            val robots = fetcher.fetchRobots().fold(
                ifLeft = { RobotsRules.allowAll() },
                ifRight = { it }
            )
            val sitemapEntries = fetcher.fetchSitemap().bind()
                .asSequence()
                .mapNotNull { entry ->
                    val canonical = urlPolicy.canonicalize(entry.url) ?: return@mapNotNull null
                    if (!robots.isAllowed(canonical)) return@mapNotNull null
                    entry.copy(url = canonical)
                }
                .distinctBy { it.url }
                .toList()

            val priorityEntries = GtuLibraryKnowledgeSource.ALL_PRIORITY_URLS
                .mapNotNull { url ->
                    val canonical = urlPolicy.canonicalize(url) ?: return@mapNotNull null
                    if (!robots.isAllowed(canonical)) return@mapNotNull null
                    SitemapEntry(url = canonical, lastModifiedAt = null)
                }
                .distinctBy { it.url }
                .filter { priority -> sitemapEntries.none { it.url == priority.url } }

            val entries = (sitemapEntries + priorityEntries)
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

        return persistRunMetadata(
            startedAt = startedAt,
            finishedAt = Instant.now(),
            result = result
        )
    }

    private suspend fun persistRunMetadata(
        startedAt: Instant,
        finishedAt: Instant,
        result: Either<InfrastructureError, KnowledgeIngestionReport>
    ): Either<InfrastructureError, KnowledgeIngestionReport> {
        val run = result.fold(
            ifLeft = { error ->
                KnowledgeIngestionRun(
                    id = UUID.randomUUID(),
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    status = KnowledgeIngestionRunStatus.FAILED,
                    pagesSeen = 0,
                    pagesChanged = 0,
                    errorMessage = error.cause.message ?: error.cause::class.simpleName
                )
            },
            ifRight = { report ->
                KnowledgeIngestionRun(
                    id = UUID.randomUUID(),
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    status = KnowledgeIngestionRunStatus.SUCCEEDED,
                    pagesSeen = report.pagesSeen,
                    pagesChanged = report.pagesChanged,
                    errorMessage = null
                )
            }
        )

        val persisted = saveKnowledgeIngestionRunPort(run)
        return when {
            persisted is Either.Left && result is Either.Left -> {
                val original = result.value.cause
                val metadataFailure = persisted.value.cause
                original.addSuppressed(metadataFailure)
                Either.Left(InfrastructureError(original))
            }

            persisted is Either.Left -> Either.Left(persisted.value)
            else -> result
        }
    }

    private fun sourceRegistrations(createdAt: Instant): List<KnowledgeSourceRegistration> =
        config.allowedDomains
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .map { domain ->
                KnowledgeSourceRegistration(
                    domain = domain,
                    rootUrl = domain.toRootUrl(),
                    enabled = true,
                    createdAt = createdAt
                )
            }
            .toList()
}

private fun String.toRootUrl(): String =
    runCatching {
        val uri = URI(this)
        if (!uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank()) {
            "${uri.scheme}://${uri.host}/"
        } else {
            "https://${trim('/')}/"
        }
    }.getOrElse {
        "https://${trim('/')}/"
    }

internal fun String.priorityScore(): Int {
    val url = lowercase()
    val path = substringAfter("://", this)
        .substringAfter('/', "")
        .substringBefore('?')
        .trim('/')
    val segments = path.split('/').filter { it.isNotBlank() }
    val firstSegment = segments.firstOrNull().orEmpty()
    val isFacultyScoped = firstSegment.isNotBlank() && firstSegment !in ROOT_SITE_SEGMENTS

    val base = when {
        "/en/library/" in url || "/library/" in url -> 300
        "/el-books/" in url -> 290
        "/digital-library" in url -> 285
        "/databases.php" in url -> 280
        "/opac." in url -> 275
        "/acad-personal.php" in url || "/academic-personal.php" in url -> 270
        "/faculty-council.php" in url -> 260
        "/my.gtu.ge/en/faculties" in url -> 255
        "/en/students/edu/calendar.php" in url || "/students/edu/calendar.php" in url -> 250
        "/en/students/learning/calendar.php" in url || "/students/learning/calendar.php" in url -> 240
        "/en/students/edu/" in url || "/students/edu/" in url -> 220
        "/en/students/" in url || "/students/" in url -> 170
        "/en/apply/" in url || "/apply/" in url -> 150
        "/en/gtu/structure/faculties" in url || "/faculties/" in url -> 135
        "/en/gtu/about/" in url || "/about/" in url -> 120
        "/en/research/" in url || "/research/" in url -> 110
        "/en/" in url -> 90
        else -> 20
    }
    val facultyPenalty = if (isFacultyScoped) 45 else 0
    val depthPenalty = segments.size.coerceAtLeast(1) * 2

    return base - facultyPenalty - depthPenalty
}

private val ROOT_SITE_SEGMENTS = setOf(
    "en",
    "students",
    "apply",
    "gtu",
    "research",
    "news",
    "ka"
)

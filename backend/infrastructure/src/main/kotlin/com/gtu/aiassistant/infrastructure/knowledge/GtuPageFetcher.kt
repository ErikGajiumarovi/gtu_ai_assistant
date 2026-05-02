package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GtuPageFetcher(
    private val client: HttpClient,
    private val config: KnowledgeIngestionConfig
) {
    suspend fun fetchSitemap(): Either<InfrastructureError, List<SitemapEntry>> =
        fetchText(config.sitemapUrl).map { raw ->
            SITEMAP_URL_REGEX
                .findAll(raw)
                .mapNotNull { match ->
                    val url = match.groups["loc"]?.value?.trim() ?: return@mapNotNull null
                    val lastModified = match.groups["lastmod"]?.value?.trim()?.parseSitemapInstantOrNull()
                    SitemapEntry(url = url, lastModifiedAt = lastModified)
                }
                .toList()
        }

    suspend fun fetchRobots(): Either<InfrastructureError, RobotsRules> =
        fetchText(config.robotsUrl).map(RobotsRules::parse)

    suspend fun fetchPage(url: String): Either<InfrastructureError, FetchedPage?> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val response = client.get(url) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }
                if (response.status.value !in 200..299) {
                    return@catch null
                }

                val contentType = response.headers[HttpHeaders.ContentType]?.lowercase().orEmpty()

                if (contentType.isNotBlank() && !contentType.contains("text/html")) {
                    return@catch null
                }

                val body = response.bodyAsText().take(config.maxContentCharacters)
                if (body.isBlank()) {
                    null
                } else {
                    FetchedPage(
                        url = url,
                        html = body,
                        fetchedAt = Instant.now(),
                        sourceLastModifiedAt = response.headers["Last-Modified"]?.parseHttpDateOrNull()
                    )
                }
            }.mapLeft(::InfrastructureError)
        }

    private suspend fun fetchText(url: String): Either<InfrastructureError, String> =
        withContext(Dispatchers.IO) {
            Either.catch {
                client.get(url) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }.bodyAsText()
            }.mapLeft(::InfrastructureError)
        }

    companion object {
        private const val USER_AGENT = "GTU AI Assistant RAG Bot"
        private val SITEMAP_URL_REGEX = Regex(
            pattern = """<url>\s*<loc>(?<loc>.*?)</loc>(?:\s*<lastmod>(?<lastmod>.*?)</lastmod>)?.*?</url>""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )
    }
}

data class SitemapEntry(
    val url: String,
    val lastModifiedAt: Instant?
)

data class FetchedPage(
    val url: String,
    val html: String,
    val fetchedAt: Instant,
    val sourceLastModifiedAt: Instant?
)

private fun String.parseSitemapInstantOrNull(): Instant? =
    runCatching { ZonedDateTime.parse(this).toInstant() }
        .getOrNull()

private fun String.parseHttpDateOrNull(): Instant? =
    runCatching { ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
        .getOrNull()

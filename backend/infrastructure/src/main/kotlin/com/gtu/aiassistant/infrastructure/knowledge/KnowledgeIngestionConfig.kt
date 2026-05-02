package com.gtu.aiassistant.infrastructure.knowledge

import java.time.ZoneId

data class KnowledgeIngestionConfig(
    val enabled: Boolean,
    val schedulerEnabled: Boolean,
    val ingestOnStartup: Boolean,
    val sitemapUrl: String,
    val robotsUrl: String,
    val allowedDomains: Set<String>,
    val maxPagesPerRun: Int,
    val maxContentCharacters: Int,
    val refreshHour: Int,
    val zoneId: ZoneId
) {
    companion object {
        val DEFAULT_ALLOWED_DOMAINS = setOf(
            "gtu.ge",
            "www.gtu.edu.ge",
            "gtu.edu.ge",
            "my.gtu.ge",
            "studinfo.gtu.ge",
            "leqtori.gtu.ge",
            "institutes.gtu.ge",
            "library.gtu.ge",
            "elearning.gtu.ge",
            "testing.gtu.ge",
            "vici.gtu.ge",
            "syllabus.gtu.ge"
        )
    }
}

data class KnowledgeIngestionReport(
    val pagesSeen: Int,
    val pagesFetched: Int,
    val pagesChanged: Int,
    val pagesFailed: Int
)

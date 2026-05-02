package com.gtu.aiassistant.infrastructure.knowledge

import com.gtu.aiassistant.infrastructure.ai.embedding.HashingEmbeddingPort
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GtuKnowledgeUtilitiesTest {
    @Test
    fun `url policy allows public GTU pages and rejects private or asset URLs`() {
        val policy = GtuUrlPolicy(KnowledgeIngestionConfig.DEFAULT_ALLOWED_DOMAINS)

        assertEquals(
            "https://gtu.ge/en/students/edu/calendar.php",
            policy.canonicalize("https://www.gtu.ge/en/students/edu/calendar.php?utm_source=test#top")
        )
        assertFalse(policy.isAllowed("https://gtu.ge/Auth/"))
        assertFalse(policy.isAllowed("https://gtu.ge/local/logo.png"))
        assertFalse(policy.isAllowed("https://example.com/en/students/"))
    }

    @Test
    fun `robots rules apply allow and disallow patterns`() {
        val rules = RobotsRules.parse(
            """
            User-agent: *
            Disallow: */search/
            Disallow: *?q=
            Allow: /en/students/
            """.trimIndent()
        )

        assertTrue(rules.isAllowed("https://gtu.ge/en/students/edu/calendar.php"))
        assertFalse(rules.isAllowed("https://gtu.ge/en/search/"))
        assertFalse(rules.isAllowed("https://gtu.ge/en/news/?q=calendar"))
    }

    @Test
    fun `hashing embedding is deterministic and normalized`() = runBlocking {
        val port = HashingEmbeddingPort(dimensions = 32)

        val first = port("GTU academic calendar").getOrNull() ?: error("Expected embedding")
        val second = port("GTU academic calendar").getOrNull() ?: error("Expected embedding")

        assertEquals(32, first.size)
        assertEquals(first, second)
        val norm = sqrt(first.fold(0.0) { acc, value -> acc + value * value })
        assertTrue(norm > 0.99 && norm < 1.01)
    }
}

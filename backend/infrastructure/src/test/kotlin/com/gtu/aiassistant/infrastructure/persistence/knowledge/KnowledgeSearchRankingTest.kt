package com.gtu.aiassistant.infrastructure.persistence.knowledge

import com.gtu.aiassistant.infrastructure.ai.embedding.HashingEmbeddingPort
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnowledgeSearchRankingTest {
    @Test
    fun `hybrid ranking prefers academic calendar over generic student pages`() = runBlocking {
        val embeddings = HashingEmbeddingPort(dimensions = 128)
        val query = "When does the first fall semester of 2025-2026 begin at GTU?"
        val queryEmbedding = embeddings(query).getOrNull() ?: error("Expected query embedding")
        val signals = buildKnowledgeQuerySignals(query)

        val calendarText =
            "Academic Calendar of the Georgian Technical University for the 2025 2026 academic year. " +
                "The first fall semester begins on October 20 2025. Final exams are held in February 2026."
        val genericText =
            "Achievements of students of the Faculty of Architecture and Urbanism. " +
                "The page describes exhibitions, competitions and student projects."

        val calendarScore = scoreKnowledgeCandidate(
            signals = signals,
            queryEmbedding = queryEmbedding,
            title = "Academic Calendar",
            url = "https://gtu.ge/en/students/edu/calendar.php",
            text = calendarText,
            candidateEmbedding = embeddings("Academic Calendar\n$calendarText").getOrNull()
                ?: error("Expected calendar embedding")
        )
        val genericScore = scoreKnowledgeCandidate(
            signals = signals,
            queryEmbedding = queryEmbedding,
            title = "Student Achievements",
            url = "https://gtu.ge/arch/en/students/student-achievements.php",
            text = genericText,
            candidateEmbedding = embeddings("Student Achievements\n$genericText").getOrNull()
                ?: error("Expected generic embedding")
        )

        assertTrue(calendarScore.finalScore > genericScore.finalScore)
        assertTrue(shouldKeepKnowledgeCandidate(signals, calendarScore, minScore = 0.2))
        assertFalse(shouldKeepKnowledgeCandidate(signals, genericScore, minScore = 0.2))
    }

    @Test
    fun `hybrid ranking rejects candidates with numeric mismatch`() = runBlocking {
        val embeddings = HashingEmbeddingPort(dimensions = 128)
        val query = "What is the academic calendar for 2025 2026?"
        val queryEmbedding = embeddings(query).getOrNull() ?: error("Expected query embedding")
        val signals = buildKnowledgeQuerySignals(query)
        val mismatchedText =
            "Academic calendar for 2022 2023. The spring semester begins in March 2023."

        val score = scoreKnowledgeCandidate(
            signals = signals,
            queryEmbedding = queryEmbedding,
            title = "Academic Calendar",
            url = "https://gtu.ge/en/students/edu/calendar.php",
            text = mismatchedText,
            candidateEmbedding = embeddings("Academic Calendar\n$mismatchedText").getOrNull()
                ?: error("Expected mismatched embedding")
        )

        assertFalse(shouldKeepKnowledgeCandidate(signals, score, minScore = 0.2))
    }
}

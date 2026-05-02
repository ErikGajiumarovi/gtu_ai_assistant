package com.gtu.aiassistant.domain.knowledge.model

import java.time.Instant
import java.util.UUID

data class KnowledgeDocument(
    val id: UUID,
    val sourceUrl: String,
    val canonicalUrl: String,
    val title: String,
    val contentHash: String,
    val fetchedAt: Instant,
    val sourceLastModifiedAt: Instant?,
    val chunks: List<KnowledgeChunk>
)

data class KnowledgeChunk(
    val id: UUID,
    val documentId: UUID,
    val chunkIndex: Int,
    val title: String,
    val url: String,
    val text: String,
    val embedding: List<Float>
)

data class KnowledgeSearchQuery(
    val text: String,
    val maxResults: Int = 6,
    val minScore: Double = 0.2
)

data class KnowledgeSearchHit(
    val chunkId: UUID,
    val documentId: UUID,
    val title: String,
    val url: String,
    val snippet: String,
    val score: Double
)

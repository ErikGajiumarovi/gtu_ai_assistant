package com.gtu.aiassistant.infrastructure.persistence.knowledge

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchHit
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchQuery
import com.gtu.aiassistant.domain.knowledge.port.output.SearchKnowledgePort
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.selectAll

class SearchKnowledgePortImpl(
    private val executor: JdbcPersistenceExecutor,
    private val embeddingPort: EmbeddingPort
) : SearchKnowledgePort {
    override suspend fun invoke(query: KnowledgeSearchQuery) =
        either {
            val queryEmbedding = embeddingPort(query.text).bind()
            val querySignals = buildKnowledgeQuerySignals(query.text)

            executor.execute {
                KnowledgeChunkRecords.table
                    .selectAll()
                    .map { row ->
                        val text = row[KnowledgeChunkRecords.text]
                        val score = scoreKnowledgeCandidate(
                            signals = querySignals,
                            queryEmbedding = queryEmbedding,
                            title = row[KnowledgeChunkRecords.title],
                            url = row[KnowledgeChunkRecords.url],
                            text = text,
                            candidateEmbedding = row[KnowledgeChunkRecords.embedding]
                        )

                        score to KnowledgeSearchHit(
                            chunkId = java.util.UUID.fromString(row[KnowledgeChunkRecords.id]),
                            documentId = java.util.UUID.fromString(row[KnowledgeChunkRecords.documentId]),
                            title = row[KnowledgeChunkRecords.title],
                            url = row[KnowledgeChunkRecords.url],
                            snippet = text.toSnippet(),
                            score = score.finalScore
                        )
                    }
                    .asSequence()
                    .filter { (score, _) ->
                        shouldKeepKnowledgeCandidate(
                            signals = querySignals,
                            score = score,
                            minScore = query.minScore
                        )
                    }
                    .map { (_, hit) -> hit }
                    .sortedByDescending { it.score }
                    .toList()
                    .filterByTopScore(query.minScore)
                    .asSequence()
                    .distinctBy { it.url to it.snippet }
                    .take(query.maxResults)
                    .toList()
            }.bind()
        }
}

private fun List<KnowledgeSearchHit>.filterByTopScore(minScore: Double): List<KnowledgeSearchHit> {
    val topScore = firstOrNull()?.score ?: return emptyList()
    val threshold = maxOf(minScore, topScore * 0.72, topScore - 0.18)
    return filter { it.score >= threshold }
}

private fun String.toSnippet(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= 700) normalized else normalized.take(697).trimEnd() + "..."
}

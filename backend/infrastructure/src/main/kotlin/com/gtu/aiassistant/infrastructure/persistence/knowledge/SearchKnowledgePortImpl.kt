package com.gtu.aiassistant.infrastructure.persistence.knowledge

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchHit
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchQuery
import com.gtu.aiassistant.domain.knowledge.port.output.SearchKnowledgePort
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.math.sqrt

class SearchKnowledgePortImpl(
    private val executor: JdbcPersistenceExecutor,
    private val embeddingPort: EmbeddingPort
) : SearchKnowledgePort {
    override suspend fun invoke(query: KnowledgeSearchQuery) =
        either {
            val queryEmbedding = embeddingPort(query.text).bind()

            executor.execute {
                KnowledgeChunkRecords.table
                    .selectAll()
                    .map { row ->
                        val text = row[KnowledgeChunkRecords.text]
                        KnowledgeSearchHit(
                            chunkId = java.util.UUID.fromString(row[KnowledgeChunkRecords.id]),
                            documentId = java.util.UUID.fromString(row[KnowledgeChunkRecords.documentId]),
                            title = row[KnowledgeChunkRecords.title],
                            url = row[KnowledgeChunkRecords.url],
                            snippet = text.toSnippet(),
                            score = cosineSimilarity(queryEmbedding, row[KnowledgeChunkRecords.embedding])
                        )
                    }
                    .asSequence()
                    .filter { it.score >= query.minScore }
                    .sortedByDescending { it.score }
                    .distinctBy { it.url to it.snippet }
                    .take(query.maxResults)
                    .toList()
            }.bind()
        }
}

private fun cosineSimilarity(left: List<Float>, right: List<Float>): Double {
    val size = minOf(left.size, right.size)
    if (size == 0) return 0.0

    var dot = 0.0
    var leftNorm = 0.0
    var rightNorm = 0.0

    for (index in 0 until size) {
        val leftValue = left[index].toDouble()
        val rightValue = right[index].toDouble()
        dot += leftValue * rightValue
        leftNorm += leftValue * leftValue
        rightNorm += rightValue * rightValue
    }

    if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
    return dot / (sqrt(leftNorm) * sqrt(rightNorm))
}

private fun String.toSnippet(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= 700) normalized else normalized.take(697).trimEnd() + "..."
}

package com.gtu.aiassistant.infrastructure.persistence.materials

import arrow.core.raise.either
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.materials.model.MaterialSearchHit
import com.gtu.aiassistant.domain.materials.model.MaterialSearchQuery
import com.gtu.aiassistant.domain.materials.port.output.SearchUserMaterialsPort
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.Locale
import java.util.UUID

class SearchUserMaterialsPortImpl(
    private val executor: JdbcPersistenceExecutor,
    private val embeddingPort: EmbeddingPort
) : SearchUserMaterialsPort {
    override suspend fun invoke(query: MaterialSearchQuery) =
        either {
            val normalizedQuery = query.text.trim()
            if (normalizedQuery.isBlank()) {
                return@either emptyList()
            }

            val queryEmbedding = embeddingPort(normalizedQuery).bind()
            val maxResults = query.maxResults.coerceIn(1, 20)
            val candidateLimit = (maxResults * 4).coerceIn(maxResults, 80)
            val queryTokens = normalizedQuery.searchTokens()

            executor.execute {
                val candidates = selectCandidates(
                    query = query,
                    queryEmbedding = queryEmbedding,
                    limit = candidateLimit
                )

                candidates
                    .asSequence()
                    .map { candidate ->
                        val lexicalScore = lexicalScore(queryTokens, candidate.title, candidate.text, candidate.headingPath)
                        val finalScore = (candidate.vectorScore * 0.85 + lexicalScore * 0.15).coerceIn(0.0, 1.0)
                        candidate.toHit(score = finalScore)
                    }
                    .filter { hit -> hit.score >= query.minScore }
                    .sortedByDescending { hit -> hit.score }
                    .take(maxResults)
                    .toList()
            }.bind()
        }

    private fun selectCandidates(
        query: MaterialSearchQuery,
        queryEmbedding: List<Float>,
        limit: Int
    ): List<MaterialSearchCandidate> {
        val sql = buildString {
            append(
                """
                SELECT
                    c.id AS chunk_id,
                    c.document_id,
                    c.collection_id,
                    d.title,
                    c.text,
                    c.heading_path,
                    c.page_start,
                    c.page_end,
                    (1 - (c.embedding <=> '${queryEmbedding.toVectorLiteral()}'::vector)) AS vector_score
                FROM material_chunks c
                INNER JOIN material_documents d ON d.id = c.document_id
                WHERE c.owner_user_id = '${query.ownerUserId.value}'
                  AND d.owner_user_id = '${query.ownerUserId.value}'
                  AND d.ingestion_status = '${MaterialIngestionStatus.READY.name}'
                """.trimIndent()
            )
            if (query.collectionIds.isNotEmpty()) {
                append("\n  AND c.collection_id IN (${query.collectionIds.joinCollectionSqlUuidList()})")
            }
            if (query.documentIds.isNotEmpty()) {
                append("\n  AND c.document_id IN (${query.documentIds.joinDocumentSqlUuidList()})")
            }
            append("\nORDER BY c.embedding <=> '${queryEmbedding.toVectorLiteral()}'::vector")
            append("\nLIMIT $limit")
        }

        return TransactionManager.current().exec(sql) { resultSet ->
            val candidates = mutableListOf<MaterialSearchCandidate>()
            while (resultSet.next()) {
                candidates += MaterialSearchCandidate(
                    chunkId = UUID.fromString(resultSet.getString("chunk_id")),
                    documentId = MaterialDocumentId.fromTrusted(UUID.fromString(resultSet.getString("document_id"))),
                    collectionId = resultSet.getString("collection_id")?.let { value ->
                        MaterialCollectionId.fromTrusted(UUID.fromString(value))
                    },
                    title = resultSet.getString("title"),
                    text = resultSet.getString("text"),
                    vectorScore = resultSet.getDouble("vector_score").coerceIn(0.0, 1.0),
                    headingPath = resultSet.getString("heading_path"),
                    pageStart = resultSet.getIntOrNull("page_start"),
                    pageEnd = resultSet.getIntOrNull("page_end")
                )
            }
            candidates
        }.orEmpty()
    }
}

private data class MaterialSearchCandidate(
    val chunkId: UUID,
    val documentId: MaterialDocumentId,
    val collectionId: MaterialCollectionId?,
    val title: String,
    val text: String,
    val vectorScore: Double,
    val headingPath: String?,
    val pageStart: Int?,
    val pageEnd: Int?
) {
    fun toHit(score: Double): MaterialSearchHit =
        MaterialSearchHit(
            chunkId = chunkId,
            documentId = documentId,
            collectionId = collectionId,
            title = title,
            snippet = text.toSnippet(),
            score = score,
            headingPath = headingPath,
            pageStart = pageStart,
            pageEnd = pageEnd
        )
}

private fun java.sql.ResultSet.getIntOrNull(columnLabel: String): Int? {
    val value = getInt(columnLabel)
    return if (wasNull()) null else value
}

private fun List<MaterialCollectionId>.joinCollectionSqlUuidList(): String =
    joinToString { id -> "'${id.value}'" }

private fun List<MaterialDocumentId>.joinDocumentSqlUuidList(): String =
    joinToString { id -> "'${id.value}'" }

private fun List<Float>.toVectorLiteral(): String =
    joinToString(prefix = "[", postfix = "]") { value ->
        val safeValue = if (value.isFinite()) value else 0.0f
        String.format(Locale.US, "%.8f", safeValue)
    }

private fun lexicalScore(queryTokens: Set<String>, title: String, text: String, headingPath: String?): Double {
    if (queryTokens.isEmpty()) return 0.0

    val titleTokens = title.searchTokens()
    val headingTokens = headingPath.orEmpty().searchTokens()
    val textTokens = text.searchTokens()

    val titleOverlap = overlapRatio(queryTokens, titleTokens)
    val headingOverlap = overlapRatio(queryTokens, headingTokens)
    val textOverlap = overlapRatio(queryTokens, textTokens)

    return (textOverlap * 0.70 + titleOverlap * 0.20 + headingOverlap * 0.10).coerceIn(0.0, 1.0)
}

private fun overlapRatio(queryTokens: Set<String>, targetTokens: Set<String>): Double {
    if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
    return queryTokens.count { token -> token in targetTokens }.toDouble() / queryTokens.size
}

private fun String.searchTokens(): Set<String> =
    SEARCH_TOKEN_REGEX.findAll(lowercase())
        .map { match -> match.value }
        .filterNot { token -> token.length <= 1 }
        .toSet()

private fun String.toSnippet(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= 700) normalized else normalized.take(697).trimEnd() + "..."
}

private val SEARCH_TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

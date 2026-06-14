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
            val signals = buildMaterialQuerySignals(normalizedQuery)

            executor.execute {
                val candidates = (
                    selectVectorCandidates(
                        query = query,
                        queryEmbedding = queryEmbedding,
                        limit = candidateLimit
                    ) + selectLexicalCandidates(
                        query = query,
                        queryEmbedding = queryEmbedding,
                        signals = signals,
                        limit = candidateLimit
                    )
                ).distinctBy { it.chunkId }

                candidates
                    .asSequence()
                    .map { candidate ->
                        val finalScore = scoreMaterialCandidate(
                            signals = signals,
                            vectorScore = candidate.vectorScore,
                            title = candidate.title,
                            text = candidate.text,
                            headingPath = candidate.headingPath
                        )
                        candidate.toHit(score = finalScore)
                    }
                    .filter { hit -> hit.score >= query.minScore }
                    .sortedByDescending { hit -> hit.score }
                    .take(maxResults)
                    .toList()
            }.bind()
        }

    private fun selectVectorCandidates(
        query: MaterialSearchQuery,
        queryEmbedding: List<Float>,
        limit: Int
    ): List<MaterialSearchCandidate> =
        runCandidateQuery(
            sql = buildString {
                append(candidateSelectSql(queryEmbedding))
                append(candidateWhereSql(query))
                append("\nORDER BY c.embedding <=> '${queryEmbedding.toVectorLiteral()}'::vector")
                append("\nLIMIT $limit")
            }
        )

    private fun selectLexicalCandidates(
        query: MaterialSearchQuery,
        queryEmbedding: List<Float>,
        signals: MaterialQuerySignals,
        limit: Int
    ): List<MaterialSearchCandidate> {
        if (!signals.hasLexicalSignal) return emptyList()

        val lexicalConditions = signals.keywordTokens
            .flatMap { token -> token.toSqlLikePatterns() }
            .distinct()
            .joinToString(separator = "\n  OR ") { pattern ->
                "d.title ILIKE '$pattern' OR c.heading_path ILIKE '$pattern' OR c.text ILIKE '$pattern'"
            }

        return runCandidateQuery(
            sql = buildString {
                append(candidateSelectSql(queryEmbedding))
                append(candidateWhereSql(query))
                append("\n  AND ($lexicalConditions)")
                append("\nORDER BY c.chunk_index ASC")
                append("\nLIMIT $limit")
            }
        )
    }

    private fun candidateSelectSql(queryEmbedding: List<Float>): String =
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
        """.trimIndent()

    private fun candidateWhereSql(query: MaterialSearchQuery): String {
        val sql = buildString {
            append(
                """

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
        }

        return sql
    }

    private fun runCandidateQuery(sql: String): List<MaterialSearchCandidate> {
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

private fun String.toSqlLikePatterns(): List<String> {
    val escaped = replace("'", "''")
    val stem = take(PREFIX_SQL_MATCH_LENGTH).takeIf { length > PREFIX_SQL_MATCH_LENGTH }
    return listOfNotNull(
        "%$escaped%",
        stem?.let { "%${it.replace("'", "''")}%" }
    )
}

private fun List<Float>.toVectorLiteral(): String =
    joinToString(prefix = "[", postfix = "]") { value ->
        val safeValue = if (value.isFinite()) value else 0.0f
        String.format(Locale.US, "%.8f", safeValue)
    }

private fun String.toSnippet(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= 700) normalized else normalized.take(697).trimEnd() + "..."
}

private const val PREFIX_SQL_MATCH_LENGTH = 6

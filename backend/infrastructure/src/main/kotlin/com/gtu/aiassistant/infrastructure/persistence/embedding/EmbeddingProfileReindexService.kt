package com.gtu.aiassistant.infrastructure.persistence.embedding

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingConfig
import com.gtu.aiassistant.infrastructure.ai.embedding.EmbeddingPort
import com.gtu.aiassistant.infrastructure.persistence.schema.AppSettingsTable
import com.gtu.aiassistant.infrastructure.persistence.schema.KnowledgeDocumentsTable
import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialChunksTable
import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialDocumentsTable
import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialIngestionJobsTable
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

interface EmbeddingProfileReindexService {
    suspend fun sync(): Either<InfrastructureError, EmbeddingProfileReindexReport>
}

class PostgresEmbeddingProfileReindexService(
    private val executor: JdbcPersistenceExecutor,
    private val embeddingConfig: EmbeddingConfig,
    private val embeddingPort: EmbeddingPort
) : EmbeddingProfileReindexService {
    override suspend fun sync(): Either<InfrastructureError, EmbeddingProfileReindexReport> =
        either {
            val currentFingerprint = embeddingConfig.profileFingerprint()
            val previousFingerprint = readPreviousFingerprint().bind()

            if (previousFingerprint == currentFingerprint) {
                return@either EmbeddingProfileReindexReport(
                    changed = false,
                    previousFingerprint = previousFingerprint,
                    currentFingerprint = currentFingerprint
                )
            }

            embeddingPort(PROBE_TEXT).bind()
            resetIndexesAndSaveFingerprint(currentFingerprint).bind()

            EmbeddingProfileReindexReport(
                changed = true,
                previousFingerprint = previousFingerprint,
                currentFingerprint = currentFingerprint
            )
        }

    private suspend fun readPreviousFingerprint(): Either<InfrastructureError, String?> =
        executor.execute {
            AppSettingsTable
                .selectAll()
                .where { AppSettingsTable.key eq EMBEDDING_PROFILE_KEY }
                .singleOrNull()
                ?.get(AppSettingsTable.value)
        }

    private suspend fun resetIndexesAndSaveFingerprint(currentFingerprint: String): Either<InfrastructureError, Unit> =
        executor.execute {
            MaterialChunksTable.deleteAll()
            MaterialIngestionJobsTable.deleteAll()
            MaterialDocumentsTable.update({
                MaterialDocumentsTable.ingestionStatus inList REINDEXABLE_MATERIAL_STATUSES
            }) {
                it[ingestionStatus] = MATERIAL_STATUS_UPLOADED
                it[ingestionError] = null
                it[ocrMetadata] = null
                it[updatedAt] = Instant.now()
            }

            KnowledgeDocumentsTable.deleteAll()

            val updatedRows = AppSettingsTable.update({
                AppSettingsTable.key eq EMBEDDING_PROFILE_KEY
            }) {
                it[value] = currentFingerprint
            }
            if (updatedRows == 0) {
                AppSettingsTable.insert {
                    it[key] = EMBEDDING_PROFILE_KEY
                    it[value] = currentFingerprint
                }
            }
            Unit
        }

    companion object {
        const val EMBEDDING_PROFILE_KEY = "embedding.profile"
        private const val PROBE_TEXT = "GTU AI Assistant embedding profile probe"
        private const val MATERIAL_STATUS_UPLOADED = "UPLOADED"
        private val REINDEXABLE_MATERIAL_STATUSES = listOf("READY", "FAILED", "PROCESSING")
    }
}

class DisabledEmbeddingProfileReindexService(
    private val embeddingConfig: EmbeddingConfig
) : EmbeddingProfileReindexService {
    override suspend fun sync(): Either<InfrastructureError, EmbeddingProfileReindexReport> =
        Either.Right(
            EmbeddingProfileReindexReport(
                changed = false,
                previousFingerprint = null,
                currentFingerprint = embeddingConfig.profileFingerprint()
            )
        )
}

data class EmbeddingProfileReindexReport(
    val changed: Boolean,
    val previousFingerprint: String?,
    val currentFingerprint: String
)

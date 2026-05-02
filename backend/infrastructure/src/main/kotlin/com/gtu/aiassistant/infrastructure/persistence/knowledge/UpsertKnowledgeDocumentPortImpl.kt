package com.gtu.aiassistant.infrastructure.persistence.knowledge

import com.gtu.aiassistant.domain.knowledge.model.KnowledgeDocument
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentResult
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class UpsertKnowledgeDocumentPortImpl(
    private val executor: JdbcPersistenceExecutor
) : UpsertKnowledgeDocumentPort {
    override suspend fun invoke(document: KnowledgeDocument) =
        executor.execute {
            val existing = KnowledgeDocumentRecords.table
                .selectAll()
                .where { KnowledgeDocumentRecords.canonicalUrl eq document.canonicalUrl }
                .singleOrNull()

            val persistedDocumentId = existing?.get(KnowledgeDocumentRecords.id) ?: document.id.toString()

            if (existing == null) {
                KnowledgeDocumentRecords.table.insert {
                    it[KnowledgeDocumentRecords.id] = persistedDocumentId
                    it[KnowledgeDocumentRecords.sourceUrl] = document.sourceUrl
                    it[KnowledgeDocumentRecords.canonicalUrl] = document.canonicalUrl
                    it[KnowledgeDocumentRecords.title] = document.title
                    it[KnowledgeDocumentRecords.contentHash] = document.contentHash
                    it[KnowledgeDocumentRecords.fetchedAt] = document.fetchedAt
                    it[KnowledgeDocumentRecords.sourceLastModifiedAt] = document.sourceLastModifiedAt
                }
            } else {
                KnowledgeDocumentRecords.table.update({
                    KnowledgeDocumentRecords.id eq persistedDocumentId
                }) {
                    it[KnowledgeDocumentRecords.sourceUrl] = document.sourceUrl
                    it[KnowledgeDocumentRecords.title] = document.title
                    it[KnowledgeDocumentRecords.fetchedAt] = document.fetchedAt
                    it[KnowledgeDocumentRecords.sourceLastModifiedAt] = document.sourceLastModifiedAt
                    it[KnowledgeDocumentRecords.contentHash] = document.contentHash
                }
            }

            val changed = existing?.get(KnowledgeDocumentRecords.contentHash) != document.contentHash
            if (changed) {
                KnowledgeChunkRecords.table.deleteWhere {
                    KnowledgeChunkRecords.documentId eq persistedDocumentId
                }

                KnowledgeChunkRecords.table.batchInsert(document.chunks) { chunk ->
                    this[KnowledgeChunkRecords.id] = chunk.id.toString()
                    this[KnowledgeChunkRecords.documentId] = persistedDocumentId
                    this[KnowledgeChunkRecords.chunkIndex] = chunk.chunkIndex
                    this[KnowledgeChunkRecords.title] = chunk.title
                    this[KnowledgeChunkRecords.url] = chunk.url
                    this[KnowledgeChunkRecords.text] = chunk.text
                    this[KnowledgeChunkRecords.embedding] = chunk.embedding
                }
            }

            UpsertKnowledgeDocumentResult(changed = changed)
        }
}

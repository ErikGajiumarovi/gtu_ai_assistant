package com.gtu.aiassistant.infrastructure.persistence.knowledge

import com.gtu.aiassistant.infrastructure.persistence.schema.KnowledgeChunksTable
import com.gtu.aiassistant.infrastructure.persistence.schema.KnowledgeDocumentsTable
import com.gtu.aiassistant.infrastructure.persistence.schema.KnowledgeSourcesTable
import com.gtu.aiassistant.infrastructure.persistence.schema.IngestionRunsTable

internal object KnowledgeDocumentRecords {
    val table = KnowledgeDocumentsTable
    val id = table.id
    val sourceUrl = table.sourceUrl
    val canonicalUrl = table.canonicalUrl
    val title = table.title
    val contentHash = table.contentHash
    val fetchedAt = table.fetchedAt
    val sourceLastModifiedAt = table.sourceLastModifiedAt
}

internal object KnowledgeChunkRecords {
    val table = KnowledgeChunksTable
    val id = table.id
    val documentId = table.documentId
    val chunkIndex = table.chunkIndex
    val title = table.title
    val url = table.url
    val text = table.text
    val embedding = table.embedding
}

internal object KnowledgeSourceRecords {
    val table = KnowledgeSourcesTable
    val id = table.id
    val rootUrl = table.rootUrl
    val domain = table.domain
    val enabled = table.enabled
    val createdAt = table.createdAt
}

internal object KnowledgeIngestionRunRecords {
    val table = IngestionRunsTable
    val id = table.id
    val startedAt = table.startedAt
    val finishedAt = table.finishedAt
    val status = table.status
    val pagesSeen = table.pagesSeen
    val pagesChanged = table.pagesChanged
    val errorMessage = table.errorMessage
}

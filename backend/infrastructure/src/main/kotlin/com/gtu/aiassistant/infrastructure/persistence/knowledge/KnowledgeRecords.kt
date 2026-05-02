package com.gtu.aiassistant.infrastructure.persistence.knowledge

import com.gtu.aiassistant.infrastructure.persistence.schema.KnowledgeChunksTable
import com.gtu.aiassistant.infrastructure.persistence.schema.KnowledgeDocumentsTable

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

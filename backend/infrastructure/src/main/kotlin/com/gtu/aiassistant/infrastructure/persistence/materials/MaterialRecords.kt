package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialChunksTable
import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialCollectionsTable
import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialDocumentsTable
import com.gtu.aiassistant.infrastructure.persistence.schema.MaterialIngestionJobsTable

internal object MaterialCollectionRecords {
    val table = MaterialCollectionsTable
    val id = table.id
    val version = table.version
    val ownerUserId = table.ownerUserId
    val name = table.name
    val createdAt = table.createdAt
    val updatedAt = table.updatedAt
}

internal object MaterialDocumentRecords {
    val table = MaterialDocumentsTable
    val id = table.id
    val version = table.version
    val ownerUserId = table.ownerUserId
    val collectionId = table.collectionId
    val title = table.title
    val originalFileName = table.originalFileName
    val contentType = table.contentType
    val sizeBytes = table.sizeBytes
    val storageObjectKey = table.storageObjectKey
    val ingestionStatus = table.ingestionStatus
    val ingestionError = table.ingestionError
    val ocrMetadata = table.ocrMetadata
    val createdAt = table.createdAt
    val updatedAt = table.updatedAt
}

internal object MaterialChunkRecords {
    val table = MaterialChunksTable
    val id = table.id
    val ownerUserId = table.ownerUserId
    val documentId = table.documentId
    val collectionId = table.collectionId
    val chunkIndex = table.chunkIndex
    val text = table.text
    val embedding = table.embedding
    val headingPath = table.headingPath
    val pageStart = table.pageStart
    val pageEnd = table.pageEnd
}

internal object MaterialIngestionJobRecords {
    val table = MaterialIngestionJobsTable
    val id = table.id
    val ownerUserId = table.ownerUserId
    val documentId = table.documentId
    val status = table.status
    val errorMessage = table.errorMessage
    val createdAt = table.createdAt
    val updatedAt = table.updatedAt
}

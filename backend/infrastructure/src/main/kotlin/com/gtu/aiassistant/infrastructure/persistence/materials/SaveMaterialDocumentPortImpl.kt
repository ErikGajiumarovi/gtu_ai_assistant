package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialDocumentPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SaveMaterialDocumentPortImpl(
    private val executor: JdbcPersistenceExecutor
) : SaveMaterialDocumentPort {
    override suspend fun invoke(document: MaterialDocument) =
        executor.execute {
            val exists = MaterialDocumentRecords.table
                .selectAll()
                .where { MaterialDocumentRecords.id eq document.id.value.toString() }
                .singleOrNull() != null

            if (!exists) {
                MaterialDocumentRecords.table.insert {
                    it[MaterialDocumentRecords.id] = document.id.value.toString()
                    it[MaterialDocumentRecords.version] = document.version
                    it[MaterialDocumentRecords.ownerUserId] = document.ownerUserId.value.toString()
                    it[MaterialDocumentRecords.collectionId] = document.collectionId?.value?.toString()
                    it[MaterialDocumentRecords.title] = document.title
                    it[MaterialDocumentRecords.originalFileName] = document.originalFileName
                    it[MaterialDocumentRecords.contentType] = document.contentType
                    it[MaterialDocumentRecords.sizeBytes] = document.sizeBytes
                    it[MaterialDocumentRecords.storageObjectKey] = document.storageObjectKey
                    it[MaterialDocumentRecords.ingestionStatus] = document.ingestionStatus.name
                    it[MaterialDocumentRecords.ingestionError] = document.ingestionError
                    it[MaterialDocumentRecords.ocrMetadata] = document.ocrMetadata
                    it[MaterialDocumentRecords.createdAt] = document.createdAt
                    it[MaterialDocumentRecords.updatedAt] = document.updatedAt
                }
            } else {
                MaterialDocumentRecords.table.update({
                    MaterialDocumentRecords.id eq document.id.value.toString()
                }) {
                    it[MaterialDocumentRecords.version] = document.version
                    it[MaterialDocumentRecords.ownerUserId] = document.ownerUserId.value.toString()
                    it[MaterialDocumentRecords.collectionId] = document.collectionId?.value?.toString()
                    it[MaterialDocumentRecords.title] = document.title
                    it[MaterialDocumentRecords.originalFileName] = document.originalFileName
                    it[MaterialDocumentRecords.contentType] = document.contentType
                    it[MaterialDocumentRecords.sizeBytes] = document.sizeBytes
                    it[MaterialDocumentRecords.storageObjectKey] = document.storageObjectKey
                    it[MaterialDocumentRecords.ingestionStatus] = document.ingestionStatus.name
                    it[MaterialDocumentRecords.ingestionError] = document.ingestionError
                    it[MaterialDocumentRecords.ocrMetadata] = document.ocrMetadata
                    it[MaterialDocumentRecords.createdAt] = document.createdAt
                    it[MaterialDocumentRecords.updatedAt] = document.updatedAt
                }
            }

            document
        }
}

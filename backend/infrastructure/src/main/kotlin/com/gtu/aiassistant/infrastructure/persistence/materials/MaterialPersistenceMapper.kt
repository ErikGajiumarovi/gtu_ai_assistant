package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.user.model.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import java.util.UUID

internal fun ResultRow.toDomainMaterialCollection(): MaterialCollection =
    MaterialCollection.fromTrusted(
        id = MaterialCollectionId.fromTrusted(UUID.fromString(this[MaterialCollectionRecords.id])),
        version = this[MaterialCollectionRecords.version],
        ownerUserId = UserId.fromTrusted(UUID.fromString(this[MaterialCollectionRecords.ownerUserId])),
        name = this[MaterialCollectionRecords.name],
        createdAt = this[MaterialCollectionRecords.createdAt],
        updatedAt = this[MaterialCollectionRecords.updatedAt]
    )

internal fun ResultRow.toDomainMaterialDocument(): MaterialDocument =
    MaterialDocument.fromTrusted(
        id = MaterialDocumentId.fromTrusted(UUID.fromString(this[MaterialDocumentRecords.id])),
        version = this[MaterialDocumentRecords.version],
        ownerUserId = UserId.fromTrusted(UUID.fromString(this[MaterialDocumentRecords.ownerUserId])),
        collectionId = this[MaterialDocumentRecords.collectionId]
            ?.let { MaterialCollectionId.fromTrusted(UUID.fromString(it)) },
        title = this[MaterialDocumentRecords.title],
        originalFileName = this[MaterialDocumentRecords.originalFileName],
        contentType = this[MaterialDocumentRecords.contentType],
        sizeBytes = this[MaterialDocumentRecords.sizeBytes],
        storageObjectKey = this[MaterialDocumentRecords.storageObjectKey],
        ingestionStatus = MaterialIngestionStatus.valueOf(this[MaterialDocumentRecords.ingestionStatus]),
        ingestionError = this[MaterialDocumentRecords.ingestionError],
        ocrMetadata = this[MaterialDocumentRecords.ocrMetadata],
        createdAt = this[MaterialDocumentRecords.createdAt],
        updatedAt = this[MaterialDocumentRecords.updatedAt]
    )

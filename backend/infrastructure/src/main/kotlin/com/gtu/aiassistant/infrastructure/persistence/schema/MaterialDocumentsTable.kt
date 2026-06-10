package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object MaterialDocumentsTable : Table(name = "material_documents") {
    val id = varchar("id", length = 36)
    val version = long("version")
    val ownerUserId = reference("owner_user_id", UsersTable.id)
    val collectionId = reference("collection_id", MaterialCollectionsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val title = text("title")
    val originalFileName = text("original_file_name")
    val contentType = varchar("content_type", length = 255)
    val sizeBytes = long("size_bytes")
    val storageObjectKey = text("storage_object_key")
    val ingestionStatus = varchar("ingestion_status", length = 32)
    val ingestionError = text("ingestion_error").nullable()
    val ocrMetadata = text("ocr_metadata").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerUserId)
        index(false, collectionId)
        index(false, ingestionStatus)
    }
}

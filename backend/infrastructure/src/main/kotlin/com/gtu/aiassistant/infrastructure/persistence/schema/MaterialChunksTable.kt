package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object MaterialChunksTable : Table(name = "material_chunks") {
    val id = varchar("id", length = 36)
    val ownerUserId = reference("owner_user_id", UsersTable.id)
    val documentId = reference("document_id", MaterialDocumentsTable.id, onDelete = ReferenceOption.CASCADE)
    val collectionId = reference("collection_id", MaterialCollectionsTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val chunkIndex = integer("chunk_index")
    val text = text("text")
    val embedding = vector("embedding")
    val headingPath = text("heading_path").nullable()
    val pageStart = integer("page_start").nullable()
    val pageEnd = integer("page_end").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerUserId)
        index(false, documentId)
        index(false, collectionId)
        uniqueIndex(documentId, chunkIndex)
    }
}

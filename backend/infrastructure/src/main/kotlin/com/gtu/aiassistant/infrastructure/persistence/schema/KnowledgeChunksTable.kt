package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object KnowledgeChunksTable : Table(name = "knowledge_chunks") {
    val id = varchar("id", length = 36)
    val documentId = reference("document_id", KnowledgeDocumentsTable.id, onDelete = ReferenceOption.CASCADE)
    val chunkIndex = integer("chunk_index")
    val title = text("title")
    val url = text("url")
    val text = text("text")
    val embedding = vector("embedding")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(documentId, chunkIndex)
    }
}

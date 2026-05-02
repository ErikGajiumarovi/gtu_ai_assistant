package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object KnowledgeDocumentsTable : Table(name = "knowledge_documents") {
    val id = varchar("id", length = 36)
    val sourceUrl = text("source_url")
    val canonicalUrl = text("canonical_url")
    val title = text("title")
    val contentHash = varchar("content_hash", length = 64)
    val fetchedAt = timestamp("fetched_at")
    val sourceLastModifiedAt = timestamp("source_last_modified_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(canonicalUrl)
    }
}

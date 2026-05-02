package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object KnowledgeSourcesTable : Table(name = "knowledge_sources") {
    val id = varchar("id", length = 36)
    val rootUrl = text("root_url")
    val domain = varchar("domain", length = 255)
    val enabled = bool("enabled")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(domain)
    }
}

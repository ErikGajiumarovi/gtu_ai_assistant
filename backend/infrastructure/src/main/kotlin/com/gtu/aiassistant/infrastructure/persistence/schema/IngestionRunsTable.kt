package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object IngestionRunsTable : Table(name = "ingestion_runs") {
    val id = varchar("id", length = 36)
    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at").nullable()
    val status = varchar("status", length = 32)
    val pagesSeen = integer("pages_seen")
    val pagesChanged = integer("pages_changed")
    val errorMessage = text("error_message").nullable()

    override val primaryKey = PrimaryKey(id)
}

package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object MaterialIngestionJobsTable : Table(name = "material_ingestion_jobs") {
    val id = varchar("id", length = 36)
    val ownerUserId = reference("owner_user_id", UsersTable.id)
    val documentId = reference("document_id", MaterialDocumentsTable.id, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", length = 32)
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, ownerUserId)
        index(false, documentId)
        index(false, status)
    }
}

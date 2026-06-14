package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object PersistenceSchema {
    fun create(database: Database) {
        transaction(database) {
            exec("CREATE EXTENSION IF NOT EXISTS vector")

            SchemaUtils.create(
                UsersTable,
                ChatsTable,
                ChatMessagesTable,
                ChatMessageCitationsTable,
                KnowledgeSourcesTable,
                KnowledgeDocumentsTable,
                KnowledgeChunksTable,
                IngestionRunsTable,
                MaterialCollectionsTable,
                MaterialDocumentsTable,
                MaterialChunksTable,
                MaterialIngestionJobsTable,
                GeneratedArtifactsTable,
                AppSettingsTable
            )

            exec("ALTER TABLE chat_message_citations ADD COLUMN IF NOT EXISTS document_id varchar(36)")
            exec("ALTER TABLE chat_message_citations ADD COLUMN IF NOT EXISTS page_start integer")
            exec("ALTER TABLE chat_message_citations ADD COLUMN IF NOT EXISTS page_end integer")
            exec("ALTER TABLE material_documents ADD COLUMN IF NOT EXISTS ocr_metadata text")
            exec("ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS artifacts_json text")

            // HNSW requires a fixed-dimension vector column. The current schema stores
            // embeddings in a generic pgvector column, so keep startup portable and
            // skip the index here.
        }
    }
}

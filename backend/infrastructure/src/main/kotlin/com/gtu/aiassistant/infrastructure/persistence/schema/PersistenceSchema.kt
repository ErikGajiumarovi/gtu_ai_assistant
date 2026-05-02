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
                IngestionRunsTable
            )
        }
    }
}

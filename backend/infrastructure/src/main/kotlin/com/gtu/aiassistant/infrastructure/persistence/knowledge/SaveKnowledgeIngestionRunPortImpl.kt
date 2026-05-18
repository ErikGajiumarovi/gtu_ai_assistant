package com.gtu.aiassistant.infrastructure.persistence.knowledge

import com.gtu.aiassistant.domain.knowledge.port.output.SaveKnowledgeIngestionRunPort
import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeIngestionRun
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.insert

class SaveKnowledgeIngestionRunPortImpl(
    private val executor: JdbcPersistenceExecutor
) : SaveKnowledgeIngestionRunPort {
    override suspend fun invoke(run: KnowledgeIngestionRun) =
        executor.execute {
            KnowledgeIngestionRunRecords.table.insert {
                it[KnowledgeIngestionRunRecords.id] = run.id.toString()
                it[KnowledgeIngestionRunRecords.startedAt] = run.startedAt
                it[KnowledgeIngestionRunRecords.finishedAt] = run.finishedAt
                it[KnowledgeIngestionRunRecords.status] = run.status.name
                it[KnowledgeIngestionRunRecords.pagesSeen] = run.pagesSeen
                it[KnowledgeIngestionRunRecords.pagesChanged] = run.pagesChanged
                it[KnowledgeIngestionRunRecords.errorMessage] = run.errorMessage
            }
            Unit
        }
}

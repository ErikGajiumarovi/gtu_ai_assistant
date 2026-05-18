package com.gtu.aiassistant.infrastructure.persistence.knowledge

import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeSourceRegistration
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeSourcesPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class UpsertKnowledgeSourcesPortImpl(
    private val executor: JdbcPersistenceExecutor
) : UpsertKnowledgeSourcesPort {
    override suspend fun invoke(sources: List<KnowledgeSourceRegistration>) =
        executor.execute {
            sources.distinctBy { it.domain }.forEach { source ->
                val existing = KnowledgeSourceRecords.table
                    .selectAll()
                    .where { KnowledgeSourceRecords.domain eq source.domain }
                    .singleOrNull()

                if (existing == null) {
                    KnowledgeSourceRecords.table.insert {
                        it[KnowledgeSourceRecords.id] = source.domain.toStableUuid()
                        it[KnowledgeSourceRecords.rootUrl] = source.rootUrl
                        it[KnowledgeSourceRecords.domain] = source.domain
                        it[KnowledgeSourceRecords.enabled] = source.enabled
                        it[KnowledgeSourceRecords.createdAt] = source.createdAt
                    }
                } else {
                    KnowledgeSourceRecords.table.update({
                        KnowledgeSourceRecords.domain eq source.domain
                    }) {
                        it[KnowledgeSourceRecords.rootUrl] = source.rootUrl
                        it[KnowledgeSourceRecords.enabled] = source.enabled
                    }
                }
            }
        }
}

private fun String.toStableUuid(): String =
    java.util.UUID.nameUUIDFromBytes(toByteArray()).toString()

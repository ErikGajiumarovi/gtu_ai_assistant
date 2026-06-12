package com.gtu.aiassistant.infrastructure.persistence.artifacts

import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.port.output.FindGeneratedArtifactPort
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class FindGeneratedArtifactPortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindGeneratedArtifactPort {
    override suspend fun byId(id: GeneratedArtifactId) =
        executor.execute {
            GeneratedArtifactRecords.table
                .selectAll()
                .where { GeneratedArtifactRecords.id eq id.value.toString() }
                .singleOrNull()
                ?.toGeneratedArtifact()
        }

    override suspend fun byChat(chatId: ChatId) =
        executor.execute {
            GeneratedArtifactRecords.table
                .selectAll()
                .where { GeneratedArtifactRecords.chatId eq chatId.value.toString() }
                .orderBy(GeneratedArtifactRecords.createdAt to SortOrder.DESC)
                .map { it.toGeneratedArtifact() }
        }
}

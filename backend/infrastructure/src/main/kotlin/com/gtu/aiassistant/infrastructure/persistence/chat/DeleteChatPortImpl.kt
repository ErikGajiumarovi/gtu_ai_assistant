package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.port.output.DeleteChatPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

class DeleteChatPortImpl(
    private val executor: JdbcPersistenceExecutor
) : DeleteChatPort {
    override suspend fun invoke(chatId: ChatId) =
        executor.execute {
            ChatRecords.table.deleteWhere {
                ChatRecords.id eq chatId.value.toString()
            }

            Unit
        }
}

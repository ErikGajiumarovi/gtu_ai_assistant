package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class FindChatPortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindChatPort {
    override suspend fun invoke(strategy: FindChatPort.Strategy) =
        executor.execute {
            when (strategy) {
                is FindChatPort.Strategy.ById -> FindChatPort.Result.Single(
                    chat = findSingleChat(strategy.chatId.value)
                )

                is FindChatPort.Strategy.ByOwnedBy -> FindChatPort.Result.Multiple(
                    chats = findChatsByOwner(strategy.userId.value)
                )
            }
        }

    private fun findSingleChat(chatId: UUID): Chat? {
        val chatRow = ChatRecords.table
            .selectAll()
            .where { ChatRecords.id eq chatId.toString() }
            .singleOrNull()
            ?: return null

        val messages = ChatMessageRecords.table
            .selectAll()
            .where { ChatMessageRecords.chatId eq chatId.toString() }
            .orderBy(ChatMessageRecords.orderIndex to SortOrder.ASC)
            .map { it.toDomainMessage() }

        return chatRow.toChatSnapshot(messages)
    }

    private fun findChatsByOwner(userId: UUID): List<Chat> =
        ChatRecords.table
            .selectAll()
            .where { ChatRecords.ownedBy eq userId.toString() }
            .map { chatRow ->
                val chatId = UUID.fromString(chatRow[ChatRecords.id])
                val messages = ChatMessageRecords.table
                    .selectAll()
                    .where { ChatMessageRecords.chatId eq chatId.toString() }
                    .orderBy(ChatMessageRecords.orderIndex to SortOrder.ASC)
                    .map { it.toDomainMessage() }

                chatRow.toChatSnapshot(messages)
            }
}

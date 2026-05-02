package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SaveChatPortImpl(
    private val executor: JdbcPersistenceExecutor
) : SaveChatPort {
    override suspend fun invoke(chat: Chat) =
        executor.execute {
            val exists = ChatRecords.table
                .selectAll()
                .where { ChatRecords.id eq chat.id.value.toString() }
                .singleOrNull() != null

            if (!exists) {
                ChatRecords.table.insert {
                    it[ChatRecords.id] = chat.id.value.toString()
                    it[ChatRecords.version] = chat.version
                    it[ChatRecords.createdAt] = chat.createdAt
                    it[ChatRecords.updatedAt] = chat.updatedAt
                    it[ChatRecords.ownedBy] = chat.ownedBy.value.toString()
                }
            } else {
                val updatedRows = ChatRecords.table.update({
                    (ChatRecords.id eq chat.id.value.toString()) and
                        (ChatRecords.version eq (chat.version - 1))
                }) {
                    it[ChatRecords.version] = chat.version
                    it[ChatRecords.createdAt] = chat.createdAt
                    it[ChatRecords.updatedAt] = chat.updatedAt
                    it[ChatRecords.ownedBy] = chat.ownedBy.value.toString()
                }

                check(updatedRows == 1) {
                    "Chat optimistic lock failed for id=${chat.id.value}"
                }

                ChatMessageRecords.table.deleteWhere {
                    ChatMessageRecords.chatId eq chat.id.value.toString()
                }
            }

            ChatMessageRecords.table.batchInsert(
                data = chat.messages.withIndex().toList()
            ) { (index, message) ->
                this[ChatMessageRecords.id] = message.id.toString()
                this[ChatMessageRecords.chatId] = chat.id.value.toString()
                this[ChatMessageRecords.orderIndex] = index
                this[ChatMessageRecords.originalText] = message.originalText
                this[ChatMessageRecords.senderType] = message.senderType.name
                this[ChatMessageRecords.createdAt] = message.createdAt
            }

            val citations = chat.messages.flatMap { message ->
                message.citations.withIndex().map { (index, citation) ->
                    PersistableCitation(
                        messageId = message.id.toString(),
                        orderIndex = index,
                        title = citation.title,
                        url = citation.url,
                        snippet = citation.snippet,
                        sourceType = citation.sourceType.name
                    )
                }
            }

            ChatMessageCitationRecords.table.batchInsert(citations) { citation ->
                this[ChatMessageCitationRecords.messageId] = citation.messageId
                this[ChatMessageCitationRecords.orderIndex] = citation.orderIndex
                this[ChatMessageCitationRecords.title] = citation.title
                this[ChatMessageCitationRecords.url] = citation.url
                this[ChatMessageCitationRecords.snippet] = citation.snippet
                this[ChatMessageCitationRecords.sourceType] = citation.sourceType
            }

            chat
        }
}

private data class PersistableCitation(
    val messageId: String,
    val orderIndex: Int,
    val title: String,
    val url: String,
    val snippet: String,
    val sourceType: String
)

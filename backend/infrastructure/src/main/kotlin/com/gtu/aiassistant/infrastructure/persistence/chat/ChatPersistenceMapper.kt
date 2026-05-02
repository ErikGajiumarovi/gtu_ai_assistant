package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageCitation
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.user.model.UserId
import org.jetbrains.exposed.v1.core.ResultRow
import java.util.UUID

internal fun ResultRow.toDomainMessage(citations: List<MessageCitation> = emptyList()): Message =
    Message(
        id = UUID.fromString(this[ChatMessageRecords.id]),
        originalText = this[ChatMessageRecords.originalText],
        senderType = MessageSenderType.valueOf(this[ChatMessageRecords.senderType]),
        createdAt = this[ChatMessageRecords.createdAt],
        citations = citations
    )

internal fun ResultRow.toDomainMessageCitation(): MessageCitation =
    MessageCitation(
        title = this[ChatMessageCitationRecords.title],
        url = this[ChatMessageCitationRecords.url],
        snippet = this[ChatMessageCitationRecords.snippet],
        sourceType = MessageCitationSourceType.valueOf(this[ChatMessageCitationRecords.sourceType])
    )

internal fun ResultRow.toChatSnapshot(messages: List<Message>): Chat =
    Chat.fromTrusted(
        id = ChatId.fromTrusted(UUID.fromString(this[ChatRecords.id])),
        version = this[ChatRecords.version],
        messages = messages,
        createdAt = this[ChatRecords.createdAt],
        updatedAt = this[ChatRecords.updatedAt],
        ownedBy = UserId.fromTrusted(UUID.fromString(this[ChatRecords.ownedBy]))
    )

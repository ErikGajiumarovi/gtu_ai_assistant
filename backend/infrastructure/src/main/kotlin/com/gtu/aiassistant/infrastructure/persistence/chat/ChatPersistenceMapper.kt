package com.gtu.aiassistant.infrastructure.persistence.chat

import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.model.MessageArtifact
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageCitation
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.user.model.UserId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import java.util.UUID

private val chatPersistenceJson = Json { ignoreUnknownKeys = true }

internal fun ResultRow.toDomainMessage(citations: List<MessageCitation> = emptyList()): Message =
    Message(
        id = UUID.fromString(this[ChatMessageRecords.id]),
        originalText = this[ChatMessageRecords.originalText],
        senderType = MessageSenderType.valueOf(this[ChatMessageRecords.senderType]),
        createdAt = this[ChatMessageRecords.createdAt],
        citations = citations,
        artifacts = decodeArtifacts(this[ChatMessageRecords.artifactsJson])
    )

internal fun encodeArtifacts(artifacts: List<MessageArtifact>): String? =
    artifacts.takeIf { it.isNotEmpty() }?.let { values ->
        chatPersistenceJson.encodeToString(values.map { it.toPersistedArtifact() })
    }

internal fun ResultRow.toDomainMessageCitation(): MessageCitation =
    MessageCitation(
        title = this[ChatMessageCitationRecords.title],
        url = this[ChatMessageCitationRecords.url],
        snippet = this[ChatMessageCitationRecords.snippet],
        sourceType = MessageCitationSourceType.valueOf(this[ChatMessageCitationRecords.sourceType]),
        documentId = this[ChatMessageCitationRecords.documentId]?.let { value ->
            MaterialDocumentId.fromTrusted(UUID.fromString(value))
        },
        pageStart = this[ChatMessageCitationRecords.pageStart],
        pageEnd = this[ChatMessageCitationRecords.pageEnd]
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

private fun decodeArtifacts(value: String?): List<MessageArtifact> =
    if (value.isNullOrBlank()) {
        emptyList()
    } else {
        runCatching {
            chatPersistenceJson.decodeFromString<List<PersistedMessageArtifact>>(value)
                .map { it.toDomainArtifact() }
        }.getOrDefault(emptyList())
    }

private fun MessageArtifact.toPersistedArtifact(): PersistedMessageArtifact =
    PersistedMessageArtifact(
        id = id.value.toString(),
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl,
        viewUrl = viewUrl
    )

private fun PersistedMessageArtifact.toDomainArtifact(): MessageArtifact =
    MessageArtifact(
        id = GeneratedArtifactId.fromTrusted(UUID.fromString(id)),
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        downloadUrl = downloadUrl,
        viewUrl = viewUrl
    )

@Serializable
private data class PersistedMessageArtifact(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val viewUrl: String? = null
)

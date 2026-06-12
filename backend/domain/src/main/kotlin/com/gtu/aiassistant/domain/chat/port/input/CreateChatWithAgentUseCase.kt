package com.gtu.aiassistant.domain.chat.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatSources
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

interface CreateChatWithAgentUseCase {
    suspend operator fun invoke(command: CreateChatWithAgentCommand): Either<CreateChatWithAgentError, CreateChatWithAgentResult>
    suspend fun stream(
        command: CreateChatWithAgentCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit = {}
    ): Either<CreateChatWithAgentError, CreateChatWithAgentResult>
}

data class CreateChatWithAgentCommand(
    val userId: UserId,
    val message: Message,
    val sources: ChatSources = ChatSources(),
    val collectionIds: List<MaterialCollectionId> = emptyList(),
    val documentIds: List<MaterialDocumentId> = emptyList()
)

data class CreateChatWithAgentResult(
    val chat: Chat
)

sealed interface CreateChatWithAgentError {
    data class InvalidDomainState(
        val reason: DomainError
    ) : CreateChatWithAgentError

    data class MessageGenerationFailed(
        val reason: InfrastructureError
    ) : CreateChatWithAgentError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : CreateChatWithAgentError
}

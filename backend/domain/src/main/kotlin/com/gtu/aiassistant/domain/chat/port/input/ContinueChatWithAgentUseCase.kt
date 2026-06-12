package com.gtu.aiassistant.domain.chat.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.ChatSources
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

interface ContinueChatWithAgentUseCase {
    suspend operator fun invoke(command: ContinueChatWithAgentCommand): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult>
    suspend fun stream(
        command: ContinueChatWithAgentCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit = {}
    ): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult>
}

data class ContinueChatWithAgentCommand(
    val chatId: ChatId,
    val userId: UserId,
    val message: Message,
    val sources: ChatSources = ChatSources(),
    val collectionIds: List<MaterialCollectionId> = emptyList(),
    val documentIds: List<MaterialDocumentId> = emptyList()
)

data class ContinueChatWithAgentResult(
    val chat: Chat
)

sealed interface ContinueChatWithAgentError {
    data object ChatNotFound : ContinueChatWithAgentError

    data object AccessDenied : ContinueChatWithAgentError

    data class InvalidDomainState(
        val reason: DomainError
    ) : ContinueChatWithAgentError

    data class MessageGenerationFailed(
        val reason: InfrastructureError
    ) : ContinueChatWithAgentError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : ContinueChatWithAgentError
}

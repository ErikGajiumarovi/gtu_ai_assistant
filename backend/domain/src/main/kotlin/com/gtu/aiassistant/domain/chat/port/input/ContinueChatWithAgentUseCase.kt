package com.gtu.aiassistant.domain.chat.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface ContinueChatWithAgentUseCase {
    suspend operator fun invoke(command: ContinueChatWithAgentCommand): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult>
}

data class ContinueChatWithAgentCommand(
    val chatId: ChatId,
    val userId: UserId,
    val message: Message
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

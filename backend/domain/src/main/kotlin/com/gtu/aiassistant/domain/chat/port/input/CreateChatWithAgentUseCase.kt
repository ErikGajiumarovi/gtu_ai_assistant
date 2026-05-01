package com.gtu.aiassistant.domain.chat.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface CreateChatWithAgentUseCase {
    suspend operator fun invoke(command: CreateChatWithAgentCommand): Either<CreateChatWithAgentError, CreateChatWithAgentResult>
}

data class CreateChatWithAgentCommand(
    val userId: UserId,
    val message: Message
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

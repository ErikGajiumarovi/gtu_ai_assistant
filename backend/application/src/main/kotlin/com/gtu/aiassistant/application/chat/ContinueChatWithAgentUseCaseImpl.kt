package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration

class ContinueChatWithAgentUseCaseImpl(
    private val findChatPort: FindChatPort,
    private val generateMessagePort: GenerateMessagePort,
    private val saveChatPort: SaveChatPort
) : ContinueChatWithAgentUseCase {
    override suspend fun invoke(
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand
    ): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult> =
        either {
            val existingChat = findChatPort
                .invoke(
                    FindChatPort.Strategy.ById(
                        chatId = command.chatId
                    )
                )
                .mapLeft(ContinueChatWithAgentError::PersistenceFailed)
                .bind()
                .expectSingle()
                .mapLeft(ContinueChatWithAgentError::PersistenceFailed)
                .bind()

            ensure(existingChat != null) { ContinueChatWithAgentError.ChatNotFound }
            ensure(existingChat.ownedBy == command.userId) { ContinueChatWithAgentError.AccessDenied }

            val historyForGeneration = (existingChat.messages + command.message)
                .validateForMessageGeneration()
                .mapLeft(ContinueChatWithAgentError::InvalidDomainState)
                .bind()

            val generatedMessage = generateMessagePort
                .invoke(historyForGeneration)
                .mapLeft(ContinueChatWithAgentError::MessageGenerationFailed)
                .bind()

            val updatedChat = existingChat
                .appendMessages(
                    userMessage = command.message,
                    aiMessage = generatedMessage
                )
                .mapLeft(ContinueChatWithAgentError::InvalidDomainState)
                .bind()

            val persistedChat = saveChatPort
                .invoke(updatedChat)
                .mapLeft(ContinueChatWithAgentError::PersistenceFailed)
                .bind()

            ContinueChatWithAgentResult(
                chat = persistedChat
            )
        }
}

private fun Chat.appendMessages(
    userMessage: com.gtu.aiassistant.domain.chat.model.Message,
    aiMessage: com.gtu.aiassistant.domain.chat.model.Message
): Either<com.gtu.aiassistant.domain.model.DomainError, Chat> =
    Chat.create(
        id = id,
        version = version + 1,
        messages = messages + userMessage + aiMessage,
        createdAt = createdAt,
        updatedAt = aiMessage.createdAt,
        ownedBy = ownedBy
    )

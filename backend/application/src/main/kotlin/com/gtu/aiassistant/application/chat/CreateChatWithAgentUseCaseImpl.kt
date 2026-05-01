package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import java.util.UUID

class CreateChatWithAgentUseCaseImpl(
    private val generateMessagePort: GenerateMessagePort,
    private val saveChatPort: SaveChatPort
) : CreateChatWithAgentUseCase {
    override suspend fun invoke(
        command: com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand
    ): Either<CreateChatWithAgentError, CreateChatWithAgentResult> =
        either {
            val chatId = ChatId
                .create(UUID.randomUUID())
                .mapLeft(CreateChatWithAgentError::InvalidDomainState)
                .bind()

            val historyForGeneration = listOf(command.message)
                .validateForMessageGeneration()
                .mapLeft(CreateChatWithAgentError::InvalidDomainState)
                .bind()

            val generatedMessage = generateMessagePort
                .invoke(historyForGeneration)
                .mapLeft(CreateChatWithAgentError::MessageGenerationFailed)
                .bind()

            val chat = Chat
                .create(
                    id = chatId,
                    version = 0L,
                    messages = listOf(command.message, generatedMessage),
                    createdAt = command.message.createdAt,
                    updatedAt = generatedMessage.createdAt,
                    ownedBy = command.userId
                )
                .mapLeft(CreateChatWithAgentError::InvalidDomainState)
                .bind()

            val persistedChat = saveChatPort
                .invoke(chat)
                .mapLeft(CreateChatWithAgentError::PersistenceFailed)
                .bind()

            CreateChatWithAgentResult(
                chat = persistedChat
            )
        }
}

package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentError
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort

class ContinueChatWithAgentUseCaseImpl(
    private val findChatPort: FindChatPort,
    private val generateMessagePort: GenerateMessagePort,
    private val saveChatPort: SaveChatPort,
    private val findMaterialDocumentPort: FindMaterialDocumentPort,
    private val findMaterialCollectionPort: FindMaterialCollectionPort
) : ContinueChatWithAgentUseCase {
    override suspend fun invoke(
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand
    ): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult> =
        either {
            val existingChat = resolveChat(command).bind()
            val historyForGeneration = buildHistory(existingChat, command).bind()
            validateFilters(command).bind()
            val generatedMessage = generateMessagePort
                .invoke(command.toGenerateMessageCommand(historyForGeneration))
                .mapLeft(ContinueChatWithAgentError::MessageGenerationFailed)
                .bind()
            saveUpdatedChat(existingChat, command, generatedMessage).bind()
        }

    override suspend fun stream(
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit
    ): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult> =
        either {
            val existingChat = resolveChat(command).bind()
            val historyForGeneration = buildHistory(existingChat, command).bind()
            validateFilters(command).bind()
            val generatedMessage = generateMessagePort
                .stream(command.toGenerateMessageCommand(historyForGeneration), onToken, onStatus)
                .mapLeft(ContinueChatWithAgentError::MessageGenerationFailed)
                .bind()
            saveUpdatedChat(existingChat, command, generatedMessage).bind()
        }

    private suspend fun resolveChat(
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand
    ): Either<ContinueChatWithAgentError, com.gtu.aiassistant.domain.chat.model.Chat> = either {
        val existingChat = findChatPort
            .invoke(FindChatPort.Strategy.ById(chatId = command.chatId))
            .mapLeft(ContinueChatWithAgentError::PersistenceFailed)
            .bind()
            .expectSingle()
            .mapLeft(ContinueChatWithAgentError::PersistenceFailed)
            .bind()

        ensure(existingChat != null) { ContinueChatWithAgentError.ChatNotFound }
        ensure(existingChat.ownedBy == command.userId) { ContinueChatWithAgentError.AccessDenied }
        existingChat
    }

    private fun buildHistory(
        existingChat: com.gtu.aiassistant.domain.chat.model.Chat,
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand
    ): Either<ContinueChatWithAgentError, List<com.gtu.aiassistant.domain.chat.model.Message>> = either {
        (existingChat.messages + command.message)
            .validateForMessageGeneration()
            .mapLeft(ContinueChatWithAgentError::InvalidDomainState)
            .bind()
    }

    private suspend fun validateFilters(
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand
    ): Either<ContinueChatWithAgentError, Unit> =
        validateMaterialFilters(
            userId = command.userId,
            collectionIds = command.collectionIds,
            documentIds = command.documentIds,
            findMaterialDocumentPort = findMaterialDocumentPort,
            findMaterialCollectionPort = findMaterialCollectionPort
        ).mapLeft { error ->
            when (error) {
                is MaterialFilterValidationError.InvalidDomainState -> ContinueChatWithAgentError.InvalidDomainState(error.reason)
                is MaterialFilterValidationError.PersistenceFailed -> ContinueChatWithAgentError.PersistenceFailed(error.reason)
            }
        }

    private suspend fun saveUpdatedChat(
        existingChat: com.gtu.aiassistant.domain.chat.model.Chat,
        command: com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand,
        generatedMessage: com.gtu.aiassistant.domain.chat.model.Message
    ): Either<ContinueChatWithAgentError, ContinueChatWithAgentResult> = either {
        val updatedChat = existingChat.appendMessages(command.message, generatedMessage)
            .mapLeft(ContinueChatWithAgentError::InvalidDomainState)
            .bind()

        val persistedChat = saveChatPort
            .invoke(updatedChat)
            .mapLeft(ContinueChatWithAgentError::PersistenceFailed)
            .bind()

        ContinueChatWithAgentResult(chat = persistedChat)
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

private fun com.gtu.aiassistant.domain.chat.port.input.ContinueChatWithAgentCommand.toGenerateMessageCommand(
    messages: List<com.gtu.aiassistant.domain.chat.model.Message>
): GenerateMessageCommand =
    GenerateMessageCommand(
        messages = messages,
        userId = userId,
        sources = sources,
        collectionIds = collectionIds,
        documentIds = documentIds
    )

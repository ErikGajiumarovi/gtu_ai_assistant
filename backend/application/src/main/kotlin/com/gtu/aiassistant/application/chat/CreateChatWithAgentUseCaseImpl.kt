package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentResult
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentUseCase
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.chat.port.output.validateForMessageGeneration
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import java.util.UUID

class CreateChatWithAgentUseCaseImpl(
    private val generateMessagePort: GenerateMessagePort,
    private val saveChatPort: SaveChatPort,
    private val findMaterialDocumentPort: FindMaterialDocumentPort,
    private val findMaterialCollectionPort: FindMaterialCollectionPort
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

            validateMaterialFilters(
                userId = command.userId,
                collectionIds = command.collectionIds,
                documentIds = command.documentIds,
                findMaterialDocumentPort = findMaterialDocumentPort,
                findMaterialCollectionPort = findMaterialCollectionPort
            ).mapLeft { error ->
                when (error) {
                    is MaterialFilterValidationError.InvalidDomainState -> CreateChatWithAgentError.InvalidDomainState(error.reason)
                    is MaterialFilterValidationError.PersistenceFailed -> CreateChatWithAgentError.PersistenceFailed(error.reason)
                }
            }.bind()

            val generatedMessage = generateMessagePort
                .invoke(command.toGenerateMessageCommand(historyForGeneration))
                .mapLeft(CreateChatWithAgentError::MessageGenerationFailed)
                .bind()

            buildChat(chatId, command, generatedMessage).bind()
        }

    override suspend fun stream(
        command: com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit
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

            validateMaterialFilters(
                userId = command.userId,
                collectionIds = command.collectionIds,
                documentIds = command.documentIds,
                findMaterialDocumentPort = findMaterialDocumentPort,
                findMaterialCollectionPort = findMaterialCollectionPort
            ).mapLeft { error ->
                when (error) {
                    is MaterialFilterValidationError.InvalidDomainState -> CreateChatWithAgentError.InvalidDomainState(error.reason)
                    is MaterialFilterValidationError.PersistenceFailed -> CreateChatWithAgentError.PersistenceFailed(error.reason)
                }
            }.bind()

            val generatedMessage = generateMessagePort
                .stream(command.toGenerateMessageCommand(historyForGeneration), onToken, onStatus)
                .mapLeft(CreateChatWithAgentError::MessageGenerationFailed)
                .bind()

            buildChat(chatId, command, generatedMessage).bind()
        }

    private suspend fun buildChat(
        chatId: ChatId,
        command: com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand,
        generatedMessage: com.gtu.aiassistant.domain.chat.model.Message
    ): Either<CreateChatWithAgentError, CreateChatWithAgentResult> = either {
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

        CreateChatWithAgentResult(chat = persistedChat)
    }
}

private fun com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand.toGenerateMessageCommand(
    messages: List<com.gtu.aiassistant.domain.chat.model.Message>
): GenerateMessageCommand =
    GenerateMessageCommand(
        messages = messages,
        userId = userId,
        sources = sources,
        collectionIds = collectionIds,
        documentIds = documentIds
    )

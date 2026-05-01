package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatError
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatResult
import com.gtu.aiassistant.domain.chat.port.input.DeleteChatUseCase
import com.gtu.aiassistant.domain.chat.port.output.DeleteChatPort
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort

class DeleteChatUseCaseImpl(
    private val findChatPort: FindChatPort,
    private val deleteChatPort: DeleteChatPort
) : DeleteChatUseCase {
    override suspend fun invoke(
        command: com.gtu.aiassistant.domain.chat.port.input.DeleteChatCommand
    ): Either<DeleteChatError, DeleteChatResult> =
        either {
            val existingChat = findChatPort
                .invoke(
                    FindChatPort.Strategy.ById(
                        chatId = command.chatId
                    )
                )
                .mapLeft(DeleteChatError::FindFailed)
                .bind()
                .expectSingle()
                .mapLeft(DeleteChatError::FindFailed)
                .bind()

            ensure(existingChat != null) { DeleteChatError.ChatNotFound }
            ensure(existingChat.ownedBy == command.userId) { DeleteChatError.AccessDenied }

            deleteChatPort
                .invoke(command.chatId)
                .mapLeft(DeleteChatError::DeleteFailed)
                .bind()

            DeleteChatResult
        }
}

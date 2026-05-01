package com.gtu.aiassistant.domain.chat.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface DeleteChatUseCase {
    suspend operator fun invoke(command: DeleteChatCommand): Either<DeleteChatError, DeleteChatResult>
}

data class DeleteChatCommand(
    val userId: UserId,
    val chatId: ChatId
)

data object DeleteChatResult

sealed interface DeleteChatError {
    data object ChatNotFound : DeleteChatError

    data object AccessDenied : DeleteChatError

    data class FindFailed(
        val reason: InfrastructureError
    ) : DeleteChatError

    data class DeleteFailed(
        val reason: InfrastructureError
    ) : DeleteChatError
}

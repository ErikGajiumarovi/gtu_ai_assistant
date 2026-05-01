package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.domain.model.InfrastructureError

internal fun FindChatPort.Result.expectSingle(): Either<InfrastructureError, Chat?> =
    when (this) {
        is FindChatPort.Result.Single -> chat.right()
        is FindChatPort.Result.Multiple -> InfrastructureError(
            cause = IllegalStateException("Expected single chat result but received multiple chats")
        ).left()
    }

internal fun FindChatPort.Result.expectMultiple(): Either<InfrastructureError, List<Chat>> =
    when (this) {
        is FindChatPort.Result.Multiple -> chats.right()
        is FindChatPort.Result.Single -> InfrastructureError(
            cause = IllegalStateException("Expected multiple chats result but received single chat")
        ).left()
    }

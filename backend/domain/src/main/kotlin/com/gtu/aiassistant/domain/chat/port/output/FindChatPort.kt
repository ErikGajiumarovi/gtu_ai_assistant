package com.gtu.aiassistant.domain.chat.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface FindChatPort {
    suspend operator fun invoke(strategy: Strategy): Either<InfrastructureError, Result>

    sealed interface Strategy {
        data class ById(
            val chatId: ChatId
        ) : Strategy

        data class ByOwnedBy(
            val userId: UserId
        ) : Strategy
    }

    sealed interface Result {
        data class Single(
            val chat: Chat?
        ) : Result

        data class Multiple(
            val chats: List<Chat>
        ) : Result
    }
}

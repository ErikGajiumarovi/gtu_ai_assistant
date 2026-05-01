package com.gtu.aiassistant.app.memory

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.output.DeleteChatPort
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import java.time.Instant
import java.util.UUID

class InMemoryFindChatPort(
    private val state: InMemoryState
) : FindChatPort {
    override suspend fun invoke(strategy: FindChatPort.Strategy): Either<InfrastructureError, FindChatPort.Result> =
        Either.Right(
            when (strategy) {
                is FindChatPort.Strategy.ById -> FindChatPort.Result.Single(
                    chat = state.chats[strategy.chatId.value.toString()]
                )

                is FindChatPort.Strategy.ByOwnedBy -> FindChatPort.Result.Multiple(
                    chats = state.chats.values
                        .filter { it.ownedBy == strategy.userId }
                        .sortedBy { it.createdAt }
                )
            }
        )
}

class InMemorySaveChatPort(
    private val state: InMemoryState
) : SaveChatPort {
    override suspend fun invoke(chat: Chat): Either<InfrastructureError, Chat> {
        state.chats[chat.id.value.toString()] = chat
        return Either.Right(chat)
    }
}

class InMemoryDeleteChatPort(
    private val state: InMemoryState
) : DeleteChatPort {
    override suspend fun invoke(chatId: com.gtu.aiassistant.domain.chat.model.ChatId): Either<InfrastructureError, Unit> {
        state.chats.remove(chatId.value.toString())
        return Either.Right(Unit)
    }
}

class InMemoryGenerateMessagePort : GenerateMessagePort {
    override suspend fun invoke(messages: List<Message>): Either<InfrastructureError, Message> {
        val lastMessage = messages.last()

        return Either.Right(
            Message(
                id = UUID.randomUUID(),
                originalText = "AI response to: ${lastMessage.originalText}",
                senderType = MessageSenderType.AI,
                createdAt = maxOf(Instant.now(), lastMessage.createdAt.plusMillis(1))
            )
        )
    }
}

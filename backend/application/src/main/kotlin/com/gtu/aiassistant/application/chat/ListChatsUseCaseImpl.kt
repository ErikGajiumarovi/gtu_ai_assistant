package com.gtu.aiassistant.application.chat

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.chat.port.input.ListChatsError
import com.gtu.aiassistant.domain.chat.port.input.ListChatsResult
import com.gtu.aiassistant.domain.chat.port.input.ListChatsUseCase
import com.gtu.aiassistant.domain.chat.port.output.FindChatPort

class ListChatsUseCaseImpl(
    private val findChatPort: FindChatPort
) : ListChatsUseCase {
    override suspend fun invoke(
        query: com.gtu.aiassistant.domain.chat.port.input.ListChatsQuery
    ): Either<ListChatsError, ListChatsResult> =
        either {
            val chats = findChatPort
                .invoke(
                    FindChatPort.Strategy.ByOwnedBy(
                        userId = query.userId
                    )
                )
                .mapLeft(ListChatsError::FindFailed)
                .bind()
                .expectMultiple()
                .mapLeft(ListChatsError::FindFailed)
                .bind()

            ListChatsResult(
                chats = chats
            )
        }
}

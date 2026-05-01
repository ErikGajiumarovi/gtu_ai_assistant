package com.gtu.aiassistant.domain.chat.port.input

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface ListChatsUseCase {
    suspend operator fun invoke(query: ListChatsQuery): Either<ListChatsError, ListChatsResult>
}

data class ListChatsQuery(
    val userId: UserId
)

data class ListChatsResult(
    val chats: List<Chat>
)

sealed interface ListChatsError {
    data class FindFailed(
        val reason: InfrastructureError
    ) : ListChatsError
}

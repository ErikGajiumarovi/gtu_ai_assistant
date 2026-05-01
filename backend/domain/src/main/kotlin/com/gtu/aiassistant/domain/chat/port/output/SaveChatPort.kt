package com.gtu.aiassistant.domain.chat.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Chat
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface SaveChatPort {
    suspend operator fun invoke(chat: Chat): Either<InfrastructureError, Chat>
}

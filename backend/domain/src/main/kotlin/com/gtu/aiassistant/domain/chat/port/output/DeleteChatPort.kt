package com.gtu.aiassistant.domain.chat.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface DeleteChatPort {
    suspend operator fun invoke(chatId: ChatId): Either<InfrastructureError, Unit>
}

package com.gtu.aiassistant.domain.chat.model

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.model.DomainError
import java.util.UUID
import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class ChatId private constructor(
    val value: UUID
) {
    companion object {
        fun create(value: String): Either<DomainError, ChatId> =
            Either
                .catch { UUID.fromString(value.trim()) }
                .map(::ChatId)
                .mapLeft { ChatIdError.InvalidFormat }

        fun create(value: UUID): Either<DomainError, ChatId> =
            either { ChatId(value) }

        fun fromTrusted(value: UUID): ChatId =
            ChatId(value)
    }
}

sealed interface ChatIdError : DomainError {
    data object InvalidFormat : ChatIdError
}

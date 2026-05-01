package com.gtu.aiassistant.domain.chat.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.AggregateRoot
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.user.model.UserId
import java.time.Instant

class Chat private constructor(
    override val id: ChatId,
    override val version: Long,
    val messages: List<Message>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val ownedBy: UserId
) : AggregateRoot<ChatId>(id, version) {
    companion object {
        fun create(
            id: ChatId,
            version: Long,
            messages: List<Message>,
            createdAt: Instant,
            updatedAt: Instant,
            ownedBy: UserId
        ): Either<DomainError, Chat> =
            either {
                ensure(version >= 0L) { ChatError.InvalidVersion }
                ensure(updatedAt >= createdAt) { ChatError.InvalidTimestamps }
                ensure(messages.isSortedByCreatedAt()) { ChatError.MessagesAreNotSorted }
                ensure(messages.isAlternatingBySenderType()) { ChatError.MessagesMustAlternateBySenderType }

                Chat(
                    id = id,
                    version = version,
                    messages = messages.toList(),
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    ownedBy = ownedBy
                )
            }

        fun fromTrusted(
            id: ChatId,
            version: Long,
            messages: List<Message>,
            createdAt: Instant,
            updatedAt: Instant,
            ownedBy: UserId
        ): Chat =
            Chat(
                id = id,
                version = version,
                messages = messages.toList(),
                createdAt = createdAt,
                updatedAt = updatedAt,
                ownedBy = ownedBy
            )
    }
}

sealed interface ChatError : DomainError {
    data object InvalidVersion : ChatError
    data object InvalidTimestamps : ChatError
    data object MessagesAreNotSorted : ChatError
    data object MessagesMustAlternateBySenderType : ChatError
}

private fun List<Message>.isSortedByCreatedAt(): Boolean =
    zipWithNext().all { (left, right) -> left.createdAt <= right.createdAt }

private fun List<Message>.isAlternatingBySenderType(): Boolean =
    zipWithNext().all { (left, right) -> left.senderType != right.senderType }

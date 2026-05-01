package com.gtu.aiassistant.domain.chat.port.output

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface GenerateMessagePort {
    suspend operator fun invoke(messages: List<Message>): Either<InfrastructureError, Message>
}

fun List<Message>.validateForMessageGeneration(): Either<DomainError, List<Message>> =
    either {
        ensure(isNotEmpty()) { GenerateMessageHistoryError.EmptyHistory }
        ensure(isSortedByCreatedAt()) { GenerateMessageHistoryError.MessagesAreNotSorted }
        ensure(isAlternatingBySenderType()) { GenerateMessageHistoryError.MessagesMustAlternateBySenderType }
        ensure(last().senderType == MessageSenderType.USER) { GenerateMessageHistoryError.LastMessageMustBeFromUser }

        this@validateForMessageGeneration
    }

sealed interface GenerateMessageHistoryError : DomainError {
    data object EmptyHistory : GenerateMessageHistoryError
    data object MessagesAreNotSorted : GenerateMessageHistoryError
    data object MessagesMustAlternateBySenderType : GenerateMessageHistoryError
    data object LastMessageMustBeFromUser : GenerateMessageHistoryError
}

private fun List<Message>.isSortedByCreatedAt(): Boolean =
    zipWithNext().all { (left, right) -> left.createdAt <= right.createdAt }

private fun List<Message>.isAlternatingBySenderType(): Boolean =
    zipWithNext().all { (left, right) -> left.senderType != right.senderType }

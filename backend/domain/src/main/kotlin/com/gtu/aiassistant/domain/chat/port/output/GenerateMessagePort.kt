package com.gtu.aiassistant.domain.chat.port.output

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.chat.model.ChatSources
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

interface GenerateMessagePort {
    suspend operator fun invoke(command: GenerateMessageCommand): Either<InfrastructureError, Message>
    suspend fun stream(
        command: GenerateMessageCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (GenerateMessageStreamStatus) -> Unit = {}
    ): Either<InfrastructureError, Message>
}

data class GenerateMessageStreamStatus(
    val phase: String,
    val message: String
)

data class GenerateMessageCommand(
    val messages: List<Message>,
    val userId: UserId,
    val sources: ChatSources = ChatSources(),
    val collectionIds: List<MaterialCollectionId> = emptyList(),
    val documentIds: List<MaterialDocumentId> = emptyList()
)

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

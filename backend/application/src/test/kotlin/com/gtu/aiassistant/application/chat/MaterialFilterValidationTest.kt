package com.gtu.aiassistant.application.chat

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentError
import com.gtu.aiassistant.domain.chat.port.input.CreateChatWithAgentCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessageCommand
import com.gtu.aiassistant.domain.chat.port.output.GenerateMessagePort
import com.gtu.aiassistant.domain.chat.port.output.SaveChatPort
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class MaterialFilterValidationTest {
    @Test
    fun `create chat rejects unknown selected material document before generation`() = runBlocking {
        var generateCalled = false
        val useCase = CreateChatWithAgentUseCaseImpl(
            generateMessagePort = GenerateMessagePort { command ->
                generateCalled = true
                Either.Right(command.messages.last())
            },
            saveChatPort = SaveChatPort { Either.Right(it) },
            findMaterialDocumentPort = FindMaterialDocumentPort {
                Either.Right(FindMaterialDocumentPort.Result.Single(document = null))
            },
            findMaterialCollectionPort = FindMaterialCollectionPort {
                Either.Right(FindMaterialCollectionPort.Result.Single(collection = null))
            }
        )

        val result = useCase(
            CreateChatWithAgentCommand(
                userId = UserId.fromTrusted(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                message = Message(
                    id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    originalText = "Use my file",
                    senderType = MessageSenderType.USER,
                    createdAt = Instant.EPOCH
                ),
                documentIds = listOf(MaterialDocumentId.fromTrusted(UUID.fromString("33333333-3333-3333-3333-333333333333")))
            )
        )

        assertIs<CreateChatWithAgentError.InvalidDomainState>(result.leftOrNull())
        assertFalse(generateCalled)
    }
}

private fun interface TestGenerateMessageHandler {
    suspend fun invoke(command: GenerateMessageCommand): Either<InfrastructureError, Message>
}

private fun GenerateMessagePort(handler: TestGenerateMessageHandler): GenerateMessagePort = object : GenerateMessagePort {
    override suspend fun invoke(command: GenerateMessageCommand): Either<InfrastructureError, Message> = handler.invoke(command)

    override suspend fun stream(
        command: GenerateMessageCommand,
        onToken: suspend (String) -> Unit,
        onStatus: suspend (com.gtu.aiassistant.domain.chat.port.output.GenerateMessageStreamStatus) -> Unit
    ): Either<InfrastructureError, Message> = handler.invoke(command)
}

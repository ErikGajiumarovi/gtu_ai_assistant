package com.gtu.aiassistant.infrastructure.ai

import com.gtu.aiassistant.domain.chat.model.Message
import com.gtu.aiassistant.domain.chat.model.MessageSenderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant
import java.util.UUID

class GenerateMessagePortImplIntegrationTest {

    @Test
    fun `generate message works with default gpt oss 20b`() = runIntegrationTest(
        config = AiConfig.default20b()
    )

    @Test
    fun `generate message works with gpt oss 120b`() = runIntegrationTest(
        config = AiConfig.default120b()
    )

    private fun runIntegrationTest(
        config: AiConfig
    ) {
        val port = GenerateMessagePortImpl.create(config)
        val baseTime = Instant.parse("2026-05-02T00:00:00Z")

        val messages = listOf(
            Message(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                originalText = "You are helping me test a chat application.",
                senderType = MessageSenderType.USER,
                createdAt = baseTime
            ),
            Message(
                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                originalText = "Understood. I will keep answers concise.",
                senderType = MessageSenderType.AI,
                createdAt = baseTime.plusSeconds(1)
            ),
            Message(
                id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                originalText = "Reply with one short sentence that confirms the integration is working.",
                senderType = MessageSenderType.USER,
                createdAt = baseTime.plusSeconds(2)
            )
        )

        val result = kotlinx.coroutines.runBlocking {
            port(messages)
        }

        result.fold(
            ifLeft = { error ->
                throw AssertionError("Expected AI message, got error: $error")
            },
            ifRight = { generatedMessage ->
                assertEquals(MessageSenderType.AI, generatedMessage.senderType)
                assertFalse(generatedMessage.originalText.isBlank())
                assertTrue(generatedMessage.createdAt > messages.last().createdAt)
            }
        )
    }
}

package com.gtu.aiassistant.presentation

import com.gtu.aiassistant.domain.model.DomainError
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val id: String,
    val name: String,
    val lastName: String,
    val email: String
)

@Serializable
data class CreateChatWithAgentRequest(
    val userId: String,
    val originalText: String
)

@Serializable
data class ContinueChatWithAgentRequest(
    val userId: String,
    val originalText: String
)

@Serializable
data class UserResponse(
    val id: String,
    val version: Long,
    val name: String,
    val lastName: String,
    val email: String
)

@Serializable
data class MessageResponse(
    val id: String,
    val originalText: String,
    val senderType: String,
    val createdAt: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val version: Long,
    val ownedBy: String,
    val createdAt: String,
    val updatedAt: String,
    val messages: List<MessageResponse>
)

@Serializable
data class ListChatsResponse(
    val chats: List<ChatResponse>
)

@Serializable
data class DeleteChatResponse(
    val deleted: Boolean
)

@Serializable
data class HealthResponse(
    val status: String
)

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String
) {
    companion object {
        fun fromDomainError(error: DomainError): ApiErrorResponse =
            ApiErrorResponse(
                code = error::class.simpleName ?: "domain_error",
                message = error.toString()
            )
    }
}

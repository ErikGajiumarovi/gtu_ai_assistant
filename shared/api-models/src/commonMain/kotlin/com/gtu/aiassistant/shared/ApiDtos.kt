package com.gtu.aiassistant.shared

import kotlinx.serialization.Serializable

@Serializable
data class RegisterUserRequest(
    val name: String,
    val lastName: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginInRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginInResponse(
    val jwt: String
)

@Serializable
data class AgentSources(
    val gtu: Boolean = true,
    val materials: Boolean = true,
    val web: Boolean = false
)

@Serializable
data class CreateChatWithAgentRequest(
    val originalText: String,
    val sources: AgentSources = AgentSources(),
    val collectionIds: List<String> = emptyList(),
    val documentIds: List<String> = emptyList()
)

@Serializable
data class ContinueChatWithAgentRequest(
    val originalText: String,
    val sources: AgentSources = AgentSources(),
    val collectionIds: List<String> = emptyList(),
    val documentIds: List<String> = emptyList()
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
    val createdAt: String,
    val citations: List<CitationResponse> = emptyList(),
    val artifacts: List<ArtifactResponse> = emptyList()
)

@Serializable
data class ArtifactResponse(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val viewUrl: String? = null
)

@Serializable
data class ListArtifactsResponse(
    val artifacts: List<ArtifactResponse>
)

@Serializable
data class CitationResponse(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceType: String,
    val documentId: String? = null,
    val pageStart: Int? = null,
    val pageEnd: Int? = null
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
data class MaterialResponse(
    val id: String,
    val version: Long,
    val ownerUserId: String,
    val collectionId: String?,
    val title: String,
    val originalFileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val ingestionStatus: String,
    val ingestionError: String?,
    val ocrMetadata: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MaterialCollectionResponse(
    val id: String,
    val version: Long,
    val ownerUserId: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateMaterialCollectionRequest(
    val name: String
)

@Serializable
data class ListMaterialsResponse(
    val materials: List<MaterialResponse>
)

@Serializable
data class ListMaterialCollectionsResponse(
    val collections: List<MaterialCollectionResponse>
)

@Serializable
data class DeleteMaterialResponse(
    val deleted: Boolean
)

@Serializable
data class DeleteMaterialCollectionResponse(
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
)

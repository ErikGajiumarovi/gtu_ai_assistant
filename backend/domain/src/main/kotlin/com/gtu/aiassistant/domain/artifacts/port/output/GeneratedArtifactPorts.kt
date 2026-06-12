package com.gtu.aiassistant.domain.artifacts.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.artifacts.model.ArtifactContent
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifact
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.model.StoreGeneratedArtifactCommand
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface StoreGeneratedArtifactPort {
    suspend operator fun invoke(command: StoreGeneratedArtifactCommand): Either<InfrastructureError, GeneratedArtifact>
}

interface FindGeneratedArtifactPort {
    suspend fun byId(id: GeneratedArtifactId): Either<InfrastructureError, GeneratedArtifact?>

    suspend fun byChat(chatId: ChatId): Either<InfrastructureError, List<GeneratedArtifact>>
}

interface GeneratedArtifactObjectStoragePort {
    suspend fun save(command: SaveGeneratedArtifactObjectCommand): Either<InfrastructureError, SaveGeneratedArtifactObjectResult>

    suspend fun read(key: String): Either<InfrastructureError, GeneratedArtifactObject>

    suspend fun delete(key: String): Either<InfrastructureError, Unit>
}

fun interface ReadGeneratedArtifactContentPort {
    suspend operator fun invoke(id: GeneratedArtifactId): Either<InfrastructureError, ArtifactContent?>
}

data class SaveGeneratedArtifactObjectCommand(
    val ownerUserId: UserId,
    val artifactId: GeneratedArtifactId,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

data class SaveGeneratedArtifactObjectResult(
    val key: String
)

data class GeneratedArtifactObject(
    val key: String,
    val bytes: ByteArray,
    val contentType: String
)

package com.gtu.aiassistant.app.memory

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.artifacts.model.ArtifactContent
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifact
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.model.StoreGeneratedArtifactCommand
import com.gtu.aiassistant.domain.artifacts.port.output.FindGeneratedArtifactPort
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObject
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.domain.artifacts.port.output.ReadGeneratedArtifactContentPort
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectCommand
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectResult
import com.gtu.aiassistant.domain.artifacts.port.output.StoreGeneratedArtifactPort
import com.gtu.aiassistant.domain.chat.model.ChatId
import com.gtu.aiassistant.domain.model.InfrastructureError

class InMemoryStoreGeneratedArtifactPort(
    private val state: InMemoryState,
    private val objectStorage: GeneratedArtifactObjectStoragePort
) : StoreGeneratedArtifactPort {
    override suspend fun invoke(command: StoreGeneratedArtifactCommand): Either<InfrastructureError, GeneratedArtifact> = either {
        val artifactId = GeneratedArtifactId.fromTrusted(java.util.UUID.randomUUID())
        val key = objectStorage.save(
            SaveGeneratedArtifactObjectCommand(
                ownerUserId = command.ownerUserId,
                artifactId = artifactId,
                fileName = command.fileName,
                contentType = command.contentType,
                bytes = command.bytes
            )
        ).bind().key
        val artifact = GeneratedArtifact(
            id = artifactId,
            ownerUserId = command.ownerUserId,
            chatId = command.chatId,
            messageId = command.messageId,
            fileName = command.fileName,
            contentType = command.contentType,
            sizeBytes = command.bytes.size.toLong(),
            objectKey = key,
            createdAt = command.createdAt
        )
        state.generatedArtifacts[artifact.id.value.toString()] = artifact
        artifact
    }
}

class InMemoryFindGeneratedArtifactPort(
    private val state: InMemoryState
) : FindGeneratedArtifactPort {
    override suspend fun byId(id: GeneratedArtifactId): Either<InfrastructureError, GeneratedArtifact?> =
        Either.Right(state.generatedArtifacts[id.value.toString()])

    override suspend fun byChat(chatId: ChatId): Either<InfrastructureError, List<GeneratedArtifact>> =
        Either.Right(state.generatedArtifacts.values.filter { it.chatId == chatId })
}

class InMemoryGeneratedArtifactObjectStoragePort : GeneratedArtifactObjectStoragePort {
    private val objects = java.util.concurrent.ConcurrentHashMap<String, GeneratedArtifactObject>()

    override suspend fun save(command: SaveGeneratedArtifactObjectCommand): Either<InfrastructureError, SaveGeneratedArtifactObjectResult> {
        val key = "generated-artifacts/${command.ownerUserId.value}/${command.artifactId.value}/${command.fileName}"
        objects[key] = GeneratedArtifactObject(key, command.bytes, command.contentType)
        return Either.Right(SaveGeneratedArtifactObjectResult(key))
    }

    override suspend fun read(key: String): Either<InfrastructureError, GeneratedArtifactObject> =
        objects[key]?.let { Either.Right(it) }
            ?: Either.Left(InfrastructureError(IllegalArgumentException("Artifact object not found: $key")))

    override suspend fun delete(key: String): Either<InfrastructureError, Unit> {
        objects.remove(key)
        return Either.Right(Unit)
    }
}

class InMemoryReadGeneratedArtifactContentPort(
    private val findGeneratedArtifactPort: FindGeneratedArtifactPort,
    private val objectStorage: GeneratedArtifactObjectStoragePort
) : ReadGeneratedArtifactContentPort {
    override suspend fun invoke(id: GeneratedArtifactId): Either<InfrastructureError, ArtifactContent?> = either {
        val artifact = findGeneratedArtifactPort.byId(id).bind() ?: return@either null
        val content = objectStorage.read(artifact.objectKey).bind()
        ArtifactContent(artifact, content.bytes)
    }
}

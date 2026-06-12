package com.gtu.aiassistant.infrastructure.persistence.artifacts

import arrow.core.raise.either
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifact
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.model.MAX_GENERATED_ARTIFACT_BYTES
import com.gtu.aiassistant.domain.artifacts.model.StoreGeneratedArtifactCommand
import com.gtu.aiassistant.domain.artifacts.model.normalizeGeneratedArtifactFileName
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectCommand
import com.gtu.aiassistant.domain.artifacts.port.output.StoreGeneratedArtifactPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.jdbc.insert
import java.util.UUID

class StoreGeneratedArtifactPortImpl(
    private val executor: JdbcPersistenceExecutor,
    private val objectStorage: GeneratedArtifactObjectStoragePort
) : StoreGeneratedArtifactPort {
    override suspend fun invoke(command: StoreGeneratedArtifactCommand) = either {
        val fileName = normalizeGeneratedArtifactFileName(command.fileName)
            .mapLeft { InfrastructureError(IllegalArgumentException("Invalid artifact file name")) }
            .bind()
        if (command.bytes.size > MAX_GENERATED_ARTIFACT_BYTES) {
            raise(InfrastructureError(IllegalArgumentException("Artifact exceeds $MAX_GENERATED_ARTIFACT_BYTES bytes")))
        }

        val artifactId = GeneratedArtifactId.fromTrusted(UUID.randomUUID())
        val objectKey = objectStorage.save(
            SaveGeneratedArtifactObjectCommand(
                ownerUserId = command.ownerUserId,
                artifactId = artifactId,
                fileName = fileName,
                contentType = command.contentType,
                bytes = command.bytes
            )
        ).bind().key

        val artifact = GeneratedArtifact(
            id = artifactId,
            ownerUserId = command.ownerUserId,
            chatId = command.chatId,
            messageId = command.messageId,
            fileName = fileName,
            contentType = command.contentType,
            sizeBytes = command.bytes.size.toLong(),
            objectKey = objectKey,
            createdAt = command.createdAt
        )

        executor.execute {
            GeneratedArtifactRecords.table.insert {
                it[GeneratedArtifactRecords.id] = artifact.id.value.toString()
                it[GeneratedArtifactRecords.ownerUserId] = artifact.ownerUserId.value.toString()
                it[GeneratedArtifactRecords.chatId] = artifact.chatId?.value?.toString()
                it[GeneratedArtifactRecords.messageId] = artifact.messageId?.toString()
                it[GeneratedArtifactRecords.fileName] = artifact.fileName
                it[GeneratedArtifactRecords.contentType] = artifact.contentType
                it[GeneratedArtifactRecords.sizeBytes] = artifact.sizeBytes
                it[GeneratedArtifactRecords.objectKey] = artifact.objectKey
                it[GeneratedArtifactRecords.createdAt] = artifact.createdAt
            }
            artifact
        }.bind()
    }
}

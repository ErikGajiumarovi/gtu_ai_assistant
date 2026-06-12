package com.gtu.aiassistant.infrastructure.storage

import arrow.core.Either
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObject
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectCommand
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectResult
import com.gtu.aiassistant.domain.model.InfrastructureError
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name

class LocalGeneratedArtifactObjectStoragePort(
    rootDirectory: Path
) : GeneratedArtifactObjectStoragePort {
    private val rootDirectory = rootDirectory.toAbsolutePath().normalize()

    override suspend fun save(command: SaveGeneratedArtifactObjectCommand): Either<InfrastructureError, SaveGeneratedArtifactObjectResult> =
        Either.catch {
            val key = buildObjectKey(command)
            val target = resolveKey(key)
            target.parent.createDirectories()
            Files.write(target, command.bytes)
            SaveGeneratedArtifactObjectResult(key)
        }.mapLeft(::InfrastructureError)

    override suspend fun read(key: String): Either<InfrastructureError, GeneratedArtifactObject> =
        Either.catch {
            val path = resolveKey(key)
            GeneratedArtifactObject(
                key = key,
                bytes = Files.readAllBytes(path),
                contentType = Files.probeContentType(path) ?: "application/octet-stream"
            )
        }.mapLeft(::InfrastructureError)

    override suspend fun delete(key: String): Either<InfrastructureError, Unit> =
        Either.catch {
            val path = resolveKey(key)
            if (path.exists()) Files.delete(path)
            Unit
        }.mapLeft(::InfrastructureError)

    private fun buildObjectKey(command: SaveGeneratedArtifactObjectCommand): String {
        val extension = command.fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        val storedFileName = if (extension.isBlank()) {
            command.artifactId.value.toString()
        } else {
            "${command.artifactId.value}.$extension"
        }
        return Path.of("generated-artifacts", command.ownerUserId.value.toString(), storedFileName).toString()
    }

    private fun resolveKey(key: String): Path {
        val resolved = rootDirectory.resolve(key).toAbsolutePath().normalize()
        require(resolved.startsWith(rootDirectory)) { "Storage key escapes root directory" }
        return resolved
    }
}

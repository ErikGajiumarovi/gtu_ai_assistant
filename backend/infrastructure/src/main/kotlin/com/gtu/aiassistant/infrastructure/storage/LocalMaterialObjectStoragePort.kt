package com.gtu.aiassistant.infrastructure.storage

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.port.output.MaterialObject
import com.gtu.aiassistant.domain.materials.port.output.MaterialObjectStoragePort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialObjectCommand
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialObjectResult
import com.gtu.aiassistant.domain.model.InfrastructureError
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name

class LocalMaterialObjectStoragePort(
    rootDirectory: Path
) : MaterialObjectStoragePort {
    private val rootDirectory = rootDirectory.toAbsolutePath().normalize()

    override suspend fun save(command: SaveMaterialObjectCommand): Either<InfrastructureError, SaveMaterialObjectResult> =
        Either.catch {
            val extension = command.originalFileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            val fileName = if (extension.isBlank()) UUID.randomUUID().toString() else "${UUID.randomUUID()}.$extension"
            val key = Path.of("materials", command.ownerUserId.value.toString(), fileName).toString()
            val target = resolveKey(key)

            target.parent.createDirectories()
            Files.write(target, command.bytes)

            SaveMaterialObjectResult(key = key)
        }.mapLeft(::InfrastructureError)

    override suspend fun read(key: String): Either<InfrastructureError, MaterialObject> =
        Either.catch {
            val path = resolveKey(key)
            MaterialObject(
                key = key,
                bytes = Files.readAllBytes(path),
                contentType = contentTypeFor(path)
            )
        }.mapLeft(::InfrastructureError)

    override suspend fun delete(key: String): Either<InfrastructureError, Unit> =
        Either.catch {
            val path = resolveKey(key)
            if (path.exists()) {
                Files.delete(path)
            }
            Unit
        }.mapLeft(::InfrastructureError)

    private fun resolveKey(key: String): Path {
        val resolved = rootDirectory.resolve(key).toAbsolutePath().normalize()
        require(resolved.startsWith(rootDirectory)) { "Storage key escapes root directory" }
        return resolved
    }

    private fun contentTypeFor(path: Path): String =
        when (path.name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> Files.probeContentType(path) ?: "application/octet-stream"
        }
}

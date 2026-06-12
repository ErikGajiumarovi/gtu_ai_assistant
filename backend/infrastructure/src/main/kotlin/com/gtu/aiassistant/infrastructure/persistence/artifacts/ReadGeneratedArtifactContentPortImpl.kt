package com.gtu.aiassistant.infrastructure.persistence.artifacts

import arrow.core.raise.either
import com.gtu.aiassistant.domain.artifacts.model.ArtifactContent
import com.gtu.aiassistant.domain.artifacts.model.GeneratedArtifactId
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.domain.artifacts.port.output.FindGeneratedArtifactPort
import com.gtu.aiassistant.domain.artifacts.port.output.ReadGeneratedArtifactContentPort

class ReadGeneratedArtifactContentPortImpl(
    private val findGeneratedArtifactPort: FindGeneratedArtifactPort,
    private val objectStorage: GeneratedArtifactObjectStoragePort
) : ReadGeneratedArtifactContentPort {
    override suspend fun invoke(id: GeneratedArtifactId) = either {
        val artifact = findGeneratedArtifactPort.byId(id).bind() ?: return@either null
        val content = objectStorage.read(artifact.objectKey).bind()
        ArtifactContent(
            artifact = artifact,
            bytes = content.bytes
        )
    }
}

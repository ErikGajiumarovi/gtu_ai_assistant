package com.gtu.aiassistant.infrastructure.storage

import arrow.core.Either
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObject
import com.gtu.aiassistant.domain.artifacts.port.output.GeneratedArtifactObjectStoragePort
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectCommand
import com.gtu.aiassistant.domain.artifacts.port.output.SaveGeneratedArtifactObjectResult
import com.gtu.aiassistant.domain.model.InfrastructureError
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Locale

class MinioGeneratedArtifactObjectStoragePort(
    private val client: MinioClient,
    private val bucket: String
) : GeneratedArtifactObjectStoragePort {
    override suspend fun save(command: SaveGeneratedArtifactObjectCommand): Either<InfrastructureError, SaveGeneratedArtifactObjectResult> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val key = buildObjectKey(command)
                client.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(key)
                        .contentType(command.contentType)
                        .stream(ByteArrayInputStream(command.bytes), command.bytes.size.toLong(), -1)
                        .build()
                )
                SaveGeneratedArtifactObjectResult(key)
            }.mapLeft(::InfrastructureError)
        }

    override suspend fun read(key: String): Either<InfrastructureError, GeneratedArtifactObject> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val stat = client.statObject(
                    StatObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(key)
                        .build()
                )
                val bytes = client.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(key)
                        .build()
                ).use { it.readBytes() }
                GeneratedArtifactObject(
                    key = key,
                    bytes = bytes,
                    contentType = stat.contentType()?.takeIf(String::isNotBlank) ?: "application/octet-stream"
                )
            }.mapLeft(::InfrastructureError)
        }

    override suspend fun delete(key: String): Either<InfrastructureError, Unit> =
        withContext(Dispatchers.IO) {
            Either.catch {
                client.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .`object`(key)
                        .build()
                )
                Unit
            }.mapLeft(::InfrastructureError)
        }

    private fun buildObjectKey(command: SaveGeneratedArtifactObjectCommand): String {
        val extension = command.fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        val storedFileName = if (extension.isBlank()) {
            command.artifactId.value.toString()
        } else {
            "${command.artifactId.value}.$extension"
        }
        return "generated-artifacts/${command.ownerUserId.value}/$storedFileName"
    }
}

object MinioGeneratedArtifactObjectStorageFactory {
    fun create(config: MinioMaterialObjectStorageConfig): MinioGeneratedArtifactObjectStoragePort {
        val clientBuilder = MinioClient.builder()
            .endpoint(config.endpoint)
            .credentials(config.accessKey, config.secretKey)
        if (!config.region.isNullOrBlank()) {
            clientBuilder.region(config.region)
        }
        val client = clientBuilder.build()
        ensureBucketExists(client, config.bucket, config.region)
        return MinioGeneratedArtifactObjectStoragePort(client, config.bucket)
    }

    private fun ensureBucketExists(client: MinioClient, bucket: String, region: String?) {
        val exists = client.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucket)
                .build()
        )
        if (!exists) {
            val makeBucketBuilder = MakeBucketArgs.builder().bucket(bucket)
            if (!region.isNullOrBlank()) {
                makeBucketBuilder.region(region)
            }
            client.makeBucket(makeBucketBuilder.build())
        }
    }
}

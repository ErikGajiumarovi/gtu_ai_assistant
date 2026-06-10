package com.gtu.aiassistant.infrastructure.storage

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.port.output.MaterialObject
import com.gtu.aiassistant.domain.materials.port.output.MaterialObjectStoragePort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialObjectCommand
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialObjectResult
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
import java.util.UUID

class MinioMaterialObjectStoragePortImpl(
    private val client: MinioClient,
    private val bucket: String
) : MaterialObjectStoragePort {
    override suspend fun save(command: SaveMaterialObjectCommand): Either<InfrastructureError, SaveMaterialObjectResult> =
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

                SaveMaterialObjectResult(key = key)
            }.mapLeft(::InfrastructureError)
        }

    override suspend fun read(key: String): Either<InfrastructureError, MaterialObject> =
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

                MaterialObject(
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

    private fun buildObjectKey(command: SaveMaterialObjectCommand): String {
        val extension = command.originalFileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        val fileName = if (extension.isBlank()) UUID.randomUUID().toString() else "${UUID.randomUUID()}.$extension"
        return "materials/${command.ownerUserId.value}/$fileName"
    }
}

data class MinioMaterialObjectStorageConfig(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String?
)

object MinioMaterialObjectStorageFactory {
    fun create(config: MinioMaterialObjectStorageConfig): MinioMaterialObjectStoragePortImpl {
        val clientBuilder = MinioClient.builder()
            .endpoint(config.endpoint)
            .credentials(config.accessKey, config.secretKey)
        if (!config.region.isNullOrBlank()) {
            clientBuilder.region(config.region)
        }
        val client = clientBuilder.build()

        ensureBucketExists(client, config.bucket, config.region)

        return MinioMaterialObjectStoragePortImpl(
            client = client,
            bucket = config.bucket
        )
    }

    private fun ensureBucketExists(
        client: MinioClient,
        bucket: String,
        region: String?
    ) {
        try {
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
        } catch (cause: Exception) {
            throw IllegalStateException("Failed to ensure MinIO bucket '$bucket' exists", cause)
        }
    }
}

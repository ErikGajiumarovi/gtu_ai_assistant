package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

interface MaterialObjectStoragePort {
    suspend fun save(command: SaveMaterialObjectCommand): Either<InfrastructureError, SaveMaterialObjectResult>

    suspend fun read(key: String): Either<InfrastructureError, MaterialObject>


    suspend fun delete(key: String): Either<InfrastructureError, Unit>
}

data class SaveMaterialObjectCommand(
    val ownerUserId: UserId,
    val originalFileName: String,
    val contentType: String,
    val bytes: ByteArray
)

data class SaveMaterialObjectResult(
    val key: String
)

data class MaterialObject(
    val key: String,
    val bytes: ByteArray,
    val contentType: String
)

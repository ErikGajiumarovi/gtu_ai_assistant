package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.ReplaceMaterialDocumentChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialChunksPort
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert

class SaveMaterialChunksPortImpl(
    private val executor: JdbcPersistenceExecutor
) : SaveMaterialChunksPort {
    override suspend fun invoke(chunks: List<MaterialChunk>) =
        executor.execute {
            chunks.forEach { chunk ->
                MaterialChunkRecords.table.insertChunk(chunk)
            }
            Unit
        }
}

class ReplaceMaterialDocumentChunksPortImpl(
    private val executor: JdbcPersistenceExecutor
) : ReplaceMaterialDocumentChunksPort {
    override suspend fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId, chunks: List<MaterialChunk>) =
        executor.execute {
            MaterialChunkRecords.table.deleteWhere {
                (MaterialChunkRecords.ownerUserId eq ownerUserId.value.toString()) and
                    (MaterialChunkRecords.documentId eq documentId.value.toString())
            }
            chunks.forEach { chunk ->
                MaterialChunkRecords.table.insertChunk(chunk)
            }
            Unit
        }
}

class DeleteMaterialChunksPortImpl(
    private val executor: JdbcPersistenceExecutor
) : DeleteMaterialChunksPort {
    override suspend fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId) =
        executor.execute {
            MaterialChunkRecords.table.deleteWhere {
                (MaterialChunkRecords.ownerUserId eq ownerUserId.value.toString()) and
                    (MaterialChunkRecords.documentId eq documentId.value.toString())
            }
            Unit
        }
}

private fun org.jetbrains.exposed.v1.core.Table.insertChunk(chunk: MaterialChunk) {
    insert {
        it[MaterialChunkRecords.id] = chunk.id.toString()
        it[MaterialChunkRecords.ownerUserId] = chunk.ownerUserId.value.toString()
        it[MaterialChunkRecords.documentId] = chunk.documentId.value.toString()
        it[MaterialChunkRecords.collectionId] = chunk.collectionId?.value?.toString()
        it[MaterialChunkRecords.chunkIndex] = chunk.chunkIndex
        it[MaterialChunkRecords.text] = chunk.text
        it[MaterialChunkRecords.embedding] = chunk.embedding
        it[MaterialChunkRecords.headingPath] = chunk.headingPath
        it[MaterialChunkRecords.pageStart] = chunk.pageStart
        it[MaterialChunkRecords.pageEnd] = chunk.pageEnd
    }
}

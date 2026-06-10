package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialDocumentPort
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

class DeleteMaterialDocumentPortImpl(
    private val executor: JdbcPersistenceExecutor
) : DeleteMaterialDocumentPort {
    override suspend fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId) =
        executor.execute {
            MaterialDocumentRecords.table.deleteWhere {
                (MaterialDocumentRecords.ownerUserId eq ownerUserId.value.toString()) and
                    (MaterialDocumentRecords.id eq documentId.value.toString())
            }

            Unit
        }
}

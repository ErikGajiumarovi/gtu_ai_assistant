package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialCollectionPort
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

class DeleteMaterialCollectionPortImpl(
    private val executor: JdbcPersistenceExecutor
) : DeleteMaterialCollectionPort {
    override suspend fun invoke(ownerUserId: UserId, collectionId: MaterialCollectionId) =
        executor.execute {
            MaterialCollectionRecords.table.deleteWhere {
                (MaterialCollectionRecords.ownerUserId eq ownerUserId.value.toString()) and
                    (MaterialCollectionRecords.id eq collectionId.value.toString())
            }

            Unit
        }
}

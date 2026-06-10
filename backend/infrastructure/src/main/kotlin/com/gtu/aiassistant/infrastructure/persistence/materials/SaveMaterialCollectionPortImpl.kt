package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialCollection
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialCollectionPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SaveMaterialCollectionPortImpl(
    private val executor: JdbcPersistenceExecutor
) : SaveMaterialCollectionPort {
    override suspend fun invoke(collection: MaterialCollection) =
        executor.execute {
            val exists = MaterialCollectionRecords.table
                .selectAll()
                .where { MaterialCollectionRecords.id eq collection.id.value.toString() }
                .singleOrNull() != null

            if (!exists) {
                MaterialCollectionRecords.table.insert {
                    it[MaterialCollectionRecords.id] = collection.id.value.toString()
                    it[MaterialCollectionRecords.version] = collection.version
                    it[MaterialCollectionRecords.ownerUserId] = collection.ownerUserId.value.toString()
                    it[MaterialCollectionRecords.name] = collection.name
                    it[MaterialCollectionRecords.createdAt] = collection.createdAt
                    it[MaterialCollectionRecords.updatedAt] = collection.updatedAt
                }
            } else {
                MaterialCollectionRecords.table.update({
                    MaterialCollectionRecords.id eq collection.id.value.toString()
                }) {
                    it[MaterialCollectionRecords.version] = collection.version
                    it[MaterialCollectionRecords.ownerUserId] = collection.ownerUserId.value.toString()
                    it[MaterialCollectionRecords.name] = collection.name
                    it[MaterialCollectionRecords.createdAt] = collection.createdAt
                    it[MaterialCollectionRecords.updatedAt] = collection.updatedAt
                }
            }

            collection
        }
}

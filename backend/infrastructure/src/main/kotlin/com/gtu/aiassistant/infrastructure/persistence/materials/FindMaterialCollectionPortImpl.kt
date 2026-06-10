package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class FindMaterialCollectionPortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindMaterialCollectionPort {
    override suspend fun invoke(strategy: FindMaterialCollectionPort.Strategy) =
        executor.execute {
            when (strategy) {
                is FindMaterialCollectionPort.Strategy.ById -> FindMaterialCollectionPort.Result.Single(
                    collection = MaterialCollectionRecords.table
                        .selectAll()
                        .where {
                            (MaterialCollectionRecords.ownerUserId eq strategy.ownerUserId.value.toString()) and
                                (MaterialCollectionRecords.id eq strategy.collectionId.value.toString())
                        }
                        .singleOrNull()
                        ?.toDomainMaterialCollection()
                )

                is FindMaterialCollectionPort.Strategy.ByOwner -> FindMaterialCollectionPort.Result.Multiple(
                    collections = MaterialCollectionRecords.table
                        .selectAll()
                        .where { MaterialCollectionRecords.ownerUserId eq strategy.ownerUserId.value.toString() }
                        .orderBy(MaterialCollectionRecords.createdAt to SortOrder.DESC)
                        .map { it.toDomainMaterialCollection() }
                )
            }
        }
}

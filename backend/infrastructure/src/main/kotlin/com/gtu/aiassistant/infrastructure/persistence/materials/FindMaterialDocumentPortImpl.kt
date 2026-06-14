package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

class FindMaterialDocumentPortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindMaterialDocumentPort {
    override suspend fun invoke(strategy: FindMaterialDocumentPort.Strategy) =
        executor.execute {
            when (strategy) {
                is FindMaterialDocumentPort.Strategy.ById -> FindMaterialDocumentPort.Result.Single(
                    document = MaterialDocumentRecords.table
                        .selectAll()
                        .where {
                            (MaterialDocumentRecords.ownerUserId eq strategy.ownerUserId.value.toString()) and
                                (MaterialDocumentRecords.id eq strategy.documentId.value.toString())
                        }
                        .singleOrNull()
                        ?.toDomainMaterialDocument()
                )

                is FindMaterialDocumentPort.Strategy.ByIds -> {
                    val documentIds = strategy.documentIds.map { it.value.toString() }.distinct()
                    FindMaterialDocumentPort.Result.Multiple(
                        documents = if (documentIds.isEmpty()) {
                            emptyList()
                        } else {
                            MaterialDocumentRecords.table
                                .selectAll()
                                .where {
                                    (MaterialDocumentRecords.ownerUserId eq strategy.ownerUserId.value.toString()) and
                                        (MaterialDocumentRecords.id inList documentIds)
                                }
                                .orderBy(MaterialDocumentRecords.createdAt to SortOrder.DESC)
                                .map { it.toDomainMaterialDocument() }
                        }
                    )
                }

                is FindMaterialDocumentPort.Strategy.ByOwner -> FindMaterialDocumentPort.Result.Multiple(
                    documents = MaterialDocumentRecords.table
                        .selectAll()
                        .where {
                            val ownerCondition = MaterialDocumentRecords.ownerUserId eq strategy.ownerUserId.value.toString()
                            strategy.collectionId
                                ?.let { ownerCondition and (MaterialDocumentRecords.collectionId eq it.value.toString()) }
                                ?: ownerCondition
                        }
                        .orderBy(MaterialDocumentRecords.createdAt to SortOrder.DESC)
                        .map { it.toDomainMaterialDocument() }
                )

                is FindMaterialDocumentPort.Strategy.ByStatus -> FindMaterialDocumentPort.Result.Multiple(
                    documents = MaterialDocumentRecords.table
                        .selectAll()
                        .where { MaterialDocumentRecords.ingestionStatus eq strategy.status.name }
                        .orderBy(MaterialDocumentRecords.createdAt to SortOrder.ASC)
                        .limit(strategy.limit)
                        .map { it.toDomainMaterialDocument() }
                )
            }
        }
}

package com.gtu.aiassistant.infrastructure.persistence.materials

import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlinePort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlineQuery
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsQuery
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentOutline
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentOutlineEntry
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentSection
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentSectionChunk
import com.gtu.aiassistant.domain.materials.port.output.ReplaceMaterialDocumentChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialChunksPort
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.infrastructure.persistence.support.JdbcPersistenceExecutor
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

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

class FindMaterialDocumentOutlinePortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindMaterialDocumentOutlinePort {
    override suspend fun invoke(query: FindMaterialDocumentOutlineQuery) =
        executor.execute {
            val documentIds = query.documentIds.map { it.value.toString() }.distinct()
            if (documentIds.isEmpty()) {
                return@execute emptyList()
            }

            MaterialChunkRecords.table
                .selectAll()
                .where {
                    (MaterialChunkRecords.ownerUserId eq query.ownerUserId.value.toString()) and
                        (MaterialChunkRecords.documentId inList documentIds)
                }
                .orderBy(MaterialChunkRecords.chunkIndex)
                .map { row ->
                    OutlineCandidate(
                        documentId = MaterialDocumentId.fromTrusted(java.util.UUID.fromString(row[MaterialChunkRecords.documentId])),
                        headingPath = row[MaterialChunkRecords.headingPath],
                        text = row[MaterialChunkRecords.text]
                    )
                }
                .groupBy { it.documentId }
                .map { (documentId, candidates) ->
                    MaterialDocumentOutline(
                        documentId = documentId,
                        entries = candidates
                            .toOutlineEntries()
                            .take(query.maxEntriesPerDocument.coerceIn(1, 120))
                    )
                }
        }
}

class FindMaterialDocumentSectionsPortImpl(
    private val executor: JdbcPersistenceExecutor
) : FindMaterialDocumentSectionsPort {
    override suspend fun invoke(query: FindMaterialDocumentSectionsQuery) =
        executor.execute {
            val documentIds = query.documentIds.map { it.value.toString() }.distinct()
            val sectionTitles = query.sectionTitles.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (documentIds.isEmpty() || sectionTitles.isEmpty()) {
                return@execute emptyList()
            }

            sectionTitles.flatMap { sectionTitle ->
                val rows = MaterialChunkRecords.table
                    .selectAll()
                    .where {
                        (MaterialChunkRecords.ownerUserId eq query.ownerUserId.value.toString()) and
                            (MaterialChunkRecords.documentId inList documentIds)
                    }
                    .orderBy(MaterialChunkRecords.chunkIndex)
                    .map { row ->
                        SectionCandidate(
                            documentId = MaterialDocumentId.fromTrusted(java.util.UUID.fromString(row[MaterialChunkRecords.documentId])),
                            headingPath = row[MaterialChunkRecords.headingPath],
                            text = row[MaterialChunkRecords.text],
                            pageStart = row[MaterialChunkRecords.pageStart],
                            pageEnd = row[MaterialChunkRecords.pageEnd]
                        )
                    }
                    .filter { candidate -> candidate.headingPath?.containsSectionTitle(sectionTitle) == true }
                    .groupBy { it.documentId }

                rows.map { (documentId, candidates) ->
                    MaterialDocumentSection(
                        documentId = documentId,
                        title = sectionTitle,
                        chunks = candidates
                            .take(query.maxChunksPerSection.coerceIn(1, 5))
                            .map { candidate ->
                                MaterialDocumentSectionChunk(
                                    text = candidate.text,
                                    headingPath = candidate.headingPath,
                                    pageStart = candidate.pageStart,
                                    pageEnd = candidate.pageEnd
                                )
                            }
                    )
                }
            }
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

private data class OutlineCandidate(
    val documentId: MaterialDocumentId,
    val headingPath: String?,
    val text: String
)

private data class SectionCandidate(
    val documentId: MaterialDocumentId,
    val headingPath: String?,
    val text: String,
    val pageStart: Int?,
    val pageEnd: Int?
)

private fun List<OutlineCandidate>.toOutlineEntries(): List<MaterialDocumentOutlineEntry> {
    val seen = linkedSetOf<String>()
    val entries = mutableListOf<MaterialDocumentOutlineEntry>()

    for (candidate in this) {
        val tocEntry = candidate.text.toTocOutlineEntry()
        if (tocEntry != null && seen.add(tocEntry.title.normalizedOutlineKey())) {
            entries += tocEntry
        }

        candidate.headingPath
            ?.split(">")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEachIndexed { index, heading ->
                val entry = MaterialDocumentOutlineEntry(
                    title = heading.toDisplayHeading(),
                    level = index + 1
                )
                if (seen.add(entry.title.normalizedOutlineKey())) {
                    entries += entry
                }
            }
    }

    return entries
}

private fun String.toTocOutlineEntry(): MaterialDocumentOutlineEntry? {
    val normalized = replace(Regex("\\s+"), " ").trim()
    if (normalized.length !in 3..160) return null
    if (normalized.contains("http", ignoreCase = true)) return null
    if (normalized.contains("{") || normalized.contains(";")) return null

    val withoutPage = normalized.replace(Regex("\\s+\\d{1,4}$"), "").trim()
    val level = when {
        withoutPage.equals("введение", ignoreCase = true) -> 1
        withoutPage.equals("заключение", ignoreCase = true) -> 1
        withoutPage.matches(Regex("""(?i)^глава\s+\d+.*""")) -> 1
        withoutPage.matches(Regex("""^\d+\.\s+\S.*""")) -> 1
        withoutPage.matches(Regex("""^\d+(\.\d+)+\.\s+\S.*""")) -> withoutPage
            .substringBefore(' ')
            .count { it == '.' }
            .coerceAtLeast(2)
        else -> return null
    }

    return MaterialDocumentOutlineEntry(
        title = withoutPage.toDisplayHeading(),
        level = level
    )
}

private fun String.toDisplayHeading(): String =
    replace(Regex("\\s+"), " ").trim()

private fun String.normalizedOutlineKey(): String =
    lowercase().replace(Regex("\\s+"), " ").trim()

private fun String.containsSectionTitle(sectionTitle: String): Boolean {
    val normalizedHeading = normalizedOutlineKey()
    val normalizedTitle = sectionTitle.normalizedOutlineKey()
    val titleWithoutNumber = normalizedTitle.replace(Regex("""^\d+(\.\d+)*\.?\s+"""), "")

    return normalizedHeading.contains(normalizedTitle) ||
        (titleWithoutNumber.length >= 6 && normalizedHeading.contains(titleWithoutNumber))
}

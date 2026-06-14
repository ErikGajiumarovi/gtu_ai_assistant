package com.gtu.aiassistant.app.memory

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.materials.model.MaterialSearchHit
import com.gtu.aiassistant.domain.materials.model.MaterialSearchQuery
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.DeleteMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlinePort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlineQuery
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsQuery
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentOutline
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentOutlineEntry
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentSection
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentSectionChunk
import com.gtu.aiassistant.domain.materials.port.output.ReplaceMaterialDocumentChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialChunksPort
import com.gtu.aiassistant.domain.materials.port.output.SaveMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.SearchUserMaterialsPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

class InMemorySaveMaterialDocumentPort(
    private val state: InMemoryState
) : SaveMaterialDocumentPort {
    override suspend fun invoke(document: MaterialDocument): Either<InfrastructureError, MaterialDocument> {
        state.materialDocuments[document.id.value.toString()] = document
        return Either.Right(document)
    }
}

class InMemoryFindMaterialDocumentPort(
    private val state: InMemoryState
) : FindMaterialDocumentPort {
    override suspend fun invoke(strategy: FindMaterialDocumentPort.Strategy): Either<InfrastructureError, FindMaterialDocumentPort.Result> =
        Either.Right(
            when (strategy) {
                is FindMaterialDocumentPort.Strategy.ById -> FindMaterialDocumentPort.Result.Single(
                    document = state.materialDocuments[strategy.documentId.value.toString()]
                        ?.takeIf { it.ownerUserId == strategy.ownerUserId }
                )

                is FindMaterialDocumentPort.Strategy.ByIds -> {
                    val ids = strategy.documentIds.map { it.value.toString() }.toSet()
                    FindMaterialDocumentPort.Result.Multiple(
                        documents = state.materialDocuments.values
                            .filter { it.ownerUserId == strategy.ownerUserId }
                            .filter { it.id.value.toString() in ids }
                            .sortedByDescending { it.createdAt }
                    )
                }

                is FindMaterialDocumentPort.Strategy.ByOwner -> FindMaterialDocumentPort.Result.Multiple(
                    documents = state.materialDocuments.values
                        .filter { it.ownerUserId == strategy.ownerUserId }
                        .filter { strategy.collectionId == null || it.collectionId == strategy.collectionId }
                        .sortedByDescending { it.createdAt }
                )

                is FindMaterialDocumentPort.Strategy.ByStatus -> FindMaterialDocumentPort.Result.Multiple(
                    documents = state.materialDocuments.values
                        .filter { it.ingestionStatus == strategy.status }
                        .sortedBy { it.createdAt }
                        .take(strategy.limit)
                )
            }
        )
}

class InMemoryDeleteMaterialDocumentPort(
    private val state: InMemoryState
) : DeleteMaterialDocumentPort {
    override suspend fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId): Either<InfrastructureError, Unit> {
        state.materialDocuments[documentId.value.toString()]
            ?.takeIf { it.ownerUserId == ownerUserId }
            ?.let { state.materialDocuments.remove(documentId.value.toString()) }
        return Either.Right(Unit)
    }
}

class InMemorySaveMaterialCollectionPort(
    private val state: InMemoryState
) : SaveMaterialCollectionPort {
    override suspend fun invoke(collection: com.gtu.aiassistant.domain.materials.model.MaterialCollection): Either<InfrastructureError, com.gtu.aiassistant.domain.materials.model.MaterialCollection> {
        state.materialCollections[collection.id.value.toString()] = collection
        return Either.Right(collection)
    }
}

class InMemoryFindMaterialCollectionPort(
    private val state: InMemoryState
) : FindMaterialCollectionPort {
    override suspend fun invoke(strategy: FindMaterialCollectionPort.Strategy): Either<InfrastructureError, FindMaterialCollectionPort.Result> =
        Either.Right(
            when (strategy) {
                is FindMaterialCollectionPort.Strategy.ById -> FindMaterialCollectionPort.Result.Single(
                    collection = state.materialCollections[strategy.collectionId.value.toString()]
                        ?.takeIf { it.ownerUserId == strategy.ownerUserId }
                )
                is FindMaterialCollectionPort.Strategy.ByOwner -> FindMaterialCollectionPort.Result.Multiple(
                    collections = state.materialCollections.values
                        .filter { it.ownerUserId == strategy.ownerUserId }
                        .sortedByDescending { it.createdAt }
                )
            }
        )
}

class InMemoryDeleteMaterialCollectionPort(
    private val state: InMemoryState
) : DeleteMaterialCollectionPort {
    override suspend fun invoke(ownerUserId: UserId, collectionId: MaterialCollectionId): Either<InfrastructureError, Unit> {
        state.materialCollections[collectionId.value.toString()]
            ?.takeIf { it.ownerUserId == ownerUserId }
            ?.let {
                state.materialCollections.remove(collectionId.value.toString())
                state.materialDocuments.replaceAll { _, document ->
                    if (document.ownerUserId == ownerUserId && document.collectionId == collectionId) {
                        MaterialDocument.fromTrusted(
                            id = document.id,
                            version = document.version + 1,
                            ownerUserId = document.ownerUserId,
                            collectionId = null,
                            title = document.title,
                            originalFileName = document.originalFileName,
                            contentType = document.contentType,
                            sizeBytes = document.sizeBytes,
                            storageObjectKey = document.storageObjectKey,
                            ingestionStatus = document.ingestionStatus,
                            ingestionError = document.ingestionError,
                            ocrMetadata = document.ocrMetadata,
                            createdAt = document.createdAt,
                            updatedAt = java.time.Instant.now()
                        )
                    } else {
                        document
                    }
                }
                state.materialChunks.replaceAll { _, chunk ->
                    if (chunk.ownerUserId == ownerUserId && chunk.collectionId == collectionId) {
                        chunk.copy(collectionId = null)
                    } else {
                        chunk
                    }
                }
            }
        return Either.Right(Unit)
    }
}

class InMemorySaveMaterialChunksPort(
    private val state: InMemoryState
) : SaveMaterialChunksPort {
    override suspend fun invoke(chunks: List<MaterialChunk>): Either<InfrastructureError, Unit> {
        chunks.forEach { chunk -> state.materialChunks[chunk.id.toString()] = chunk }
        return Either.Right(Unit)
    }
}

class InMemoryReplaceMaterialDocumentChunksPort(
    private val state: InMemoryState
) : ReplaceMaterialDocumentChunksPort {
    override suspend fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId, chunks: List<MaterialChunk>): Either<InfrastructureError, Unit> {
        state.materialChunks.entries.removeIf { (_, chunk) ->
            chunk.ownerUserId == ownerUserId && chunk.documentId == documentId
        }
        chunks.forEach { chunk -> state.materialChunks[chunk.id.toString()] = chunk }
        return Either.Right(Unit)
    }
}

class InMemoryDeleteMaterialChunksPort(
    private val state: InMemoryState
) : DeleteMaterialChunksPort {
    override suspend fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId): Either<InfrastructureError, Unit> {
        state.materialChunks.entries.removeIf { (_, chunk) ->
            chunk.ownerUserId == ownerUserId && chunk.documentId == documentId
        }
        return Either.Right(Unit)
    }
}

class InMemoryFindMaterialDocumentOutlinePort(
    private val state: InMemoryState
) : FindMaterialDocumentOutlinePort {
    override suspend fun invoke(query: FindMaterialDocumentOutlineQuery): Either<InfrastructureError, List<MaterialDocumentOutline>> {
        val documentIds = query.documentIds.toSet()
        val outlines = state.materialChunks.values
            .asSequence()
            .filter { chunk -> chunk.ownerUserId == query.ownerUserId }
            .filter { chunk -> chunk.documentId in documentIds }
            .sortedBy { chunk -> chunk.chunkIndex }
            .groupBy { chunk -> chunk.documentId }
            .map { (documentId, chunks) ->
                MaterialDocumentOutline(
                    documentId = documentId,
                    entries = chunks.toOutlineEntries().take(query.maxEntriesPerDocument.coerceIn(1, 120))
                )
            }
        return Either.Right(outlines)
    }
}

class InMemoryFindMaterialDocumentSectionsPort(
    private val state: InMemoryState
) : FindMaterialDocumentSectionsPort {
    override suspend fun invoke(query: FindMaterialDocumentSectionsQuery): Either<InfrastructureError, List<MaterialDocumentSection>> {
        val documentIds = query.documentIds.toSet()
        val sections = query.sectionTitles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .flatMap { sectionTitle ->
                state.materialChunks.values
                    .asSequence()
                    .filter { chunk -> chunk.ownerUserId == query.ownerUserId }
                    .filter { chunk -> chunk.documentId in documentIds }
                    .filter { chunk -> chunk.headingPath?.containsSectionTitle(sectionTitle) == true }
                    .sortedBy { chunk -> chunk.chunkIndex }
                    .groupBy { chunk -> chunk.documentId }
                    .map { (documentId, chunks) ->
                        MaterialDocumentSection(
                            documentId = documentId,
                            title = sectionTitle,
                            chunks = chunks
                                .take(query.maxChunksPerSection.coerceIn(1, 5))
                                .map { chunk ->
                                    MaterialDocumentSectionChunk(
                                        text = chunk.text,
                                        headingPath = chunk.headingPath,
                                        pageStart = chunk.pageStart,
                                        pageEnd = chunk.pageEnd
                                    )
                                }
                        )
                    }
            }
        return Either.Right(sections)
    }
}

class InMemorySearchUserMaterialsPort(
    private val state: InMemoryState
) : SearchUserMaterialsPort {
    override suspend fun invoke(query: MaterialSearchQuery): Either<InfrastructureError, List<MaterialSearchHit>> {
        val normalizedQuery = query.text.trim()
        if (normalizedQuery.isBlank()) {
            return Either.Right(emptyList())
        }

        val queryTokens = normalizedQuery.searchTokens()
        val maxResults = query.maxResults.coerceIn(1, 20)

        val hits = state.materialChunks.values
            .asSequence()
            .filter { chunk -> chunk.ownerUserId == query.ownerUserId }
            .filter { chunk -> query.collectionIds.isEmpty() || chunk.collectionId in query.collectionIds }
            .filter { chunk -> query.documentIds.isEmpty() || chunk.documentId in query.documentIds }
            .mapNotNull { chunk ->
                val document = state.materialDocuments[chunk.documentId.value.toString()]
                    ?.takeIf { document -> document.ownerUserId == query.ownerUserId }
                    ?.takeIf { document -> document.ingestionStatus == MaterialIngestionStatus.READY }
                    ?: return@mapNotNull null

                val score = lexicalScore(queryTokens, document.title, chunk.text, chunk.headingPath)
                MaterialSearchHit(
                    chunkId = chunk.id,
                    documentId = chunk.documentId,
                    collectionId = chunk.collectionId,
                    title = document.title,
                    snippet = chunk.text.toSnippet(),
                    score = score,
                    headingPath = chunk.headingPath,
                    pageStart = chunk.pageStart,
                    pageEnd = chunk.pageEnd
                )
            }
            .filter { hit -> hit.score >= query.minScore }
            .sortedByDescending { hit -> hit.score }
            .take(maxResults)
            .toList()

        return Either.Right(hits)
    }
}

private fun lexicalScore(queryTokens: Set<String>, title: String, text: String, headingPath: String?): Double {
    if (queryTokens.isEmpty()) return 0.0

    val titleTokens = title.searchTokens()
    val headingTokens = headingPath.orEmpty().searchTokens()
    val textTokens = text.searchTokens()

    val titleOverlap = overlapRatio(queryTokens, titleTokens)
    val headingOverlap = overlapRatio(queryTokens, headingTokens)
    val textOverlap = overlapRatio(queryTokens, textTokens)

    return (textOverlap * 0.70 + titleOverlap * 0.20 + headingOverlap * 0.10).coerceIn(0.0, 1.0)
}

private fun overlapRatio(queryTokens: Set<String>, targetTokens: Set<String>): Double {
    if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
    return queryTokens.count { token -> token in targetTokens }.toDouble() / queryTokens.size
}

private fun String.searchTokens(): Set<String> =
    SEARCH_TOKEN_REGEX.findAll(lowercase())
        .map { match -> match.value }
        .filterNot { token -> token.length <= 1 }
        .toSet()

private fun String.toSnippet(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= 700) normalized else normalized.take(697).trimEnd() + "..."
}

private fun List<MaterialChunk>.toOutlineEntries(): List<MaterialDocumentOutlineEntry> {
    val seen = linkedSetOf<String>()
    val entries = mutableListOf<MaterialDocumentOutlineEntry>()

    for (chunk in this) {
        val tocEntry = chunk.text.toTocOutlineEntry()
        if (tocEntry != null && seen.add(tocEntry.title.normalizedOutlineKey())) {
            entries += tocEntry
        }

        chunk.headingPath
            ?.split(">")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEachIndexed { index, heading ->
                val entry = MaterialDocumentOutlineEntry(heading.toDisplayHeading(), index + 1)
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

    return MaterialDocumentOutlineEntry(withoutPage.toDisplayHeading(), level)
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

private val SEARCH_TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

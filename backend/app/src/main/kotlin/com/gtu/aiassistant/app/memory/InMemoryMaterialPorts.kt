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
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialCollectionPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
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

class InMemorySaveMaterialCollectionPort : SaveMaterialCollectionPort {
    override suspend fun invoke(collection: com.gtu.aiassistant.domain.materials.model.MaterialCollection) =
        Either.Right(collection)
}

class InMemoryFindMaterialCollectionPort : FindMaterialCollectionPort {
    override suspend fun invoke(strategy: FindMaterialCollectionPort.Strategy): Either<InfrastructureError, FindMaterialCollectionPort.Result> =
        Either.Right(
            when (strategy) {
                is FindMaterialCollectionPort.Strategy.ById -> FindMaterialCollectionPort.Result.Single(collection = null)
                is FindMaterialCollectionPort.Strategy.ByOwner -> FindMaterialCollectionPort.Result.Multiple(collections = emptyList())
            }
        )
}

class InMemoryDeleteMaterialCollectionPort : DeleteMaterialCollectionPort {
    override suspend fun invoke(ownerUserId: UserId, collectionId: MaterialCollectionId): Either<InfrastructureError, Unit> =
        Either.Right(Unit)
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

private val SEARCH_TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

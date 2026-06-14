package com.gtu.aiassistant.infrastructure.ai.tools

import arrow.core.Either
import arrow.core.flatMap
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.materials.model.MaterialSearchQuery
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlinePort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlineQuery
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsQuery
import com.gtu.aiassistant.domain.materials.port.output.SearchUserMaterialsPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

class UserMaterialSearchTool(
    private val searchUserMaterialsPort: SearchUserMaterialsPort,
    private val findMaterialDocumentPort: FindMaterialDocumentPort,
    private val findMaterialDocumentOutlinePort: FindMaterialDocumentOutlinePort,
    private val findMaterialDocumentSectionsPort: FindMaterialDocumentSectionsPort
) {
    suspend fun resolve(
        ownerUserId: UserId,
        query: String,
        collectionIds: List<MaterialCollectionId>,
        documentIds: List<MaterialDocumentId>,
        maxResults: Int = 6
    ): Either<InfrastructureError, UserMaterialContext> =
        loadDocuments(
            ownerUserId = ownerUserId,
            collectionIds = collectionIds,
            documentIds = documentIds
        ).flatMap { documents ->
            val readyDocumentIds = documents
                .filter { document -> document.ingestionStatus == MaterialIngestionStatus.READY }
                .map { document -> document.id }
            val outlinesByDocumentId = loadOutlines(ownerUserId, readyDocumentIds)
                .fold(
                    ifLeft = { emptyMap() },
                    ifRight = { outlines ->
                        outlines.associate { outline ->
                            outline.documentId to outline.entries.map { entry ->
                                UserMaterialOutlineEntry(
                                    title = entry.title,
                                    level = entry.level
                                )
                            }
                        }
                    }
                )
            val contextDocuments = documents.map { document ->
                document.toContextDocument(outlinesByDocumentId[document.id].orEmpty())
            }
            val sectionSources = loadRelevantSectionSources(
                ownerUserId = ownerUserId,
                query = query,
                documents = contextDocuments,
                documentIds = readyDocumentIds
            ).fold(
                ifLeft = { emptyList() },
                ifRight = { it }
            )

            if (readyDocumentIds.isEmpty()) {
                return@flatMap Either.Right(
                    UserMaterialContext(
                        documents = contextDocuments,
                        sources = emptyList(),
                        searchError = null
                    )
                )
            }

            search(
                ownerUserId = ownerUserId,
                query = query,
                collectionIds = collectionIds,
                documentIds = readyDocumentIds,
                maxResults = maxResults
            ).fold(
                ifLeft = { error ->
                    Either.Right(
                        UserMaterialContext(
                            documents = contextDocuments,
                            sources = sectionSources,
                            searchError = error.toMaterialSearchError()
                        )
                    )
                },
                ifRight = { sources ->
                    Either.Right(
                        UserMaterialContext(
                            documents = contextDocuments,
                            sources = (sectionSources + sources)
                                .distinctBy { source -> source.url to source.snippet },
                            searchError = null
                        )
                    )
                }
            )
        }

    suspend fun search(
        ownerUserId: UserId,
        query: String,
        collectionIds: List<MaterialCollectionId>,
        documentIds: List<MaterialDocumentId>,
        maxResults: Int = 6
    ): Either<InfrastructureError, List<AgentSource>> =
        searchUserMaterialsPort(
            MaterialSearchQuery(
                ownerUserId = ownerUserId,
                text = query,
                collectionIds = collectionIds,
                documentIds = documentIds,
                maxResults = maxResults,
                minScore = 0.2
            )
        ).map { hits ->
            hits.map { hit ->
                AgentSource(
                    title = hit.title,
                    url = "material://${hit.documentId.value}#chunk=${hit.chunkId}",
                    snippet = hit.toSnippetWithLocation(),
                    score = hit.score,
                    sourceType = MessageCitationSourceType.USER_MATERIAL,
                    documentId = hit.documentId,
                    pageStart = hit.pageStart,
                    pageEnd = hit.pageEnd
                )
            }
        }

    private suspend fun loadDocuments(
        ownerUserId: UserId,
        collectionIds: List<MaterialCollectionId>,
        documentIds: List<MaterialDocumentId>
    ): Either<InfrastructureError, List<MaterialDocument>> {
        val result = if (documentIds.isNotEmpty()) {
            findMaterialDocumentPort(
                FindMaterialDocumentPort.Strategy.ByIds(ownerUserId, documentIds)
            )
        } else {
            findMaterialDocumentPort(
                FindMaterialDocumentPort.Strategy.ByOwner(ownerUserId)
            )
        }

        return result.map { portResult ->
            val documents = (portResult as FindMaterialDocumentPort.Result.Multiple).documents
            if (collectionIds.isEmpty()) {
                documents
            } else {
                documents.filter { document -> document.collectionId in collectionIds }
            }
        }
    }

    private suspend fun loadOutlines(
        ownerUserId: UserId,
        documentIds: List<MaterialDocumentId>
    ) =
        findMaterialDocumentOutlinePort(
            FindMaterialDocumentOutlineQuery(
                ownerUserId = ownerUserId,
                documentIds = documentIds,
                maxEntriesPerDocument = 50
            )
        )

    private suspend fun loadRelevantSectionSources(
        ownerUserId: UserId,
        query: String,
        documents: List<UserMaterialDocumentContext>,
        documentIds: List<MaterialDocumentId>
    ): Either<InfrastructureError, List<AgentSource>> {
        val sectionTitles = documents
            .flatMap { document -> document.outline.map { it.title } }
            .filter { title -> title.matchesQueryIntent(query) }
            .distinct()
            .take(MAX_RELEVANT_SECTIONS)

        if (sectionTitles.isEmpty()) return Either.Right(emptyList())

        val titleByDocumentId = documents.associate { it.id to it.title }

        return findMaterialDocumentSectionsPort(
            FindMaterialDocumentSectionsQuery(
                ownerUserId = ownerUserId,
                documentIds = documentIds,
                sectionTitles = sectionTitles,
                maxChunksPerSection = 2
            )
        ).map { sections ->
            sections.flatMap { section ->
                section.chunks.mapIndexed { index, chunk ->
                    AgentSource(
                        title = titleByDocumentId[section.documentId] ?: section.title,
                        url = "material://${section.documentId.value}#section=${section.title.hashCode()}-$index",
                        snippet = chunk.toSectionSnippet(section.title),
                        score = 0.95,
                        sourceType = MessageCitationSourceType.USER_MATERIAL,
                        documentId = section.documentId,
                        pageStart = chunk.pageStart,
                        pageEnd = chunk.pageEnd
                    )
                }
            }
        }
    }
}

data class UserMaterialContext(
    val documents: List<UserMaterialDocumentContext>,
    val sources: List<AgentSource>,
    val searchError: String?
)

data class UserMaterialDocumentContext(
    val id: MaterialDocumentId,
    val title: String,
    val originalFileName: String,
    val ingestionStatus: MaterialIngestionStatus,
    val ingestionError: String?,
    val outline: List<UserMaterialOutlineEntry>
)

data class UserMaterialOutlineEntry(
    val title: String,
    val level: Int?
)

private fun MaterialDocument.toContextDocument(outline: List<UserMaterialOutlineEntry>): UserMaterialDocumentContext =
    UserMaterialDocumentContext(
        id = id,
        title = title,
        originalFileName = originalFileName,
        ingestionStatus = ingestionStatus,
        ingestionError = ingestionError,
        outline = outline
    )

private fun InfrastructureError.toMaterialSearchError(): String =
    cause.message
        ?.takeIf(String::isNotBlank)
        ?.let { "Uploaded material search failed: $it" }
        ?: "Uploaded material search failed: ${cause::class.simpleName ?: "unknown error"}"

private fun com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentSectionChunk.toSectionSnippet(sectionTitle: String): String =
    buildString {
        val location = listOfNotNull(
            headingPath?.takeIf { it.isNotBlank() } ?: sectionTitle,
            pageStart?.let { start ->
                val end = pageEnd?.takeIf { it != start }
                if (end == null) "page $start" else "pages $start-$end"
            }
        ).joinToString(separator = ", ")

        append("[$location] ")
        append(text.replace(Regex("\\s+"), " ").trim().let { normalized ->
            if (normalized.length <= 900) normalized else normalized.take(897).trimEnd() + "..."
        })
    }

private fun String.matchesQueryIntent(query: String): Boolean {
    val queryTokens = query.searchTokensForMaterialTool()
    if (queryTokens.isEmpty()) return false
    val titleTokens = searchTokensForMaterialTool()
    return titleTokens.any { titleToken ->
        queryTokens.any { queryToken -> titleToken.matchesMaterialToken(queryToken) }
    }
}

private fun String.searchTokensForMaterialTool(): Set<String> =
    MATERIAL_TOOL_TOKEN_REGEX.findAll(lowercase())
        .map { it.value }
        .filter { it.length >= 4 }
        .filterNot { it in MATERIAL_TOOL_LOW_SIGNAL_TOKENS }
        .toSet()

private fun String.matchesMaterialToken(other: String): Boolean =
    this == other ||
        (length >= MATERIAL_TOOL_PREFIX_LENGTH && other.length >= MATERIAL_TOOL_PREFIX_LENGTH &&
            (startsWith(other) || other.startsWith(this) || take(MATERIAL_TOOL_PREFIX_LENGTH) == other.take(MATERIAL_TOOL_PREFIX_LENGTH)))

private fun com.gtu.aiassistant.domain.materials.model.MaterialSearchHit.toSnippetWithLocation(): String =
    buildString {
        val location = listOfNotNull(
            headingPath?.takeIf { it.isNotBlank() },
            pageStart?.let { start ->
                val end = pageEnd?.takeIf { it != start }
                if (end == null) "page $start" else "pages $start-$end"
            }
        ).joinToString(separator = ", ")

        if (location.isNotBlank()) {
            append("[$location] ")
        }
        append(snippet)
    }

private const val MAX_RELEVANT_SECTIONS = 4
private const val MATERIAL_TOOL_PREFIX_LENGTH = 5
private val MATERIAL_TOOL_TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")
private val MATERIAL_TOOL_LOW_SIGNAL_TOKENS = setOf(
    "какой",
    "какая",
    "какие",
    "моем",
    "моём",
    "есть",
    "используется",
    "используются"
)

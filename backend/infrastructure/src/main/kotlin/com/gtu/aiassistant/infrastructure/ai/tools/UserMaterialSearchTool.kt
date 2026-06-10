package com.gtu.aiassistant.infrastructure.ai.tools

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.materials.model.MaterialCollectionId
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialSearchQuery
import com.gtu.aiassistant.domain.materials.port.output.SearchUserMaterialsPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

class UserMaterialSearchTool(
    private val searchUserMaterialsPort: SearchUserMaterialsPort
) {
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
}

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

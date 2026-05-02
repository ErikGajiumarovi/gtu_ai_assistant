package com.gtu.aiassistant.infrastructure.ai.tools

import arrow.core.Either
import com.gtu.aiassistant.domain.chat.model.MessageCitationSourceType
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchQuery
import com.gtu.aiassistant.domain.knowledge.port.output.SearchKnowledgePort
import com.gtu.aiassistant.domain.model.InfrastructureError

class GtuKnowledgeSearchTool(
    private val searchKnowledgePort: SearchKnowledgePort
) {
    suspend fun search(query: String, maxResults: Int = 6): Either<InfrastructureError, List<AgentSource>> =
        searchKnowledgePort(
            KnowledgeSearchQuery(
                text = query,
                maxResults = maxResults,
                minScore = 0.18
            )
        ).map { hits ->
            hits.map { hit ->
                AgentSource(
                    title = hit.title,
                    url = hit.url,
                    snippet = hit.snippet,
                    score = hit.score,
                    sourceType = MessageCitationSourceType.RAG
                )
            }
        }
}

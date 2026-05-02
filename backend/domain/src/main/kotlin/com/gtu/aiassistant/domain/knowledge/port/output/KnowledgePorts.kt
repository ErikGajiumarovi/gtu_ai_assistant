package com.gtu.aiassistant.domain.knowledge.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeDocument
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchHit
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchQuery
import com.gtu.aiassistant.domain.model.InfrastructureError

fun interface SearchKnowledgePort {
    suspend operator fun invoke(query: KnowledgeSearchQuery): Either<InfrastructureError, List<KnowledgeSearchHit>>
}

fun interface UpsertKnowledgeDocumentPort {
    suspend operator fun invoke(document: KnowledgeDocument): Either<InfrastructureError, UpsertKnowledgeDocumentResult>
}

data class UpsertKnowledgeDocumentResult(
    val changed: Boolean
)

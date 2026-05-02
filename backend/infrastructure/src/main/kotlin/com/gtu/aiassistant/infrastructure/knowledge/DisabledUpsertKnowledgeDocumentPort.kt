package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeDocument
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentResult
import com.gtu.aiassistant.domain.model.InfrastructureError

class DisabledUpsertKnowledgeDocumentPort : UpsertKnowledgeDocumentPort {
    override suspend fun invoke(document: KnowledgeDocument): Either<InfrastructureError, UpsertKnowledgeDocumentResult> =
        Either.Right(UpsertKnowledgeDocumentResult(changed = false))
}

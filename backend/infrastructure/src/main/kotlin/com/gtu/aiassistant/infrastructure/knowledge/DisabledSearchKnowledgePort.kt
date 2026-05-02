package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchHit
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchQuery
import com.gtu.aiassistant.domain.knowledge.port.output.SearchKnowledgePort
import com.gtu.aiassistant.domain.model.InfrastructureError

class DisabledSearchKnowledgePort : SearchKnowledgePort {
    override suspend fun invoke(query: KnowledgeSearchQuery): Either<InfrastructureError, List<KnowledgeSearchHit>> =
        Either.Right(emptyList())
}

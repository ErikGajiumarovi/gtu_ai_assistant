package com.gtu.aiassistant.infrastructure.knowledge

import arrow.core.Either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeDocument
import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeIngestionRun
import com.gtu.aiassistant.domain.knowledge.port.output.KnowledgeSourceRegistration
import com.gtu.aiassistant.domain.knowledge.port.output.SaveKnowledgeIngestionRunPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentPort
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeDocumentResult
import com.gtu.aiassistant.domain.knowledge.port.output.UpsertKnowledgeSourcesPort
import com.gtu.aiassistant.domain.model.InfrastructureError

class DisabledUpsertKnowledgeDocumentPort : UpsertKnowledgeDocumentPort {
    override suspend fun invoke(document: KnowledgeDocument): Either<InfrastructureError, UpsertKnowledgeDocumentResult> =
        Either.Right(UpsertKnowledgeDocumentResult(changed = false))
}

class DisabledUpsertKnowledgeSourcesPort : UpsertKnowledgeSourcesPort {
    override suspend fun invoke(sources: List<KnowledgeSourceRegistration>): Either<InfrastructureError, Unit> =
        Either.Right(Unit)
}

class DisabledSaveKnowledgeIngestionRunPort : SaveKnowledgeIngestionRunPort {
    override suspend fun invoke(run: KnowledgeIngestionRun): Either<InfrastructureError, Unit> =
        Either.Right(Unit)
}

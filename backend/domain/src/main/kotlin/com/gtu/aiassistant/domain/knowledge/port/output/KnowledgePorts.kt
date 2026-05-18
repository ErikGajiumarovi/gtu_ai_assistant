package com.gtu.aiassistant.domain.knowledge.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeDocument
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchHit
import com.gtu.aiassistant.domain.knowledge.model.KnowledgeSearchQuery
import com.gtu.aiassistant.domain.model.InfrastructureError
import java.time.Instant
import java.util.UUID

fun interface SearchKnowledgePort {
    suspend operator fun invoke(query: KnowledgeSearchQuery): Either<InfrastructureError, List<KnowledgeSearchHit>>
}

fun interface UpsertKnowledgeDocumentPort {
    suspend operator fun invoke(document: KnowledgeDocument): Either<InfrastructureError, UpsertKnowledgeDocumentResult>
}

data class UpsertKnowledgeDocumentResult(
    val changed: Boolean
)

fun interface UpsertKnowledgeSourcesPort {
    suspend operator fun invoke(sources: List<KnowledgeSourceRegistration>): Either<InfrastructureError, Unit>
}

fun interface SaveKnowledgeIngestionRunPort {
    suspend operator fun invoke(run: KnowledgeIngestionRun): Either<InfrastructureError, Unit>
}

data class KnowledgeSourceRegistration(
    val domain: String,
    val rootUrl: String,
    val enabled: Boolean,
    val createdAt: Instant
)

data class KnowledgeIngestionRun(
    val id: UUID,
    val startedAt: Instant,
    val finishedAt: Instant,
    val status: KnowledgeIngestionRunStatus,
    val pagesSeen: Int,
    val pagesChanged: Int,
    val errorMessage: String?
)

enum class KnowledgeIngestionRunStatus {
    SUCCEEDED,
    FAILED
}

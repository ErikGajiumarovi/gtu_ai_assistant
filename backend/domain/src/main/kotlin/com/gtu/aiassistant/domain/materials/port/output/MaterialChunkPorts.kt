package com.gtu.aiassistant.domain.materials.port.output

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialChunk
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId

fun interface SaveMaterialChunksPort {
    suspend operator fun invoke(chunks: List<MaterialChunk>): Either<InfrastructureError, Unit>
}

fun interface ReplaceMaterialDocumentChunksPort {
    suspend operator fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId, chunks: List<MaterialChunk>): Either<InfrastructureError, Unit>
}

fun interface DeleteMaterialChunksPort {
    suspend operator fun invoke(ownerUserId: UserId, documentId: MaterialDocumentId): Either<InfrastructureError, Unit>
}

fun interface FindMaterialDocumentOutlinePort {
    suspend operator fun invoke(query: FindMaterialDocumentOutlineQuery): Either<InfrastructureError, List<MaterialDocumentOutline>>
}

fun interface FindMaterialDocumentSectionsPort {
    suspend operator fun invoke(query: FindMaterialDocumentSectionsQuery): Either<InfrastructureError, List<MaterialDocumentSection>>
}

data class FindMaterialDocumentOutlineQuery(
    val ownerUserId: UserId,
    val documentIds: List<MaterialDocumentId>,
    val maxEntriesPerDocument: Int = 40
)

data class MaterialDocumentOutline(
    val documentId: MaterialDocumentId,
    val entries: List<MaterialDocumentOutlineEntry>
)

data class MaterialDocumentOutlineEntry(
    val title: String,
    val level: Int?
)

data class FindMaterialDocumentSectionsQuery(
    val ownerUserId: UserId,
    val documentIds: List<MaterialDocumentId>,
    val sectionTitles: List<String>,
    val maxChunksPerSection: Int = 2
)

data class MaterialDocumentSection(
    val documentId: MaterialDocumentId,
    val title: String,
    val chunks: List<MaterialDocumentSectionChunk>
)

data class MaterialDocumentSectionChunk(
    val text: String,
    val headingPath: String?,
    val pageStart: Int?,
    val pageEnd: Int?
)

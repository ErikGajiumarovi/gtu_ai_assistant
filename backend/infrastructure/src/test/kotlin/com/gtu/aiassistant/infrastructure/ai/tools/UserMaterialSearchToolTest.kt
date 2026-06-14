package com.gtu.aiassistant.infrastructure.ai.tools

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlinePort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentOutlineQuery
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsPort
import com.gtu.aiassistant.domain.materials.port.output.FindMaterialDocumentSectionsQuery
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentOutline
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentOutlineEntry
import com.gtu.aiassistant.domain.materials.port.output.MaterialDocumentSection
import com.gtu.aiassistant.domain.materials.port.output.SearchUserMaterialsPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserMaterialSearchToolTest {
    @Test
    fun `resolve includes ready material inventory when no excerpts match`() = runBlocking {
        val ownerUserId = UserId.fromTrusted(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val document = materialDocument(ownerUserId, MaterialIngestionStatus.READY)
        val tool = UserMaterialSearchTool(
            searchUserMaterialsPort = SearchUserMaterialsPort { Either.Right(emptyList()) },
            findMaterialDocumentPort = FakeFindMaterialDocumentPort(listOf(document)),
            findMaterialDocumentOutlinePort = FakeFindMaterialDocumentOutlinePort(
                listOf(
                    MaterialDocumentOutline(
                        documentId = document.id,
                        entries = listOf(
                            MaterialDocumentOutlineEntry("Введение", 1),
                            MaterialDocumentOutlineEntry("1. Постановка задачи и требования к системе", 1)
                        )
                    )
                )
            ),
            findMaterialDocumentSectionsPort = FakeFindMaterialDocumentSectionsPort(emptyList())
        )

        val context = tool.resolve(
            ownerUserId = ownerUserId,
            query = "к каким материалам у тебя есть доступ?",
            collectionIds = emptyList(),
            documentIds = listOf(document.id)
        ).getOrNull() ?: error("Expected material context")

        assertEquals(1, context.documents.size)
        assertEquals(document.id, context.documents.single().id)
        assertEquals(MaterialIngestionStatus.READY, context.documents.single().ingestionStatus)
        assertEquals("Введение", context.documents.single().outline.first().title)
        assertTrue(context.sources.isEmpty())
    }

    @Test
    fun `resolve lists selected non-ready document but does not search its chunks`() = runBlocking {
        val ownerUserId = UserId.fromTrusted(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val document = materialDocument(ownerUserId, MaterialIngestionStatus.PROCESSING)
        var searchCalls = 0
        val tool = UserMaterialSearchTool(
            searchUserMaterialsPort = SearchUserMaterialsPort {
                searchCalls += 1
                Either.Right(emptyList())
            },
            findMaterialDocumentPort = FakeFindMaterialDocumentPort(listOf(document)),
            findMaterialDocumentOutlinePort = FakeFindMaterialDocumentOutlinePort(emptyList()),
            findMaterialDocumentSectionsPort = FakeFindMaterialDocumentSectionsPort(emptyList())
        )

        val context = tool.resolve(
            ownerUserId = ownerUserId,
            query = "что внутри файла?",
            collectionIds = emptyList(),
            documentIds = listOf(document.id)
        ).getOrNull() ?: error("Expected material context")

        assertEquals(0, searchCalls)
        assertEquals(MaterialIngestionStatus.PROCESSING, context.documents.single().ingestionStatus)
        assertTrue(context.sources.isEmpty())
    }
}

private class FakeFindMaterialDocumentPort(
    private val documents: List<MaterialDocument>
) : FindMaterialDocumentPort {
    override suspend fun invoke(strategy: FindMaterialDocumentPort.Strategy): Either<InfrastructureError, FindMaterialDocumentPort.Result> =
        Either.Right(
            when (strategy) {
                is FindMaterialDocumentPort.Strategy.ById -> FindMaterialDocumentPort.Result.Single(
                    documents.singleOrNull { it.ownerUserId == strategy.ownerUserId && it.id == strategy.documentId }
                )

                is FindMaterialDocumentPort.Strategy.ByIds -> FindMaterialDocumentPort.Result.Multiple(
                    documents.filter { document ->
                        document.ownerUserId == strategy.ownerUserId && document.id in strategy.documentIds
                    }
                )

                is FindMaterialDocumentPort.Strategy.ByOwner -> FindMaterialDocumentPort.Result.Multiple(
                    documents.filter { document -> document.ownerUserId == strategy.ownerUserId }
                )

                is FindMaterialDocumentPort.Strategy.ByStatus -> FindMaterialDocumentPort.Result.Multiple(
                    documents.filter { document -> document.ingestionStatus == strategy.status }.take(strategy.limit)
                )
            }
        )
}

private class FakeFindMaterialDocumentOutlinePort(
    private val outlines: List<MaterialDocumentOutline>
) : FindMaterialDocumentOutlinePort {
    override suspend fun invoke(query: FindMaterialDocumentOutlineQuery): Either<InfrastructureError, List<MaterialDocumentOutline>> =
        Either.Right(
            outlines.filter { outline -> outline.documentId in query.documentIds }
        )
}

private class FakeFindMaterialDocumentSectionsPort(
    private val sections: List<MaterialDocumentSection>
) : FindMaterialDocumentSectionsPort {
    override suspend fun invoke(query: FindMaterialDocumentSectionsQuery): Either<InfrastructureError, List<MaterialDocumentSection>> =
        Either.Right(
            sections.filter { section -> section.documentId in query.documentIds }
        )
}

private fun materialDocument(
    ownerUserId: UserId,
    status: MaterialIngestionStatus
): MaterialDocument =
    MaterialDocument.fromTrusted(
        id = MaterialDocumentId.fromTrusted(UUID.randomUUID()),
        version = 0L,
        ownerUserId = ownerUserId,
        collectionId = null,
        title = "МойЗайм Дипломная Работа v3",
        originalFileName = "МойЗайм Дипломная Работа v3.docx",
        contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        sizeBytes = 1024L,
        storageObjectKey = "materials/test.docx",
        ingestionStatus = status,
        ingestionError = null,
        ocrMetadata = null,
        createdAt = Instant.parse("2026-06-14T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-14T00:00:01Z")
    )

package com.gtu.aiassistant.application.materials

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrCommand
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrResult
import com.gtu.aiassistant.domain.materials.model.MaterialDocument
import com.gtu.aiassistant.domain.materials.model.MaterialDocumentId
import com.gtu.aiassistant.domain.materials.model.MaterialIngestionStatus
import com.gtu.aiassistant.domain.user.model.UserId
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MaterialTextExtractionServiceTest {
    private val service = MaterialTextExtractionService()

    @Test
    fun `pdf extraction returns one segment per text page with page metadata`() = runBlocking {
        val result = service.extract("sample.pdf", samplePdfBytes()).getOrNull()

        assertNotNull(result)
        assertEquals(2, result.segments.size)
        assertTrue(result.segments[0].text.contains("First PDF page text"))
        assertEquals(1, result.segments[0].pageStart)
        assertEquals(1, result.segments[0].pageEnd)
        assertTrue(result.segments[1].text.contains("Second PDF page text"))
        assertEquals(2, result.segments[1].pageStart)
        assertEquals(2, result.segments[1].pageEnd)
    }

    @Test
    fun `pdf extraction uses OCR when text layer is below threshold`() = runBlocking {
        val ocrPort = MaterialOcrPort { command ->
            Either.Right(
                MaterialOcrResult(
                    text = "OCR page ${command.pageNumber} Russian English text",
                    averageConfidence = 91.5
                )
            )
        }
        val ocrService = MaterialTextExtractionService(
            ocrPort = ocrPort,
            config = MaterialTextExtractionConfig(
                ocrEnabled = true,
                pdfMinTextLayerCharacters = 1,
                ocrRenderDpi = 72
            )
        )

        val result = ocrService.extract("scan.pdf", blankPdfBytes()).getOrNull()

        assertNotNull(result)
        assertEquals(1, result.segments.size)
        assertEquals("OCR page 1 Russian English text", result.segments.single().text)
        assertEquals(1, result.segments.single().pageStart)
        assertEquals(1, result.segments.single().pageEnd)
        assertTrue(result.ocrMetadata?.used == true)
        assertEquals(listOf(1), result.ocrMetadata?.attemptedPages)
        assertEquals(91.5, result.ocrMetadata?.averageConfidence)
    }

    @Test
    fun `chunk builder preserves extracted pdf page metadata`() = runBlocking {
        val extractionResult = service.extract("sample.pdf", samplePdfBytes()).getOrNull()

        assertNotNull(extractionResult)
        val chunks = MaterialChunkBuilder(chunkSizeWords = 20, overlapWords = 0)
            .build(sampleDocument(), extractionResult)

        assertEquals(2, chunks.size)
        assertEquals(1, chunks[0].pageStart)
        assertEquals(1, chunks[0].pageEnd)
        assertEquals(2, chunks[1].pageStart)
        assertEquals(2, chunks[1].pageEnd)
    }

    @Test
    fun `docx extraction returns paragraphs with heading path metadata`() = runBlocking {
        val result = service.extract("sample.docx", sampleDocxBytes()).getOrNull()

        assertNotNull(result)
        assertEquals(2, result.segments.size)
        assertEquals("Intro paragraph text.", result.segments[0].text)
        assertEquals("Chapter One", result.segments[0].headingPath)
        assertEquals("Details paragraph text.", result.segments[1].text)
        assertEquals("Chapter One > Section A", result.segments[1].headingPath)
    }
}

private fun blankPdfBytes(): ByteArray =
    PDDocument().use { document ->
        document.addPage(PDPage())
        ByteArrayOutputStream().use { output ->
            document.save(output)
            output.toByteArray()
        }
    }

private fun samplePdfBytes(): ByteArray =
    PDDocument().use { document ->
        document.addTextPage("First PDF page text.")
        document.addTextPage("Second PDF page text.")
        ByteArrayOutputStream().use { output ->
            document.save(output)
            output.toByteArray()
        }
    }

private fun PDDocument.addTextPage(text: String) {
    val page = PDPage()
    addPage(page)
    PDPageContentStream(this, page).use { content ->
        content.beginText()
        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
        content.newLineAtOffset(72f, 720f)
        content.showText(text)
        content.endText()
    }
}

private fun sampleDocxBytes(): ByteArray =
    XWPFDocument().use { document ->
        document.createParagraph().apply {
            style = "Heading1"
            createRun().setText("Chapter One")
        }
        document.createParagraph().createRun().setText("Intro paragraph text.")
        document.createParagraph().apply {
            style = "Heading2"
            createRun().setText("Section A")
        }
        document.createParagraph().createRun().setText("Details paragraph text.")

        ByteArrayOutputStream().use { output ->
            document.write(output)
            output.toByteArray()
        }
    }

private fun sampleDocument(): MaterialDocument =
    MaterialDocument.create(
        id = MaterialDocumentId.fromTrusted(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        version = 0L,
        ownerUserId = UserId.fromTrusted(UUID.fromString("22222222-2222-2222-2222-222222222222")),
        collectionId = null,
        title = "Sample",
        originalFileName = "sample.pdf",
        contentType = "application/pdf",
        sizeBytes = 1L,
        storageObjectKey = "materials/sample.pdf",
        ingestionStatus = MaterialIngestionStatus.PROCESSING,
        ingestionError = null,
        ocrMetadata = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    ).getOrNull()!!

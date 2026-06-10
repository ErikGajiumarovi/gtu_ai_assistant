package com.gtu.aiassistant.application.materials

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrCommand
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrPort
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

class MaterialTextExtractionService(
    private val ocrPort: MaterialOcrPort? = null,
    private val config: MaterialTextExtractionConfig = MaterialTextExtractionConfig()
) {
    suspend fun extract(
        fileName: String,
        bytes: ByteArray
    ): Either<MaterialTextExtractionError, MaterialTextExtractionResult> =
        either {
            val format = MaterialTextFormat.fromFileName(fileName).bind()
            val extractionResult = when (format) {
                MaterialTextFormat.MARKDOWN,
                MaterialTextFormat.PLAIN_TEXT -> {
                    val rawText = bytes.decodeUtf8Strict().bind()
                    val segments = when (format) {
                        MaterialTextFormat.MARKDOWN -> extractMarkdownSegments(rawText)
                        MaterialTextFormat.PLAIN_TEXT -> listOf(
                            ExtractedMaterialTextSegment(
                                text = normalizeText(rawText),
                                headingPath = null,
                                pageStart = null,
                                pageEnd = null
                            )
                        )
                    }
                    MaterialTextExtractionResult(segments = segments)
                }
                MaterialTextFormat.PDF -> extractPdfSegments(bytes, ocrPort, config).bind()
                MaterialTextFormat.DOCX -> extractDocxSegments(bytes).bind()
                    .let { MaterialTextExtractionResult(segments = it) }
            }
            val segments = extractionResult.segments.filter { it.text.isNotBlank() }

            ensure(segments.isNotEmpty()) { MaterialTextExtractionError.EmptyText }

            extractionResult.copy(segments = segments)
        }
}

private suspend fun extractPdfSegments(
    bytes: ByteArray,
    ocrPort: MaterialOcrPort?,
    config: MaterialTextExtractionConfig
): Either<MaterialTextExtractionError, MaterialTextExtractionResult> {
    try {
        Loader.loadPDF(bytes).use { document ->
            val stripper = PDFTextStripper()
            val textSegments = (1..document.numberOfPages).mapNotNull { pageNumber ->
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                val text = normalizeText(stripper.getText(document))
                text.takeIf(String::isNotBlank)?.let {
                    ExtractedMaterialTextSegment(
                        text = it,
                        headingPath = null,
                        pageStart = pageNumber,
                        pageEnd = pageNumber
                    )
                }
            }

            val totalTextLength = textSegments.sumOf { it.text.normalizedTextLayerLength() }
            if (!config.ocrEnabled || ocrPort == null || totalTextLength >= config.pdfMinTextLayerCharacters) {
                return Either.Right(
                    MaterialTextExtractionResult(
                        segments = textSegments,
                        ocrMetadata = if (totalTextLength < config.pdfMinTextLayerCharacters) {
                            MaterialOcrMetadata(
                                used = false,
                                attemptedPages = emptyList(),
                                averageConfidence = null,
                                error = "OCR disabled; PDF text layer below threshold"
                            )
                        } else {
                            null
                        }
                    )
                )
            }

            val renderer = PDFRenderer(document)
            val ocrSegments = mutableListOf<ExtractedMaterialTextSegment>()
            val confidences = mutableListOf<Double>()
            val attemptedPages = (1..document.numberOfPages).toList()
            for (pageNumber in attemptedPages) {
                val image = renderer.renderImageWithDPI(pageNumber - 1, config.ocrRenderDpi.toFloat(), ImageType.RGB)
                val imageBytes = ByteArrayOutputStream().use { output ->
                    ImageIO.write(image, "png", output)
                    output.toByteArray()
                }
                val ocrResult = ocrPort(
                    MaterialOcrCommand(
                        imageBytes = imageBytes,
                        imageContentType = "image/png",
                        pageNumber = pageNumber
                    )
                ).fold(
                    ifLeft = { return Either.Left(MaterialTextExtractionError.OcrFailed) },
                    ifRight = { it }
                )
                val text = normalizeText(ocrResult.text)
                if (text.isNotBlank()) {
                    ocrSegments += ExtractedMaterialTextSegment(
                        text = text,
                        headingPath = null,
                        pageStart = pageNumber,
                        pageEnd = pageNumber
                    )
                }
                ocrResult.averageConfidence?.let { confidences += it }
            }

            return Either.Right(
                MaterialTextExtractionResult(
                    segments = ocrSegments.ifEmpty { textSegments },
                    ocrMetadata = MaterialOcrMetadata(
                        used = ocrSegments.isNotEmpty(),
                        attemptedPages = attemptedPages,
                        averageConfidence = confidences.takeIf { it.isNotEmpty() }?.average(),
                        error = null
                    )
                )
            )
        }
    } catch (_: Exception) {
        return Either.Left(MaterialTextExtractionError.ExtractionFailed)
    }
}

private fun extractDocxSegments(bytes: ByteArray): Either<MaterialTextExtractionError, List<ExtractedMaterialTextSegment>> =
    Either.catch {
        XWPFDocument(bytes.inputStream()).use { document ->
            val headingStack = mutableListOf<String>()
            var currentHeadingPath: String? = null
            val segments = mutableListOf<ExtractedMaterialTextSegment>()

            document.paragraphs.forEach { paragraph ->
                val text = normalizeText(paragraph.text)
                if (text.isBlank()) return@forEach

                val headingLevel = paragraph.style?.wordHeadingLevel()
                if (headingLevel != null) {
                    while (headingStack.size >= headingLevel) {
                        headingStack.removeAt(headingStack.lastIndex)
                    }
                    headingStack += text
                    currentHeadingPath = headingStack.joinToString(" > ")
                } else {
                    segments += ExtractedMaterialTextSegment(
                        text = text,
                        headingPath = currentHeadingPath,
                        pageStart = null,
                        pageEnd = null
                    )
                }
            }

            segments
        }
    }.mapLeft { MaterialTextExtractionError.ExtractionFailed }

data class MaterialTextExtractionResult(
    val segments: List<ExtractedMaterialTextSegment>,
    val ocrMetadata: MaterialOcrMetadata? = null
) {
    val text: String = segments.joinToString("\n\n") { it.text }
}

data class MaterialOcrMetadata(
    val used: Boolean,
    val attemptedPages: List<Int>,
    val averageConfidence: Double?,
    val error: String?
) {
    fun toStorageString(): String = buildString {
        append("used=").append(used)
        append("; pages=").append(attemptedPages.joinToString(","))
        averageConfidence?.let { append("; averageConfidence=").append("%.2f".format(Locale.US, it)) }
        error?.takeIf(String::isNotBlank)?.let { append("; error=").append(it) }
    }
}

data class MaterialTextExtractionConfig(
    val ocrEnabled: Boolean = false,
    val pdfMinTextLayerCharacters: Int = DEFAULT_PDF_MIN_TEXT_LAYER_CHARACTERS,
    val ocrRenderDpi: Int = DEFAULT_OCR_RENDER_DPI
) {
    init {
        require(pdfMinTextLayerCharacters >= 0) { "pdfMinTextLayerCharacters must not be negative" }
        require(ocrRenderDpi in 72..600) { "ocrRenderDpi must be between 72 and 600" }
    }

    companion object {
        const val DEFAULT_PDF_MIN_TEXT_LAYER_CHARACTERS = 80
        const val DEFAULT_OCR_RENDER_DPI = 200
    }
}

data class ExtractedMaterialTextSegment(
    val text: String,
    val headingPath: String?,
    val pageStart: Int?,
    val pageEnd: Int?
)

sealed interface MaterialTextExtractionError {
    data object UnsupportedFormat : MaterialTextExtractionError
    data object InvalidUtf8 : MaterialTextExtractionError
    data object EmptyText : MaterialTextExtractionError
    data object ExtractionFailed : MaterialTextExtractionError
    data object OcrFailed : MaterialTextExtractionError
}

private enum class MaterialTextFormat {
    MARKDOWN,
    PLAIN_TEXT,
    PDF,
    DOCX;

    companion object {
        fun fromFileName(fileName: String): Either<MaterialTextExtractionError, MaterialTextFormat> =
            when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
                "md" -> Either.Right(MARKDOWN)
                "txt" -> Either.Right(PLAIN_TEXT)
                "pdf" -> Either.Right(PDF)
                "docx" -> Either.Right(DOCX)
                else -> Either.Left(MaterialTextExtractionError.UnsupportedFormat)
            }
    }
}

private fun ByteArray.decodeUtf8Strict(): Either<MaterialTextExtractionError, String> =
    Either.catch {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    }.mapLeft { MaterialTextExtractionError.InvalidUtf8 }

private fun extractMarkdownSegments(rawText: String): List<ExtractedMaterialTextSegment> {
    val headingStack = mutableListOf<String>()
    val currentLines = mutableListOf<String>()
    val segments = mutableListOf<ExtractedMaterialTextSegment>()
    var currentHeadingPath: String? = null

    fun flush() {
        val normalized = normalizeText(currentLines.joinToString("\n"))
        if (normalized.isNotBlank()) {
            segments += ExtractedMaterialTextSegment(
                text = normalized,
                headingPath = currentHeadingPath,
                pageStart = null,
                pageEnd = null
            )
        }
        currentLines.clear()
    }

    rawText.lineSequence().forEach { line ->
        val heading = line.markdownHeadingOrNull()
        if (heading != null) {
            flush()
            while (headingStack.size >= heading.level) {
                headingStack.removeAt(headingStack.lastIndex)
            }
            headingStack += heading.title
            currentHeadingPath = headingStack.joinToString(" > ")
        } else {
            currentLines += line
        }
    }
    flush()

    return segments
}

private data class MarkdownHeading(
    val level: Int,
    val title: String
)

private fun String.markdownHeadingOrNull(): MarkdownHeading? {
    val trimmedStart = trimStart()
    val markerCount = trimmedStart.takeWhile { it == '#' }.length
    if (markerCount !in 1..6) return null
    if (trimmedStart.length == markerCount || !trimmedStart[markerCount].isWhitespace()) return null
    val title = trimmedStart
        .drop(markerCount)
        .trim()
        .trimEnd('#')
        .trim()
    return title.takeIf(String::isNotBlank)?.let { MarkdownHeading(markerCount, it) }
}

private fun normalizeText(rawText: String): String =
    rawText
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map { line -> line.trim().replace(Regex("[\\t ]+"), " ") }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

private fun String.normalizedTextLayerLength(): Int =
    count { !it.isWhitespace() }

private fun String.wordHeadingLevel(): Int? {
    val compact = filterNot { it == ' ' || it == '-' || it == '_' }.lowercase(Locale.ROOT)
    if (!compact.startsWith("heading")) return null
    return compact.drop("heading".length).takeWhile(Char::isDigit).toIntOrNull()?.takeIf { it in 1..6 }
}

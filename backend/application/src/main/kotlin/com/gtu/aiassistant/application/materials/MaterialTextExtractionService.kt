package com.gtu.aiassistant.application.materials

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

class MaterialTextExtractionService {
    fun extract(
        fileName: String,
        bytes: ByteArray
    ): Either<MaterialTextExtractionError, MaterialTextExtractionResult> =
        either {
            val format = MaterialTextFormat.fromFileName(fileName).bind()
            val rawText = bytes.decodeUtf8Strict().bind()
            val segments = when (format) {
                MaterialTextFormat.MARKDOWN -> extractMarkdownSegments(rawText)
                MaterialTextFormat.PLAIN_TEXT -> listOf(
                    ExtractedMaterialTextSegment(
                        text = normalizeText(rawText),
                        headingPath = null
                    )
                )
            }.filter { it.text.isNotBlank() }

            ensure(segments.isNotEmpty()) { MaterialTextExtractionError.EmptyText }

            MaterialTextExtractionResult(segments = segments)
        }
}

data class MaterialTextExtractionResult(
    val segments: List<ExtractedMaterialTextSegment>
) {
    val text: String = segments.joinToString("\n\n") { it.text }
}

data class ExtractedMaterialTextSegment(
    val text: String,
    val headingPath: String?
)

sealed interface MaterialTextExtractionError {
    data object UnsupportedFormat : MaterialTextExtractionError
    data object InvalidUtf8 : MaterialTextExtractionError
    data object EmptyText : MaterialTextExtractionError
}

private enum class MaterialTextFormat {
    MARKDOWN,
    PLAIN_TEXT;

    companion object {
        fun fromFileName(fileName: String): Either<MaterialTextExtractionError, MaterialTextFormat> =
            when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)) {
                "md" -> Either.Right(MARKDOWN)
                "txt" -> Either.Right(PLAIN_TEXT)
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
                headingPath = currentHeadingPath
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

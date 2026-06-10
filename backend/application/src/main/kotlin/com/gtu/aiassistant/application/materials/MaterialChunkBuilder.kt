package com.gtu.aiassistant.application.materials

import com.gtu.aiassistant.domain.materials.model.MaterialDocument

class MaterialChunkBuilder(
    private val chunkSizeWords: Int = DEFAULT_CHUNK_SIZE_WORDS,
    private val overlapWords: Int = DEFAULT_OVERLAP_WORDS
) {
    init {
        require(chunkSizeWords in 1..2_000) { "chunkSizeWords must be between 1 and 2000" }
        require(overlapWords >= 0) { "overlapWords must not be negative" }
        require(overlapWords < chunkSizeWords) { "overlapWords must be smaller than chunkSizeWords" }
    }

    fun build(
        document: MaterialDocument,
        extractionResult: MaterialTextExtractionResult
    ): List<MaterialChunkCandidate> {
        var chunkIndex = 0
        return extractionResult.segments.flatMap { segment ->
            val words = WORD_REGEX.findAll(segment.text).map { it.value }.toList()
            if (words.isEmpty()) return@flatMap emptyList()

            val chunks = mutableListOf<MaterialChunkCandidate>()
            var start = 0
            while (start < words.size) {
                val end = (start + chunkSizeWords).coerceAtMost(words.size)
                val text = words.subList(start, end).joinToString(" ")
                chunks += MaterialChunkCandidate(
                    chunkIndex = chunkIndex++,
                    text = text,
                    embeddingInput = buildEmbeddingInput(document, segment.headingPath, text),
                    headingPath = segment.headingPath,
                    pageStart = segment.pageStart,
                    pageEnd = segment.pageEnd
                )
                if (end == words.size) break
                start = (end - overlapWords).coerceAtLeast(start + 1)
            }
            chunks
        }
    }

    private fun buildEmbeddingInput(
        document: MaterialDocument,
        headingPath: String?,
        text: String
    ): String = buildString {
        appendLine("Title: ${document.title}")
        if (!headingPath.isNullOrBlank()) {
            appendLine("Heading: $headingPath")
        }
        appendLine("Content:")
        append(text)
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE_WORDS = 400
        const val DEFAULT_OVERLAP_WORDS = 75
        private val WORD_REGEX = Regex("\\S+")
    }
}

data class MaterialChunkCandidate(
    val chunkIndex: Int,
    val text: String,
    val embeddingInput: String,
    val headingPath: String?,
    val pageStart: Int?,
    val pageEnd: Int?
)

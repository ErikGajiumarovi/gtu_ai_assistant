package com.gtu.aiassistant.infrastructure.persistence.knowledge

import kotlin.math.sqrt

internal data class KnowledgeQuerySignals(
    val normalizedQuery: String,
    val orderedKeywordTokens: List<String>,
    val keywordTokens: Set<String>,
    val numericTokens: Set<String>,
    val bigrams: Set<String>
) {
    val requiresLexicalEvidence: Boolean =
        keywordTokens.size >= 3 || numericTokens.isNotEmpty() || bigrams.isNotEmpty()
}

internal data class KnowledgeHybridScore(
    val semanticScore: Double,
    val lexicalScore: Double,
    val numericRecall: Double,
    val phraseRecall: Double,
    val finalScore: Double
)

internal fun buildKnowledgeQuerySignals(query: String): KnowledgeQuerySignals {
    val normalizedQuery = query.normalizeForSearch()
    val tokens = tokenizeForSearch(query)
    val orderedKeywordTokens = tokens.filterNot(::isLowSignalToken).distinct()
    val keywordTokens = orderedKeywordTokens.toSet()
    val numericTokens = keywordTokens.filter(::isNumericToken).toSet()
    val bigrams = orderedKeywordTokens.windowed(size = 2, step = 1, partialWindows = false)
        .map { it.joinToString(" ") }
        .toSet()

    return KnowledgeQuerySignals(
        normalizedQuery = normalizedQuery,
        orderedKeywordTokens = orderedKeywordTokens,
        keywordTokens = keywordTokens,
        numericTokens = numericTokens,
        bigrams = bigrams
    )
}

internal fun scoreKnowledgeCandidate(
    signals: KnowledgeQuerySignals,
    queryEmbedding: List<Float>,
    title: String,
    url: String,
    text: String,
    candidateEmbedding: List<Float>
): KnowledgeHybridScore {
    val semanticScore = cosineSimilarity(queryEmbedding, candidateEmbedding).coerceAtLeast(0.0)
    val titleTokens = tokenizeForSearch(title).toSet()
    val urlTokens = tokenizeForSearch(url).toSet()
    val textTokens = tokenizeForSearch(text).toSet()
    val allTokens = titleTokens + urlTokens + textTokens

    val textRecall = overlapRatio(signals.keywordTokens, textTokens)
    val titleRecall = overlapRatio(signals.keywordTokens, titleTokens)
    val urlRecall = overlapRatio(signals.keywordTokens, urlTokens)
    val numericRecall = overlapRatio(signals.numericTokens, allTokens)

    val normalizedTitle = title.normalizeForSearch()
    val normalizedUrl = url.normalizeForSearch()
    val normalizedText = text.normalizeForSearch()
    val phraseRecall = phraseRecall(signals.bigrams, normalizedTitle, normalizedUrl, normalizedText)
    val exactQueryBonus = when {
        signals.normalizedQuery.length >= 8 && normalizedTitle.contains(signals.normalizedQuery) -> 0.18
        signals.normalizedQuery.length >= 8 && normalizedUrl.contains(signals.normalizedQuery) -> 0.15
        else -> 0.0
    }

    val lexicalScore = (
        textRecall * 0.35 +
            titleRecall * 0.25 +
            urlRecall * 0.10 +
            numericRecall * 0.20 +
            phraseRecall * 0.10 +
            exactQueryBonus
        ).coerceIn(0.0, 1.0)

    val numericPenalty = if (signals.numericTokens.isNotEmpty() && numericRecall == 0.0) 0.65 else 1.0
    val finalScore = (
        semanticScore * 0.35 +
            lexicalScore * 0.65
        ) * numericPenalty

    return KnowledgeHybridScore(
        semanticScore = semanticScore,
        lexicalScore = lexicalScore,
        numericRecall = numericRecall,
        phraseRecall = phraseRecall,
        finalScore = finalScore.coerceIn(0.0, 1.0)
    )
}

internal fun shouldKeepKnowledgeCandidate(
    signals: KnowledgeQuerySignals,
    score: KnowledgeHybridScore,
    minScore: Double
): Boolean {
    if (score.finalScore < minScore) {
        return false
    }

    if (signals.numericTokens.isNotEmpty() && score.numericRecall == 0.0 && score.phraseRecall < 0.34) {
        return false
    }

    if (signals.requiresLexicalEvidence && score.lexicalScore < 0.16 && score.semanticScore < 0.30) {
        return false
    }

    return true
}

internal fun cosineSimilarity(left: List<Float>, right: List<Float>): Double {
    val size = minOf(left.size, right.size)
    if (size == 0) return 0.0

    var dot = 0.0
    var leftNorm = 0.0
    var rightNorm = 0.0

    for (index in 0 until size) {
        val leftValue = left[index].toDouble()
        val rightValue = right[index].toDouble()
        dot += leftValue * rightValue
        leftNorm += leftValue * leftValue
        rightNorm += rightValue * rightValue
    }

    if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
    return dot / (sqrt(leftNorm) * sqrt(rightNorm))
}

private fun tokenizeForSearch(value: String): List<String> =
    TOKEN_REGEX.findAll(value.lowercase())
        .map { it.value }
        .toList()

private fun String.normalizeForSearch(): String =
    tokenizeForSearch(this).joinToString(" ")

private fun overlapRatio(queryTokens: Set<String>, targetTokens: Set<String>): Double {
    if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
    return queryTokens.count { it in targetTokens }.toDouble() / queryTokens.size
}

private fun phraseRecall(
    bigrams: Set<String>,
    normalizedTitle: String,
    normalizedUrl: String,
    normalizedText: String
): Double {
    if (bigrams.isEmpty()) return 0.0

    val hits = bigrams.count { phrase ->
        normalizedTitle.contains(phrase) || normalizedUrl.contains(phrase) || normalizedText.contains(phrase)
    }

    return hits.toDouble() / bigrams.size
}

private fun isNumericToken(token: String): Boolean =
    token.all(Char::isDigit)

private fun isLowSignalToken(token: String): Boolean =
    token.length <= 1 ||
        token in LOW_SIGNAL_TOKENS ||
        (token.length <= 2 && !isNumericToken(token))

private val TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

private val LOW_SIGNAL_TOKENS = setOf(
    "a",
    "an",
    "and",
    "are",
    "at",
    "be",
    "does",
    "for",
    "from",
    "gtu",
    "how",
    "i",
    "in",
    "is",
    "it",
    "me",
    "of",
    "on",
    "or",
    "please",
    "tell",
    "that",
    "the",
    "their",
    "this",
    "to",
    "university",
    "what",
    "when",
    "where",
    "which",
    "with",
    "you",
    "your",
    "georgian",
    "technical",
    "students",
    "student",
    "doesnt"
)

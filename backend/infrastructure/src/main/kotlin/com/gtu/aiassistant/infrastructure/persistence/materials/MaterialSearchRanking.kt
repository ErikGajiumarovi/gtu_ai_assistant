package com.gtu.aiassistant.infrastructure.persistence.materials

internal data class MaterialQuerySignals(
    val keywordTokens: Set<String>,
    val bigrams: Set<String>
) {
    val hasLexicalSignal: Boolean = keywordTokens.isNotEmpty()
}

internal fun buildMaterialQuerySignals(query: String): MaterialQuerySignals {
    val tokens = query.searchTokens().filterNot(::isLowSignalToken).distinct()
    return MaterialQuerySignals(
        keywordTokens = tokens.toSet(),
        bigrams = tokens.windowed(size = 2, step = 1, partialWindows = false)
            .map { it.joinToString(" ") }
            .toSet()
    )
}

internal fun scoreMaterialCandidate(
    signals: MaterialQuerySignals,
    vectorScore: Double,
    title: String,
    text: String,
    headingPath: String?
): Double {
    val lexicalScore = lexicalScore(signals, title, text, headingPath)
    val semanticScore = vectorScore.coerceIn(0.0, 1.0)
    val semanticWeight = if (signals.hasLexicalSignal) 0.25 else 1.0
    val lexicalWeight = if (signals.hasLexicalSignal) 0.75 else 0.0

    return (semanticScore * semanticWeight + lexicalScore * lexicalWeight).coerceIn(0.0, 1.0)
}

private fun lexicalScore(
    signals: MaterialQuerySignals,
    title: String,
    text: String,
    headingPath: String?
): Double {
    if (!signals.hasLexicalSignal) return 0.0

    val titleTokens = title.searchTokens().toSet()
    val headingTokens = headingPath.orEmpty().searchTokens().toSet()
    val textTokens = text.searchTokens().toSet()
    val normalizedTitle = title.normalizeForSearch()
    val normalizedHeading = headingPath.orEmpty().normalizeForSearch()
    val normalizedText = text.normalizeForSearch()

    val textRecall = overlapRatio(signals.keywordTokens, textTokens)
    val titleRecall = overlapRatio(signals.keywordTokens, titleTokens)
    val headingRecall = overlapRatio(signals.keywordTokens, headingTokens)
    val phraseRecall = phraseRecall(signals.bigrams, normalizedTitle, normalizedHeading, normalizedText)

    return (
        textRecall * 0.60 +
            titleRecall * 0.20 +
            headingRecall * 0.10 +
            phraseRecall * 0.10
        ).coerceIn(0.0, 1.0)
}

private fun phraseRecall(
    bigrams: Set<String>,
    normalizedTitle: String,
    normalizedHeading: String,
    normalizedText: String
): Double {
    if (bigrams.isEmpty()) return 0.0
    val hits = bigrams.count { phrase ->
        normalizedTitle.contains(phrase) ||
            normalizedHeading.contains(phrase) ||
            normalizedText.contains(phrase)
    }
    return hits.toDouble() / bigrams.size
}

private fun overlapRatio(queryTokens: Set<String>, targetTokens: Set<String>): Double {
    if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
    return queryTokens.count { token ->
        targetTokens.any { targetToken -> token.matchesSearchToken(targetToken) }
    }.toDouble() / queryTokens.size
}

private fun String.matchesSearchToken(other: String): Boolean =
    this == other ||
        (length >= PREFIX_MATCH_MIN_LENGTH && other.length >= PREFIX_MATCH_MIN_LENGTH &&
            (startsWith(other) || other.startsWith(this) || take(PREFIX_MATCH_MIN_LENGTH) == other.take(PREFIX_MATCH_MIN_LENGTH)))

internal fun String.searchTokens(): List<String> =
    SEARCH_TOKEN_REGEX.findAll(lowercase())
        .map { match -> match.value }
        .filterNot { token -> token.length <= 1 }
        .toList()

private fun String.normalizeForSearch(): String =
    searchTokens().joinToString(" ")

private fun isLowSignalToken(token: String): Boolean =
    token in LOW_SIGNAL_TOKENS || (token.length <= 2 && token.all(Char::isLetter))

private val SEARCH_TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

private const val PREFIX_MATCH_MIN_LENGTH = 5

private val LOW_SIGNAL_TOKENS = setOf(
    "a",
    "an",
    "and",
    "are",
    "at",
    "be",
    "for",
    "from",
    "how",
    "in",
    "is",
    "it",
    "of",
    "on",
    "or",
    "please",
    "that",
    "the",
    "this",
    "to",
    "what",
    "when",
    "where",
    "which",
    "with",
    "you",
    "your",
    "а",
    "в",
    "и",
    "к",
    "о",
    "об",
    "по",
    "с",
    "у",
    "что",
    "это",
    "как",
    "какой",
    "какая",
    "какие",
    "каким",
    "тебя",
    "меня",
    "есть"
)

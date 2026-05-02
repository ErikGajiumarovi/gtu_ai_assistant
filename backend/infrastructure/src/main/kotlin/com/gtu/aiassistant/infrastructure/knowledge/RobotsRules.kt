package com.gtu.aiassistant.infrastructure.knowledge

import java.net.URI

class RobotsRules private constructor(
    private val allows: List<String>,
    private val disallows: List<String>
) {
    fun isAllowed(url: String): Boolean {
        val pathAndQuery = runCatching {
            val uri = URI(url)
            buildString {
                append(uri.path.ifBlank { "/" })
                if (!uri.query.isNullOrBlank()) {
                    append('?')
                    append(uri.query)
                }
            }
        }.getOrDefault("/")

        val bestAllow = allows.filter { it.matchesRobots(pathAndQuery) }.maxByOrNull { it.length }
        val bestDisallow = disallows.filter { it.matchesRobots(pathAndQuery) }.maxByOrNull { it.length }

        return when {
            bestDisallow == null -> true
            bestAllow == null -> false
            else -> bestAllow.length >= bestDisallow.length
        }
    }

    companion object {
        fun parse(raw: String): RobotsRules {
            val allows = mutableListOf<String>()
            val disallows = mutableListOf<String>()
            var appliesToAll = false

            raw.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotBlank() }
                .forEach { line ->
                    val key = line.substringBefore(':').trim().lowercase()
                    val value = line.substringAfter(':', "").trim()

                    when (key) {
                        "user-agent" -> appliesToAll = value == "*"
                        "allow" -> if (appliesToAll && value.isNotBlank()) allows += value
                        "disallow" -> if (appliesToAll && value.isNotBlank()) disallows += value
                    }
                }

            return RobotsRules(
                allows = allows,
                disallows = disallows
            )
        }

        fun allowAll(): RobotsRules = RobotsRules(emptyList(), emptyList())
    }
}

private fun String.matchesRobots(pathAndQuery: String): Boolean {
    val regex = Regex(
        pattern = buildString {
            append("^")
            this@matchesRobots.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '$' -> append("\\z")
                    else -> append(Regex.escape(char.toString()))
                }
            }
        }
    )

    return regex.containsMatchIn(pathAndQuery)
}

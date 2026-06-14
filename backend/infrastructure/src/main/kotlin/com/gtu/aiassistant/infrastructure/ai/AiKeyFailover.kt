package com.gtu.aiassistant.infrastructure.ai

internal fun Throwable.isAiKeyFailoverError(): Boolean =
    generateSequence(this) { it.cause }
        .map { it.message.orEmpty() }
        .any { message ->
            message.contains("Status code: 429") ||
                message.contains("status code 429", ignoreCase = true) ||
                message.contains("was 429") ||
                message.contains("Status code: 401") ||
                message.contains("status code 401", ignoreCase = true) ||
                message.contains("was 401")
        }

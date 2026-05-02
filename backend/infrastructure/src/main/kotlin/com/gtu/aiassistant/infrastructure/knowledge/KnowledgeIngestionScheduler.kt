package com.gtu.aiassistant.infrastructure.knowledge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

class KnowledgeIngestionScheduler(
    private val config: KnowledgeIngestionConfig,
    private val ingestionService: KnowledgeIngestionService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!config.enabled || !config.schedulerEnabled) return

        scope.launch {
            if (config.ingestOnStartup) {
                ingestionService.ingestOnce()
            }

            while (isActive) {
                delay(delayUntilNextRunMillis())
                ingestionService.ingestOnce()
            }
        }
    }

    private fun delayUntilNextRunMillis(): Long {
        val now = ZonedDateTime.now(config.zoneId)
        var nextRun = now
            .withHour(config.refreshHour)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }

        return Duration.between(now, nextRun).toMillis().coerceAtLeast(1_000L)
    }
}

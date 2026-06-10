package com.gtu.aiassistant.app.materials

import com.gtu.aiassistant.application.materials.MaterialIngestionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

class MaterialIngestionScheduler(
    private val config: MaterialIngestionSchedulerConfig,
    private val worker: MaterialIngestionWorker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!config.enabled) return

        scope.launch {
            while (isActive) {
                worker.processOnce()
                delay(config.interval)
            }
        }
    }
}

data class MaterialIngestionSchedulerConfig(
    val enabled: Boolean,
    val interval: Duration
)

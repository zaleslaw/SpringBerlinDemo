package com.zaleslaw.berlinprotesttracker.importjob

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DemonstrationImportScheduler(
    private val importService: DemonstrationImportService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    @Async("importExecutor")
    fun onStartup() {
        log.info("Application ready — triggering startup import")
        when (val result = importService.runImport()) {
            is ImportResult.Success -> log.info("Startup import succeeded: {} events", result.eventCount)
            is ImportResult.Failure -> log.warn("Startup import failed: {}", result.reason)
            ImportResult.AlreadyRunning -> log.info("Startup import skipped: already running")
        }
    }

    @Scheduled(cron = "\${app.import-job.cron}")
    @Async("importExecutor")
    fun scheduledImport() {
        log.info("Scheduled import triggered")
        when (val result = importService.runImport()) {
            is ImportResult.Success -> log.info("Scheduled import succeeded: {} events", result.eventCount)
            is ImportResult.Failure -> log.warn("Scheduled import failed: {}", result.reason)
            ImportResult.AlreadyRunning -> log.info("Scheduled import skipped: already running")
        }
    }
}

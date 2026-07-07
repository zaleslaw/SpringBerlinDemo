package com.zaleslaw.berlinprotesttracker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AsyncConfig {

    /**
     * Dedicated bounded executor for imports. Without an explicit executor, @Async falls back to
     * an unbounded SimpleAsyncTaskExecutor with unnamed threads. Imports are long-running and
     * serialized by the AtomicBoolean guard in DemonstrationImportService, so a tiny pool is enough.
     */
    @Bean("importExecutor")
    fun importExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 2
        queueCapacity = 10
        setThreadNamePrefix("import-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
        initialize()
    }
}

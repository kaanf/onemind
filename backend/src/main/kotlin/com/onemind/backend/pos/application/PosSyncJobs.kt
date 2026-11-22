package com.onemind.backend.pos.application

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Runs [RunPosSync] every two minutes (STORYOUS.md: polling only, ~2 min target). */
@Configuration(proxyBeanMethods = false)
class PosSyncJobs {
    @Bean
    fun posSyncTask(sync: RunPosSync): RecurringTask<Void> =
        Tasks
            .recurring(TASK, FixedDelay.of(Duration.ofMinutes(2)))
            .execute { _, _ -> sync.run() }

    companion object {
        const val TASK = "pos-sync"
    }
}

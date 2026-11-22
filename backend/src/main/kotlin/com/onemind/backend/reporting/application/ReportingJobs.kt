package com.onemind.backend.reporting.application

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Runs [RebuildRollups] every hour. */
@Configuration(proxyBeanMethods = false)
class ReportingJobs {
    @Bean
    fun rebuildRollupsTask(rebuild: RebuildRollups): RecurringTask<Void> =
        Tasks
            .recurring(TASK, FixedDelay.of(Duration.ofHours(1)))
            .execute { _, _ -> rebuild.run() }

    companion object {
        const val TASK = "reporting-rebuild"
    }
}

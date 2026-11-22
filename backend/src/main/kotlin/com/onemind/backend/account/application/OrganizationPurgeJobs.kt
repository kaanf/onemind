package com.onemind.backend.account.application

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Runs [PurgeDueOrganizations] every hour (DB_SCHEMA `account`, account deletion). */
@Configuration(proxyBeanMethods = false)
class OrganizationPurgeJobs {
    @Bean
    fun purgeDueOrganizationsTask(purge: PurgeDueOrganizations): RecurringTask<Void> =
        Tasks
            .recurring(PurgeDueOrganizations.TASK, FixedDelay.of(Duration.ofHours(1)))
            .execute { _, _ -> purge.run() }
}

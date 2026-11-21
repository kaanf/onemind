package com.onemind.backend.common.events

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.modulith.events.CompletedEventPublications
import org.springframework.modulith.events.FailedEventPublications
import org.springframework.modulith.events.ResubmissionOptions
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Maintenance of the event publication registry (ADR 0009, DB_SCHEMA `platform.event_publication`).
 * Failed publications are resubmitted; listeners are idempotent, so a repeated delivery is safe.
 */
@Component
class EventPublicationMaintenance(
    private val completed: CompletedEventPublications,
    private val failed: FailedEventPublications,
) {
    fun purgeCompleted() = completed.deletePublicationsOlderThan(COMPLETED_RETENTION)

    fun resubmitFailed() = failed.resubmit(ResubmissionOptions.defaults().withMinAge(RESUBMIT_AFTER))

    companion object {
        val COMPLETED_RETENTION: Duration = Duration.ofDays(7)
        val RESUBMIT_AFTER: Duration = Duration.ofMinutes(10)
    }
}

@Configuration(proxyBeanMethods = false)
class EventPublicationJobs {
    @Bean
    fun purgeCompletedEventPublications(maintenance: EventPublicationMaintenance): RecurringTask<Void> =
        Tasks
            .recurring(PURGE_TASK, FixedDelay.of(Duration.ofHours(1)))
            .execute { _, _ -> maintenance.purgeCompleted() }

    @Bean
    fun resubmitFailedEventPublications(maintenance: EventPublicationMaintenance): RecurringTask<Void> =
        Tasks
            .recurring(RESUBMIT_TASK, FixedDelay.of(Duration.ofMinutes(5)))
            .execute { _, _ -> maintenance.resubmitFailed() }

    companion object {
        const val PURGE_TASK = "purge-completed-event-publications"
        const val RESUBMIT_TASK = "resubmit-failed-event-publications"
    }
}

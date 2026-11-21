package com.onemind.backend.common.idempotency

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import com.onemind.backend.common.idempotency.infra.persistence.IdempotencyRecordRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

/** Deletes `platform.idempotency_record` rows older than their 24 h lifetime. */
@Configuration(proxyBeanMethods = false)
class IdempotencyJobs {
    @Bean
    fun purgeExpiredIdempotencyRecords(
        records: IdempotencyRecordRepository,
        clock: Clock,
    ): RecurringTask<Void> =
        Tasks
            .recurring(PURGE_TASK, FixedDelay.of(Duration.ofHours(1)))
            .execute { _, _ -> records.deleteExpired(clock.instant()) }

    companion object {
        const val PURGE_TASK = "purge-expired-idempotency-records"
    }
}

package com.onemind.backend.account.infra

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

/** Deletes sign-in codes older than a day and expired refresh tokens (DB_SCHEMA `account`). */
@Configuration(proxyBeanMethods = false)
class AuthJobs {
    @Bean
    fun purgeAuthRecords(
        codes: EmailLoginCodeRepository,
        refreshTokens: RefreshTokenRepository,
        clock: Clock,
    ): RecurringTask<Void> =
        Tasks
            .recurring(PURGE_TASK, FixedDelay.of(Duration.ofHours(1)))
            .execute { _, _ ->
                val now = clock.instant()
                codes.deleteCreatedBefore(now - CODE_RETENTION)
                refreshTokens.deleteExpiredBefore(now)
            }

    companion object {
        const val PURGE_TASK = "purge-auth-records"
        val CODE_RETENTION: Duration = Duration.ofDays(1)
    }
}

package com.onemind.backend.common.events

import com.onemind.backend.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class, EventPublicationTests.ListenerConfig::class)
@SpringBootTest
class EventPublicationTests {
    data class SomethingHappened(
        val id: UUID,
    )

    // open: the listener is proxied (async + transactional), and kotlin-spring only opens annotated classes.
    open class Listener {
        open val received = CopyOnWriteArrayList<SomethingHappened>()

        @ApplicationModuleListener
        open fun on(event: SomethingHappened) {
            received += event
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ListenerConfig {
        @Bean
        fun listener() = Listener()
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var events: ApplicationEventPublisher

    @Autowired
    lateinit var transactions: TransactionTemplate

    @Autowired
    lateinit var listener: Listener

    @Autowired
    lateinit var maintenance: EventPublicationMaintenance

    @BeforeEach
    fun clearRegistry() {
        jdbc.update("delete from platform.event_publication")
    }

    @Test
    fun `an event published in a transaction is stored in platform and completed after delivery`() {
        val event = SomethingHappened(UUID.randomUUID())

        transactions.executeWithoutResult { events.publishEvent(event) }

        awaitTrue { listener.received.contains(event) }
        awaitTrue {
            jdbc.queryForObject(
                "select count(*) from platform.event_publication where completion_date is not null and serialized_event like ?",
                Int::class.java,
                "%${event.id}%",
            ) == 1
        }
    }

    @Test
    fun `purge removes completed publications older than the retention and keeps the rest`() {
        val now = Instant.now()
        val oldCompleted = insertPublication(completedAt = now.minus(8, ChronoUnit.DAYS))
        val recentCompleted = insertPublication(completedAt = now.minus(1, ChronoUnit.DAYS))
        val oldIncomplete = insertPublication(completedAt = null, publishedAt = now.minus(8, ChronoUnit.DAYS))

        maintenance.purgeCompleted()

        val remaining = jdbc.queryForList("select id from platform.event_publication", UUID::class.java).toSet()
        assertEquals(setOf(recentCompleted, oldIncomplete), remaining)
        assertTrue(oldCompleted !in remaining)
    }

    @Test
    fun `maintenance jobs are scheduled in platform scheduled_tasks`() {
        awaitTrue {
            jdbc
                .queryForList("select task_name from platform.scheduled_tasks", String::class.java)
                .containsAll(listOf(EventPublicationJobs.PURGE_TASK, EventPublicationJobs.RESUBMIT_TASK))
        }
    }

    private fun insertPublication(
        completedAt: Instant?,
        publishedAt: Instant = completedAt ?: Instant.now(),
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            insert into platform.event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date, status)
            values (?, 'listener', 'event', '{}', ?, ?, ?)
            """.trimIndent(),
            id,
            java.sql.Timestamp.from(publishedAt),
            completedAt?.let { java.sql.Timestamp.from(it) },
            if (completedAt == null) "PUBLISHED" else "COMPLETED",
        )
        return id
    }

    private fun awaitTrue(
        timeout: Duration = Duration.ofSeconds(10),
        condition: () -> Boolean,
    ) {
        val deadline = Instant.now().plus(timeout)
        while (!condition()) {
            check(Instant.now().isBefore(deadline)) { "Condition not met within $timeout" }
            Thread.sleep(50)
        }
    }
}

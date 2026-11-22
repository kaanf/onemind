package com.onemind.backend.common.lock

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.lock.infra.persistence.advisoryXactLock
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * [advisoryXactLock] takes exactly the lock `pg_advisory_xact_lock(hashtextextended(key, 0))` takes, the
 * statement the auth repositories ran by hand before, so moving a lock to the helper keeps its lock ID.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AdvisoryLockTests {
    @Autowired
    lateinit var dsl: DSLContext

    @Autowired
    lateinit var transactions: TransactionTemplate

    @Test
    fun `the helper locks the same ID as the hand-written statement and holds it until the transaction ends`() {
        val keys =
            listOf(
                "email-login-code:owner@example.com",
                "refresh-token-family:${UUID.randomUUID()}",
                "pos-connection:${UUID.randomUUID()}",
                "rollup:${UUID.randomUUID()}:2026-11-20",
            )
        for (key in keys) {
            val (held, expected) =
                requireNotNull(
                    transactions.execute {
                        dsl.advisoryXactLock(key)
                        // An advisory lock on a bigint shows as classid (high 32 bits) and objid (low 32 bits).
                        val held =
                            dsl.fetchValue(
                                "select (classid::bigint << 32) | objid::bigint from pg_locks " +
                                    "where locktype = 'advisory' and objsubid = 1 and pid = pg_backend_pid()",
                            ) as Long
                        held to (dsl.fetchValue("select hashtextextended(?, 0)", key) as Long)
                    },
                )
            assertEquals(expected, held, key)
        }
        val left = dsl.fetchValue("select count(*) from pg_locks where locktype = 'advisory' and pid = pg_backend_pid()") as Long
        assertEquals(0L, left)
    }
}

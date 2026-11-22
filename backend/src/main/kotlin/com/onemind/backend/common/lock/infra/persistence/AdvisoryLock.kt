package com.onemind.backend.common.lock.infra.persistence

import org.jooq.DSLContext
import org.jooq.impl.DSL.function
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.value
import org.jooq.impl.SQLDataType

/**
 * Takes a PostgreSQL advisory lock on [key] until the current transaction ends
 * (`pg_advisory_xact_lock(hashtextextended(key, 0))`), so callers holding the same key run one after another.
 * A hash collision between two keys only makes them wait for each other.
 */
fun DSLContext.advisoryXactLock(key: String) {
    select(
        function("pg_advisory_xact_lock", SQLDataType.OTHER, function("hashtextextended", SQLDataType.BIGINT, value(key), inline(0L))),
    ).fetch()
}

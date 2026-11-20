# 0002 · PostgreSQL + Flyway + jOOQ (no JPA)

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The backend is mainly a **reporting** system over POS data:

- Reports need heavy SQL: `GROUP BY` per hour, weekday × hour averages over 8 weeks, percentiles
  (*usual deleted items*), comparisons with the same time last week, window functions,
  `generate_series`. JPQL can't express most of this, so with JPA we would write untyped native
  SQL strings anyway.
- The POS sync bulk-writes tens of thousands of bills and items (90 days of history) with
  `INSERT … ON CONFLICT DO UPDATE`. JPA's persistence context and dirty checking get in the way,
  and JPA has no native upsert.
- Stock movements are append-only, so JPA's entity lifecycle adds nothing there.
- JPA entities fit Kotlin poorly (open classes, no-arg constructors, mutable fields, lazy loading,
  N+1 queries).

## Decision

- **PostgreSQL** (16+) is the only database. *pgvector* for embeddings later (separate decision).
- **Flyway** migrations in `backend/src/main/resources/db/migration` are the source of truth for
  the schema.
- **jOOQ** (open-source edition, free for PostgreSQL) for all data access, through
  `spring-boot-starter-jooq` and Spring transactions (`@Transactional`).
- **No JPA / Hibernate** dependency in the project.

### Code generation

- jOOQ classes are generated from the **real schema**: the build starts PostgreSQL with
  Testcontainers, runs the Flyway migrations, then runs jOOQ codegen. Implemented with the
  `dev.monosoul.jooq-docker` Gradle plugin (task `generateJooqClasses`, `KotlinGenerator`); jOOQ and
  Flyway versions on its classpath come from the Spring Boot BOM.
- Generated code goes to `build/generated-src/jooq`, not committed.
- Building the backend therefore needs **Docker**, locally and in CI.

### Usage rules

- jOOQ `Record` types stay inside `<context>/infra/`. Repositories map them to domain objects and
  return those. Controllers and domain code never see jOOQ types.
- Each context queries only its own tables. A cross-context read goes through that context's
  application service, or a dedicated read-model query owned by the reporting context.
- Bulk sync writes use `insertInto(...).onConflict(...).doUpdate()` with batching.
- Money columns are `bigint` minor units. Timestamps are `timestamptz`, generated as `Instant`.
  `currency char(3)` columns are generated as `java.util.Currency` (`common/money/CurrencyConverter`);
  repositories build `common/money/Money` from the amount and currency columns (an amount and its
  currency are two columns, so a single-column converter can't produce `Money`).
- `citext` and `inet` use the bindings from `jooq-postgres-extensions`, so citext comparisons stay
  case-insensitive.

### Report aggregation

- **Hourly rollup tables** (for example `sales_hourly`, `product_sales_hourly`,
  `staff_shift_stats`) keyed by venue, business day and hour. They are refreshed incrementally
  after each sync batch for the affected business days. The reporting endpoints read from them.
- "Now" values (open tabs, occupancy, on shift) are queried live from the base tables.
- Rollups can always be rebuilt from the base tables. A rebuild job exists for when a metric
  definition changes.

## Alternatives considered

- **JPA/Hibernate, with native SQL for reports:** familiar, but two data-access styles, untyped SQL
  strings for the most important queries, and friction for bulk upserts.
- **Spring Data JDBC, with SQL for reports:** simpler than JPA, but reports would still be untyped
  SQL.
- **Exposed (JetBrains):** Kotlin-native, but weaker support for advanced PostgreSQL features and
  fewer projects use it.

## Consequences

- Type-safe SQL: a wrong column name or type is a compile error after a migration.
- Every PostgreSQL feature is available (window functions, `ON CONFLICT`, JSONB, CTEs).
- More explicit code (mapping records to domain objects by hand). That is accepted.
- The build needs Docker for codegen and integration tests.

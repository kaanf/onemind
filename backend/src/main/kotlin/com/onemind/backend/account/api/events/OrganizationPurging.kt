package com.onemind.backend.account.api.events

import java.util.UUID

/**
 * An organization past its `deletion_due_at` is being deleted with all its data
 * (`docs/architecture/DB_SCHEMA.md`, account deletion). Unlike the other events this one is
 * **synchronous** (ADR 0013, events): it is published inside the purge transaction, before the venues
 * and the organization are deleted. Every module that stores venue- or organization-scoped data
 * listens with a plain `@EventListener` (not `@ApplicationModuleListener`) and deletes all of it for
 * [organizationId] and [venueIds]. A failing listener rolls the whole organization back; the job tries
 * again an hour later, so listeners delete by key and are idempotent. Listeners run in no fixed order:
 * a listener whose rows another module's rows reference (a future `stock` listener, for example)
 * declares its place with `@Order`.
 */
data class OrganizationPurging(
    val organizationId: UUID,
    val venueIds: List<UUID>,
)

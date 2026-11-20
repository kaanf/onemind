# 0001 · OpenAPI contract-first, no shared Kotlin module

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

Backend (Kotlin/JVM, Spring Boot) and mobile (Kotlin Multiplatform) exchange the same data. Both
are Kotlin, so a shared `contract` module with DTO classes was an option. But the mobile app ships
through app stores, and old versions keep running on users' phones for months. A shared module
makes both sides compile after a breaking change, which hides the risk for installed apps. It
would also tie the backend build to the KMP Gradle plugin and force kotlinx.serialization on the
backend.

## Decision

The API is defined **contract-first** in an OpenAPI 3.1 document at `docs/api/openapi.yaml`. That
file is the single source of truth. Both sides generate code from it. There is **no shared Kotlin
module** between `backend/` and `mobile/`.

- **Backend:** OpenAPI Generator (Gradle plugin), generator `kotlin-spring`, options
  `interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`. It generates controller
  interfaces and DTOs with Jackson. Our `@RestController` classes implement the generated
  interfaces. Generated sources go to `build/generated`, never committed.
- **Mobile:** OpenAPI Generator, generator `kotlin`, `library=multiplatform` (Ktor +
  kotlinx.serialization), into `build/generated` of the shared module. If the generated client is
  not good enough, generate **models only** and write the Ktor calls by hand in
  `feature/*/data`. Generated DTOs stay in the data layer and are mapped to app models.
- Change workflow: edit `openapi.yaml` first, then backend and mobile in the same PR.

### Compatibility rules (API v1)

- Base path `/api/v1`. Inside v1, changes are **additive only**: new endpoints, new optional
  fields, new enum values.
- Never rename or remove a field, change its type, or make an optional field required in v1. Do
  that in a new version or a new field.
- Mobile JSON parsing uses `ignoreUnknownKeys = true` and treats unknown enum values as an
  `UNKNOWN` fallback.
- CI runs a breaking-change check (`oasdiff breaking`) against the main branch's spec, and fails on
  breaking changes.
- The app sends `X-App-Version` and `X-Platform`. The backend can answer `426 Upgrade Required`
  below a minimum supported version.
- Spec is linted in CI (e.g. Redocly or Spectral).

## Alternatives considered

- **Shared KMP `contract` module:** one definition, compile-time safety across both sides. Rejected:
  hides breaking changes for installed apps, couples the builds, forces kotlinx.serialization on
  Spring.
- **Hand-written DTOs on both sides:** least setup. Rejected: mismatches show up only at runtime,
  no API documentation.
- **Code-first (springdoc generates the spec from controllers):** less YAML. Rejected: the
  contract follows implementation details, and mobile work can't start before the backend exists.

## Consequences

- API changes start with a spec edit, which also gives us API documentation for free.
- Mobile can build against the spec with a mock server before the backend endpoint exists.
- Some generator setup and occasional generator quirks to manage.
- `docs/api/README.md` keeps conventions; endpoint shapes live in `openapi.yaml`.

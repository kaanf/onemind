# 0011 · Mobile architecture: crew-kmp structure with generated API models

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The mobile app is Kotlin Multiplatform with shared Compose UI (ADR 0003). The owner already
maintains a production KMP app, **crew-kmp** (`~/Projects/crew/crew-kmp`), with a proven,
documented structure: feature × layer Gradle modules, convention plugins, ViewModel +
State/Action/Event, Koin, typed Navigation Compose, Ktor, DataStore. Using the same patterns in
both projects lowers cognitive load for the owner and for AI agents.

JetBrains' 2026 default (`sharedLogic` + `sharedUI`) was considered. crew-kmp's structure solves the
iOS constraint the default addresses (one framework) by exporting a single `ComposeApp`
framework from `composeApp`, and adds compile-time layer boundaries through modules.

One difference: crew-kmp writes DTOs by hand; OneMind's API is contract-first (ADR 0001).

## Decision

### Modules

```
mobile/
├── build-logic/                 ← convention plugins (start from crew-kmp's)
├── core/
│   ├── api/                     ← GENERATED DTOs from docs/api/openapi.yaml (models only, not committed)
│   ├── data/                    ← Ktor HttpClient, auth/session refresh, error mapping, ETag, DataStore, secure storage
│   ├── domain/                  ← shared domain models (Money, Quantity, VenueScope, …), Result/error types, interfaces
│   ├── presentation/            ← shared UI helpers (ObserveAsEvents, formatters for Money/dates, UiText)
│   └── designsystem/            ← tokens, typography, components, charts (docs/design/DESIGN_SYSTEM.md)
├── feature/
│   ├── auth/{domain,data,presentation}       ← A1–A5
│   ├── today/{domain,data,presentation}      ← 01, 01a (tables)
│   ├── sales/{domain,data,presentation}      ← 02, 02a–02c
│   └── staff/{domain,data,presentation}      ← 03, 03a
│   (later: stock, assistant)
├── composeApp/                  ← composes everything; navigation root; Koin setup; exports the iOS framework
├── androidApp/                  ← Android application (thin host)
└── iosApp/                      ← Xcode project, SwiftUI host (thin)
```

Packages: `com.onemind.<feature|core>.<layer>` (e.g. `com.onemind.sales.presentation`);
application ID and bundle ID stay `com.onemind.app`.

### Layer rules (as in crew-kmp)

- `domain` modules have **no** dependency on data, presentation, Ktor, DataStore, Koin or
  `core:api`. Repository interfaces live in domain; implementations in data.
- `data` implements repositories with the shared Ktor client from `core:data`, uses DTOs from
  `core:api` and maps them to domain models. **DTOs never leave data.**
- `presentation` depends on its domain and on `core:presentation` / `core:designsystem`.
- Use cases only where they add logic (most screens are thin: the backend computes metrics).

### API models (difference from crew-kmp)

- `core:api` is generated from `docs/api/openapi.yaml` with OpenAPI Generator (`kotlin`,
  kotlinx.serialization, **models only**). Never edit or commit generated code.
- Requests (method, path, headers, idempotency key) are written by hand in feature data
  repositories, using crew's networking helpers. We don't use a generated client, so it can't
  clash with the session refresh, diagnostics and error mapping in `core:data`.
- JSON: `ignoreUnknownKeys = true`; unknown enum values map to `UNKNOWN` (ADR 0001, 0010).

### Presentation

- Per screen: `XxxViewModel` (androidx multiplatform ViewModel), immutable `XxxState` data class
  exposed as `StateFlow`, sealed `XxxAction` (user intents in), sealed `XxxEvent` (one-off
  effects out) through a `Channel`, collected with `ObserveAsEvents` on the main dispatcher
  (`Dispatchers.Main.immediate`), lifecycle-aware.
- `XxxRoot` composable resolves the ViewModel (Koin) and collects state; `XxxScreen` is stateless
  (`state`, `onAction`) with previews.
- Every state models loading, content, empty, error and **cached/offline** (`dataAsOf`, ADR 0006).

### Navigation and DI

- Navigation Compose with typed, serializable routes; one graph per feature; the root in
  `composeApp` owns cross-feature transitions and the tab bar.
- Koin modules per feature layer, assembled in `composeApp`; platform bindings via expect/actual.
  A Koin graph verification test runs in CI.

### Data and platform

- Local storage: DataStore for settings and the response cache; platform secure storage
  (Keychain / Keystore) for tokens. A database (Room, via crew's convention plugin) only when
  offline stock counting arrives (ADR 0006).
- Observability: native Sentry SDKs initialised in the Android and iOS hosts, with crew's bridge
  pattern for shared networking diagnostics (ADR 0008).
- Polling and ETag per ADR 0005; errors by `code` with English `detail` fallback (ADR 0010).

## Alternatives considered

- **JetBrains default `sharedLogic` + `sharedUI`:** fewer modules, but no compile-time layer
  boundaries and a different structure from the owner's other app.
- **Full generated Ktor client:** less handwritten request code, but its own networking layer
  would duplicate/clash with crew's session refresh and diagnostics; multiplatform generator
  quality uncertain.
- **Navigation 3:** newer back-stack-as-state model; multiplatform maturity unclear, and crew
  already uses Navigation Compose.
- **Events as state (Google guidance):** valid; Channel + `ObserveAsEvents` kept for consistency
  with crew, with collection on `Main.immediate` to avoid lost events.

## Consequences

- More Gradle modules from day one, kept manageable by convention plugins.
- Contract changes surface as compile errors in mobile data modules after regenerating
  `core:api`.
- `mobile/AGENTS.md` follows crew-kmp's patterns; agents can use crew-kmp as a reference
  implementation for structure (not for product code).

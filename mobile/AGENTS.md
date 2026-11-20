# mobile/AGENTS.md

Kotlin Multiplatform app (Android + iOS) for OneMind. Read the root [`../AGENTS.md`](../AGENTS.md)
first.

> **Status:** the project is not scaffolded yet. Items marked *Proposed* follow
> `docs/architecture/README.md` and may change after the architecture discussion (notably
> Compose Multiplatform vs native iOS UI). If you scaffold the project, follow this file and update
> it with the real commands.

## Responsibilities

- Render the screens in `docs/product/SCREENS.md` with the design system in
  `docs/design/DESIGN_SYSTEM.md`.
- Call the backend API (`docs/api/`). **No business calculations in the app**: metrics, order-plan
  quantities and insights come from the backend. The app formats and displays them.
- Auth UI (Apple, Google, email OTP), secure token storage, Storyous connect hand-off (system
  browser / auth session + deep link).
- Input flows: stock count, pull list, invoice photo capture, corrections, chat, document upload.

## Stack

Decided: Compose Multiplatform ([ADR 0003](../docs/architecture/adr/0003_COMPOSE_MULTIPLATFORM_UI.md)),
own JWT auth ([ADR 0004](../docs/architecture/adr/0004_OWN_JWT_AUTH.md)), polling + SSE
([ADR 0005](../docs/architecture/adr/0005_POLLING_AND_SSE.md)), offline strategy
([ADR 0006](../docs/architecture/adr/0006_OFFLINE_STRATEGY.md)). Other items are proposed.

- Kotlin Multiplatform, targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`.
- **Compose Multiplatform** for shared UI; iOS hosts it via `ComposeUIViewController`.
- Coroutines + `StateFlow`; kotlinx.serialization; kotlinx-datetime.
- Ktor client (OkHttp engine on Android, Darwin on iOS).
- API client and DTOs **generated from `docs/api/openapi.yaml`** with OpenAPI Generator
  (`kotlin`, `library=multiplatform`) into `build/generated`
  ([ADR 0001](../docs/architecture/adr/0001_OPENAPI_CONTRACT_FIRST.md)). Fallback if the client
  output is poor: generate models only and write the Ktor calls by hand. There is no shared Kotlin
  module with the backend.
- Koin for DI.
- Compose Navigation (multiplatform).
- Compose resources for fonts (Inter, Inter Tight), strings, icons.
- Secure storage: Android Keystore / EncryptedSharedPreferences, iOS Keychain (expect/actual or a
  library).
- Local DB: **SQLDelight** (response cache, count sheets, outbox).
- Ktor SSE client for streamed assistant answers.
- Testing: kotlin.test, Turbine (flows), Ktor MockEngine; Compose UI tests on Android.

## Layout (proposed)

```
mobile/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── composeApp/                       ← shared KMP module (UI + presentation + data)
│   └── src/
│       ├── commonMain/kotlin/com/onemind/app/
│       │   ├── app/                  ← App(), navigation graph, root DI
│       │   ├── core/
│       │   │   ├── designsystem/     ← theme, tokens, typography, components
│       │   │   ├── network/          ← Ktor client, auth/refresh, ETag, error mapping, SSE
│       │   │   ├── database/         ← SQLDelight schema, response cache
│       │   │   ├── sync/             ← outbox, retry, idempotency keys (offline counts)
│       │   │   ├── model/            ← Money, Delta, Period, VenueScope …
│       │   │   ├── format/           ← money/number/date formatters (cs-CZ / en)
│       │   │   └── session/          ← auth tokens, current user, selected venue
│       │   └── feature/
│       │       ├── auth/             ← A1–A5
│       │       ├── today/            ← 01, 01a tables
│       │       ├── sales/            ← 02, 02a, 02b, 02c
│       │       ├── staff/            ← 03, 03a
│       │       ├── stock/            ← 04, S1–S7
│       │       └── assistant/        ← 05, 05a
│       ├── androidMain/
│       ├── iosMain/
│       └── commonTest/
├── androidApp/                       ← Android application shell (if split from composeApp)
└── iosApp/                           ← Xcode project, SwiftUI entry hosting Compose
```

Each feature package:

```
feature/<name>/
├── data/          ← repository implementation using the generated API client, mappers DTO → app models
├── domain/        ← feature models (only if needed), repository interfaces
└── presentation/  ← <Screen>Screen.kt (stateless composable), <Screen>ViewModel.kt, <Screen>UiState.kt
```

## Architecture rules

- **Unidirectional data flow.** ViewModel exposes `StateFlow<UiState>`; the screen sends events
  (`onPeriodSelected(Period.Week)`). One immutable `UiState` per screen, with explicit
  `Loading | Content | Empty | Error` handling, plus a **cached/offline** flag with the
  `dataAsOf` time (ADR 0006).
- **Polling (ADR 0005):** only while the screen is visible and the app is in the foreground
  (tie it to the lifecycle, e.g. `repeatOnLifecycle`-style). Today 30 s (5 min when the venue is
  closed), Staff/Tables 60 s, sync status 2 s. Send `If-None-Match`; treat `304` as "unchanged".
  Never poll in the background.
- **Offline (ADR 0006):** every read goes cache-first then network. The only offline write flow is
  the stock count: save locally, enqueue in the outbox with an `Idempotency-Key`, sync when online,
  show pending count in the UI. Don't add offline writes to other flows without a new ADR.
- **Auth (ADR 0004):** tokens only in Keychain/Keystore-backed storage, never in plain settings or
  logs. On `401`, refresh once (single-flight), retry, otherwise log out.
- Screen composables are **stateless**: `@Composable fun SalesScreen(state, onEvent)`. A separate
  `SalesRoute` wires the ViewModel. This keeps previews and tests easy.
- No platform code in `commonMain`. Use `expect/actual` or interfaces injected from platform
  modules (camera, secure storage, auth sessions, push).
- Selected venue (`VenueScope`) and session live in `core/session` and are observed by features.
- Repositories return domain models or `Result`-like types, not DTOs.
- Generated DTOs stay in `data/`; never edit generated code. A missing field means a spec change in
  `docs/api/openapi.yaml` first (backend and mobile in the same PR).
- Follow `docs/api/README.md` ([ADR 0010](../docs/architecture/adr/0010_API_DESIGN.md)). Errors:
  switch on `code` and show a localised string; if the code is unknown, show the server's English
  `detail`. Hint cards with an unknown `code` are hidden. Money arrives as minor units, quantities and
  rates as decimal strings: never parse them into `Double`.
- Send an `Idempotency-Key` (UUID) on creating POSTs and reuse it on retries of the same action.
- Be tolerant of API evolution: JSON uses `ignoreUnknownKeys = true`; unknown enum values map to an
  `UNKNOWN` fallback; the client sends `X-App-Version` and `X-Platform` and handles
  `426 Upgrade Required` with a "please update" screen.
- During development, screens can be built against a mock server generated from the spec before the
  backend endpoint exists.

## UI rules

- Use only design-system tokens and components from `core/designsystem`. **No hard-coded colors,
  sizes or font sizes in feature code.** If a token is missing, add it to the design system and to
  `docs/design/DESIGN_SYSTEM.md`.
- Match the Cross-platform design: tab bar with 5 tabs, top accent bar on the active tab, cards with
  1 px border and radius 16, Inter / Inter Tight with tabular numbers.
- Touch targets ≥ 44 dp. Content descriptions on icon-only buttons. Heatmap cells and table tiles
  have spoken labels.
- All user-facing text in string resources (prepare for Czech). Copy from the design is the
  default English text.
- Money/number formatting only through `core/format` (thin-space thousands, `Kč` suffix, real
  minus sign `−`). See `docs/domain/METRICS.md#formatting-rules-shared-with-mobile`.
- Charts (bar chart, heatmap, floor plan) are custom Compose components in the design system;
  avoid heavy chart libraries unless agreed.
- Provide `@Preview`s with sample `UiState` for every screen (Content, Empty, Error).
- Never ship mock numbers in production code paths. Sample data lives only in previews/tests.

## Platform notes

- **iOS:** Apple Sign-In is required if Google sign-in is offered. Use `ASWebAuthenticationSession`
  for the Storyous connect flow. Camera permission strings for invoice scanning.
- **Android:** Credential Manager for Google sign-in; Custom Tabs for Storyous connect; CameraX
  for invoice photos.
- Deep links: see `docs/product/NAVIGATION.md#deep-links-proposed`.
- Push (FCM/APNs) for invoice approvals and stockout risk: later phase.

## Testing

- ViewModel tests in `commonTest` with fake repositories and Turbine.
- Formatter tests (money, deltas, dates, business day labels).
- API layer tests with Ktor `MockEngine` using JSON fixtures that match `docs/api/` examples.
- Screenshot/UI tests on Android for key screens (*Proposed*).

## Commands

To be filled in when the project is scaffolded. Expected:

```bash
./gradlew :composeApp:assembleDebug          # Android debug build
./gradlew :composeApp:allTests               # shared tests
./gradlew :composeApp:testDebugUnitTest
# iOS: open iosApp/iosApp.xcodeproj in Xcode and run, or
./gradlew :composeApp:iosSimulatorArm64Test
```

## Definition of done (mobile)

- Screen matches the design (spacing, tokens, copy) and handles loading, empty and error states.
- Works on Android and iOS (build both).
- ViewModel tests added; previews added.
- No hard-coded colors/strings in features; docs updated if a token, screen or flow changed.

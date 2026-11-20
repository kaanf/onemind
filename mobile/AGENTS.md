# mobile/AGENTS.md

Kotlin Multiplatform app (Android + iOS, shared Compose UI) for OneMind. Read the root
[`../AGENTS.md`](../AGENTS.md) first, then
[ADR 0011](../docs/architecture/adr/0011_MOBILE_ARCHITECTURE.md).

> **Status:** not scaffolded yet. The structure follows the owner's other KMP app,
> **crew-kmp** (`~/Projects/crew/crew-kmp`). Use it as the reference implementation for
> structure, Gradle convention plugins and patterns — not for product code or copy.
> When you scaffold, start from crew-kmp's `build-logic` and version catalog, bump versions to
> current stable, and update the Commands section below with real, verified commands.

## Responsibilities

- Render the screens in `docs/product/SCREENS.md` within the first-release scope
  (`docs/product/MVP_SCOPE.md`) using `docs/design/DESIGN_SYSTEM.md`.
- Call the backend API (`docs/api/README.md`). **No business calculations in the app**: metrics
  come from the backend; the app formats and displays them.
- Auth UI (Apple, Google, email code), secure token storage, onboarding.

## Modules

```
mobile/
├── build-logic/            ← convention plugins (KMP library, CMP library/feature, Android app, …)
├── core/
│   ├── api/                ← GENERATED DTOs from ../docs/api/openapi.yaml (models only). Never edit, never commit.
│   ├── data/               ← Ktor HttpClient, auth + session refresh, error mapping, ETag/polling helpers,
│   │                         DataStore cache, secure storage, Koin data module
│   ├── domain/             ← shared models (Money, Quantity, VenueScope, Period), Result + error types, interfaces
│   ├── presentation/       ← ObserveAsEvents, UiText, formatters (money, dates, deltas), shared UI state helpers
│   └── designsystem/       ← tokens, typography, components, charts (bar chart, heatmap, table grid)
├── feature/
│   ├── auth/{domain,data,presentation}     ← A1–A5
│   ├── today/{domain,data,presentation}    ← 01, 01a
│   ├── sales/{domain,data,presentation}    ← 02, 02a, 02b, 02c
│   └── staff/{domain,data,presentation}    ← 03, 03a
├── composeApp/             ← navigation root + tab bar, Koin setup, exports the single iOS framework
├── androidApp/             ← thin Android host (Application, MainActivity, Sentry init)
└── iosApp/                 ← thin SwiftUI host (ComposeUIViewController, Sentry init)
```

Packages: `com.onemind.<feature|core>.<layer>` (e.g. `com.onemind.sales.presentation`).
Application ID / bundle ID: `com.onemind.app` (dev builds `com.onemind.app.dev`).

## Layer rules

- **domain** depends only on `core:domain` (and coroutines for Flow contracts). No data,
  presentation, Ktor, DataStore, Koin or `core:api`. Repository interfaces live here.
- **data** implements repositories with the shared Ktor client from `core:data`, uses DTOs from
  `core:api` and maps them to domain models. **DTOs never leave data.**
- **presentation** depends on its feature domain, `core:presentation` and `core:designsystem`.
  Never call repositories from composables.
- Use cases only where they add logic. Many screens are a ViewModel calling a repository.
- Keep Android/iOS hosts thin; shared code in `commonMain`. Platform behaviour via interfaces bound
  in platform DI or `expect/actual`.

## API calls

- Contract changes start in `docs/api/openapi.yaml`; regenerate `core:api`; fix data modules.
- Write requests by hand in the feature repository (method, path, query, headers). Send an
  `Idempotency-Key` (UUID) on creating POSTs and reuse it on retries of the same action.
- JSON: `ignoreUnknownKeys = true`; unknown enum values → `UNKNOWN`.
- Errors: map RFC 9457 problems to domain errors by `code`; show a localised string; for unknown
  codes show the server's English `detail`. Hint cards with an unknown `code` are hidden.
- Money arrives as minor units, quantities and rates as decimal strings: never parse into `Double`.
- Live screens poll only while visible and in the foreground (Today 30 s, 5 min when closed;
  Staff/Tables 60 s; sync status 2 s), send `If-None-Match`, treat `304` as unchanged
  (ADR 0005). Clients send `X-App-Version` / `X-Platform`; handle `426` with an update screen.

## Presentation pattern (same as crew-kmp)

Per screen, in `feature/<x>/presentation/<screen>/`:

- `XxxState` — immutable data class (loading / content / empty / error, plus `isCached` and
  `dataAsOf` for offline data).
- `XxxAction` — sealed interface of user intents.
- `XxxEvent` — sealed interface of one-off effects (navigation, snackbar), sent through a
  `Channel` and collected with `ObserveAsEvents` (lifecycle-aware, `Dispatchers.Main.immediate`).
- `XxxViewModel` — androidx multiplatform ViewModel: `state: StateFlow<XxxState>`,
  `onAction(XxxAction)`, `events: Flow<XxxEvent>`.
- `XxxRoot` — resolves the ViewModel with Koin, collects state with
  `collectAsStateWithLifecycle`, handles events. `XxxScreen` — stateless (`state`, `onAction`),
  with `@Preview`s for content, empty and error.

## Navigation and DI

- Navigation Compose with typed `@Serializable` routes; one graph per feature
  (`XxxGraphRoutes`, `XxxGraph`). The root in `composeApp` owns the tab bar
  (Today · Sales · Staff · Stock later) and cross-feature transitions. Navigation stays out of
  business logic.
- Koin: one module per feature layer (`XxxDataModule`, `XxxPresentationModule`), assembled in
  `composeApp`; platform modules via `expect/actual`. Keep a Koin graph verification test green.

## UI rules

- Only design-system tokens and components; no hard-coded colors, sizes or font sizes in features.
  Missing token → add it to `core:designsystem` and `docs/design/DESIGN_SYSTEM.md`.
- Inter / Inter Tight bundled as Compose resources; tabular numbers.
- Touch targets ≥ 44 dp; content descriptions on icon-only buttons; heatmap cells and table tiles
  have spoken labels.
- All user-facing text in string resources (English now, Czech later).
- Values derived indirectly (e.g. estimated table capacity) are labelled as estimates.
- No mock numbers in production code paths; sample data only in previews/tests.

## Data and platform

- DataStore for settings and the read cache (last response per screen key; cleared on logout).
  Secure storage (Keychain / Keystore) for tokens — never in DataStore or logs.
- No database until offline stock counting (then Room via the convention plugin, ADR 0006).
- Sentry via native SDKs in the hosts, crew-style bridge for shared networking diagnostics.
- Auth: refresh once on `401` (single-flight), retry, else log out. Sign in with Apple required on
  iOS when Google is offered.

## Environments, text and diagnostics ([ADR 0012](../docs/architecture/adr/0012_CROSS_CUTTING_CONCERNS.md))

- Variants `dev` (`com.onemind.app.dev`) and `prod`; base URL and Sentry DSN via BuildKonfig.
- Strings in Compose resources (English; Czech before release). Plurals use CLDR categories
  (Czech: one/few/many/other) — test them.
- Money via platform formatters (`expect/actual` in `core:presentation`), by the venue's currency,
  independent of UI language. Times in the venue's time zone from the API.
- Send `X-Request-Id` per request; add it to Sentry breadcrumbs. No analytics SDK in the first
  release.

## Testing

- ViewModel tests in `commonTest` with fake repositories and Turbine.
- Data tests with Ktor `MockEngine` and JSON fixtures matching the OpenAPI examples.
- Formatter tests (money, deltas, dates, business-day labels).
- Koin graph verification test. Compose UI tests for key screens; screenshot tests later.
- Android success does not verify iOS: build/test both targets.

## Commands

To be filled in when scaffolded (copy the verified commands from crew-kmp's `docs/COMMANDS.md`
and adapt module names).

## Definition of done (mobile)

- Matches the design (tokens, spacing, copy) and handles loading, empty, error and cached states.
- Builds on Android and iOS; ViewModel tests and previews added.
- No hard-coded colors/strings in features; docs updated if a token, screen or flow changed.

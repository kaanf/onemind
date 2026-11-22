# mobile/AGENTS.md

Kotlin Multiplatform app (Android + iOS, shared Compose UI) for OneMind. Read the root
[`../AGENTS.md`](../AGENTS.md) first, then
[ADR 0011](../docs/architecture/adr/0011_MOBILE_ARCHITECTURE.md).

> **Status:** build logic, version catalog, module skeleton, `core:api` generation, design system,
> the app shell (navigation root, 4-tab bar with placeholder screens, Koin) and the `dev`/`prod`
> variants are in place; screens come next (Sentry is deferred to phase 7), see `docs/ROADMAP.md`.
> The structure follows the owner's
> other KMP app, **crew-kmp** (`~/Projects/crew/crew-kmp`). Use it as the reference implementation for structure, Gradle
> convention plugins and patterns — not for product code or copy.

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
- `core:api` bundles the split spec with Redocly (`npx`, same version as CI lint), then generates
  models with OpenAPI Generator (`kotlin`, `multiplatform`) into `build/generated/openapi`, package
  `com.onemind.core.api.model`. Free-form values (`additionalProperties: true`) are `JsonElement`.
  String enums get an `UNKNOWN` entry and a serializer that reads any value the app doesn't know as
  `UNKNOWN` (two generator templates in `core/api/templates`; every change is marked `OneMind:` and
  sits inside the `multiplatform` section; re-copy them from the generator jar on a version bump).
  Encoding `UNKNOWN` throws: it is never sent. Mappers decide per field, never showing a wrong figure:
  leave it out when it isn't drawn (an unavailable figure), keep its place empty (a chart hour), keep
  it as "other" when it is part of a total (a loss component → "and other losses"), and answer
  `DataError.Network.NEEDS_APP_UPDATE` ("update the app") when guessing could show a wrong figure (a
  data status).
- One `HttpClient` from `core:data` (`HttpClientFactory`, Koin): base `ApiConfig.baseUrl + /api/v1/`,
  so paths are relative (`client.post("auth/email/start")`). It adds `X-App-Version`, `X-Platform`
  and a fresh `X-Request-Id` to every request, the access token to every call outside `auth/...`, and
  reports `426 APP_UPGRADE_REQUIRED` to `AppUpdateRepository`. No logging plugin: never log requests,
  bodies, tokens, `AuthTokens` or `StoredSession` (its `toString()` is redacted; generated DTOs are not).
  `X-App-Version` is the store version: iOS reads `CFBundleShortVersionString` (Xcode
  `MARKETING_VERSION`) at runtime, Android uses `projectVersionName` (its `versionName`); keep the two
  equal when releasing.
- Wrap each call in `safeCall<T> { client.post(...) }`: it returns `Result<T, DataError>` (`core:domain`):
  the body on 2xx, `DataError.Api(status, code, detail, params)` for an RFC 9457 problem (params as
  strings), `DataError.Network.*` for transport failures; it never throws except on cancellation.
- Write requests by hand in the feature repository (method, path, query, headers). Send an
  `Idempotency-Key` (UUID) on creating POSTs and reuse it on retries of the same action.
- JSON: `ignoreUnknownKeys = true`; unknown enum values → `UNKNOWN`.
- Errors: map RFC 9457 problems to domain errors by `code`; show a localised string; for unknown
  codes show the server's English `detail`. Features map their own codes to `UiText` first and fall
  back to `DataError.toUiText()` (`core:presentation`: network errors, `RATE_LIMITED`, else `detail`). Hint cards with an unknown `code` are hidden.
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
  `Channel` and collected with `ObserveAsEvents` (`core:presentation`; collects only while STARTED,
  on `Dispatchers.Main.immediate`, so events sent while stopped wait instead of being lost).
- User-facing text from a ViewModel is `UiText` (`core:presentation`): `Resource(id, args)` or
  `Dynamic(value)`; composables call `asString()`.
- `XxxViewModel` — androidx multiplatform ViewModel: `state: StateFlow<XxxState>`,
  `onAction(XxxAction)`, `events: Flow<XxxEvent>`.
- `XxxRoot` — resolves the ViewModel with Koin, collects state with
  `collectAsStateWithLifecycle`, handles events. `XxxScreen` — stateless (`state`, `onAction`),
  with `@Preview`s for content, empty and error.

## Navigation and DI

- Navigation Compose with typed `@Serializable` routes; one graph per feature
  (`XxxGraphRoutes` with a `Graph` route, `NavGraphBuilder.xxxGraph()`). `NavigationRoot` in
  `composeApp` owns the tab bar (`TopLevelDestination`: Today · Sales · Staff · Stock) and
  cross-feature transitions, passed into feature graphs as callbacks. The active tab is the one whose
  graph contains the current destination, so detail screens keep their parent tab active; switching
  tabs saves and restores each tab's back stack. Navigation stays out of business logic.
- The Stock graph lives in `composeApp` until `feature:stock` exists (phase 6).
- The app root (`composeApp/App.kt`) is the auth gate: `appScreen(session, update)` picks the update
  screen (any `426`), a blank screen while the session loads, the auth graph (`authGraph`, A1 → A2)
  when signed out, or `NavigationRoot` (tabs) when signed in. Each has its own `NavController`, so a
  sign-out lands on a fresh A1 and a sign-in on Today; screens never navigate across the gate.
- Koin: one module per feature layer (`xxxDataModule`, `xxxPresentationModule`), listed in
  `appModules` (`composeApp/di/KoinHelper.kt`); platform bindings in `expect val platformModule`.
  Hosts call `initKoin` once (`OneMindApplication` on Android, `iOSApp.init` on iOS).
  `KoinGraphTest` (`composeApp/src/androidHostTest`) verifies the graph; keep it green.
- The selected venue / *All venues* is `VenueScopeRepository` (`core:domain`), a single instance
  (in memory for now; persisted with the venue switcher, P7). The venue list is `VenueRepository`
  (`GET /venues`; each load keeps the scope valid with `defaultVenueScope`), the profile
  `ProfileRepository` (`GET /me`, initials). `SignedInDataLoader` (core:data, started once by
  `initKoin`, not by Koin itself, so `KoinGraphTest` needs no Android context) loads both on sign-in and
  clears them and the scope on sign-out. Dashboards turn a scope into `venueIds` with
  `VenueScope.venueIds(venues)` (*All venues* = followed, else every venue).

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
- Session (`core:data/session`, ADR 0004): `SessionManager` is the only owner of the tokens. It
  loads `SecureSessionStore` once at start (`SessionState.Unknown` → `SignedIn`/`SignedOut`) and
  serializes every read and write behind one mutex. `SecureSessionStore`, with no third-party library:
  - Android `AndroidSecureSessionStore`: AES-256-GCM, the key generated in the Android Keystore
    (non-exportable), a random IV per write, the file in `noBackupFilesDir`;
  - iOS `IosSecureSessionStore`: a Keychain generic password,
    `AfterFirstUnlockThisDeviceOnly`;
  - an unreadable value (lost or invalidated key, tampering, unknown format) is deleted and reads as
    signed out. `StoredSession.toString()` never prints tokens.
- Features save tokens after sign-in with `SessionManager.save(StoredSession.from(tokens))`;
  presentation only sees `SessionRepository` (`state`, `signOut()`).
- `401` on an authenticated call: `SessionRefresher` runs one `auth/refresh` at a time, in the app
  scope (a cancelled caller can't drop rotated tokens); a caller whose rejected token is already
  replaced reuses the new one (a rotated refresh token is never sent twice). The original request is
  retried once. A `401` from refresh signs out; other failures keep the session. Calls under
  `auth/...` never refresh.
- `signOut()` forgets the tokens first (not cancellable), then calls `auth/logout` in the app scope.
- A store that throws on read (iOS Keychain before first unlock) keeps `SessionState.Unknown` and is
  retried with backoff; only an undecodable value is discarded. The iOS store deletes a Keychain item
  left over from a previous install (flag in `NSUserDefaults`); the Android store replaces a key the
  Keystore can no longer use.
- No database until offline stock counting (then Room via the convention plugin, ADR 0006).
- Sentry via native SDKs in the hosts, crew-style bridge for shared networking diagnostics (phase 7).
- Auth: refresh once on `401` (single-flight), retry, else log out (see Session above). Sign in with Apple required on
  iOS when Google is offered.

## Environments, text and diagnostics ([ADR 0012](../docs/architecture/adr/0012_CROSS_CUTTING_CONCERNS.md))

- Variants `dev` (`com.onemind.app.dev`, "OneMind Dev") and `prod` (`com.onemind.app`), installable
  side by side. One switch, the Gradle property `onemind.environment` (`dev` default in
  `gradle.properties`, `-Ponemind.environment=prod` for prod):
  - it is the BuildKonfig flavor of `core:data`, which generates `ENVIRONMENT` and `API_BASE_URL`
    (host only; the client appends `/api/v1`). Read them through `ApiConfig` (bound in
    `coreDataModule`, environment as `AppEnvironment`), never `BuildKonfig` directly from other
    modules. The unflavored `defaultConfigs` holds the dev values; the convention plugin fails the
    build when `defaultConfigs("prod")` misses a field or a module uses unflavored `targetConfigs`;
  - Android has flavors `dev`/`prod`, and only the flavor matching `onemind.environment` is enabled
    (shared modules are compiled once per build, so a dev APK can never carry prod values).
    `devRelease` is disabled as well: release builds (and the release key) are always prod, so a
    plain `assembleRelease` without `-Ponemind.environment=prod` fails with "task not found". The dev
    flavor allows plain HTTP (`src/dev/res/xml/network_security_config.xml`); prod is HTTPS only;
  - iOS: the Debug configuration is dev, Release is prod (`iosApp/Configuration/Debug.xcconfig`,
    `Release.xcconfig`, both including `Config.xcconfig`); the Gradle build phase passes
    `ONEMIND_ENVIRONMENT` as `-Ponemind.environment`. `Debug.xcconfig` also sets `ONEMIND_DEV=1`,
    which keeps the local-networking ATS exception in `Info.plist`. An IDE-driven iOS build
    (`OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`) skips that phase and builds the framework with the
    default `dev`: for a prod build from the IDE, set `onemind.environment=prod` there, or build with
    Xcode/`xcodebuild -configuration Release`.
- API hosts: dev is the local docker backend, `http://10.0.2.2:8080` on the Android emulator and
  `http://localhost:8080` on the iOS simulator; a phone on the LAN uses
  `onemind.apiBaseUrl=http://<ip>:8080` in the git-ignored `local.properties` (dev only; checked to
  be `scheme://host[:port]`). Prod is
  `https://api.onemind.app`, a **placeholder** (the JWT issuer of ADR 0004) to be confirmed with
  the hosting setup in phase 7. The app contains no secrets; Sentry DSN joins BuildKonfig with Sentry
  (phase 7).
- Strings in Compose resources (English; Czech before release). Plurals use CLDR categories
  (Czech: one/few/many/other) — test them.
- Money via platform formatters (`expect/actual` in `core:presentation`), by the venue's currency,
  independent of UI language. Use `Money(amountMinor, Currency)` and `Quantity(DecimalValue, unit)`
  from `core:domain` (exact: minor units, decimal strings; never `Float`/`Double`). Show amounts with
  `formatMoney(money, locale, MoneyStyle.Summary | Precise)` and chart labels with `formatChartLabel`
  (`core:presentation/format`, METRICS.md rules: U+202F grouping, `−`, no decimals ≥ 100 in summaries,
  `12.4k`), counts with `formatCount` (`1 846`, same grouping), and percentage changes from the API with
  `formatDeltaPercent` (`+12.4%`, `−3.0%`, digits as sent). The locale is `VenueLocale.forCurrency(currency)` **for now** (CZK → `cs-CZ`, others
  `en-US`): the API has no venue locale yet, and that seam is the one place to change. Times in the venue's time zone from the API (`formatClockTime`).
- No business-day calculation in the app: the backend returns the business date; the app only
  formats times in the venue's time zone (bar nights across midnight and DST are tested).
- The system clock is read only in `coreDataModule` (`core/data/.../di/CoreDataModule.kt`, which
  binds `Clock.System` as `kotlin.time.Clock`); everything else injects `Clock`. The device time
  zone is never used.
- Send `X-Request-Id` per request; add it to Sentry breadcrumbs. No analytics SDK in the first
  release.

## Testing

- ViewModel tests in `commonTest` with fake repositories and Turbine.
- Data tests with Ktor `MockEngine` and JSON fixtures matching the OpenAPI examples.
- Formatter tests (money, deltas, dates, business-day labels).
- Koin graph verification test. Compose UI tests for key screens; screenshot tests later.
- Android success does not verify iOS: build/test both targets. `core:data` has an iOS Keychain test
  (`src/iosTest`, `./gradlew :core:data:iosSimulatorArm64Test`), which needs an installed iOS simulator
  runtime; CI runs it in the `ios-compile` job.

## Guardrails (`:core:konsist`)

`mobile/core/konsist` is a guard-only JVM module (Konsist, tests only, no production code). It runs
with `./gradlew :core:konsist:test` in CI and checks:

- layers come from the Gradle module path (`feature/sales/domain/…` is domain), and every file's
  package matches its module, so paths and packages can't drift;
- features never import another feature (only `composeApp` wires them);
- domain layers import nothing from `core:api`, data, presentation, `core:designsystem`,
  `composeApp`, Ktor, Koin, Android/androidx or Compose; presentation imports no data;
- generated `core:api` models are imported only in data layers;
- money-named declarations are never `Float`/`Double`, in every layer (product rule 5). Money names
  are whole camelCase/snake tokens from `moneyWords` in `docs/api/lint/money-words.json`, the list
  shared with the backend; a plural `s`/`es` also matches (`prices`, `salesByHour`). Names whose last
  word is in `notMoneySuffixes` (rates, ratios, percentages, counts, …) are not money. The guard reads
  the file at test time (it is a declared input of `:core:konsist:test`), so there is no copy in
  Kotlin: change the list only in that file. A new array key in the file fails the guard until the
  mobile side reads it. `Height`/`Width`/`Fraction` are mobile-only suffixes for drawing values, kept
  in `MoneyNames`. Percentages and drawing values may stay floating point.
  Known limit: the guard reads declared type names, not resolved types, so a `typealias X = Double`
  or a value class wrapping `Double` on a money name is not caught. Don't alias or wrap money as
  floating point. In domain and data, a line naming money
  also has no floating-point literal or `toDouble()`/`toFloat()`;
- no system clock outside the clock seam (`CoreDataModule.kt`) and no device time zone anywhere
  (product rule 6);
- feature presentation and `composeApp` main sources have no `Color(0x…)`, no literal `.sp`, no
  `Text("…")`, `text = "…"` or `contentDescription = "…"` (spoken by screen readers; decorative images
  pass `null`), also split over lines; `@Preview` functions, comments and test sources are exempt.
  Not caught yet: `label =`/`title =`/`placeholder =` literals, `Color.Red`/`Color(red = …)`, `.dp`;
- the scopes are not empty (so the guards can't pass by scanning nothing).

`GuardFixturesTest` runs every rule on planted violations in `src/test/fixtures` (laid out like
`mobile/`, not compiled) and expects exactly those offenders, so each rule is proven to catch
something. Add a fixture when you add or change a rule.

It is a guard file (`docs/PARALLEL_WORK.md#guard-files`): never weaken a rule to make a change pass.

## Build logic

`mobile/` is its own Gradle build (wrapper 9.9.2, run on JDK 21; versions in
`gradle/libs.versions.toml`). Convention plugins in `build-logic/convention`, applied with
`alias(libs.plugins.convention.<name>)`:

| Plugin | For | Adds |
|--------|-----|------|
| `kmp.library` | domain, data, `core:api` | KMP Android library + `iosArm64`/`iosSimulatorArm64`, serialization, ktlint |
| `cmp.library` | `core:designsystem`, `core:presentation` | `kmp.library` + Compose Multiplatform, Material 3, resources, previews |
| `cmp.feature` | `feature:*:presentation` | `cmp.library` + `core:presentation`, `core:designsystem`, ViewModel, Navigation, Koin, Turbine |
| `cmp.application` | `composeApp` | `cmp.library` + the single static iOS framework `ComposeApp` |
| `android.application(.compose)` | `androidApp` | AGP 9 app (built-in Kotlin), `dev`/`prod` flavors (only the selected one enabled, no `devRelease`), release signing from `ONEMIND_*` properties/env, Compose |
| `buildkonfig` | `core:data` | BuildKonfig with the module's package; flavor = `onemind.environment` (a conflicting `-Pbuildkonfig.flavor` fails); every field set for prod too |
| `ktlint` | applied by the above | same ktlint setup as the backend |

Namespaces come from the Gradle path (`:feature:sales:presentation` → `com.onemind.sales.presentation`).
Compose versions come from Compose Multiplatform only (no androidx Compose BOM). Lifecycle and
Navigation versions must stay aligned with the Compose Multiplatform version.

## Commands

Run from `mobile/`. Needs JDK 21, Node.js (`npx`, for the `core:api` spec bundle), the Android SDK
(`ANDROID_HOME` or `local.properties` with `sdk.dir`) and Xcode with an iOS simulator runtime.

The OpenAPI bundle task finds `npx` when it runs (not at configuration): on `PATH`, then in
`/opt/homebrew/bin` and `/usr/local/bin`, for IDE builds whose environment omits Homebrew. For another Node installation,
set `onemind.nodeBinDir=/absolute/path/to/bin` in `~/.gradle/gradle.properties`; that directory
must contain both `node` and `npx`.

```sh
./gradlew :build-logic:convention:build                    # compile + validate convention plugins
./gradlew ktlintCheck                                      # lint every module (ktlintFormat to fix)
./gradlew testAndroidHostTest                              # commonTest of every module on the JVM
./gradlew :core:konsist:test                               # architecture, money and clock guards
./gradlew :core:api:openApiGenerate                        # regenerate API models after a spec change
./gradlew :androidApp:assembleDebug                        # Android dev debug APK (com.onemind.app.dev)
./gradlew -Ponemind.environment=prod :androidApp:assembleRelease  # Android prod release (R8); unsigned without ONEMIND_* signing values
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64  # iOS framework (shared code compiles for iOS)

# Full iOS app for the simulator (runs the Gradle framework build as an Xcode build phase);
# -configuration Debug is dev, Release is prod
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Open `iosApp/iosApp.xcodeproj` in Xcode to run on a simulator or device. The project uses folder
synchronised groups: new Swift files in `iosApp/iosApp/` are picked up without editing the
project. iOS deployment target is 18.5 (the minimum Compose Multiplatform's native libraries are
built for). The signing team is set in `iosApp/Configuration/Config.xcconfig`.

## Definition of done (mobile)

- Matches the design (tokens, spacing, copy) and handles loading, empty, error and cached states.
- Builds on Android and iOS; ViewModel tests and previews added.
- No hard-coded colors/strings in features; docs updated if a token, screen or flow changed.

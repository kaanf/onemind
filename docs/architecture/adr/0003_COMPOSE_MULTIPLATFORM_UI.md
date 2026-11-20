# 0003 · Compose Multiplatform for the shared UI

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The mobile app is Kotlin Multiplatform (Android + iOS). The chosen design is the
**Cross-platform** direction: one visual language for both platforms, not a native look per
platform. Many screens use custom components (hourly bar charts, heatmap, floor plan, steppers,
OTP input) that would otherwise be built twice.

## Decision

Use **Compose Multiplatform** for the UI on both platforms. UI, presentation (ViewModels) and data
layers all live in the shared `composeApp` module.

- iOS hosts Compose through `ComposeUIViewController` inside a minimal SwiftUI app (`iosApp/`).
- Platform-specific features go through interfaces / `expect`-`actual`, implemented per platform:
  - Sign in with Apple (`AuthenticationServices`) and Google (Credential Manager on Android,
    Google Sign-In SDK on iOS),
  - Storyous connect: `ASWebAuthenticationSession` (iOS), Custom Tabs (Android),
  - secure token storage (Keychain / Android Keystore),
  - camera for invoice scanning (CameraX / AVFoundation, or the system camera / document scanner),
  - push notifications (later).
- If a screen part needs a truly native view (for example the camera preview), embed it with
  `UIKitView` / `AndroidView` instead of rebuilding it in Compose.

## Alternatives considered

- **Native UI per platform (Jetpack Compose + SwiftUI) with shared logic:** best platform feel,
  but every screen and custom chart is built twice, and the design is intentionally platform-neutral.
- **Shared logic only, UI later:** delays the decision and doubles UI work anyway.

## Consequences

- One UI codebase; custom charts and the design system are built once.
- We must check iOS-specific behaviour on real devices: text input and keyboard, scrolling feel,
  VoiceOver accessibility, safe areas, app size.
- The iOS project stays thin. Swift code is only for the app entry point and platform services.

# 0004 · Own JWT auth with Apple, Google and email codes

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The design has passwordless sign-in only: **Continue with Apple**, **Continue with Google**, or a
**6-digit email code** that expires in 10 minutes (A1, A2). There are no passwords, no social
graph, no enterprise SSO. A managed auth service would add cost, a vendor dependency, and one more
place for user data, for a small and well-understood flow.

## Decision

The backend issues and verifies **its own tokens** with Spring Security.

### Tokens

- **Access token:** JWT, lifetime **15 minutes**, signed with an asymmetric key (ES256 or RS256).
  The private key comes from a secret store. The token carries `sub` (user ID) and a
  `kid` for key rotation. Roles and venue access are **not** trusted from the token; they are
  loaded per request (memberships change).
- **Refresh token:** opaque random value, lifetime **30 days**, stored **hashed** in the database,
  **rotated on every use**. If an already-used refresh token is presented, revoke the whole token
  family (theft detection).
- Backend validates access tokens as an OAuth2 resource server (`spring-boot-starter-oauth2-resource-server`)
  with our own public key.
- Mobile stores tokens in Keychain (iOS) / Keystore-backed storage (Android), and refreshes
  automatically on `401`.

### Sign-in methods

- **Email code:** `POST /auth/email/start` creates a 6-digit code, stored hashed, valid
  **10 minutes**, max **5** wrong attempts, resend allowed after **30 s** (the design shows a
  countdown). Rate-limit per email and per IP. Always answer `202` (no account enumeration).
- **Apple:** the app sends the Apple identity token. The backend checks the signature (Apple JWKS),
  `iss`, `aud` (our bundle ID), expiry and nonce. Handle Apple's private relay emails.
- **Google:** the app sends the Google ID token. The backend checks it against Google JWKS and our
  client IDs (Android + iOS).
- Accounts are linked by **verified email**: signing in with Google using the same verified email
  as an existing account signs into that account.

### Other requirements

- In-app **account deletion** (App Store requirement): deletes the user, revokes tokens and
  removes Apple/Google links.
- Logout revokes the refresh token family.
- Emails are sent through a transactional email provider (chosen with hosting).
- All auth events are logged (no codes or tokens in logs).

## Implementation notes (2026-11-21)

- **Libraries:** none beyond Spring Security; the resource-server starter brings Nimbus JOSE+JWT,
  used to sign (`NimbusJwtEncoder`) and verify (`NimbusJwtDecoder`). Apple/Google tokens will use
  `NimbusJwtDecoder.withJwkSetUri` with their JWKS. Email goes out through `spring-boot-starter-mail`
  (SMTP) behind `EmailSender`; the provider is open (`docs/architecture/README.md`, item 11).
- **Access token:** ES256 (P-256). The signing key is a private JWK with `kid` in `JWT_SIGNING_KEY`
  (`./gradlew generateJwtKey`); required in `prod`, a temporary key per process otherwise. Claims:
  `iss` `https://api.onemind.app`, `aud` `onemind-app`, `sub` user ID, `iat`, `exp`, `jti`.
- **Key rotation:** generate a new key, set it as `JWT_SIGNING_KEY` and put the old key's public JWK
  in `JWT_PREVIOUS_PUBLIC_KEY`; remove it after 15 minutes (the access-token lifetime). Refresh tokens
  are not affected.
- **Email codes:** HMAC-SHA256 of address and code with a pepper (`EMAIL_CODE_PEPPER`; required in
  `prod`), so a database leak doesn't reveal codes (a plain hash of 6 digits is trivial to reverse).
  Only the latest code of an address counts. Limits: 5 codes per address per hour, resend after
  30 s (database); 10 starts and 30 verifications per IP per 10 minutes (in memory, one instance per
  ADR 0008). The client IP comes from `X-Forwarded-For` only behind a private-network proxy
  (`server.forward-headers-strategy: native`).
- **Sending:** after the code's transaction commits, in the request; a failure is logged and the user
  asks again after 30 s. Not through the event publication registry, which would store the plain
  code in `platform.event_publication`.
- **Apple/Google:** `POST /auth/apple` and `/auth/google` verify the ID token with
  `NimbusJwtDecoder.withJwkSetUri` (RS256; Apple `https://appleid.apple.com/auth/keys`, Google
  `https://www.googleapis.com/oauth2/v3/certs`; keys cached, refetched for an unknown `kid`; 5 s
  timeouts), then `iss` (Apple `https://appleid.apple.com`; Google `https://accounts.google.com` or
  `accounts.google.com`), `aud` and `exp` with the app clock. Accepted audiences come from
  `APPLE_CLIENT_IDS` and `GOOGLE_CLIENT_IDS` (comma-separated; empty turns that provider off). Apple's
  `nonce` claim must be the SHA-256 (hex) of the raw nonce the app sends; Google's must equal the
  nonce if the app sends one (without it, a captured Google token can be replayed until it expires,
  about an hour; the app should always send one). `email_verified` may be a boolean or `"true"` (Apple). A Google email
  is trusted only for `@gmail.com` or when it is in the token's `hd` (Workspace) domain; Google does
  not vouch for other addresses, so those accounts sign in only by subject (no linking or creation,
  `422 EMAIL_NOT_VERIFIED`), which prevents pre-hijacking an existing account. The user is found
  by provider subject, else by verified email (linking any existing account, private relay addresses
  included), else created; an unknown subject without a verified email is refused. 30 sign-ins per IP
  per 10 minutes. The JWKS being unreachable is a `500`, not `401`.
- **Logout:** `POST /auth/logout` with the refresh token (no access token needed) revokes its
  family; always `204`, also for an unknown or revoked token. The access token runs out by itself
  (at most 15 minutes).
- **Account deletion:** `DELETE /me` deletes `app_user` in one transaction; identities, refresh
  tokens, memberships, followed venues and idempotency records cascade. Organizations whose only
  owner this was get `deletion_due_at` = now + 30 days (rows locked first, so two owners deleting at
  once can't both see the other as remaining owner). A still-valid access token of a deleted user
  gets `401`. Revoking the user's Apple tokens is open (`docs/architecture/README.md`, item 12).
- **Venue access:** `account/api/VenueAccess` reads the membership of the venue's organization on
  every call (never from the token); no membership, an unknown venue or an organization scheduled
  for deletion is `404 VENUE_NOT_FOUND`, a too-low role `403 VENUE_ROLE_REQUIRED`. Only OWNER/any
  member is distinguished until STAFF permissions are decided (`docs/OPEN_QUESTIONS.md` P2).
- **Refresh tokens:** 256 random bits, stored as SHA-256. A refresh rotates within the family; a
  used, revoked or expired token answers `401 REFRESH_TOKEN_INVALID`, and a used one also revokes the
  family. An hourly job deletes expired refresh tokens and codes older than a day.

## Alternatives considered

- **Managed (Firebase Auth, Auth0, Supabase Auth, Clerk):** less code, but cost per user, vendor
  lock-in, and user data in a third place. The flow is small enough to own.
- **Self-hosted Keycloak:** powerful, but heavy to run for passwordless mobile sign-in.

## Consequences

- We own security-sensitive code: it needs focused tests (code expiry, attempts, rotation, reuse
  detection, token validation) and a security review before launch.
- No external auth dependency; all user data stays in our database.
- Key rotation procedure must be documented with hosting.

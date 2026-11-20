# 0005 · Polling for live figures, SSE for chat streaming

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

Some screens show near-live data (Today: paid so far, open tabs, occupancy, on shift; A4: sync
progress). Our own data is only as fresh as the last POS sync (target: about 2 minutes), so
sub-second push adds little. The assistant's answers take seconds and should appear as they are
generated.

## Decision

### Polling for live data

- The app polls while the screen is **visible and the app is in the foreground**. It never polls
  in the background.
- Intervals: Today **30 s** while the venue is open, **5 min** when closed; Staff and Tables
  (*Now*) 60 s; first sync status (A4) 2 s until done. The server may suggest an interval with a
  `Refresh-After` hint in the response body.
- Responses carry an `ETag`. The app sends `If-None-Match`; unchanged data returns `304` with no
  body.
- Every response includes `dataAsOf` (last POS sync), shown in the UI where relevant.
- Pull-to-refresh triggers an immediate request.

### SSE for assistant answers

- `POST /api/v1/venues/{venueId}/assistant/messages` with `Accept: text/event-stream` returns a
  **Server-Sent Events** stream.
- Event types: `delta` (text chunk), `block` (structured content such as a table), `citation`,
  `suggestedReplies`, `done` (final message ID), `error`.
- The final message is also saved, so `GET …/messages` returns it even if the stream breaks.
  On disconnect, the app reloads the message instead of resending the question.
- Backend: Spring MVC `SseEmitter` on virtual threads (Java 21). Mobile: Ktor SSE client.
- OpenAPI describes the endpoint with `text/event-stream` and documents each event payload as a
  schema in `components`.

## Alternatives considered

- **WebSockets for everything:** true push, but adds connection management, scaling and auth
  complexity for data that only changes every couple of minutes.
- **Polling for chat too:** simple, but the answer would appear all at once after several seconds.

## Consequences

- Plain HTTP everywhere; works through proxies and load balancers with standard settings
  (SSE needs proxy buffering turned off for that route).
- Battery and data use are bounded by foreground-only polling and `304` responses.
- Push notifications (invoice waiting for approval, stockout risk) remain a separate, later
  feature (FCM/APNs).

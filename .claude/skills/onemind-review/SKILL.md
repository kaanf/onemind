---
name: onemind-review
description: Review a OneMind pull request against the repo's product rules, ADRs and docs, then post findings on the PR and report to the orchestrator. Use when the reviewer lane receives a REVIEW message, or when asked to review a PR or audit the codebase.
---

# OneMind PR review

Input: a PR number (or "audit" for a whole-codebase review). Output: findings on the PR and a
`REVIEW-RESULT` message to the orchestrator.

## 1. Understand the change

- `gh pr view <n> --json title,body,files,baseRefName,headRefName` and `gh pr diff <n>`.
- Check out the head detached (`git fetch && git switch --detach origin/<head>`).
- Read the roadmap item the PR claims, and the docs the touched area points to (AGENTS.md
  "Where to look" table): METRICS.md, MODEL.md, STOCK.md, DESIGN_SYSTEM.md, docs/api, the ADRs.

## 2. Correctness

Run the built-in `/code-review` on the PR (level `high`) for bugs, then read the diff yourself for
what it can miss: concurrency, transactions, time zones, null/empty data, error paths.

## 3. OneMind rules (each one is a blocker if broken)

1. POS read-only: no mutating Storyous call anywhere.
2. No invented numbers: every figure traces to POS data, user input or a documented calculation;
   missing data → empty state, never a placeholder.
3. Assistant names sources; staff role sees only *All staff* documents.
4. Stock history is append-only; transfers never change the venue total.
5. Money is integer minor units or `BigDecimal` + currency, never `Float`/`Double`.
6. Business day crosses midnight, in the venue's time zone.
7. Every query scoped by venue.
8. No secrets in code or config; no personal data in logs (IDs only); `traceId` on log lines.

## 4. Architecture and contract

- API change starts in `docs/api/openapi.yaml`, additive within `/api/v1`, generated code used.
- Backend: jOOQ only (no JPA), module boundaries (ADR 0009), RFC 9457 errors (ADR 0010).
- Mobile: module layout and layering per ADR 0011, strings in resources, tokens from the design
  system (no hard-coded colours/sizes).
- New dependency, non-additive schema change or auth change → was it approved? (ask the orchestrator)
- Glossary terms used in code, API and UI copy; no synonyms.

## 5. Quality

- Tests cover the rules in the change (happy path + the edge cases above), not only plumbing.
- Duplication, dead code, needless abstraction, functions doing several things, naming.
- Docs updated in the same PR when behaviour, a metric, an endpoint or a term changed.
- UI PR: screenshots present and matching the design canvas (Cross-platform page).

## 6. Report

Rank findings: **blocker** (rule broken, bug, security), **should-fix**, **nit**. Skip anything you
are not confident about, or mark it as a question.

- Post one PR review comment (English) with the ranked list, file:line for each item. Use
  `gh pr review <n> --comment --body-file …`; never approve or request changes as the owner's account.
- Send the orchestrator:

```
REVIEW-RESULT
pr: #<n>
verdict: clean | fix-needed | blocked
blockers: <count, one line each>
should-fix: <count, one line each>
notes: <structure / risk observations in 1–3 lines>
```

For an **audit**, skip the PR comment: write the report as a Turkish summary to the orchestrator
(structure, risks ranked, spaghetti/duplication hotspots, test gaps) and keep a fuller English
version in your reply so the orchestrator can turn it into roadmap items.

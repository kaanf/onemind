---
name: onemind-review
description: Review a OneMind pull request against the repo's product rules, ADRs and docs, then post findings on the PR and report to the orchestrator. Use when the reviewer lane receives a REVIEW message, or when asked to review a PR or audit the codebase.
---

# OneMind PR review

Input: a PR number (or "audit" for a whole-codebase review). Output: findings on the PR and a
`REVIEW-RESULT` message to the orchestrator.

## 1. Understand the change

- `gh pr view <n> --json title,body,files,baseRefName,headRefName,statusCheckRollup` and
  `gh pr diff <n>`. Note which checks passed, failed or were skipped; a lane's `REPORT ready` claims
  they passed.
- Check out the head detached (`git fetch && git switch --detach origin/<head>`).
- Read the roadmap item the PR claims, and the docs the touched area points to (AGENTS.md
  "Where to look" table): METRICS.md, MODEL.md, STOCK.md, DESIGN_SYSTEM.md, docs/api, the ADRs.

## 2. Correctness

Run the built-in `/code-review` on the PR (level `high`) for bugs, then read the diff yourself for
what it can miss: concurrency, transactions, time zones, null/empty data, error paths.

## 3. OneMind rules (each one is a blocker if broken)

Rules 1–7 are AGENTS.md "Product rules that must never be broken"; rule 8 is from its "Cross-cutting
rules". Keep this list in sync with AGENTS.md.

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
- Backend: jOOQ only (no JPA), module structure and graph (ADR 0013 once accepted, else 0009),
  RFC 9457 errors (ADR 0010).
- Mobile: module layout and layering per ADR 0011, strings in resources, tokens from the design
  system (no hard-coded colours/sizes).
- New dependency, non-additive schema change or auth change → was it approved? (ask the orchestrator)
- Layer or boundary shortcuts (web calling persistence, a module reading another module's tables,
  generated models leaking past web) even where no gate catches them yet.
- Guard files (`docs/PARALLEL_WORK.md#guard-files`) touched → weakened? Rules relaxed, baselines
  grown or tests deleted to pass are a blocker. Check the PR body's `critical areas` line is honest
  and repeat it in your `REVIEW-RESULT`.
- Glossary terms used in code, API and UI copy; no synonyms.

## 5. Quality

- Tests cover the rules in the change (happy path + the edge cases above), not only plumbing.
- Can the new tests fail? Name the line whose removal or change would turn each one red; a gate PR (one that adds or
  changes a test, lint rule or CI step meant to block violations) must link a red run from a deliberate violation and the green run after it.
- Duplicated logic (the same calculation or mapping in two places).
- Swallowed errors: `catch` / `runCatching` / `getOrNull` that drops the cause without handling,
  logging or rethrowing it.
- Over-abstraction: an interface with one implementation (except the ADR's named exceptions), a
  mapper or wrapper with a single use.
- Use-case size: one use case per screen/job; more than 5 collaborators or several unrelated
  public methods means split.
- Dead code, functions doing several things, naming.
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
pr: #<n> | audit
verdict: clean | fix-needed | blocked
checks: <passed / failed / skipped, from statusCheckRollup>
blockers: <count, one line each>
should-fix: <count, one line each>
notes: <structure / risk observations in 1–3 lines>
```

For an **audit**, skip the PR comment. Send the full English report to the orchestrator with
`SendMessage` (structure, risks ranked, spaghetti/duplication hotspots, test gaps): your chat replies
never reach other sessions. Use `pr: audit` in the `REVIEW-RESULT`. The orchestrator turns it into
roadmap items and gives the owner the Turkish summary.

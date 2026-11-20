# Product overview

## One-liner

> **Your bar, hour by hour.** Sales, staff, tables and losses from your POS, with suggestions on
> what to do next.

## Problem

Bar and restaurant owners have all the data in their POS, but the POS back office is built for
accounting, not for running the night. Owners want to know, from their phone:

- How is tonight going compared to a normal Friday?
- Who is on shift, how are they selling, is anyone deleting too many items?
- Which tables and hours make money?
- What is the Happy Hour really worth?
- What do I need to order before Tuesday's delivery, and what stock is just sitting there?

## Users and roles

| Role      | Who                                   | Can do                                                                                   |
|-----------|----------------------------------------|------------------------------------------------------------------------------------------|
| **Owner** | Venue owner, connects the POS account  | Everything. Connects Storyous, picks venues, approves deliveries, manages documents and access. |
| **Staff** | Bar lead, bartender, waiter            | Stock tasks (count, pull list, scan invoice, log corrections). Asks the assistant, but only gets answers from documents marked *All staff*. |

Staff actions that change stock value (e.g. a scanned delivery) wait for **owner approval**.
Exact permissions per screen are an open question, see `docs/OPEN_QUESTIONS.md`.

## Core principles

1. **Read-only POS.** We read from Storyous and never change anything there. This is also a trust
   message in onboarding: *"Read only. We never change anything in Storyous."*
2. **Numbers with context.** A number is always compared with something meaningful
   (*+12.4% vs last Friday*, *usual for this waiter: 2 to 3*).
3. **Explain every recommendation.** Order quantities, suggestions and assistant answers show how
   they were derived (*"How we got 6"*) and which sources were used.
4. **Suggest, don't nag.** Suggestions can be applied or dismissed (*Not now*), and dismissed ones
   go away.
5. **Mobile first, one hand.** Touch targets ≥ 44 px, important figures at the top, one tap between
   any two tabs.

## Feature areas

| Area                  | Summary                                                                                     | Screens (see `SCREENS.md`)            |
|-----------------------|---------------------------------------------------------------------------------------------|----------------------------------------|
| Onboarding            | Sign in (Apple, Google, email code), connect Storyous, first sync, pick venues              | A1–A5                                  |
| Today                 | Live overview of the current business day                                                   | 01, 01a                                |
| Sales                 | Revenue by hour/day/week/month, payments, tips; drill-downs                                 | 02, 02a, 02b, 02c                      |
| Staff                 | On shift now, sales/tips/guests per person, deleted items per waiter                        | 03, 03a                                |
| Tables                | Floor plan: occupied now, revenue per seat this week                                        | 01a                                    |
| Stock & purchasing    | Order plan, item rationale, idle stock, counts, locations, transfers, invoices, recipes | 04, S1–S7                              |
| Assistant             | Tonight's briefing, chat over POS data + documents, suggestions, knowledge sources          | 05, 05a                                |

## Market and locale

- First market: **Czech Republic** (Storyous is a Czech POS). Currency **CZK (Kč)**, time zone
  **Europe/Prague**.
- UI copy in the design is **English**. Czech localisation is expected (open question).
- Number format in the design: thin-space thousands separator, currency after the number:
  `48 320 Kč`, `−1 870 Kč`, `+6 300`.

## Out of scope (for now)

- Writing to the POS (menu edits, price changes, Happy Hour configuration).
- Payroll and shift scheduling (we only *read* sign-in hours and *suggest* staffing).
- Placing orders with suppliers automatically (the order plan prepares; sending is a user action,
  channel TBD).
- Other POS systems (keep the integration behind an interface so others can be added later).
- Web dashboard (mobile only for now).

## Not yet designed

Marked as dashed cards in the App flow: **Receipt** (bill number, items, table, waiter),
**Venue switcher sheet**, **Act on suggestion** flows. Also not designed: settings, account,
staff invitation, notifications, empty/error/loading states, dark mode.

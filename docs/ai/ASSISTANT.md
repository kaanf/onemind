# AI features

> **First release:** only receipt extraction (section 6) is built. Sections 1–5 (chat, briefing,
> suggestions, generated insights, knowledge) are **later**. See `docs/product/MVP_SCOPE.md`.

All LLM features run on the **backend**. The mobile app only calls our API. Model and provider are
an architecture decision (see `../architecture/README.md`).

## Principles

1. **Grounded.** Numbers in answers come from tool calls into our own metrics/query layer, never
   from the model's imagination. The model writes the words; our code computes the numbers.
2. **Cited.** Every answer lists its sources as chips: POS datasets (*Storyous · sales*) and
   documents (*Cocktail costs.xlsx*).
3. **Access-aware.** Retrieval filters documents by the asking user's role *before* the model sees
   them. Staff only get `ALL_STAFF` documents. All data is scoped to the venue.
4. **Honest.** If data is missing or the question is out of scope, say so. No guessing.
5. **Neutral about people.** Observations about staff (deleted items) are factual and non-accusatory
   (*"It may be a busy table changing orders, worth a quick check."*).

## 1. Assistant chat (05)

- One conversation thread per venue per user **(?)**. Header shows data status
  (*Storyous live · 5 documents*).
- Capabilities shown in the design:
  - Questions about sales, products, hours, staff, tables (*How many beers did we sell at 22:00?*,
    *Why was Tuesday slow?*).
  - Analysis that joins POS data with documents (*Is Happy Hour worth it for Aperol Spritz?* uses
    sales + recipe costs from a spreadsheet).
  - Structured answers: prose + small data table + a follow-up offer.
  - Procedural answers from documents (*Closing checklist* from the staff handbook).
  - Suggested follow-up replies as chips.
  - Attach a document from the composer (adds it to Knowledge).
- **Implementation sketch (proposed):** LLM with tool use. Tools such as
  `get_sales(period, venue, groupBy)`, `get_product_sales(...)`, `get_staff_stats(...)`,
  `get_table_stats(...)`, `get_losses(...)`, `get_stock_status(...)`, `search_documents(query)`
  (RAG over permitted documents). Each tool result carries a citation label.
- Streaming responses to the app (SSE or WebSocket). **(?)**

## 2. Tonight's briefing

- Short paragraph at the top of the assistant (*"48 320 Kč so far, 12% above last Friday. At this
  pace Aperol runs out around 23:40."*).
- Generated from: today vs comparison, stockout projections, notable anomalies.
- Regenerated on a schedule while open (e.g. every 30–60 min) or after significant changes;
  cached by an inputs hash.

## 3. Suggestions

From the earlier *Insights* screen; to be surfaced inside the Assistant (placement TBD with
design). Each suggestion:

| Field     | Example |
|-----------|---------|
| type      | *Happy Hour*, *Control*, *Tables*, *Staffing*, *Stock* |
| impact    | *+2 100 Kč / week*, *9 deleted items*, *+23% per guest* |
| title     | *Extend Monday Happy Hour to 19:30* |
| why       | *On Mondays orders drop 40% right at 19:00. Happy Hour already nets +6 300 Kč a week.* |
| cta       | *Remind me to set it*, *Open receipts*, *Share with staff* |
| states    | open → applied (*✓ Reminder set for Monday*) / dismissed (*Not now*, hidden) |

- Detection is **rule-based first** (deterministic detectors over metrics: deleted-items anomaly,
  HH drop-off, table revenue concentration, staffing vs demand, stockout risk, idle stock). The LLM
  only phrases the title/why from detector output. This keeps numbers correct and testable.
- Applying a suggestion never changes the POS. Actions are reminders, navigation, sharing, or our
  own data changes.
- Do not re-raise a dismissed suggestion unless its evidence changes materially.

## 4. Generated insights on screens

Short one-liners attached to screens, same detector + phrasing approach:

- Tables: *"Best corner is the window. T1, T2 and T5 bring 40% of table revenue with 32% of the
  seats."*
- Hourly patterns: *"Thursdays after 21:00 grew 18% this month. Usually 3 staff are on shift then.
  A fourth from 21:00 would match demand."*
- Waiter detail: *"Above usual. Typical shift has 2 to 3 deleted items."*
- Losses: *"9 of today's deleted items came from one waiter."*
- Stock: price change alerts, stockout risk.

## 5. Knowledge (05a)

- Upload PDF, DOC/DOCX, XLS/XLSX (more later). Each document has access `ALL_STAFF` or
  `OWNER_ONLY` (set on upload, default `OWNER_ONLY` **(?)**).
- Processing pipeline: store file → extract text/tables → chunk → embed → index, with status
  `PROCESSING` (*Reading the document…*) → `READY` (*24 pages · ready*, *86 rows · ready*).
- Spreadsheets are also parsed as tables so that structured data (recipe costs) can be queried
  exactly, not only by semantic search.
- Live data source row: *Storyous POS · Sales, staff, tables · synced 2 min ago*.
- Privacy copy: *"Private to Main bar. Answers always name their source."*

## 6. Receipt / invoice extraction (S6) — first release

Decided in [ADR 0007](../architecture/adr/0007_RECEIPT_EXTRACTION_WITH_CLAUDE.md): ISDOC parsed
directly; otherwise Claude Haiku 5.5 vision with structured output, one Sonnet 5.5 retry when
validation fails, the owner always confirms.


- Image → OCR/vision model → structured invoice (supplier, number, date, lines, totals).
- Line matching uses supplier aliases first, then fuzzy/LLM suggestion with confidence; low
  confidence stays unmatched for the user.
- See `../domain/STOCK.md#deliveries-and-invoice-scanning-s6`.

## Evaluation and safety

- Keep a test set of questions with expected numbers per fixture venue; run in CI for prompt/tool
  changes.
- Log prompts, tool calls and citations per answer (no secrets), with retention limits.
- Rate-limit per user; cap tokens per venue per day.
- Prompt-injection: uploaded documents are untrusted. Treat document text as data; tools that
  change data are not available to the chat model, or require explicit user confirmation.

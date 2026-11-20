# 0007 · Receipt extraction with Claude Haiku 5.5

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

In the first release, stock comes in through purchase receipts and supplier invoices (S6): the
owner photographs or uploads them, we extract the product lines, the owner checks and corrects them,
then adds them to stock (`docs/domain/STOCK.md#purchase-receipts-how-stock-comes-in-s6`).

OCR alone returns raw text, not structure: it can't tell quantity from unit price, find the
supplier and total, join wrapped lines, or read Czech formats (`6KS`, `KRT12`, decimal commas).
Rule-based parsers per supplier would be brittle and expensive to maintain.

This is the **only AI feature in the first release** (`docs/product/MVP_SCOPE.md`). Receipts and
invoices are business documents with no personal data about staff, so no extra privacy measures
(pseudonymisation, EU-only inference) are needed for this feature. They will be decided when the
assistant is built.

## Decision

1. **ISDOC first.** If the upload is an ISDOC file or a PDF with embedded ISDOC XML, parse it
   directly. No AI. (Verify how common ISDOC is with real suppliers.)
2. **Otherwise a vision model with structured output:**
   - Default model **Claude Haiku 5.5** (`claude-haiku-5-5`), low effort.
   - Input: the photo (JPEG/PNG, resized on the client to a sensible maximum) or the PDF.
   - Output: JSON validated against a fixed schema: supplier name and ID number if present,
     document number, date, currency, lines `{rawText, quantity, unit, unitPrice, lineTotal}`,
     document total, and a per-line confidence.
3. **Validation and one escalation.** If the sum of line totals doesn't match the document total
   (with a small rounding tolerance), the JSON fails validation, or confidence is low, retry the
   same document **once** with **Claude Sonnet 5.5** (`claude-sonnet-5-5`). If that still fails,
   show the best result and flag the lines for the owner to correct.
4. **The owner always confirms.** Nothing goes into stock without the owner checking the lines on
   S6. Corrections are stored, and manual matches become supplier aliases.
5. **Provider behind an interface.** Backend code depends on a `ReceiptExtractor` interface
   (`stock/infra`), implemented with the official Anthropic Java SDK (`com.anthropic:anthropic-java`,
   used from Kotlin). Prompt and JSON schema are versioned resource files. Switching provider or
   model means a new implementation, no change elsewhere.
6. **Cost and safety controls:**
   - Store token usage per extraction (input/output tokens, model) for cost tracking.
   - Cap extractions per venue per day (default 100) to stop runaway costs.
   - API key only in server-side secrets; the mobile app never calls the model directly.
   - The model output is untrusted data: validate it, never execute or interpret it as
     instructions.
   - Original images/PDFs are kept in our storage so an extraction can be re-run.
7. **Test set.** Keep a set of real (anonymised if needed) receipts with expected lines in the repo.
   CI runs it on prompt, schema or model changes. It is skipped when no API key is configured, so
   normal CI stays free.

## Alternatives considered

- **OCR (Tesseract) + per-supplier parsers:** no per-call cost, but weeks of work and brittle
  parsing, and still poor on thermal receipts.
- **OCR + LLM on the text:** two systems to run; vision models already read the image directly.
- **Other providers (GPT-5 mini, Gemini Flash-Lite, Mistral Small):** similar or slightly lower
  prices; not chosen for now. The interface keeps them as an option, to be compared on our test set
  if quality or cost becomes an issue.

## Consequences

- Estimated cost: about $0.002 per receipt with Haiku 5.5, about $0.02 when escalated to Sonnet 5.5.
- The backend needs an outbound HTTPS dependency on the Anthropic API. Extraction is asynchronous
  (the app uploads, then polls the receipt status).
- Extraction quality must be tracked with the test set before and after any change.

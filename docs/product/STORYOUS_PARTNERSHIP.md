# Storyous partnership: research and strategy notes

Status: **parked** (2026-11-20). The owner is handling the partnership conversation with Storyous.
Development continues in the meantime. Come back to this before the first release.

## Why it matters

- Storyous API access works with OAuth2 client credentials; a token sees only the merchants
  **assigned to our app**. There is no self-service authorization flow for owners. Without a
  partnership (or at least a registered integration) we can't read a venue's data.
- `merchantAuth` in the docs is **not** a merchant API key: it is an internal header scheme
  (`x-permissions` bitmap set by Storyous's internal API router, plus `x-api-router-secret`).
  All public endpoints use bearer tokens. So "the merchant enters their own API key" is not an
  option.
- DataSync (webhooks) is deprecated and closed to new activations: polling only.

Technical details: [docs/integrations/STORYOUS.md](../integrations/STORYOUS.md).

## How Storyous partnerships work (observed)

- Integrations are activated **by the merchant** in the Storyous web administration
  (Integrations section), "without a programmer or calling support".
- Some partners require the merchant to agree terms with the partner first (Dishboard), others
  call themselves "official Storyous integrator" and request activation for the merchant
  (Kartička).
- Partners get more than the public API: Noona (reservations) syncs **both ways**, opens bills in
  Storyous and knows when tables are occupied by walk-ins. So open bills / table occupancy are
  available to partners even though the public API doesn't expose them.
- Storyous markets its API for "websites, ordering systems, accounting programs, **analytics
  tools** and more".

## Who already uses the Storyous API

| Product | Category | What it does | Relation to us |
|---------|----------|--------------|----------------|
| Storyous Premium Analytics (own) | Analytics | Losses, write-offs, voids; staffing and margin decisions; since Oct 2026 an MCP connector for Claude/ChatGPT; self-activated per venue | **Direct competitor** (web, paid add-on) |
| Storyous Inventory (own) | Stock | Recipes, costs and margins, stock-taking, suppliers, stock-ups; cost-based price levels (Sep 2026) | Competitor for stock features |
| My Storyous (own) | Owner mobile app | Remote sales view and alerts | The product users are unhappy with |
| Dishboard | Cost / finance | Invoice capture and auto-categorisation, supplier price watch, P&L, food cost, bank matching; revenue from Storyous every 15 min; 700+ venues; claims multi-POS (Storyous, Dotykačka, Septim) | Competitor for receipt scanning / price alerts |
| Noona | Reservations | Two-way sync with Storyous bills and tables | Not a competitor; proof of a partner API |
| qasima | Web, vouchers, e-shop, CRM, loyalty | Venue website and digital ops | Not a competitor |
| Kartička, Rewardly, CareCloud | Loyalty / CRM | Discounts, coupons, cashback in the POS payment dialog | Not competitors |
| Qerko, Choice | QR order & pay | Menu pulled from Storyous | Not competitors |
| Wolt, foodora | Delivery | Orders straight into the POS | Not competitors |
| Mews | Hotel PMS | Posts restaurant charges to hotel accounts | Not a competitor |
| Pohoda, Abra Flexibee | Accounting | Exports | Not competitors |
| Membrane | Integration platform | Storyous connector for AI agents | Not a competitor |

## The demand we have

- Customers want **the data in the current design** (Today, Sales, Staff, Tables, details) on
  mobile, comfortably and in detail. Storyous's **web panel is good**, its mobile app is not
  detailed enough.
- Public evidence: My Storyous has **3.2★ from 69 ratings** on the Czech App Store; reviews say
  "no breakdown or reports" and "the only sales view is revenue by payment method"; one reports
  login problems. Polish store: 1.0★ (3 ratings).

## Positioning for the pitch

> "Your web panel is great. Your merchants want the same depth on their phones — your App Store
> reviews say so too. We deliver that on top of Storyous, read-only, at no cost to you. We don't
> touch the POS, payments or data. Your merchants stay happy and stay with Storyous."

What Storyous gains:

- Merchant demand met (strongest argument: the request comes from their own customers).
- No development cost for a better mobile app.
- Lower churn risk (merchants who want better mobile tools don't switch POS).
- Zero risk: read-only, no access to cash register, fiscalisation or payments.
- Doesn't replace Premium Analytics' web panel; complements it on mobile. Option: revenue share,
  listing in their Integrations section.

**Consider narrowing the first release to read-only dashboards** for the pitch: our stock module
overlaps Storyous Inventory and makes the message "we replace your inventory". (Open question P13.)

## Likely answers and our moves

| Storyous says | Our move |
|---------------|----------|
| Yes | Standard integration: client credentials, listing in Integrations |
| "We'll build a better mobile app ourselves" | Offer to build it for them as **white-label** |
| No | Go multi-POS (Dotykačka first); requesting merchants keep pushing Storyous |

## Before the meeting

1. Document demand: how many merchants, who, what exactly they want. Ideally they ask Storyous
   directly ("we want this integration").
2. Gap list with the customer: what My Storyous lacks that we show (hourly product breakdown,
   waiter detail, discounts & losses, table performance, multi-venue overview). Use the design
   canvas as the demo.
3. Short proposal: read-only data list, security approach, commercial model.
4. Technical questions: see "Questions for Storyous" in `docs/integrations/STORYOUS.md`.

## Ideas considered (for later)

- **Multi-POS** ("one app for every POS"): reduces dependency on Storyous and strengthens the
  pitch. Czech market: Dotykačka is the leader (~20k devices, documented API v2, access by
  application form; merchant needs FULL/UNLIMITED licence + API add-on), Storyous second (5,000+
  businesses), KiloMayo and Septim have no public API. Unified POS APIs (Chift) don't list Czech
  POS. Competitors doing multi-POS: Dishboard (finance focus), KiloMayo (own platform), Tenzo and
  Nory (UK, not in CZ). Architecture already supports it (`PosProvider`, own stock); add a
  per-POS **capability list** (guest count, open bills, item voids, desks, shifts) that screens
  and metrics follow.
- **Features that would give Storyous a reason to partner** (not requested by users so far):
  - Shift planning + staff app (clock-in on phone) + live labour cost vs sales. Storyous has
    attendance (clock in/out, breaks, worked hours) but no shift planner; POS vendors routinely
    partner with workforce apps (Toast–7shifts, Lightspeed–Planday, Revel–Deputy).
  - Smart purchasing (iiko-style sales forecast → suggested purchase list) on top of Storyous
    stock data.
- **iiko** (Syrve in Europe) for reference: POS + back office (recipes, stock, stock-taking),
  owner mobile app (iiko.biz: real-time revenue, food cost, average check, dish ranking),
  sales forecast with suggested purchases, timekeeping and payroll, roles.

## Sources

- Storyous: [Public API docs](https://docs.api.storyous.com/) ·
  [Premium Analytics](https://www.storyous.com/premium-analytics) ·
  [Product news 09/2026](https://www.storyous.com/post/product-news-09-2026) ·
  [Product news 10/2026](https://www.storyous.com/post/product-news-10-2026) ·
  [Inventory](https://www.storyous.com/manage-stock-easily) ·
  [Integrations & loyalty](https://www.storyous.com/build-customer-loyalty) ·
  [Blog: Noona & qasima](https://www.storyous.com/cs/post/integrace-kter%C3%A9-v%C3%A1m-zjednodu%C5%A1%C3%AD-v%C3%A1%C5%A1-gastro-provoz) ·
  [Help: Dishboard](https://help.storyous.com/hc/en-gb/articles/32648398647825-Dishboard-Integration) ·
  [Help: attendance](https://help.storyous.com/hc/cs-cz/articles/32827700437393-Doch%C3%A1zka-zam%C4%9Bstnanc%C5%AF) ·
  [My Storyous (App Store CZ)](https://apps.apple.com/cz/app/my-storyous/id1448495965)
- Partners: [Dishboard](https://dishboard.cz/) · [Kartička](https://www.mojekarticka.cz/pro-podniky/storyous) ·
  [Choice](https://choiceqr.com/cs/storyous/) · [Mews](https://www.mews.com/en/products/marketplace/storyous)
- Market: [Dotypos API: getting started](https://manual.dotypos.com/jakzacitsapiv2.html) ·
  [Dotypos services & extensions](https://manual.dotypos.com/sluzbyarozsireni.html) ·
  [Dotykačka buys KASAmax](https://retailnews.cz/aktualne/dotykacka-kupuje-pokladni-system-kasamax/) ·
  [POS comparison CZ 2026](https://pokladni-system.cz/clanky/srovnani-pokladnich-systemu) ·
  [Dishboard for chains](https://dishboard.io/cz/pro-retezce) ·
  [Chift POS API](https://docs.chift.eu/unified-apis/pos/pos) ·
  [KiloMayo (Forbes CZ)](https://forbes.cz/startup-kilomayo-otevira-platformu-pro-gastro-s-rizenim-restauraci-ma-pomoci-ai/)
- Workforce partners: [7shifts POS partners](https://kb.7shifts.com/hc/en-us/articles/4417514276115) ·
  [Toast × 7shifts](https://pos.toasttab.com/ca/partners/directory/7shifts) ·
  [Lightspeed × Planday](https://lightspeedhq.com/integrations/planday)
- iiko: [iiko & mobile app](https://a-lux.kz/en/blog/iiko-i-mobilnoe-prilozhenie-svyazka/) ·
  [iiko modules](https://open-s.info/blog/kakie_moduli_est_v_iiko_i_zachem_oni_nuzhny_polnyy_obzor/)

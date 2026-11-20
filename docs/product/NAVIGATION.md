# Navigation

Derived from the **App flow** artboard and the links between artboards on the Cross-platform page.

## Graph

```mermaid
flowchart LR
  subgraph Onboarding["Onboarding · once"]
    Open[Open app] --> A1[A1 Sign in<br/>Apple · Google · Email]
    A1 -->|email| A2[A2 Email code]
    A1 -->|Apple/Google| A3
    A2 --> A3[A3 Connect Storyous]
    A3 -->|OAuth on Storyous, deep link back| A4[A4 First sync]
    A4 --> A5[A5 Pick venues<br/>skipped if only one]
  end

  A5 --> Today
  A4 -.->|single venue| Today

  subgraph Tabs["Tab bar · one tap between any two"]
    Today[01 Today]
    Sales[02 Sales]
    Staff[03 Staff]
    Stock[04 Stock]
    Assistant[05 Assistant]
  end

  Today -->|Occupancy| Tables[01a Tables]
  Today -->|On shift| Staff
  Today -->|Possible loss| Loss
  Today -->|Venue button| Switcher[[Venue switcher sheet]]

  Sales --> Products[02a Products by hour]
  Sales --> Busy[02b Hourly patterns]
  Sales --> Loss[02c Discounts & losses]
  Loss -->|Review| WaiterDetail
  Staff --> WaiterDetail[03a Waiter detail]
  WaiterDetail -->|Show all with receipts| Receipt[[Receipt · not designed]]

  Stock --> Plan[Order plan]
  Stock --> Idle[S2 Idle stock]
  Stock --> Count[S3 Count]
  Plan --> Item[S1 Why this order]
  Stock --> Locations[S4 Storage locations]
  Locations --> Transfer[S5 Pull list]
  Stock --> Invoice[S6 Scan invoice]
  Stock --> Recipe[S7 Recipe & corrections]

  Assistant --> Knowledge[05a Knowledge]
  Assistant --> Act[[Act on suggestion · not designed]]
```

## Rules

- Tab bar order: **Today, Sales, Staff, Stock, Assistant**. Active tab: dark label + 3 px accent
  bar at the top edge of the tab.
- Detail screens show a back arrow with an accessible label (*Back to Sales*) and keep the parent
  tab highlighted (e.g. 02b keeps **Sales** active).
- Shortcuts across tabs: Today opens Staff (*On shift*), Tables (*Occupancy*) and Discounts &
  losses (*Possible loss*). Discounts & losses opens Waiter detail.
- The selected venue (or *All venues*) is global state, shared by all tabs.
- Entry points to S4–S7 inside the Stock tab are **not designed** (only Order plan / Idle stock /
  Count are segmented tabs). Proposed: a stock overflow menu or list; confirm with design.

## Deep links (proposed)

| Link                                  | Opens                       |
|---------------------------------------|-----------------------------|
| `onemind://auth/storyous/callback?...`   | Return from Storyous OAuth  |
| `onemind://auth/email?code=...`          | Magic-link fallback (opt.)  |
| `onemind://today`                        | Today                       |
| `onemind://staff/{staffId}`              | Waiter detail               |
| `onemind://stock/invoices/{invoiceId}`   | Invoice approval (push)     |

Scheme: `onemind://`. Universal links / App Links need a domain we own (later).

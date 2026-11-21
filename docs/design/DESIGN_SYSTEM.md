# Design system

Extracted from the **Cross-platform** page of the design canvas. Implement these as tokens in
the shared UI module (`mobile/`), never as literal values in screens.

Implemented in `mobile/core/designsystem` (`com.onemind.core.designsystem.theme`): wrap the UI in
`OneMindTheme { … }` and read `OneMindTheme.colors`, `.typography`, `.radius` and `.spacing`.

Only a **light theme** is designed. Dark theme is not designed yet (open question); still,
define colors as semantic tokens so a dark palette can be added later.

## Color tokens

| Token                    | Value      | Use |
|--------------------------|------------|-----|
| `background`             | `#F7F7F5`  | App background (warm off-white) |
| `surface`                | `#FFFFFF`  | Cards, tab bar, inputs |
| `surfaceSubtle`          | `#FAFAF8`  | Full-width button at the bottom of a card (*All receipts*) |
| `border`                 | `#E7E7E3`  | Card borders, tab bar top border |
| `borderStrong`           | `#E2E2DE`  | Inputs, outline buttons, chips |
| `divider`                | `#EFEFEC`  | Row separators inside cards |
| `surfaceMuted`           | `#F1F1EE`  | Chips, free table tile, source chips, quiet heat cell |
| `textPrimary`            | `#14161A`  | Main text, dark tiles, selected chip background |
| `textSecondary`          | `#696E77`  | Labels, meta, inactive tab (canvas `#6B7079`, darkened for WCAG AA) |
| `textTertiary`           | `#5C626C`  | Table row labels |
| `textPlaceholder`        | `#8A8F98`  | Input placeholder (decorative only) |
| `iconMuted`              | `#9BA0A8`  | Dashed/waiting icons, neutral swatches |
| `accent`                 | `#4F46E5`  | Primary buttons, active tab bar, highlights, links |
| `accentPressed`          | `#3730A3`  | Pressed/hover accent |
| `accentSoft`             | `#EEF0FD`  | Hint/insight card background |
| `onAccent`               | `#FFFFFF`  | Text on accent |
| `positive`               | `#15803D`  | Positive deltas, done states |
| `positiveSurface`        | `#ECFDF3`  | Positive delta badge background (*+12.4% vs last Friday*) |
| `negative`               | `#DC2626`  | Risk dots, negative swatch |
| `negativeText`           | `#B91C1C`  | Risk text (*Runs out Sat*), below-par meta |
| `negativeSurface`        | `#FEF2F2`  | Loss badge and icon box background |
| `warningText`            | `#B45309`  | Price changes, unmatched lines |
| `warningStrongText`      | `#92400E`  | Text on warning background |
| `warningSurface`         | `#FFFBEB`  | Variance warning background |
| `warningBorder`          | `#FDE68A`  | Warning card border |
| `selection`              | `#F59E0B`  | Selected tile / heat cell outline |
| `chartMuted`             | `#D5D7DB` (`chartMutedStrong` `#C9CCD1`) | Non-highlighted bars |
| `chartFuture`            | `#EEEFF1`  | Future hours |

Heat ramp (Hourly patterns, 7 steps): `#F1F1EE, #E0E2F8, #C4C8F3, #9AA0EC, #6B71E3, #4338CA, #312E81`.
Tables ramp (6 steps): `#F1F1EE, #E0E2F8, #C4C8F3, #9AA0EC, #6269E1, #4F46E5` (white text from
step 5; step 5 is canvas `#6B71E3` darkened for WCAG AA).

Accent theme options in the design: `#4F46E5` (indigo, default), `#14161A`, `#0F766E`, `#9F1239`.
Treat accent as a single token so it can be switched.

## Typography

- **Inter** (400, 500, 600) for UI. **Inter Tight** (500, 600, 700) for screen titles and large
  figures. Bundled as static TTFs (OFL, licenses in `mobile/core/designsystem/licenses/`).
- Tabular numerals everywhere numbers align (`font-feature-settings: 'tnum'`), plus `cv11`.
- Bundle the fonts in the app (Compose resources); do not load from the network.

| Style          | Size / weight             | Use |
|----------------|---------------------------|-----|
| `displayLarge` | Inter Tight 40 / 600, letter-spacing −0.03em, line height 1 | Hero figure on Today (*48 320 Kč*) |
| `display`      | Inter Tight 36 / 600, −0.03em, line height 1.05 | Hero figures (*312 480 Kč*, *22 410 Kč*) |
| `displaySmall` | Inter Tight 30 / 600, −0.02em | Figure in a detail card (*87 sold*) |
| `title`        | Inter Tight 28 / 600, −0.02em | Screen titles on tabs (*Today*) |
| `figure`       | Inter Tight 22 / 600      | Stat tile values (*18:40*, *3 940*) |
| `headline`     | Inter 17 / 600            | Detail screen titles, card titles |
| `body`         | Inter 15 / 400–500        | Rows, chat text (line height 1.4–1.5); section titles 15 / 600 |
| `bodySmall`    | Inter 13–14 / 400–500     | Secondary rows, buttons in chips |
| `caption`      | Inter 12 / 400            | Meta lines |
| `overline`     | Inter 11 / 600–700, letter-spacing 0.08em, UPPERCASE | Section labels (*PAID SO FAR*, *TONIGHT'S BRIEFING*) |
| `tabLabel`     | Inter 11 / 500 (active 600) | Tab bar |

## Shape and spacing

- Screen side padding **20**. Gap between sections **14–16**. Row gap in cards **12**.
- Radius: cards **16**, inner panels **14**, buttons and inputs **12**, chips **10**, small tags
  **6–8**, checkboxes **7**, table tiles **10**, heat cells **5**.
- Card: `surface` + 1 px `border`, no shadow.
- Rows: min height 44–68 dp, separated by 1 px `divider`; the last row has no divider.

## Components

| Component | Notes |
|-----------|-------|
| **Tab bar** | 5 items in the design, **4 in the first release** (no Assistant, `MVP_SCOPE.md`), icon 22 + label 11. Active: `textPrimary` + 28×3 accent bar on the top edge. White background, top border. Bottom inset for home indicator. |
| **Top bar (detail)** | 56 high, back button 44×44 (icon arrow), title 17/600 + subtitle 12. |
| **Segmented tabs** | Text tabs with 2 px accent underline on selection (*Hour/Day/Week/Month*, *Now/This week*, *Order plan/Idle stock/Count*). |
| **Chips (filter)** | 36 high, radius 10. Selected: `textPrimary` bg + white text. Unselected: white + `borderStrong`. |
| **Primary button** | 52 high (CTA) or 48, radius 12, accent bg. Disabled: `#E2E2DE`/`#E7E7E3` bg + secondary text. A dark (`textPrimary`) variant is used for final approve actions. |
| **Outline button** | White, `borderStrong` border, radius 10–12. |
| **Stepper** | − value + with 44 targets; used in S1 and S3. |
| **Fraction picker** | 4 equal toggle buttons *Full ¼ ½ ¾*. |
| **Stat tile** | Overline label + value; grid of 2–3 columns. |
| **List row** | Leading (avatar initials / swatch / dot / type badge), title + meta, trailing value(s), optional chevron. |
| **Avatar initials** | Initials (*TN*, *EP*) in a 40 × 40 rounded square (radius 12, `surfaceMuted`) in staff rows, as on the canvas; the dark 44 circle is the account button. |
| **Hint card** | `accentSoft` bg, sparkle icon in accent, bold lead sentence + body. Used for generated insights. |
| **Warning card** | `warningSurface` bg, 1 px `warningBorder`, warning icon in `warningText`, text `textPrimary` with a bold lead (canvas). Optional trailing arrow when it links. |
| **Bar chart** | Rounded bars (3 px), optional value labels, axis labels 10–11 px, highlight colors per rule. |
| **Heatmap** | Grid of buttons with accessible labels (*Sat 22:00, 54 guests*). |
| **Floor plan** | Grid of table tiles; Bar spans the row; selected outline `selection`. |
| **Progress row** | Status icon (done ✓ green / running ring accent / waiting dashed), name, meta, optional progress bar 4 px. |
| **Checklist row** | Checkbox 24 (accent when checked), name with strike-through when done. |
| **Chat bubble** | User: dark bubble right, radius 16 16 4 16. Assistant: plain text + cards. Source chips under answers. |
| **Composer** | Attach button, input with accent border (1.5), send button 48 accent. |
| **OTP input** | 6 boxes 56 high, radius 12, focused box accent border. |

Icons: simple stroke icons (1.75–2 stroke, round caps), 22 px in the tab bar. Use one icon set
consistently (e.g. Lucide-like). Never emoji. In the app they are `OneMindIcons` (`ImageVector`s
built from the canvas SVG paths); add new ones there rather than adding an icon library.

### In code (`com.onemind.core.designsystem.components`)

| Component | Composable |
|-----------|------------|
| Card | `OneMindCard`, `OneMindListCard` (for rows) |
| Tab bar | `TabBar` + `TabBarItem` |
| Top bar (detail) | `DetailTopBar` |
| Segmented tabs | `SegmentedTabs` |
| Chips (filter) | `OneMindFilterChip` |
| Primary / outline button | `PrimaryButton` (`Accent`, `Dark`), `OutlineButton` |
| Stat tile | `StatTile` (`Compact`, `Overline`) |
| List row | `ListRow` |
| Avatar initials | `AvatarInitials` |
| Delta / tag badge | `DeltaBadge` (`Positive`, `Negative`, `Neutral`) |
| Hint card / warning card | `HintCard`, `WarningCard` |
| Bar chart | `BarChart` + `Bar` |
| Heatmap | `Heatmap` + `HeatmapRow`, `HeatCell` |
| Floor plan | `FloorPlan` + `FloorTile`, `TileTone` |
| Progress row | `ProgressRow` + `ProgressStatus` |
| OTP input | `OtpInput` |

Not built yet: **Stepper**, **Fraction picker**, **Checklist row** (with the stock screens, phase 6) and
**Chat bubble**, **Composer** (assistant, out of the first release).

## Accessibility

- Touch targets ≥ 44 × 44.
- Contrast 4.5:1 for text (the palette above is chosen for this; check new colors). A test in
  `core:designsystem` checks the text tokens on their surfaces. Two canvas colours fell short and
  were darkened slightly, same hue (owner decision 2026-11-21): `textSecondary` `#6B7079` →
  `#696E77` (4.5:1 on `surfaceMuted`) and tables ramp step 5 `#6B71E3` → `#6269E1` (4.5:1 with white
  text). The canvas still shows the old values.
- Icon-only buttons have content descriptions (*Back to Sales*, *Send*, *Attach a document*).
- Heatmap cells and table tiles expose a spoken label with their value.
- Don't use color alone: risk rows have a dot **and** red text; variance has text too.
- Support dynamic type / font scaling without clipping hero figures.

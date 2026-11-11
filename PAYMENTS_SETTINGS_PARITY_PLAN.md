# Payments-settings parity plan

_Goal: bring the Android **Payments-settings** experience to true parity with iOS — main-Settings placement, the Payments-settings page itself, its overflow gear-menu (with an Edit Username entry), the currency picker (with flags), the bottom-nav Payments tab — and re-audit every iOS commit since `aba8376294` to catch behavioral changes hidden inside commits I previously dismissed as cosmetic._
_Status: **PLAN DRAFTED 2026-05-28.** Implementation begins immediately after drafting. Companion to `ADD_FUNDS_PARITY_PLAN.md` (Add Funds + Edit Username — mostly done); open items from there are carried below._

## Global rules (carried over — applies to every item)

- **iOS repo is read-only.** Reference path: `/Users/warhit/Downloads/projects/Radar`. Never modify.
- **Discuss before code** (CLAUDE.md). This plan IS the discussion artifact. Small obvious fixes proceed directly; non-obvious choices → `DECISIONS.md`.
- **Decisions → `DECISIONS.md`, risks/deferrals → `ISSUES.md`** with date, severity, and revisit criteria.
- **Localization:** Radar-specific strings live ONLY in default `values/strings.xml` (translations come from an external pipeline on Android; the 45-locale rule from CLAUDE.md is iOS-only).
- **Verify:** compile-check (`:Signal-Android:compilePlayProdDebugKotlin :Signal-Android:compilePlayProdDebugJavaWithJavac`) after each change; full `:Signal-Android:assemblePlayProdDebug` at milestones (✦).
- **Commit format:** `Payments parity: <subject>` for parity work; `Mirror iOS <sha>: <subject>` when applying an actual missed iOS commit. Always end with `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- **G-ASSETS / G-STYLE** (from prior plan): any iOS asset used must be brought into Android; buttons/text-fields/pills/toggles match iOS color/radius/spacing as closely as possible.

- Legend: `[ ]` to do · `[~]` partial · `[x]` done · `[s]` skipped · `[d]` deferred · `[?]` open question

---

## A. Settings menu placement (mirror iOS `AppSettingsViewController`)

iOS order (from `Signal/src/ViewControllers/AppSettings/AppSettingsViewController.swift`):
- Profile section
- **section1**: Account → Linked Devices (primary only) → Donate (gated by `BuildFlags.donations`, currently `false` in iOS WIP)
- **paymentsSection** (own section, with unread-count badge)
- (then Chats, Notifications, Privacy, …)

Android today (`AppSettingsFragment.kt`): Payments is around line 464 — **after Appearance / Stories / Notifications / Privacy** (i.e., far down).

- [ ] **A1 — Move Payments up to its own section right after Account/Linked-Devices/Donate** (mirrors iOS section grouping). Keep all existing Android Payments behaviors (unread badge, gating on `paymentsAvailability`, nav action). Visually it becomes the second logical group below the profile + identity rows.

## B. Payments-settings page parity (iOS `PaymentsSettingsViewController` → Android `PaymentsHomeFragment`)

This is the page that opens when the user taps "Payments" in main Settings. Verify each piece — many are already present from the original migration.

- [ ] **B1 — Title.** iOS uses `SETTINGS_PAYMENTS_VIEW_TITLE` ("Payments"). Verify Android title matches and uses the same string idiom.
- [ ] **B2 — Header design (enabled state).** iOS shows: huge centered balance (54pt regular) → conversion line (Title2 secondary) → "Add Money" + "Send" buttons (capsules) → eye toggle in the LEFT navbar item → gear (⚙️) in the RIGHT navbar item. Verify Android matches structure and styling; if not, file a sub-task.
- [ ] **B3 — Recent payments section.** iOS shows up to 4 items (`maxHistoryCount=4`) under a `SETTINGS_PAYMENTS_RECENT_PAYMENTS` header. Verify Android count/header.
- [ ] **B4 — Empty / loading states.** iOS shows "No activity" indicator + a spinner while balance is loading and not hidden.
- [ ] **B5 — Notification observers.** iOS subscribes to: `arePaymentsEnabledDidChange`, `isPaymentsVersionOutdatedDidChange`, `currentPaymentBalanceDidChange`, `paymentConversionRatesDidChange`, `balanceHiddenDidChange`, `amountTypeDidChange`. Verify Android observes equivalents (the last two are part of §G3 from the prior plan).
- [ ] **B6 — 30 s balance-refresh timer** (calls `updateCurrentPaymentBalance`). Verify Android equivalent.
- [ ] **B7 — viewDidAppear refresh + outdated-client banner.** iOS shows a banner if `isPaymentsVersionOutdated`; on appear it refreshes balance + conversion rates. Verify Android.

## C. Overflow gear menu parity (iOS `PaymentSettingsMenuViewController` → Android `PaymentsHomeFragment` overflow / new `PaymentsSettingsMenuFragment`)

iOS opens a separate VC via the gear ⚙️ navbar item. Rows in order:
1. **Set Currency** (accessory = current code)
2. **Bitcoin Unit** (accessory = "sats" / "BTC")
3. **Payments Username** → pushes `RadarUsernameViewController` (`PAYMENTS_USERNAME_SETTINGS_TITLE` = "Payments Username")
4. **View recovery passphrase**
5. **Help** (`CommonStrings.help` → `ContactSupportViewController` with filter `.payments`)
6. **Lightning Logs** (`SETTINGS_PAYMENTS_LIGHTNING_LOGS` = "Lightning Logs")
---
7. **Deactivate payments** (red destructive)
8. **Delete wallet** (red destructive)

Android today (`payments_home_fragment_menu.xml` + `PaymentsHomeFragment.onOptionsItemSelected`): set_currency, lightning_logs, deactivate_wallet, view_recovery_phrase, + a sats/BTC picker dialog (from `d0ae4ae661`). **Missing:** Payments Username, Help, Delete wallet.

- [ ] **C1 — Page-vs-overflow design.** iOS is a separate disclosure-list VC opened from the gear icon; Android is a 3-dot toolbar overflow popup. **Decision needed.** Default plan: keep the toolbar overflow (smaller change). Re-evaluate after the audit completes — if iOS treats this as a primary nav target, build a dedicated fragment. **[?]**
- [ ] **C2 — Payments Username row (NEW).** Add an overflow item → navigate to `EditLightningUsernameFragment`. String `EditLightningUsernameFragment__settings_row_title` = "Payments Username" (matches iOS `PAYMENTS_USERNAME_SETTINGS_TITLE`). This is the "edit username in the payment settings page" the user asked for.
- [x] **C3 — Help row.** Already present (`payments_home_fragment_menu_help` in menu XML). Verified 2026-05-28.
- [x] **C4 — Delete wallet row.** Already present (`payments_home_fragment_menu_delete_wallet`). Verified 2026-05-28; verify confirm-sheet copy matches iOS during the audit pass.
- [x] **C5 — Bitcoin Unit row.** Already present (`payments_home_fragment_menu_bitcoin_unit`). Verified 2026-05-28.

## D. Currency picker parity (iOS `CurrencyPickerViewController` → Android `SetCurrencyFragment` / equivalent)

iOS design:
- Title: `CURRENCY_PICKER_VIEW_TITLE`.
- Two sections: **Preferred** (no header) / **All Currencies** (header `SETTINGS_PAYMENTS_CURRENCY_VIEW_SECTION_ALL_CURRENCIES`).
- Row: 🇺🇸 **flag emoji (28pt)** + name (body) + **uppercase code (footnote, secondary)** + ✓ if selected.
- **Search bar** in nav (`UISearchController`).
- Flag computed from currency-code prefix: first two letters → ISO 3166-1 alpha-2 country → regional-indicator emoji (offset `127397`).

Android today: navigates via `R.id.action_paymentsHome_to_setCurrency` → existing Signal-style currency screen (needs inspection — likely no flags, simpler list).

- [ ] **D1 — Title** = "Set Currency" (`CURRENCY_PICKER_VIEW_TITLE`).
- [ ] **D2 — Two sections** (Preferred / All Currencies) with the iOS header copy.
- [ ] **D3 — Row layout:** flag emoji (28sp leading), name + uppercase code stack, trailing ✓ for the current code.
- [ ] **D4 — Search bar** in the toolbar (Android `SearchView` or `androidx.appcompat.widget.SearchView` mounted on toolbar).
- [ ] **D5 — Flag emoji helper:** Kotlin port of the iOS `flagEmoji(forCurrencyCode:)` (first 2 chars → regional-indicator surrogate pair via `Character.toChars(0x1F1E6 + (c - 'A'))`).

## E. Bottom-nav Payments tab (carried from `ADD_FUNDS_PARITY_PLAN.md` §H1) — **DEFERRED**

See `ISSUES.md` (entry **2026-05-28 — E**) for the full rationale and step-by-step implementation plan. Two blocking constraints: (1) the bottom-nav icon system is Lottie-only (need a `payments_28.json` or a `NavigationDestinationIcon` refactor for static drawables); (2) `PaymentsHomeFragment` uses its own `NavHostFragment` graph and can't be cleanly embedded into MainActivity's Compose `secondaryContent` without a payments `NavHostFragment` wrapper. Mitigation: §A1 moved Payments to the top of Settings (2 taps).

iOS bottom nav (`HomeTabBarController`) has a Payments tab.

- [ ] **E1 — Add a Payments tab to Android `MainActivity`** alongside Chats / Stories / Calls. Use iOS `settings-payments`/`bottom-nav-payments` icon (convert SVG to vector drawable).
- [ ] **E2 — Unread badge** (count from `PaymentFinder.unreadCount` equivalent on Android).
- [ ] **E3 — Gate on `SignalStore.payments.paymentsAvailability` / `paymentsEnabled`** (hide tab if disabled).

## F. Commit audit (re-verified all 58 iOS commits `aba8376294`..`e694108c22`)

User concern: some commits I dismissed as "UI polish" actually contained behavioral changes (one known case: `6c85cf772d`). Audit ran 2026-05-28 via a read-only Explore agent; findings below.

- [x] **F1 — Run the audit.** Done. Section A (confirmed-missing) and Section D (needs-deeper) below; B (verified-present) and C (cosmetic) confirmed for the other 40+ commits.

### F-AUDIT-FINDINGS (commits with behavior I missed; each will become its own `Mirror iOS <sha>: <subject>` commit)

- [x] **F-A1 — `6c85cf772d`** — Satoshi grouping separators. **Already done on Android** — `MoneyView.java:122` calls `numberFormat.setGroupingUsed(true)` for all amount rendering. Audit was a false positive. Verified 2026-05-28.
- [ ] **F-A2 — `c90dd2d790`** — Chat-list **payment snippet**: add `paymentSnippet` case to Android equivalent of `CLVSnippet` (likely in `ThreadRecord` / `ThreadBodyUtil` / `ConversationListItem`) + a `buildPaymentAmountText()` that respects `balanceHidden` + `isSatoshiEnabled`. Was overlapping with §G2 — they're the same fix. **MEDIUM-HIGH**.
- [ ] **F-A3 — `8f92be19fb`** — Chat-list observes `balanceHiddenDidChange` / `amountTypeDidChange` and reloads the visible snippets. Was overlapping with §G3 — same fix. **MEDIUM**.
- [d] **F-A4 — `b847a18d86`** — Wallet-address-loaded observer. **Deferred.** Android already calls `getLightningAddress()` eagerly inside `ProfileUtil`'s upload path (see DECISIONS.md 2026-05-27 entry); the iOS observer is a safety net for late-loading. Acceptable risk profile for now — revisit only if profile uploads are observed missing the Lightning address on fresh installs. Tracking in DECISIONS.md ("Revisit if Android moves profile upload off the eager `getLightningAddress` path").
- [ ] **F-A5 — `aecba8efde`** — Defensive guard: skip profile re-upload if wallet address hasn't loaded yet AND `paymentsEnabled` is true. Add a `PaymentsImpl`-ready check before calling the upload path. **LOW-MEDIUM** (defensive).
- [x] **F-A6 — `e5f586cbd3`** — On-chain address prefetch on load + error toast + revert to Lightning. **Already done** in commit `763a25d802` (Add Funds rewrite) — audit was missing the rewrite. Mark done.
- [ ] **F-A7 — `ae56882371`** — **Backup restore: `restoreOrCreateManifestIfNecessary()` before manifest rotation + `recordPendingLocalAccountUpdates()` for `paymentsEntropy` sync.** Android's backup mechanics may differ from iOS Storage Service — investigate before mirroring. **HIGH (data-loss risk).**
- [ ] **F-A8 — `37007fe8e8`** — Currency picker: (a) remove **AUD/CAD** from the preferred-currencies list (keep USD, EUR, GBP, JPY, CNY); (b) add **flag emojis** to currency rows. (b) is covered by §D3/D5; (a) is a data-list change. **LOW-MEDIUM**.
- [d] **F-A9 — `0e11af806c`** — Push relay (`RadarPushRelay`, FCM equivalent on Android). **DEFERRED** (already in `ISSUES.md`; massive new feature; product decision required).
- [d] **F-A10 — `8353e6b3c9`** — Relay disclaimer / opt-in flip. **DEFERRED** with F-A9.

### Audit Section D — needs-deeper-look (resolved here)

- [x] **D1 — `37007fe8e8`** — pinned-list change (split into F-A8a) + flag emojis (split into D3/D5). Resolved.
- [x] **D2 — `6c85cf772d`** — behavioral parts (formatter, snippet observer) split into F-A1, F-A2, F-A4. Resolved.
- [x] **D3 — `c90dd2d790`** — confirmed behavioral (split into F-A2). Resolved.

## G. Carried-over open items (from `ADD_FUNDS_PARITY_PLAN.md`)

- [ ] **G1 — BOLT11 invoice for Lightning QR** (§B7 / §C2 from prior plan). iOS Lightning QR encodes `lightning:<bolt11>` from LNURL-pay; Android encodes `lightning:<address>`. Implement LNURL-pay fetch with fallback.
- [ ] **G2 — `c90dd2d790`** → `ThreadViewModel.paymentSnippet` chat-list amount (sats unit + balance-hidden masking in chat-list previews).
- [ ] **G3 — `8f92be19fb`** → chat list observes `balanceHiddenDidChange` / `amountTypeDidChange` to refresh when display prefs change.
- [ ] **G4 — Title casing** (§B12 from prior plan): "Add funds" → "Add Funds". Trivial; bundle with another commit.

## H. Open questions

- **C1** — overflow popup vs dedicated menu fragment? Default = keep overflow, reconsider if audit shows iOS treats this as a primary destination.
- **E1** — does Android's existing `MainActivity` use a `BottomNavigationView`? Need to inspect before designing. If it doesn't (Stories may use a different pattern), this becomes a structural change.
- **D4** — Android Toolbar SearchView vs `SearchBar` from Material 3? Existing Signal-Android currency screen may already have a search idiom; reuse if so.

## Suggested execution order (highest-value-first, smallest-blast-radius-first)

1. **F1 (audit)** — already running; results drive multiple downstream items.
2. **C2 (Payments Username row)** — small, isolated, immediate user value; the user explicitly asked for it.
3. **A1 (Settings menu reorder)** — small.
4. **D (Currency picker parity)** — medium; well-scoped.
5. **C3, C4 (Help, Delete wallet rows)** — small each.
6. **C5 (verify Bitcoin Unit reachability)** — verification only.
7. **F2/F3 (apply audit findings)** — as they land.
8. **E (Bottom-nav Payments tab)** — medium-large.
9. **B (Payments-settings page parity)** — verify each sub-item, file sub-tasks as gaps found.
10. **G1 (BOLT11)**, then **G2 / G3** (chat-list).
11. **G4** title casing + any leftover polish.

Each step finishes with a green `compilePlayProdDebug*` (and a green `assemblePlayProdDebug` at the milestones marked ✦ — after #4, #6, #8, #10).

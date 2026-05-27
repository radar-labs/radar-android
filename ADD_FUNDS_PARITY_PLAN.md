# Payments parity plan (Add Funds + Edit Username + nav + assets)

_Goal: bring the Android payments UI/logic to true parity with iOS — Add Funds screen, Edit Username screen, the username-refresh fix, the bottom-nav Payments tab, and a global asset/style-parity rule._
_Status: **IMPLEMENTATION IN PROGRESS (started 2026-05-28).** Living doc — user is still adding items. Each change keeps the migration rules: decisions → `DECISIONS.md`, issues/risks → `ISSUES.md`, status here._

## Global parity rules (apply to every item below)

- [ ] **G-ASSETS — reuse iOS assets.** Any asset the iOS screen uses must be brought into the Android project and used (not a stand-in): `radar-logo`, `bitcoin-icon`, `payments-add-funds-bitcoin`, `payments-add-funds-plus-badge`, the Radar app-icon art, etc. iOS SVGs → Android **vector drawables** (`res/drawable/*.xml`, paths copied 1:1 + fill colors); iOS PNGs → density buckets. Replaces every interim `ic_coin_24`/`ic_bitcoin_lightning_24` stand-in I used.
- [ ] **G-STYLE — match iOS styling.** Buttons, text fields, pills, toggles, fonts, colors, corner radii, and spacing should be **as close as possible** to the iOS version (e.g., accent blue `#0069FE`, gray fill `rgba(120,120,128,0.16)`, capsule buttons, the segmented-switcher look) rather than defaulting to generic Signal-Android styles.

- **iOS source of truth:** `Signal/src/ViewControllers/AppSettings/Payments/PaymentsTransferInViewController.swift` (non-onboarding `buildLayout()`), @ HEAD `e694108c22`.
- **Android target:** `app/.../payments/preferences/addmoney/PaymentsAddMoneyFragment.java` + `res/layout/payments_add_money_fragment.xml` (+ `PaymentsAddMoneyViewModel`/`Repository`, `BreezSdkWrapper`).
- Legend: `[ ]` to do · `[~]` partial today · `[x]` done · `[?]` open question.

## iOS layout, top → bottom (the target)
toolbar (Done left / Share right) → **instruction** → **segmented network switcher** → **QR (blue border + center logo + spinner)** → **address row (address + pencil)** → **Copy pill** → flexible spacer.

## A. User-flagged discrepancies

- [ ] **A1 — Chain switcher is the wrong control.** iOS: a **segmented switcher** — a gray rounded pill (`#E9E9EA`, radius 24, height 48) containing two equal tabs, "Lightning" (bolt icon) and "Onchain" (bitcoin icon); the selected tab is white with accent-blue text/icon, the other is transparent with black-50% — placed **above** the QR. Android today: a single `MaterialButton` "Show on-chain address" at the **bottom**. → Build a two-tab segmented control above the QR (custom two-`MaterialButton` pill or `MaterialButtonToggleGroup`) with icons + selected styling; drive `selectedNetwork`.
- [ ] **A2 — QR UI differs and has no center logo.** iOS: white **rounded square** (`min(screenWidth-80, 300)`), **2.5pt accent-blue border**, radius 16; a **52×52 white box with the radar logo (42×42) centered on the QR**; an activity **spinner** while generating; QR inset 12pt; nearest-neighbor scaling. Android today: `QrView` in a neutral border box, fixed 240dp, **no logo, no spinner, no accent-blue border**. → Wrap QR in a `FrameLayout`: `QrView` + centered logo box + spinner; accent-blue rounded border; responsive size. (QR must use high error-correction so the center logo doesn't break scanning — verify `QrView` EC level. See C1 for the logo asset.)
- [ ] **A3 — Edit username is a separate button, not a pencil.** iOS: a **pencil icon** (SF Symbol `pencil`, primary-40%) sits **beside the address** in the address row, and is **hidden when Onchain** is selected. Android today: a separate full-width "Edit username" `MaterialButton`. → Remove the separate button; add a pencil `ImageButton`/`MaterialButton` (icon-only) next to the address `TextView` in a horizontal row; hide it in onchain mode; tap → `EditLightningUsernameFragment`.

## B. Additional discrepancies found

- [ ] **B1 — Layout order / structure.** iOS order = instruction → switcher → QR → address(+pencil) → Copy → spacer. Android = instruction → QR → address → Copy → Edit → Toggle → bottom description. → Reorder to match iOS; the switcher moves above the QR; the separate Edit + Toggle buttons go away (folded into A1/A3).
- [ ] **B2 — Instruction text.** iOS `PAYMENTS_ADD_FUNDS_INSTRUCTION` = "Send Bitcoin to the following Address and Network:". Android `PaymentsAddMoneyFragment__send_bitcoin_over_lightning` = "Send Bitcoin over the Lightning Network to the following address:". → Update the Android string to the iOS wording (it now references the network switcher).
- [ ] **B3 — Share affordance missing.** iOS has a **Share** button in the toolbar (right) that shares the currently-displayed address. Android has none. → Add a share action (toolbar menu item or icon) → share the current address.
- [ ] **B4 — Copy button styling.** iOS: gray-fill **capsule pill**, primary text, trailing copy icon (`square.on.square`), height 48. Android: blue `Signal.Widget.Button.Small.Primary`, no icon. → Restyle Copy to a gray capsule with a trailing copy icon.
- [ ] **B5 — On-chain address formatting.** iOS renders the on-chain address **monospace 14pt, in 4-char groups with alternating primary/ternary colors, up to 3 lines, char-wrapped, centered**. Android shows it in the same `TitleLarge` style as the Lightning address. → Format the on-chain address grouped/monospace; keep Lightning as `username` + accent-blue `@domain`.
- [ ] **B6 — On-chain prefetch + spinner + error handling.** iOS **prefetches** the on-chain (taproot) address on load; toggling to Onchain shows a **spinner** while fetching and, on failure, a **toast** ("Couldn't load Bitcoin address", `PAYMENTS_ADD_FUNDS_ONCHAIN_FETCH_FAILED`) and **reverts to Lightning**. Android fetches lazily on toggle (`SimpleTask`), no spinner/toast/revert. → Prefetch on load; spinner during fetch; error toast + revert on failure.
- [ ] **B7 — Lightning QR should be a BOLT11 invoice.** iOS Lightning QR prefers `lightning:<bolt11>` — it resolves the Lightning address via **LNURL-pay** (`GET https://<domain>/.well-known/lnurlp/<name>` → `callback?amount=0` → `pr`) and falls back to `lightning:<address>` / `lightning:<username>@radar.cash`. Android encodes `lightning:<address>` only. → Implement the LNURL-pay BOLT11 fetch (OkHttp/coroutine) with the same fallback. _(Was deferred during the migration; this is the parity item.)_
- [ ] **B8 — Address "Loading…" placeholder.** iOS shows `PAYMENTS_WALLET_ADDRESS_LOADING` = "Loading..." (40%-alpha) until the address is available. Android shows whatever the ViewModel emits (can be a sentinel/blank). → Show a "Loading…" placeholder until the address loads.
- [ ] **B9 — Extra bottom description.** iOS's final non-onboarding layout has **no** bottom description (only the top instruction). Android has an extra `LearnMoreTextView` ("To add funds…") at the bottom. → Remove it (or confirm we want to keep an Android-only learn-more).
- [ ] **B10 — Copy toast text.** iOS "Wallet Address Copied" (`SETTINGS_PAYMENTS_ADD_MONEY_WALLET_ADDRESS_COPIED`). Android "Copied to clipboard". → Align the toast text.
- [ ] **B11 — Copy/Share payload.** iOS copies/shares the **raw address** (not the `lightning:` URI). Confirm Android copies the plain address (it does today via `currentAddress()`), and share does the same.
- [ ] **B12 — Title casing.** iOS title = "Add Funds"; Android = "Add funds". Minor — align if desired.

## C. Asset / backend dependencies (blockers for some items)

- [ ] **C1 — Radar logo asset for the QR center (A2).** iOS uses `radar-logo`. Android has **no Radar logo** drawable (the launcher-icon/splash asset task is deferred — see `ISSUES.md`). Options: (a) convert `radar-logo.svg` → `res/drawable/radar_logo.xml` (also unblocks icon/splash), or (b) interim stand-in with `ic_coin_24` / `ic_bitcoin_lightning_24`. Decide before A2.
- [ ] **C2 — LNURL-pay HTTP for BOLT11 (B7).** Needs a network call (OkHttp is available) on a background thread; handle failures by falling back to the address.

## D. Open questions / to-investigate (user is adding)

- [?] _(add findings here)_

## E. Edit Username screen parity (iOS `RadarUsernameViewController` → Android `EditLightningUsernameFragment`)

iOS reference: `Signal/.../Payments/RadarUsernameViewController.swift`.
- [ ] **E1 — Layout/UI.** iOS: centered logo (`radar-logo`, 82pt) → title "Your Radar Username" (title2, semibold) → subtitle (50% alpha) → input row → status label → flexible spacer → Confirm (filled accent, radius 14, 52pt) → "Skip this step" text button; content sits in the upper third (scroll root, keyboard slides over). Android today: coin stand-in + plain field + two `Small.Primary` buttons. → Rebuild to match (use `radar-logo`).
- [ ] **E2 — Input field.** iOS: a **pill-shaped** field (`grayFill`, corner 26, height 52) with the username, plus a separate **`@radar.cash` suffix label** in accent blue beside it; clear button while editing; max length = `WalletAddressEditViewController.addressGlyphLimit`. Android today: a bare `EditText` + a plain `@radar.cash` TextView. → Pill container + accent-blue domain suffix + glyph limit + clear button.
- [ ] **E3 — Button styles.** iOS Confirm = filled accent capsule/14-radius, white bold; Skip = borderless accent text. Android = two `Small.Primary` buttons. → Match iOS (accent confirm + text skip).
- [ ] **E4 — Availability status.** iOS: "✓ Username Available" (green `#46B827`) / "✗ Username Unavailable" (accent red), debounced 500ms. Android has the debounce + colors but no ✓/✗ glyphs or exact styling. → Align text/glyphs/colors.

## F. Username refresh after edit — MISSED iOS behavior (functional bug)

- [ ] **F1 — Add Funds doesn't refresh after a username change.** **Root cause:** iOS posts `PaymentsImpl.walletAddressDidLoad` when the wallet address loads/changes (introduced in `07343eab15`, applied) and the Add Funds screen **observes it to refresh** — that observer was added in **`6c85cf772d`**, which I **skipped** as "UI polish." So on Android the user must leave & re-open Add Funds to see the new username. → Mirror the refresh: after `EditLightningUsernameFragment` registers a new username, the Add Funds screen must re-fetch/refresh the address + QR (re-fetch on resume, a `FragmentResult`, or an address-changed LiveData — pick the cleanest; the cached `BreezSdkWrapper`/ViewModel address must be invalidated). Document the choice in `DECISIONS.md`.

## G. Commit audit — behavioral changes missed inside skipped/UI commits

Re-checked the commits I skipped as "iOS-rendering polish"; these had **non-cosmetic** behavior that needs mirroring (the rest of those commits stays cosmetic-skip):
- [ ] **G1 — `6c85cf772d`** → the `walletAddressDidLoad` Add Funds refresh observer (= F1 above). Re-open & apply.
- [ ] **G2 — `c90dd2d790`** → `ThreadViewModel.paymentSnippet`: the chat **list** preview shows the real payment amount ("You sent X sats" / "X sent you Y"), respecting sats/hidden. Android shows generic `ThreadRecord_payment` = "Payment". → Enrich the Android thread snippet for payment messages.
- [ ] **G3 — `8f92be19fb`** → chat list observes `balanceHiddenDidChange`/`amountTypeDidChange` to refresh when display prefs change. Android's `showInSats`/`balanceHidden` changes don't notify list/bubble surfaces. → Add a refresh/observe path so toggling sats/hidden updates the chat list + bubbles (ties to G2 and the deferred-to-#58 sats breadth).
- [ ] **G4 — Re-verify the rest.** Spot-check the other `[s]` UI commits (`37007fe8e8`, `3b4634c7f7`, `81edb47849`, chat bubbles) — confirm remaining diffs are purely cosmetic/iOS-rendering (currency-flag emoji, bubble arrows, padding) with no further behavioral misses. Log any new finding here.

## H. Payments tab in the bottom navigation

- [ ] **H1 — Add a Payments tab to the Android bottom nav** (iOS `HomeTabBarController` has a Payments tab). Android `MainActivity` uses a bottom nav (Chats / Calls / Stories…). → Add a Payments destination/tab that opens the payments home, gated on `paymentsAvailability`, with the **correct icon matching iOS** (per G-ASSETS — use the iOS payment tab-bar icon/`payment-28`, brought into Android). Investigate `MainActivity`/`MainNavigationBar` + nav graph to wire it.

## Notes
- Scope is the **main** Add Funds screen (`PaymentsAddMoneyFragment`). The onboarding variant (`PaymentsOnboardingAddFundsFragment`) loosely mirrors iOS `buildOnboardingLayout()`; decide later whether to align it too.
- Android's `PaymentsAddMoneyViewModel`/`Repository` currently expose only `selfAddressB58` (the Lightning address). Supporting B5–B8 cleanly may mean extending the ViewModel (lightning address + on-chain address + loading state) rather than driving everything from the Fragment.

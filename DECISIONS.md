# Decisions Log

_Append-only. Non-obvious choices made during the iOS→Android mirror._

## 2026-05-27 — Phase 0 — Base branch = `mapping-ios-commits`
**Context:** Choosing the branch to mirror onto. Initial impression (later corrected) was that `add-breez` had the fuller Lightning backend.
**Decision:** Base on `mapping-ios-commits` and commit directly to it. Git showed it already *contains* all of `add-breez` plus `42fde6304c` (balance show/hide) and `97f461b44e` (12-word seed) — i.e. it is the superset with the most complete Lightning state.
**Alternatives considered:** Re-base on `add-breez` (would discard those 2 commits); new dedicated branch (user preferred committing directly).
**Confidence:** high
**Revisit if:** the user wants the balance-toggle / 12-word-seed work re-mirrored from iOS instead of kept.

## 2026-05-27 — Phase 0 — Traversal = per non-merge commit (50)
**Context:** iOS range has 58 commits incl. 8 merges and a feature branch whose onboarding work appears twice.
**Decision:** One Android commit per iOS non-merge commit. The 8 merge commits are skipped (their content is the sum of constituents already mirrored). The duplicated onboarding (`043b28c555` vs `07343eab15`) is de-duped — apply the first, then only the delta of the second.
**Alternatives considered:** First-parent/PR-level traversal (42 units) — rejected as too coarse (single commits would bundle 5+ distinct UI changes, hurting reviewability).
**Confidence:** high
**Revisit if:** ordering across the de-duped onboarding causes dependency gaps.

## 2026-05-27 — Phase 0 — Localization approach
**Context:** iOS edits all 45 `.strings` files per its CLAUDE.md; Android has ~67–81 locale variants.
**Decision:** New strings → default `app/src/main/res/values/strings.xml` only (translations arrive via the translation platform). Mechanical/identifier substitutions (e.g. brand names, "24"→"12") may be applied across locale files, matching existing repo practice (`97f461b44e`).
**Alternatives considered:** Hand-translate all locales (rejected — not Android/Signal practice, error-prone).
**Confidence:** high
**Revisit if:** the user wants full locale coverage for new strings.

## 2026-05-27 — Phase 0 — Verification strategy
**Context:** Signal Android builds are slow; 50 full builds is impractical.
**Decision:** Per commit, compile-check the affected module(s) (e.g. `:app:compilePlayProdDebugKotlin` / `:app:lint…` scoped). Run a full `:app:assemblePlayProdDebug` at the milestones in MIGRATION_PLAN.md and at the end.
**Alternatives considered:** Full build per commit (too slow); milestone-only (errors surface too late).
**Confidence:** medium
**Revisit if:** compile-checks miss resource/manifest errors that only a full build catches — tighten milestone cadence.

## 2026-05-27 — Phase 0 — iOS-only artifacts not mirrored
**Context:** Some iOS commits touch iOS-only files.
**Decision:** Never mirror `*.xcodeproj/project.pbxproj`, `Podfile`/`Podfile.lock` (translated to Gradle deps only when a real dependency changes), `Signal-Info.plist` (→ manifest/strings), `ci_scripts/test_apns_push.py`, the iOS `CLAUDE.md`, `.xcassets` containers (→ Android drawable/mipmap), and `test/Assets/*`.
**Confidence:** high
**Revisit if:** a Podfile change reflects a genuine SDK version bump (then mirror as a Gradle dependency change).

## 2026-05-27 — iOS aba8376294 — Edit username screen + display prefs
**Context:** iOS PR #2 ("Add the new edit username screen") bundles: a new RadarUsernameViewController (edit Lightning username), centralization of display prefs into PaymentsDisplayPreferences (sats/BTC + hide-balance, with change notifications), and a tap-to-toggle-sats gesture on the balance.
**Decision:** Built `EditLightningUsernameFragment` (Kotlin, Fragment+XML, in `payments/preferences/addmoney/`) reachable via an "Edit username" button on the receive screen (`PaymentsAddMoneyFragment`); availability is debounced (500ms) via Breez `checkLightningAddressAvailable`, confirm calls `registerLightningAddress`. Added `showInSats` + `balanceHidden` to `PaymentsValues` (the PaymentsDisplayPreferences analog). Wired the **existing** home eye-toggle (`42fde6304c`) to persist `balanceHidden`, and made the balance tappable to toggle `showInSats`. Sats rendering is localized to the home balance via a `renderBalance()` helper using `Money.Satoshi.serializeAmountString()`.
**Alternatives considered:** (a) Defer the username screen — rejected, it's backable (`registerLightningAddress` already used in `BreezSdkWrapper`). (b) Thread sats through `MoneyView` globally — deferred; that breadth belongs with iOS #58 "Sats by default". (c) Notifications/observers like iOS — used persisted prefs + existing LiveData instead (Android idiom).
**Confidence:** medium — compile-checked, not UI-run; the balance-toggle persistence and sats display are localized.
**Revisit if:** Breez `registerLightningAddress` cannot *change* an already-registered username at runtime (see ISSUES), or when #58 generalizes sats display.

## 2026-05-27 — iOS 1560256bb9 — Skipped (Android already equivalent)
**Context:** iOS removed presenting the payments-settings form sheet over the conversation after `didSendPayment` — an artifact of iOS presenting the send flow modally over the chat.
**Decision:** Skipped — no Android change needed. Android sends in-chat payments via a dedicated `PaymentsActivity` launched with `CreatePaymentFragmentArgs.setFinishOnConfirm(true)` (`AttachmentManager.selectPayment`). `ConfirmPaymentFragment` (line ~64) checks `finishOnConfirm` and calls `requireActivity().finish()`, returning to the conversation without ever showing payments settings. The behavior the iOS commit introduces is already the Android behavior.
**Alternatives considered:** Force-add a no-op change to keep a code commit — rejected as noise.
**Confidence:** high
**Revisit if:** the in-chat send flow is ever changed to navigate into the payments dashboard after confirmation.

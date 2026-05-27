# Migration Plan

- **Source:** iOS repo `Radar`, commits `aba8376294`..`e694108c22` (2026-04-30 → 2026-05-22) on `main`.
- **Target:** `signal-fork-android`, base branch **`mapping-ios-commits`** (commits made directly on it).
- **Total iOS commits in range:** 58 = **50 non-merge** (mirror candidates) + **8 merges** (skipped; captured via constituents).
- **Status legend:** `[ ]` pending · `[x]` done · `[s]` skipped · `[d]` deferred · `[!]` blocked
- **Commit message format:** `Mirror iOS <short-sha>: <subject>`
- **Verify:** compile-check affected module(s) per commit; full `:app:assemblePlayProdDebug` at milestones (✦) and at end.

## Pre-migration
- [x] **Setup commit** — DONE. Committed ktlint fix + `ic_coin_24.xml` (`3a4d02f4a5`) and migration docs (`5ecc42ca63`). JDK 17 pinned via `JAVA_HOME`; baseline `:Signal-Android:assemblePlayProdDebug` green (4m12s).

## Commits (chronological)

- [x] `aba8376294` | 04-30 | feature | Add the new edit username screen (#2) → **DONE.** New `EditLightningUsernameFragment` (receive screen entry) backed by Breez `registerLightningAddress`/`checkLightningAddressAvailable`; display-pref store (`showInSats`/`balanceHidden`) in `PaymentsValues`; home balance: persist hide/show + tap to toggle sats/BTC. (skip: iOS test jpg; balance hide/show pre-existed via `42fde6304c`)
- [x] `496108d18f` | 04-30 | feature | new receive screen design (#3) → **DONE.** Restructured `payments_add_money_fragment` (instruction text → bordered QR → `username@radar.cash` with domain tinted → copy/edit buttons → reworded description). (skip: Podfile; on-chain taproot fetch — commented out on iOS too, see ISSUES)
- [s] `1560256bb9` | 04-30 | bugfix | Do not show payments settings after payment confirmation from conversation (#6) → **SKIPPED — already equivalent.** Android's in-chat send uses a separate `PaymentsActivity` with `finishOnConfirm=true` (`AttachmentManager.selectPayment`), so `ConfirmPaymentFragment` finishes back to the chat; it never presents payments settings (the iOS-presentation-specific bug). See DECISIONS.md.
- [x] `6315c2fd19` | 05-01 | ui | More UI changes (#4) → **DONE (residual only).** Rebranded the one remaining "MobileCoins" payment string to Radar/Lightning. Rest already-present or non-mapping: amount-format unification is already central in `MoneyView`; payment copy was already Lightning-rebranded by `add-breez`; iOS icon swaps are iOS assets; "hide sticker button" has no Android analog (no standalone sticker toggle in the input). See DECISIONS.md.
- [s] `d674a1a072` | 05-01 | bugfix | Fixes for crashing while filtering chat list by unread (#7) → **SKIPPED — iOS-platform-specific.** Fix removes a `UITableView.performBatchUpdates` wrapper around `filterControl.startFiltering` (a UIKit crash) + deletes debug prints. Android's unread filter is RecyclerView + `ConversationFilterRequest` (no `performBatchUpdates`/`filterControl`); no analogous bug. See DECISIONS.md.
- [x] `b35e77bc82` | 05-01 | feature | Hide balance/amount from receive banner and low balance alert (#9) → **DONE (partial).** Masked the chat-list unread-payments banner amount (`UnreadPayments.getDescription`) with •••••• when `balanceHidden`. Insufficient-balance part N/A: Android's send flow validates inline (`InputState`), no alert embedding the balance. See DECISIONS.md.
- [x] `d0ae4ae661` | 05-01 | feature | New payment settings (#8) → **DONE (delta only).** Android already has the payment-settings menu (PaymentsHome toolbar overflow: currency, recovery, help, deactivate); added the net-new **Bitcoin Unit (BTC/sats) picker** dialog setting `showInSats`. (skip: iOS CLAUDE.md, ci_scripts/test_apns_push.py — iOS-only). See DECISIONS.md.
- [s] `7ac08be7bf` | 05-01 | bugfix | Fix for crash on filtering chat list by gesture (#10) → **SKIPPED — iOS-platform-specific** (same class as `d674a1a072`). Removes a `UITableView.performBatchUpdates` wrapper in the gesture-driven filter; Android's RecyclerView filter has no analog. See DECISIONS.md.
- [s] `f1a85d3fbe` | 05-07 | feature | finish the new send payment contact selection flow (#11) → **SKIPPED — Android equivalent exists.** Android already has `PaymentRecipientSelectionFragment` (send-to-contact) + `PaymentsTransferFragment`/`PaymentsTransferQrScanFragment` (send-to-address + QR), both wired from the PaymentsHome send menu. iOS unifies these into one "send to" screen — an iOS-specific UX, not re-worked. See DECISIONS.md.
- [d] `b0bd09c807` | 05-07 | branding | App name→Radar / icon / "sat"→"sats" (#12) → **PARTIAL.** app_name already "Radar" (done); "sat"→"sats" already satisfied (Android uses "sats" throughout, no singular "sat" identifier). **Launcher icon DEFERRED** — still Signal's; needs proper Android adaptive + legacy-density assets (converges with iOS #24). See ISSUES.md.
- [x] `07343eab15` | 05-08 | feature | Add the payment onboarding flow screens → **DONE.** New `PaymentsOnboardingActivity` + nav graph (intro → add-funds → deposit-received → setup-complete), launched once after a new registration (`RegistrationActivity`, gated by `paymentsOnboardingShown`). Username step omitted (auto-derived; editable via receive screen). See DECISIONS.md / ISSUES.md.
- [x] `bc4dd06d2b` | 05-10 | bugfix | Fix payments onboarding flow and add funds screen (#13) → **DONE (partial).** Added the **AddFundsIntro** onboarding step (intro → add-funds-intro → add-funds). Skipped iOS-specific perf/threading fixes (shared CIContext in QR generator, `Task.detached` enablePayments, warn-not-throw on address load, conversion-rate guard) — Android's QR/payments code differs, no analog. See DECISIONS.md.
- [s] `c90dd2d790` | 05-10 | feature | Add chat interface payment UI updates (#14) → **SKIPPED — iOS-rendering-specific / already-present.** iOS rewrites the `CVComponentArchivedPayment`/`PaymentAttachment` bubbles (CVComponent architecture) + adds a send-confirm VC. Android already renders payment messages via `PaymentMessageView.kt` and already has the full send-confirm flow (`ConfirmPaymentFragment`). The sats-unit/balance-hidden-in-bubble + richer chat-list snippet are deferred to the sats-default commit (`e694108c22` #58). See DECISIONS.md.
- [s] `4f48dca6a9` | 05-10 | chore | Add screenshot for PR #13 → SKIP (iOS doc asset; cancels with next).
- [s] `2544b3c13f` | 05-10 | chore | Remove accidentally added screenshot → SKIP (cancels previous).
- [ ] `8f92be19fb` | 05-11 | ui | Update chat interface UI → chat bubble refinements.
- [ ] `0700d00c3a` | 05-11 | config | Disable Signal promotional megaphone fetching (#13) → gate `RetrieveRemoteAnnouncementsJob`. (paired with revert below)
- [d] `043b28c555` | 05-11 | feature | Onboarding flow (#14) → DEDUP of `07343eab15`; apply only delta vs current tree (verify at execution).
- [s] `ab1ee03ebc` | 05-11 | merge | Merge main into chat-interface → SKIP (captured via constituents).
- [s] `6bb5e64b4a` | 05-11 | merge | Merge PR #15 chat-interface → SKIP (captured via constituents).
- [ ] `d03c5c5f94` | 05-11 | bugfix | Correctly check and set username from onboarding flow → onboarding username logic.
- [ ] `1a21f2f46a` | 05-11 | feature | use existing username if available and update the screen → prefill username + string.
- [ ] `69784aaa79` | 05-12 | revert | revert commenting out signal's promotions → re-enable megaphone fetch (revert of `0700d00c3a`).
- [s] `68a5b76c2d` | 05-12 | merge | Merge main into set-username-from-onboarding → SKIP.
- [s] `cd62ac850e` | 05-12 | merge | Merge PR #16 set-username-from-onboarding → SKIP.
- [ ] `f21e3145bc` | 05-12 | ui | update splash screen → Radar splash assets.
- [s] `ee9a028190` | 05-13 | merge | Merge PR #17 update-splash-screen → SKIP.
- [ ] `ef746878d2` | 05-13 | style | add a space between balance and currency → format tweak.
- [ ] `37007fe8e8` | 05-13 | ui | update UI from review comments → currency picker, payment cell/menu, chat-list cell, tab bar.
- [ ] `6c85cf772d` | 05-14 | ui | payments UI refresh and branding updates → bubbles, currency picker, settings/detail/history + strings.
- [ ] `3b4634c7f7` | 05-14 | ui | update the UI of the "Add Funds" screen → add-funds layout.
- [ ] `81edb47849` | 05-14 | style | add padding → add-funds spacing.
- [d] `0e11af806c` | 05-15 | feature | Add support for radar push relay (#19) → FCM push-relay equivalent. **Higher risk** — likely DEFER, see ISSUES.
- [s] `6dfdedcd5c` | 05-15 | merge | Merge PR #18 ui-fixes → SKIP (captured via constituents).
- [ ] `c349ec12a9` | 05-16 | style | Move payments tab up a bit in the settings page → settings ordering.
- [ ] `e5f586cbd3` | 05-16 | feature | Finalize the UI for onchain Add Funds → onchain add-funds UI + strings.
- [s] `192f5fdd5d` | 05-16 | merge | Merge PR #21 add-funds-onchain → SKIP.
- [ ] `58e9dc6118` | 05-17 | ui | Update the UI of onchain address → onchain address layout.
- [d] `ae56882371` | 05-18 | bugfix | Fix backup restore issue (#20) → registration + payments-helper restore. **Backup path differs** — verify/defer.
- [ ] `951716f333` | 05-18 | feature | Add breez logs (#22) → Lightning logs viewer settings screen + logger util.
- [ ] `85485a8b7d` | 05-18 | bugfix | Fix compatability with cake (#23) → `BreezSdkWrapper` invoice/address compat.
- [ ] `1ce180c53e` | 05-18 | branding | Change app icon (#24) → new launcher icon.
- [ ] `18c479ada0` | 05-18 | bugfix | Fix send payment crashing + minor UI → send-payment crash.
- [ ] `5b4b9f52f0` | 05-18 | branding | Replace Signal/MobileCoin refs with Radar/Lightning → string substitutions. **Apply carefully** (not every "Signal" is replaceable).
- [s] `da5db7290c` | 05-18 | merge | Merge PR #26 replace-signal-mobcoin-reference → SKIP.
- [d] `42e070db35` | 05-19 | dependency | Update breez SDK (#25) → bump Breez Gradle dependency. Verify Android Breez dep exists; defer if not resolvable.
- [s] `fb97d0a9b4` | 05-19 | feature | Switch to 12 words seed (#27) → SKIP/DEDUP (already done via `97f461b44e`); verify parity, mirror deltas only.
- [ ] `16f8ccc4a9` | 05-19 | feature | Add delete wallet option (#28) → delete-wallet menu + helper logic.
- [ ] `10317857ad` | 05-19 | ui | Change button text and remove onboarding banners → splash button + remove GetStarted banner.
- [ ] `b8fee2bde8` | 05-19 | feature | Add signup screen to the onboarding → registration sign-up screen.
- [ ] `4f069a4e30` | 05-20 | bugfix | Fix QR code not compatible with Cake → payment QR encoding.
- [d] `8ab30cb69e` | 05-21 | bugfix | Fix backup using mobcoin (#29) → archived-payment + backup archiver. **Backup path differs** — verify/defer.
- [d] `9316ae4516` | 05-22 | docs | Update README with proper Radar links and info → apply Radar branding to Android README if relevant, else SKIP.
- [ ] `b847a18d86` | 05-22 | bugfix | Reupload wallet address after wallet address loaded (#30) → re-upload Lightning address to profile post-load.
- [s] `577cf1a63f` | 05-22 | docs | Fix syntax in README → fold into README decision above.
- [ ] `aecba8efde` | 05-22 | bugfix | Fixes for crash on unwrapping of paymentsRef (#32) → null-safety around payments accessor.
- [ ] `8353e6b3c9` | 05-22 | feature | Add relay disclaimer (#31) → relay disclaimer in registration/permissions (depends on push relay #19).
- [ ] `e694108c22` | 05-22 | feature | Sats by default → default display unit = sats.

## Milestones (✦ full build + review checkpoint)
- ✦ After `b0bd09c807` (#12, branding) — first ~10 applied.
- ✦ After `f21e3145bc` (splash) — onboarding + chat UI landed.
- ✦ After `81edb47849` (padding) — UI refresh series done.
- ✦ After `1ce180c53e` (#24, icon) — onchain + breez logs done.
- ✦ After `b8fee2bde8` (signup) — wallet/seed/branding done.
- ✦ After `e694108c22` (HEAD) — final full build + `MIGRATION_REPORT.md`.

## Counts (initial estimate)
- Apply: ~40 · Skip: ~6 (2 screenshots, 12-word-seed dedup, README syntax, + iOS-only sub-parts) · Defer (initial): ~6 (push relay, relay-dependent disclaimer risk, both backup fixes, breez SDK bump, onboarding dedup, README) · Merges skipped: 8.
- These will firm up during execution; every change is logged in DECISIONS.md / ISSUES.md.

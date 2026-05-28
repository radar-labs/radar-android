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

## 2026-05-27 — iOS 6315c2fd19 — More UI changes (#4): applied residual only
**Context:** iOS #4 bundled: amount-format unification (`formattedBalance`), payment-icon swaps, hiding the chat-input sticker button, balance-hidden in chat bubbles, and a payment-copy rebrand (MobileCoin/Signal → Radar/Bitcoin-over-Lightning + Cake/Spark seed messaging).
**Decision:** Applied only the **residual rebrand** on Android — one string (`PaymentsHomeFragment__you_can_use_signal_to_send_and`) still said "MobileCoins"; updated it to the Radar/Lightning wording. Everything else was already satisfied or doesn't map: (a) amount formatting is already centralized in `MoneyView` (no per-call-site threading needed); (b) payment copy was already Lightning-rebranded on `add-breez` (only the one string remained); (c) iOS icon swaps (`send-mob-24`→`payment-28`, `about-mobilecoin`→`activate-payments`) are iOS asset names — Android payment UI uses its own icons; (d) "hide sticker button" has no Android analog — Android has no standalone sticker toggle in the input (stickers live in the emoji keyboard + a suggestion strip); (e) the broader Signal→Radar copy + Cake/Spark seed messaging is handled by the dedicated rebrand commit `5b4b9f52f0` (#44).
**Alternatives considered:** Hide Android's emoji/media-keyboard toggle to mimic "hide sticker" — rejected; it would remove emoji access, not equivalent. Re-thread sats/format through Android amount displays — deferred to #58.
**Confidence:** high (residual string change is trivial and XML-validated).
**Revisit if:** #44 needs the remaining payment-copy/seed rebrand (expected).

## 2026-05-27 — iOS d674a1a072 — Skipped (iOS-platform-specific crash)
**Context:** iOS fixed a crash when filtering the chat list by unread — caused by wrapping `filterControl?.startFiltering(animated:)` / `stopFiltering` inside `tableView.performBatchUpdates { … }`. The fix removes the batch-update wrapping. It also deleted two stray `print()` debug statements in `SendPaymentCompletionActionSheet`.
**Decision:** Skipped — no Android analog. Android's chat list is a `RecyclerView` driven by `ConversationListFragment` + `ConversationFilterRequest` (pull-to-filter), not a `UITableView` with a `filterControl` and `performBatchUpdates`. The specific UIKit crash cannot occur. The `print()` removals are iOS-only debug cleanup.
**Alternatives considered:** Audit Android's filter for any unrelated crash — out of scope for this commit; Android filtering is stable in the baseline build.
**Confidence:** high
**Revisit if:** a crash report shows an analogous issue in Android's conversation-list filtering.

## 2026-05-27 — iOS b35e77bc82 — Hide amounts when balance hidden (#9): partial
**Context:** iOS masks the amount (••••••) in two places when the balance is hidden: the chat-list payment notification banner, and the insufficient-balance alert (generic message instead of embedding the balance).
**Decision:** Applied the **banner masking** — `UnreadPayments.SingleRecipient.getDescription` now shows •••••• instead of the amount when `SignalStore.payments().balanceHidden` (the pref added in commit #1). The **insufficient-balance alert** part does not map: Android's create-payment flow validates inline via `InputState` (disables send when amount > balance); there is no alert that embeds the balance to mask. No new string needed.
**Alternatives considered:** Add a hidden-balance insufficient-funds string anyway — rejected, there's no UI surface that shows it.
**Confidence:** high
**Revisit if:** Android adds an insufficient-balance dialog that reveals the balance.

## 2026-05-27 — iOS d0ae4ae661 — New payment settings (#8): added Bitcoin Unit picker
**Context:** iOS extracted a dedicated `PaymentSettingsMenuViewController` listing: Set Currency, Bitcoin Unit (sats/BTC picker), View Recovery Passphrase, Help, Deactivate. Also added the iOS `CLAUDE.md` and an `ci_scripts/test_apns_push.py`.
**Decision:** On Android the payment-settings menu already exists as the `PaymentsHomeFragment` toolbar overflow (Set currency, Recovery phrase, Help, Deactivate) — so only the **net-new "Bitcoin Unit" item** was added: a menu entry opening a single-choice dialog (BTC/sats) that sets `showInSats` and re-renders the balance. Did not create a separate menu screen (Android's toolbar menu is the idiomatic equivalent). Skipped the iOS `CLAUDE.md` (iOS localization rules, not applicable) and `ci_scripts/test_apns_push.py` (iOS APNs tooling).
**Alternatives considered:** Build a dedicated payments-settings Fragment to mirror the iOS screen 1:1 — rejected as redundant with the existing toolbar menu.
**Confidence:** high
**Revisit if:** the product wants a full dedicated payments-settings screen rather than the toolbar menu.

## 2026-05-27 — iOS 7ac08be7bf — Skipped (iOS-platform-specific crash, gesture)
**Context:** Companion to `d674a1a072`: removes a `tableView.performBatchUpdates { loadCoordinator.loadIfNecessary() }` wrapper inside `ChatListFilterControlDelegate` (the interactive gesture-driven filter), fixing a UIKit crash.
**Decision:** Skipped — no Android analog (same reasoning as `d674a1a072`). Android filtering is RecyclerView-based with no `performBatchUpdates`.
**Confidence:** high
**Revisit if:** an analogous Android conversation-list filtering crash is reported.

## 2026-05-27 — iOS f1a85d3fbe — Skipped (Android send flow already exists)
**Context:** iOS "finished" its send-payment flow by merging contact-pick and address-entry into one unified "send to" screen (search field + paste + scan-QR + contact list).
**Decision:** Skipped — Android already implements the send-payment recipient selection as two existing, wired screens: `PaymentRecipientSelectionFragment` (pick a contact) and `PaymentsTransferFragment` + `PaymentsTransferQrScanFragment` (enter/scan an address), both launched from the PaymentsHome send popup menu. The iOS change is an iOS-specific UI unification; replicating it would be a UI rework with no behavioral gain (Android's separate-screen flow is the Signal-Android idiom and already complete).
**Alternatives considered:** Build a unified Android "send to" screen — rejected as a large rework of working, idiomatic Android flows.
**Confidence:** medium-high — the flows exist and are wired; I did not exhaustively re-test contact filtering UX.
**Revisit if:** the product specifically wants the unified single-screen send experience on Android.

## 2026-05-27 — iOS b0bd09c807 — Branding (#12): name/sats already done, icon deferred
**Context:** iOS #12 set the app name to Radar, swapped the app icon to the Radar mark (removing alternate Signal icons), and changed the satoshi identifier "sat"→"sats".
**Decision:** No code change needed for name/sats — Android's `app_name` is already "Radar", and Android already renders satoshis as "sats" everywhere (the `renderBalance` helper and Bitcoin Unit picker from commits #1/#7; there is no singular "sat" identifier). The **launcher icon is deferred** (see ISSUES) — it's still Signal's and needs proper Android adaptive + legacy-density assets that can't be produced/verified reliably here, and it's re-changed in #24.
**Alternatives considered:** Hand-convert `radar-logo.svg` to an adaptive foreground only — rejected; legacy PNGs would remain Signal and masking is unverifiable. Better done once with Image Asset Studio covering #12+#24.
**Confidence:** high (name/sats); the icon is explicitly deferred.
**Revisit if:** handling iOS #24 (the icon is re-changed there) — do both at once.

## 2026-05-27 — iOS 07343eab15 — Payment onboarding flow (ported)
**Context:** iOS adds a 5-screen onboarding flow (username → payments intro → add-funds → deposit-received → setup-complete) via `PaymentsOnboardingCoordinator`, pushed onto the registration navigation controller from the splash.
**Decision (user chose "port now"):** Implemented on Android as a **self-contained `PaymentsOnboardingActivity`** with its own nav graph (`payments_onboarding.xml`), launched from `RegistrationActivity.handleSuccessfulVerify()` for **new registrations only**, gated by a one-time `SignalStore.payments.paymentsOnboardingShown` flag. Every exit (skip/complete) routes to `MainActivity.clearTop` so the user can't get stranded. Screens: `PaymentsOnboardingIntroFragment`, `PaymentsOnboardingAddFundsFragment` (reuses `PaymentsAddMoneyViewModel` for the address + QR), `PaymentsOnboardingDepositReceivedFragment`, `PaymentsOnboardingSetupCompleteFragment`.
**Deviations (deliberate):** (1) A separate activity rather than injecting into Android's registration fragment graph — far lower risk than surgery on the registration flow. (2) The **username step is omitted** from onboarding — the username is auto-derived by `BreezSdkWrapper`, and editing it is already available via the receive screen's "Edit username" (commit #1); adding it here would require cross-nav-graph reuse of `EditLightningUsernameFragment`. (3) The deposit-received screen is triggered by observing `liveMobileCoinBalance()` (0→positive transition) instead of iOS's `incomingPaymentReceived` notification. Icons use `ic_coin_24` + `ic_bitcoin_lightning_24` (no Radar logo vector on Android).
**Alternatives considered:** Inject into `RegistrationV3` nav graph (risky); reuse the preferences fragments cross-graph (fiddly Safe-Args/action-id issues).
**Confidence:** medium — compiles and is self-contained, but not UI-run; the deposit-observation trigger and the new-registration gating need real-device verification.
**Revisit if:** product wants the username step inside onboarding, or wants onboarding shown on upgrade (not just new registration).

## 2026-05-27 — iOS bc4dd06d2b — Onboarding fix + AddFundsIntro (#13): step added, perf fixes skipped
**Context:** iOS added an `AddFundsIntroViewController` step between the payments intro and the QR/address screen, plus several iOS-internal fixes (move `enablePayments()` to a background `Task.detached`; cache a shared `CIContext` and render QR off the main thread; `loadWalletAddress` warn-instead-of-throw; `_updateConversionRates` silent-return guard; return/store `LightningAddressInfo`).
**Decision:** Added the **AddFundsIntro step** on Android (`PaymentsOnboardingAddFundsIntroFragment` + layout, wired intro → addFundsIntro → addFunds in `payments_onboarding.xml`). Skipped the iOS-internal perf/threading fixes — they target iOS specifics with no Android analog: Android's `QrView`/`QrCodeUtil` is a different renderer (no `CIContext`/Metal), payments enablement and address loading run on Android's own threading via `PaymentsAddMoneyViewModel`/Rx, and there are no equivalent assert/throw sites.
**Alternatives considered:** Fold the add-funds-intro copy into the existing add-funds screen — rejected; a discrete step matches the iOS flow and the skip affordance.
**Confidence:** high (step compiles; skips are clearly non-applicable iOS internals).
**Revisit if:** Android QR rendering shows main-thread jank during onboarding (then add background rendering).

## 2026-05-27 — iOS c90dd2d790 — Skipped (iOS-rendering rewrite + already-present)
**Context:** iOS #14 rewrites the in-chat payment bubbles (`CVComponentArchivedPayment`, `CVComponentPaymentAttachment` — ~1200 lines), adds a chat-list payment snippet, and adds `SendPaymentConfirmViewController`.
**Decision:** Skipped on Android. (1) The bubble rewrite is iOS CVComponent architecture; Android renders payment messages via a different component, `conversation/ui/payment/PaymentMessageView.kt`, which already works. (2) The send-confirm step already exists on Android as the full `payments/confirm/ConfirmPaymentFragment` flow. (3) The behavioral deltas worth carrying — showing the bubble amount in sats/BTC and masking it when `balanceHidden`, plus a richer chat-list snippet (Android currently shows the generic `ThreadRecord_payment` = "Payment") — are deferred to the sats-default commit `e694108c22` (#58), where sats display is generalized across surfaces (avoids doing the broad formatting change twice).
**Alternatives considered:** Port the bubble visual redesign into `PaymentMessageView` blind — rejected; not verifiable without UI, and Android's bubble already functions.
**Confidence:** medium — payment rendering + confirm flow demonstrably exist; the cosmetic redesign is intentionally not replicated.
**Revisit if:** #58 doesn't generalize sats/hidden into `PaymentMessageView`, or product wants the specific iOS bubble look.

## 2026-05-27 — iOS 8f92be19fb — Skipped (iOS CVComponent refinement)
**Context:** Further refinement of the in-chat payment bubbles (`CVComponentArchivedPayment`, `CVComponentPaymentAttachment`, `CVComponentMessage`) + a payment-notification tweak.
**Decision:** Skipped for the same reason as `c90dd2d790` — iOS CVComponent rendering; Android uses `PaymentMessageView.kt`. The bubble sats/hidden formatting carries with #58.
**Confidence:** medium. **Revisit if:** #58 doesn't cover `PaymentMessageView`.

## 2026-05-27 — iOS 0700d00c3a — Disable promotional megaphone fetching (#13)
**Context:** iOS commented out `RemoteMegaphoneFetcher.syncRemoteMegaphonesIfNecessary()` in `AppDelegate` to stop Signal's promotional megaphones (e.g. "Donate Today") from being fetched/shown.
**Decision:** Android's analog is `RetrieveRemoteAnnouncementsJob` (fetches release-notes + remote megaphones from S3). Disabled its routine launch trigger by commenting out `RetrieveRemoteAnnouncementsJob.enqueue(true)` in `VersionTracker.updateLastSeenVersion()` (called on app version change) and removing the now-unused import. Gated at the call site (not inside `enqueue`) to avoid unreachable-code warnings and to mirror iOS's call-site disable. Left the internal/debug force-fetch in `InternalSettingsFragment` untouched (debug-only).
**Note:** This is reverted by `69784aaa79` (#16) — Android will restore the call there, matching the iOS net state (megaphones NOT disabled at HEAD). Applied both separately (non-adjacent, so not squashed).
**Confidence:** high
**Revisit if:** there's another routine enqueue path for remote announcements beyond `VersionTracker`.

## 2026-05-27 — iOS 043b28c555 — Skipped (duplicate onboarding re-land)
**Context:** `043b28c555` ("Onboarding flow #14") is a near-identical re-land of `07343eab15` (the payment onboarding flow) on main, plus a `QRCodeGenerator` CIContext perf tweak.
**Decision:** Skipped — the onboarding flow is already implemented on Android (commit for `07343eab15` + the add-funds-intro from `bc4dd06d2b`), and the QR perf tweak is the same iOS-specific change already skipped under `bc4dd06d2b`. No remaining Android delta.
**Confidence:** high (file lists overlap; both onboarding screens + QR perf already handled).
**Revisit if:** a later onboarding commit reveals a behavioral diff between the two iOS landings.

## 2026-05-27 — iOS d03c5c5f94 + 1a21f2f46a — Skipped (onboarding Signal-username step omitted)
**Context:** Both commits refine the iOS `UsernameOnboardingViewController`/`UsernameOnboardingViewModel` — the step that sets the user's **Signal profile username** (the @handle, via `localUsernameManager.confirmUsername` / `Usernames.HashedUsername`). `d03c5c5f94` wires the confirm callback + confirm logic; `1a21f2f46a` adds "use existing username" prefill/edit.
**Decision:** Skipped both (bundled — same omitted feature). The Android payments onboarding omits a username step (decided under `07343eab15`). Note: this iOS step is the **Signal profile username**, not the Radar Lightning-address username — Android already has full Signal username management under profile settings (`components/settings/app/usernamelinks/`), and the Lightning-address editor (`EditLightningUsernameFragment`, commit #1) already prefills the existing name (covering 1a21f2f46a's intent for the standalone editor).
**Alternatives considered:** Add a Signal-username step to the Android onboarding — deferred; it's available in profile settings and wasn't part of the bounded onboarding port.
**Confidence:** high
**Revisit if:** product wants a Signal-username-setup step inside the Android payments onboarding.

## 2026-05-27 — iOS 69784aaa79 — Revert megaphone disable
**Context:** iOS reverted `0700d00c3a`, re-enabling the remote-megaphone fetch.
**Decision:** Reverted the Android change — restored `RetrieveRemoteAnnouncementsJob.enqueue(true)` + its import in `VersionTracker`. Net effect of the disable+revert pair is zero (VersionTracker back to baseline), matching iOS HEAD where promotional megaphones are NOT disabled. Mirrored both commits separately (non-adjacent) rather than squashing.
**Confidence:** high (restores known-good baseline code).
**Revisit if:** Radar later re-disables megaphones in a different way.

## 2026-05-27 — iOS UI-refresh series (37007fe8e8, 6c85cf772d, 3b4634c7f7, 81edb47849) — Skipped (bundled)
**Context:** Four iOS UIKit payment-screen polish commits: payment-bubble direction arrows + colors (CVComponent), currency-picker flag emojis + layout, an `isOnboarding` layout mode on the receive VC, a username settings-menu entry, branding strings, and spacing/padding.
**Decision:** Skipped as a cluster — predominantly iOS-rendering-specific visual polish with no clean Android mapping: (a) bubble arrows/colors are iOS CVComponent; Android renders via `PaymentMessageView`. (b) The receive `isOnboarding` mode is already handled differently on Android (a dedicated `PaymentsOnboardingAddFundsFragment`). (c) Padding/spacing are iOS-stack-specific; Android's receive layout was redesigned separately (#3). (d) The username settings-menu entry is redundant — Android edits the Lightning username from the receive screen (commit #1). (e) Branding strings are covered by the dedicated rebrand `5b4b9f52f0` (#44).
**Not ported (cosmetic nicety):** currency-picker **flag emojis** (regional-indicator glyph from the currency code) — a self-contained enhancement a reviewer could add to `SetCurrencyFragment`'s adapter; left out to keep momentum.
**Confidence:** medium-high. **Revisit if:** product specifically wants the bubble arrows or currency flags on Android.

## 2026-05-27 — iOS e5f586cbd3 + 58e9dc6118 — On-chain Add Funds (implemented)
**Context:** iOS adds a Lightning/on-chain tab toggle to the receive screen, prefetching an on-chain (taproot) Bitcoin address via `fetchBitcoinTaprootAddress()` (`receivePayment(.bitcoinAddress)`), and showing the right address/QR per tab.
**Decision:** Implemented on Android. Added `BreezSdkWrapper.getOnchainAddress()` → `sdk.receivePayment(ReceivePaymentRequest(ReceivePaymentMethod.BitcoinAddress)).paymentRequest` (the Breez Android binding exposes this — confirmed in the sources jar). Added a single toggle button on `PaymentsAddMoneyFragment` ("Show on-chain address" ⇄ "Show Lightning address") that swaps the displayed address + QR; the on-chain address is fetched lazily on first switch via `SimpleTask`. Used a single toggle button rather than `MaterialButtonToggleGroup` (not used elsewhere in the app — avoided an unverified widget).
**Alternatives considered:** Tab UI like iOS — a single toggle is simpler and compile-safe; behavior is equivalent (switch Lightning ⇄ on-chain).
**Confidence:** medium — compiles; the on-chain fetch + QR/address switching weren't UI-run. The on-chain QR encodes the raw address string.
**Revisit if:** the on-chain QR should use a `bitcoin:` URI, or the toggle should be a segmented tab control.

## 2026-05-27 — iOS 85485a8b7d — Cake/BIP-39 seed compatibility (#23)
**Context:** iOS changed the Breez seed from `Seed.entropy(entropy)` to `Seed.mnemonic(BIP-39 mnemonic of the entropy)` so the displayed backup phrase is interoperable with any BIP-39 wallet (e.g. Cake).
**Decision:** Mirrored in `BreezSdkWrapper.connect`: `Seed.Mnemonic(Mnemonics.bip39EntropyToMnemonic(entropy), null)` (both APIs confirmed in the Breez Android binding + `com.mobilecoin.lib.Mnemonics`).
**Caveat (important):** this changes how the wallet is derived from the stored entropy — the derived address/keys differ from the old `Seed.Entropy` path. This matches the iOS change (deliberate, pre-GA), but any wallet created on the old Android seeding would derive differently. Acceptable for parity; flag for device verification that existing/new wallets behave as expected and the phrase restores in Cake.
**Confidence:** medium — compiles; seed-derivation behavior needs device verification.
**Revisit if:** existing test wallets need migration, or the entropy/mnemonic round-trip differs from iOS's `MnemonicSwift`.

## 2026-05-27 — iOS 18c479ada0 — Skipped (iOS-UIKit send-screen crash)
**Context:** iOS fixed a crash in `SendPaymentViewController` caused by calling `UIView.matchWidthsOfViews([...])` before the spacer views were added to their `UIStackView`, plus added a paste icon to the note field.
**Decision:** Skipped — iOS-platform-specific. The crash is a UIKit Auto Layout ordering issue with no Android analog (Android's send flow is `payments/create/CreatePaymentFragment`, different rendering). The note paste icon is minor iOS UI.
**Confidence:** high. **Revisit if:** an analogous crash is seen in Android's create-payment screen.

## 2026-05-27 — iOS 5b4b9f52f0 — Replace Signal/MobileCoin with Radar/Lightning (payment-scoped)
**Context:** iOS's "rebrand" commit touches all 45 `.strings` files but the **English** change is a single string (deactivate-payments description: "MobileCoin in Signal" → "Lightning in Radar") — the rest of the EN rebrand had already landed in earlier commits.
**Decision:** On Android, the MobileCoin residual was already cleared (commit #4); 9 **payment-context** strings still named the app "Signal" (PaymentsHomeFragment / PaymentsTransferFragment / DeactivateWalletFragment). Replaced "Signal"→"Radar" in just those (the values capitalize "Signal"; the keys use lowercase `signal_`, so the replace is value-only). Left broader, non-payment "Signal" references untouched — many are legitimate (protocol name, "Signal PIN", legal text, support URLs), and iOS's EN change was payment-scoped. Edited only `values/strings.xml` (translations come from the platform).
**Alternatives considered:** Blanket app-wide Signal→Radar — rejected (would corrupt legitimate references).
**Confidence:** high (XML-validated; payment-scoped).
**Revisit if:** product wants a full app-wide rename (a separate, careful pass).

## 2026-05-27 — iOS 10317857ad — Skipped (no get-started-cards analog; cosmetic welcome text)
**Context:** iOS changes the onboarding-splash button text ("Continue" → "I'm new to Signal/Radar", "Restore or Transfer Account" → "Restore my Signal/Radar") and removes the post-onboarding "Get Started" cards (newGroup, inviteFriends, appearance, avatarBuilder).
**Decision:** Skipped. (1) The "Get Started" cards have no Android analog — Android surfaces new-user prompts via the Megaphone system (`Megaphones.java`), not a get-started cards view, so there's nothing equivalent to remove. (2) The welcome button-text change is cosmetic, uses a placeholder-style "Signal/Radar" value, and Android's registration welcome (`WelcomeFragment`) is a structurally different flow with its own strings; it's general-app onboarding, not payment-related.
**Alternatives considered:** Rebrand the Android welcome buttons + disable onboarding megaphones — deferred as optional non-payment polish.
**Confidence:** medium-high. **Revisit if:** product wants Radar-branded welcome buttons or fewer onboarding megaphones on Android.

## 2026-05-27 — iOS b8fee2bde8 — Skipped (signup choice already on Android welcome)
**Context:** iOS adds `RegistrationSignUpViewController` after the splash — a screen with "Create account" and "Use Signal account" buttons.
**Decision:** Skipped — Android's `WelcomeFragment` already presents the same new-vs-existing-account choice ("Continue" to register a new account, "Restore or transfer" for an existing one). The iOS screen is an additional onboarding step for a choice Android already offers; adding it would mean injecting a new destination into Android's RegistrationV3 nav (risky, non-payment).
**Confidence:** medium-high. **Revisit if:** product wants the dedicated signup-choice screen styled like iOS.

## 2026-05-27 — iOS b847a18d86 + aecba8efde — Skipped (already handled / no analog)
**Context:** iOS adds a `walletAddressDidLoad` notification to re-upload the payment profile after the wallet address loads (`b847a18d86`), and guards a force-cast of `SUIEnvironment.shared.paymentsRef` to avoid a crash (`aecba8efde`).
**Decision:** Skipped both. (1) Android already uploads the Lightning address to the user profile **eagerly** inside `BreezSdkWrapper.getLightningAddress()` via `ProfileUtil.uploadLightingProfile` (3 call sites — on fetch, after register, and in `registerUsername`), so the address is published right when it loads; no notification-driven re-upload is needed. (2) Android's `AppDependencies.payments` is a non-null `Payments` val initialized by the DI provider — there is no optional/force-unwrap of a payments ref to crash, so the iOS guard has no analog.
**Confidence:** high. **Revisit if:** Android moves profile upload off the eager `getLightningAddress` path (then a load-completion hook may be needed).

## 2026-05-28 — Add Funds screen parity + username-refresh (user feedback)
**Context:** User flagged the Android Add Funds screen as not faithful to iOS (toggle-button vs segmented switcher; QR with no center logo; separate edit button vs pencil) and that the username didn't refresh after editing.
**Decision:** Rebuilt `PaymentsAddMoneyFragment` + layout to mirror iOS `PaymentsTransferInViewController`: segmented Lightning/Onchain switcher (plain `MaterialButton`s with `backgroundTint`/text/icon-tint toggled — chose this over `MaterialButtonToggleGroup`, which isn't used elsewhere), accent-blue bordered QR card with the radar logo centered + a spinner, address row with a pencil (`AppCompatImageButton` + `app:tint`, per the balance-toggle precedent) hidden on Onchain, monospace 4-char-grouped on-chain address, gray Copy capsule, toolbar Share, on-chain prefetch + spinner + error toast/revert. Imported the iOS SVGs as vector drawables (§G-ASSETS). **Username refresh:** `EditLightningUsernameFragment` posts a `FragmentResult` on success and `PaymentsAddMoneyFragment` calls `PaymentsAddMoneyViewModel.refresh()` to re-fetch — chosen over an onResume refetch (which would hit Breez on every resume) and mirrors iOS's `walletAddressDidLoad` observer (missed from `6c85cf772d`).
**Alternatives considered:** onResume refresh (too eager); `MaterialButtonToggleGroup` (unproven here); high-EC QR generation (see ISSUES — kept default EC + small logo like iOS).
**Confidence:** medium — compiles, not UI-run. The QR center logo overlays a default-EC (L) QR (matching iOS's overlay approach) — see ISSUES for scannability.
**Revisit if:** the center logo impairs QR scanning (then raise EC); B7 (BOLT11 QR) and B12 (title casing) still pending.

## 2026-05-28 — Edit Username screen parity (RadarUsernameViewController → EditLightningUsernameFragment)
**Context:** User flagged that the Edit Username screen UI/field/button styles diverge from iOS `RadarUsernameViewController`.
**Decision:** Rebuilt `fragment_edit_lightning_username.xml` + `EditLightningUsernameFragment.kt` to mirror the iOS layout: centered `radar_logo` (82dp) → 22sp bold title → 15sp 50%-alpha subtitle → 32dp gap → gray-fill pill `EditText` (new `bg_radar_username_pill.xml`, radius 26dp, height 52dp) with a programmatic clear (×) `ImageButton` (toggled on text-change) + accent-blue "@radar.cash" semibold suffix → footnote status with **✓** (green `#46B827`) / **✗** (red `#FF3B30`) glyphs prefixed in code (avoids touching 45 locale files) → flexible spacer → accent-blue Confirm capsule (radius 14, height 52) → "Cancel" text button (accent blue, borderless). Auto-focus + soft-keyboard on view-created (parity with iOS `becomeFirstResponder` in `viewDidAppear`). EditText set to `inputType="text|textNoSuggestions"` + `maxLength=24` (mirrors iOS `addressGlyphLimit=24`). String `EditLightningUsernameFragment__skip` value changed from "Skip this step" to "Cancel" in the English `values/strings.xml` (matches iOS button title); the Radar-specific keys live only in the English file per the migration rule, so no other locales required updates.
**Alternatives considered:** `TextInputLayout` with `app:endIconMode="clear_text"` — rejected because TIL adds chrome (label, helper) we'd have to hide and still wouldn't match the pure pill shape; programmatic `EditText` + `LinearLayout` pill is cleaner. Renaming the string key `__skip` → `__cancel` — rejected as gratuitous churn (key name is internal).
**Confidence:** medium — compiles, not UI-run. iOS uses a `UIScrollView` with a content view sized to the scroll frame so the keyboard slides over; Android uses `ScrollView fillViewport=true` + LinearLayout `match_parent` with a weighted Space — should behave equivalently with `windowSoftInputMode` defaults but verify on a device with a short screen.
**Revisit if:** the keyboard pushes content off-screen on small displays (then set `windowSoftInputMode=adjustResize` for the fragment's activity / wrap in a `ConstraintLayout` with `KeyboardInsets`).

## 2026-05-28 — Currency picker: flag prefix vs full-layout rebuild
**Context:** User asked for currency-picker parity with iOS (`CurrencyPickerViewController`), specifically "with the flags and style". iOS renders each row as **[flag 28pt] [name + code stack] [✓ on selected]** with a search bar. Android's `SetCurrencyFragment` uses `BaseSettingsAdapter.configureSingleSelect` with `single_select_item.xml` (RadioButton + CheckedTextView + summary).
**Decision:** First-pass parity ships **flag emoji prefixed to the displayed name** (e.g. "🇺🇸  US Dollar") + the **AUD/CAD → USD/JPY** pinned-list swap (F-A8a from `37007fe8e8`). The flag is derived via `flagEmojiForCurrencyCode()` — direct port of iOS `flagEmoji(forCurrencyCode:)`. **Deferred:** (a) the full row layout (separate leading flag column + trailing checkmark instead of radio); (b) the **search bar** in the toolbar. Both are non-blocking polish items.
**Alternatives considered:** Building a dedicated `CurrencyFlagItem` MappingModel + a new row layout + a custom adapter — better fidelity but ~200 lines of replacement code that may regress the radio-select click handling shared with other settings screens. The prefix approach reuses the existing battle-tested adapter unchanged.
**Confidence:** medium — compiles and the BuildConfig swap is correct (USD + JPY now in the default list, exchange-rate intersection on line 187 of `SetCurrencyViewModel` will surface them once `CurrencyExchange` returns those codes from the server). Not UI-run.
**Revisit if:** the user wants the exact iOS row layout / search bar — implement as `CurrencyFlagRowItem` + `payments_currency_row.xml` + a `SearchView` toolbar action with a `searchQuery` filter in the store/state.

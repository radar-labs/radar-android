# Issues Log

_Append-only. Blockers, deferrals, ambiguities, risks, tech-debt for human review._

## 2026-05-27 — Phase 0 — JDK 17 vs 21 mismatch
**Type:** risk
**What happened:** `.tool-versions` pins `openjdk-17.0.2`, but the active JDK is `21.0.9`. Signal's AGP/Gradle build may require JDK 17.
**What I tried:** Detection only (no build yet).
**Suggested next step:** Before the first build, point the build at JDK 17 (asdf/`JAVA_HOME` or `org.gradle.java.home`). Confirm a clean baseline build of `mapping-ios-commits` succeeds *before* mirroring.
**Severity:** medium
**Android files involved:** build env (`.tool-versions`, Gradle JDK config)
**Resolution (2026-05-27):** Resolved by pinning `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/...` inline on each Gradle invocation (no repo/env mutation). Baseline `:Signal-Android:assemblePlayProdDebug` is **green (4m12s)**. Also corrected the task path (`:Signal-Android`, not `:app`).

## 2026-05-27 — Phase 0 — Push relay (iOS #19 / `0e11af806c`)
**Type:** deferred
**What happened:** iOS adds `RadarPushRelay.swift` + APNs registration changes. Android push is FCM with a different registration path; a faithful equivalent is non-trivial and product-sensitive.
**Suggested next step:** Review the iOS relay design and decide the Android FCM equivalent before implementing. `8353e6b3c9` (relay disclaimer, #31) partly depends on this.
**Severity:** medium
**Android files involved:** push/FCM registration (`gcm/`/`notifications/`), notification settings.
**Detail (2026-05-27):** iOS `RadarPushRelay` (enum) talks to `https://push.radar.chat`: it provisions a "phantom linked device", keeps the relay's APNs token up to date (`ensure(apnsHexToken:)`), exposes a user toggle (`isEnabled`/`setEnabled`), and tears down on logout (`unregister`). Goal: deliver pushes even when the app is force-quit. The Android equivalent must be FCM-based (FirebaseMessagingService token instead of APNs) with the same relay registration/provisioning/teardown and the user toggle + onboarding disclaimer (`8353e6b3c9`: `ONBOARDING_PERMISSIONS_RELAY_*`, `RADAR_PUSH_RELAY_*`). This is a from-scratch feature + product decision (external relay service); both `0e11af806c` and `8353e6b3c9` are deferred together.

## 2026-05-27 — Phase 0 — Backup payment fixes (iOS #20 `ae56882371`, #29 `8ab30cb69e`)
**Type:** deferred
**What happened:** iOS fixes backup restore + "backup using mobcoin". Android backup is the Wire-based `backup/v2` archivers (different shape than iOS `BackupArchive…Archiver`). Residual MobileCoin handling differs.
**Suggested next step:** Inspect Android `backup/v2` payment archiving vs the iOS fix intent before applying; may be already handled or N/A.
**Severity:** medium
**Android files involved:** `backup/`, `backup/v2/.../Archivers/`, payment archiving.

## 2026-05-27 — Phase 0 — Breez SDK version bump (iOS #25 `42e070db35`)
**Type:** deferred
**What happened:** iOS bumps Breez via Podfile + new podspecs. Need to confirm where/whether the Android Breez SDK dependency is declared and whether the same version is available.
**Suggested next step:** Locate Breez dep in `gradle/libs.versions.toml`; bump only if the version exists for Android and doesn't break the build.
**Severity:** low
**Android files involved:** `gradle/libs.versions.toml`, payments module `build.gradle.kts`.
**Update (2026-05-27):** iOS bumped to Breez **0.14.0** (vendored podspec); Android is on **0.9.1**. Bumping is a major jump: confirm `breez_sdk_spark:bindings-android:0.14.0` exists, migrate the API usage in `BreezSdkWrapper` (getLightningAddress/registerLightningAddress/checkLightningAddressAvailable/receivePayment/initLogging were wired against 0.9.1), and full-build. **Severity raised to medium.**

## 2026-05-27 — Phase 0 — Onboarding change appears twice
**Type:** risk
**What happened:** `07343eab15` ("Add the payment onboarding flow screens") and `043b28c555` ("Onboarding flow #14") add nearly identical files (branch + main re-land).
**Suggested next step:** Apply `07343eab15`; when reaching `043b28c555`, diff against the current tree and apply only genuine deltas (e.g. QR generator), logging the dedup.
**Severity:** low
**Android files involved:** onboarding fragments, QR generator.

## 2026-05-27 — Phase 0 — 12-word seed already implemented
**Type:** tech-debt
**What happened:** iOS #27 (`fb97d0a9b4`) switches to a 12-word seed; Android already did this in `97f461b44e`.
**Suggested next step:** Treat #27 as a dedup-skip; verify string/constant parity and mirror only any residual delta.
**Severity:** low
**Android files involved:** `PaymentsConstants.java`, recovery-phrase strings.

## 2026-05-27 — Phase 0 — Brand-string replacement risk (iOS `5b4b9f52f0`)
**Type:** risk
**What happened:** Bulk "Signal→Radar, MobileCoin→Lightning" string replacement. Not every "Signal" occurrence is safe to replace (protocol names, legal text, feature names).
**Suggested next step:** Apply intent-matched substitutions to user-facing strings in `values/strings.xml`; review each rather than blanket find-replace.
**Severity:** medium
**Android files involved:** `res/values/strings.xml`.

## 2026-05-27 — Phase 0 — Residual MobileCoin references (~18 files)
**Type:** tech-debt
**What happened:** Both branches still reference MobileCoin in ~18 files (`Payments.kt` ctor takes `MobileCoinConfig`, archived-payment/backup, tests, config).
**Suggested next step:** Not in scope to remove wholesale; touch only where a mirrored commit requires it.
**Severity:** low
**Android files involved:** `payments/`, `backup/`, config.

## 2026-05-27 — iOS aba8376294 — Username change & localized sats display
**Type:** risk / tech-debt
**What happened:** Mirroring the edit-username screen, two things need a human eye: (1) `registerUsername()` calls Breez `registerLightningAddress` with the new name — it is unverified whether the SDK allows *changing* an already-registered lightning address (it may error if one exists); iOS calls the same API from its edit screen, so presumably it's supported. (2) The sats/BTC toggle currently re-renders only the **home-screen balance** (`renderBalance()`); other amount displays (chat bubbles, history, send) still show BTC. iOS threaded `isSatoshiEnabled` through all formatters.
**What I tried:** Confirmed the Breez Android binding exposes `checkLightningAddressAvailable`/`registerLightningAddress` (sources jar). Localized sats rendering to avoid a broad `MoneyView` change this early.
**Suggested next step:** Verify username-change semantics against a live wallet. Generalize sats display when mirroring iOS #58 ("Sats by default") — likely centralize in `MoneyView`/`Money` formatting.
**Severity:** medium
**Android files involved:** `BreezSdkWrapper.kt`, `EditLightningUsernameFragment.kt`, `PaymentsHomeFragment.java`, `MoneyView.java` (future).

## 2026-05-27 — iOS 496108d18f — Receive screen: on-chain address & QR encoding
**Type:** deferred / risk
**What happened:** (1) iOS added `fetchBitcoinTaprootAddress()` but it is **commented out** ("until breez clarifies"), so no on-chain receive address is wired — I skipped it to match. (2) iOS encodes the QR with the LNURL; Android currently encodes the plain `user@domain` lightning address string (existing behavior, kept). Both are scannable by LN wallets; the exact QR encoding for exchange/Cake compatibility is revisited in iOS #23/#4f069a.
**What I tried:** Mirrored the visual redesign + domain-tinted address; left QR source and on-chain fetch unchanged.
**Suggested next step:** Wire on-chain receive when Breez taproot support is finalized; reconcile QR encoding during the Cake-compat commits.
**Severity:** low
**Android files involved:** `PaymentsAddMoneyFragment.java`, `PaymentsAddMoneyViewModel.kt`, `BreezSdkWrapper.kt`.

## 2026-05-28 — F-A3 — Chat-list snippet doesn't refresh live when sats/hidden toggle
**Type:** deferred
**Severity:** low (snippets refresh on the next payment-thread update; toggling is rare).
**Constraint:** `ThreadTable.kt:1815-1837` computes the payment snippet via `ThreadBodyUtil.getFormattedBodyFor(...)` and **stores the result string into the `body` column** via `updateThread(...)`. The chat-list adapter then reads `body` from the DB row — it is **not** recomputed at render time. So when the user toggles `SignalStore.payments.balanceHidden` or `showInSats`, displayed snippets won't reflect the new pref until the next time `updateThread` runs for each payment thread (typically on a new payment message arrival).
**Two implementation paths if we ever want live refresh:**
  1. **DB invalidation sweep**: on pref change, iterate every thread that has at least one payment message and call `ThreadTable.update(threadId)` to recompute `body`. Find all such threads via `MessageTable.payments.getThreadsWithPaymentMessages()` (or equivalent). Cost: O(payment threads) on every toggle.
  2. **Render-time snippet for payment threads**: stop caching the payment snippet in `body`; in the chat list adapter (`ConversationListItem`), detect payment threads (via `ThreadRecord.messageExtras?.paymentTombstone` or a flag) and recompute the snippet at bind time using the current pref values. Cleaner but touches the adapter.
**Mitigation:** F-A2's snippet does respect `balanceHidden` at write time, so as soon as the next payment message arrives, the chat-list row will show "•••••" if hidden is on, or the amount if not.
**Android files involved:** `ThreadTable.kt`, `ConversationListItem.java`, `PaymentsValues.kt` (would need a change LiveData), `ThreadBodyUtil.java`.

## 2026-05-28 — E — Bottom-nav Payments tab (iOS HomeTabBarController parity)
**Type:** deferred
**Severity:** medium (user-explicit ask, but Payments is accessible via Settings — 2 taps).
**What's the iOS target:** `HomeTabBarController.swift` has a 4-tab bottom bar: chats (0) → **payments (1)** → calls (2) → stories (3). The payments tab uses SF Symbol `bitcoinsign.circle.fill` and hosts `PaymentsSettingsViewController(mode: .inAppSettings)`.
**Why deferred:** Two non-trivial Android-specific constraints:
1. **Icon system mismatch.** Android's `MainNavigation.kt` `NavigationDestinationIcon` consumes `@RawRes` Lottie JSONs (`chats_28.json`, `calls_28.json`, `stories_28.json`). There is no `payments_28.json`; supplying one requires either authoring a Lottie animation, OR refactoring `NavigationDestinationIcon` to optionally accept an `@DrawableRes` static fallback (clean, additive, but a Compose API change).
2. **Nav-graph hosting.** `PaymentsHomeFragment` is a destination in `payments_preferences.xml` and uses `NavHostFragment.findNavController(this) + SafeNavigation.safeNavigate(...)` to push child screens (Add Funds, transfer, recovery, etc.). It can't be cleanly embedded into `MainActivity`'s Compose `AndroidFragment`-based `secondaryContent` without a `NavHostFragment` wrapper for the payments graph. The current alternative — launching `PaymentsActivity` from a tab tap — loses the bottom-nav visibility on subsequent screens (UX regression vs iOS).
3. **Toolbar coordination.** `PaymentsHomeFragment` ships its own `Toolbar`; `MainActivity` already renders `MainToolbar`. Embedding requires hiding one or the other based on the active destination.
**Suggested implementation (when picking this up):**
- Step 1: Add a Lottie `payments_28.json` (animated bitcoin-sign or wallet glyph) under `app/src/main/res/raw/`. Or, refactor `NavigationDestinationIcon` to render `Icon(painterResource(drawableIcon))` when an optional `@DrawableRes drawableIcon` is set on the enum.
- Step 2: Add `PAYMENTS` to `MainNavigationListLocation` with the icon + a new string `ConversationListTabs__payments` = "Payments".
- Step 3: Update `MainNavigationBar` (filter + `badgeCount` switch — wire to `UnreadPaymentsLiveData` already imported in MainActivity).
- Step 4: Add `onPaymentsSelected()` to `MainNavigationViewModel` + `MainNavigationCallback`.
- Step 5: In `MainActivity.secondaryContent`, render the payments-nav-graph via a `NavHostFragment` of `R.navigation.payments_preferences`. Hide `MainToolbar` when destination is PAYMENTS.
- Step 6: Gate the tab on `SignalStore.payments.paymentsAvailability` / `paymentsEnabled`.
**Workaround in the meantime:** Per §A1 of this plan, Payments was moved up in Settings (now appears right after Account/Linked-Devices/Donate), so it's reachable in 2 taps. Acceptable for now.
**Android files involved:** `app/src/main/java/org/thoughtcrime/securesms/main/MainNavigation.kt`, `app/src/main/java/org/thoughtcrime/securesms/main/MainNavigationViewModel.kt`, `app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt`, `app/src/main/res/raw/payments_28.json` (new), `app/src/main/res/values/strings.xml`, `app/src/main/res/navigation/payments_preferences.xml`.

## 2026-05-28 — F-A7 — Backup-restore: paymentsEntropy at risk during manifest rotation
**Type:** deferred / data-loss risk **(needs user decision before patching)**
**Severity:** high
**What's at stake:** During a fresh registration that restores from a backup AEP, the storage-service manifest is re-encrypted with a new key by `StorageRotateManifestJob` (queued from `StorageSyncJob`/`SvrRepository`). On iOS, commit `ae56882371` added two pre-rotation steps — `restoreOrCreateManifestIfNecessary()` and `recordPendingLocalAccountUpdates()` — to merge the server's `AccountRecord` (including `paymentsEntropy`) into local state, and mark `paymentsEntropy` as needing re-sync. Android's `StorageRotateManifestJob` (124 lines) currently just calls `writeUnchangedManifest(...)` (line 89) which re-encrypts the manifest but does NOT touch records or hydrate local state from records.
**Reading of the Android flow (mine, not the audit's):** `writeUnchangedManifest` is name-suggestive — it doesn't touch records, which remain encrypted with the original `recordIkm` stored inside the manifest. The actual data-loss vector would be: if a subsequent `StorageSyncJob.forRemoteChange()` builds a new `AccountRecord` from CURRENT local state where `paymentsEntropy` is still empty (pre-restore) and pushes it to the server, it'd overwrite the server's good entropy with empty local entropy. Whether this happens depends on whether `StorageSyncJob` already runs (and hydrates `paymentsEntropy` from the server's `AccountRecord`) BEFORE `StorageRotateManifestJob` writes the new manifest version. The audit agent suggested inserting an `AccountRecord` read+merge between manifest fetch (line 79) and manifest write (line 87) of `StorageRotateManifestJob`.
**Why deferred:** This is a critical data path with real data-loss potential if patched incorrectly (e.g., the merge could push wrong defaults over good remote values). Per CLAUDE.md "discuss before code" — recommend the user verify the flow on a real fresh-restore device before patching, OR explicitly authorise me to apply the audit's recommended insertion (which itself needs careful review of `StorageSyncHelper.applyAccountStorageSyncUpdates` signatures and behavior in the post-rotation order).
**Suggested next step:** Either (1) reproduce on a fresh-restore device to confirm `paymentsEntropy` survives (then close as a false alarm), or (2) implement the audit's insertion in `StorageRotateManifestJob.kt` between line 79 and line 87, with a defensive `paymentsEntropy != null` check and unit-test coverage.
**Android files involved:** `app/src/main/java/org/thoughtcrime/securesms/jobs/StorageRotateManifestJob.kt`, `app/src/main/java/org/thoughtcrime/securesms/jobs/StorageSyncJob.kt`, `app/src/main/java/org/thoughtcrime/securesms/storage/StorageSyncHelper.kt`, `app/src/main/java/org/thoughtcrime/securesms/keyvalue/PaymentsValues.kt`.

## 2026-05-28 — Add Funds QR center logo on a default-EC QR
**Type:** risk
**What happened:** The Add Funds parity rewrite overlays the radar logo (52dp box / 42dp glyph) on a `QrCodeUtil`-generated QR, which uses **error-correction level L** (Signal's `QrCodeUtil.create` takes no EC hint). A center logo on an L-level QR can hurt scannability if it covers too much. iOS overlays its logo on a default-EC QR as well, so this matches the reference, but it was **not verified on a device**.
**Suggested next step:** Scan-test on a device; if unreliable, render the Add Funds QR with EC level **H** (a dedicated bitmap via ZXing with `ERROR_CORRECTION=H`, set on the view) instead of `QrView.setQrText`.
**Severity:** low
**Android files involved:** `PaymentsAddMoneyFragment.java`, `res/layout/payments_add_money_fragment.xml`.

## 2026-05-27 — iOS b0bd09c807 / 1ce180c53e — Launcher icon still Signal's
**Type:** deferred
**What happened:** iOS #12 (and again #24) replaced the app icon with the Radar mark and removed the alternate Signal icons. On Android the launcher icon is still Signal's: adaptive `drawable/ic_launcher_foreground` + `ic_launcher_background` + `ic_launcher_monochrome`, legacy `mipmap-*/ic_launcher.png` across 5 densities, plus a set of alternate icons (`ic_launcher_alt_*`). The Radar source art exists in the iOS repo (`radar-logo.svg`, `AppIcons/AppIcon.icon/Assets/radar-icon-*.png`).
**What I tried:** Inspected the SVG (single-color #0069FE radar mark) — convertible to a vector, but a correct adaptive icon needs safe-zone scaling + regenerated legacy PNGs (5 densities) that can't be visually verified here, and the icon is re-changed in #24.
**Suggested next step:** Generate the Android launcher icon from the Radar logo via Android Studio's Image Asset Studio (adaptive foreground/background + monochrome + legacy PNGs); decide whether to drop the alternate-icon feature (`ic_launcher_alt_*`) as iOS did. Apply once, covering both #12 and #24. **Also covers the splash (`f21e3145bc`):** the splash logo `res/drawable*/ic_splash*.xml` (+ `drawable-night`) is still Signal's — repoint it at a Radar logo vector. Converting `radar-logo.svg` (single-color #0069FE, viewBox 82×82) into a reusable Android vector `drawable/radar_logo.xml` would unblock the splash, the adaptive-icon foreground, and replace the `ic_coin_24` stand-ins used in the onboarding/username screens.
**Severity:** medium
**Android files involved:** `res/mipmap-*/ic_launcher*.png`, `res/mipmap-anydpi-v26/ic_launcher*.xml`, `res/drawable/ic_launcher_*`, `res/drawable*/ic_splash*.xml`.

## 2026-05-27 — iOS 07343eab15 — Payments onboarding: verify on device
**Type:** risk
**What happened:** The onboarding flow was ported as a self-contained `PaymentsOnboardingActivity` launched after a new registration. It compiles but was not UI-run. Points to verify on a device: (1) the post-registration launch + one-time gating (`paymentsOnboardingShown`) — ensure no double-show and no getting stranded (every exit calls `finishToMain()`); (2) the deposit-received transition (observes `liveMobileCoinBalance()` 0→positive — confirm it fires on a real incoming deposit and not spuriously); (3) the QR/address render in the add-funds step; (4) whether the username step should be added to onboarding (currently omitted).
**Suggested next step:** Run a fresh-registration flow on a device/emulator and walk all four screens incl. a real deposit; decide on the username step.
**Severity:** medium
**Android files involved:** `payments/onboarding/*`, `registration/ui/RegistrationActivity.kt`, `res/navigation/payments_onboarding.xml`.

## 2026-05-27 — iOS ae56882371 — Backup restore: verify payments-entropy not wiped
**Type:** deferred / risk
**What happened:** iOS fixed a StorageService bug where, on restore-from-backup, manifest rotation wiped server records (incl. `paymentsEntropy`) on conflict-retry — by merging the server manifest before rotating, and by syncing `paymentsEntropy` to StorageService. Android already includes `PAYMENTS_ENTROPY` in `PaymentsValues.getKeysToIncludeInBackup()` and calls `StorageSyncHelper.scheduleSyncForDataChange()`, so the entropy-sync intent is present. The manifest-merge-before-rotate fix is specific to iOS `RegistrationCoordinatorImpl`; Android's storage-service restore (`registration/`, `StorageServiceRestore.kt`) is a parallel implementation.
**Suggested next step:** Verify on a device that restoring an Android backup preserves the wallet seed (paymentsEntropy) — confirm Android's manifest rotation during restore doesn't wipe it; if it does, apply the analogous merge-before-rotate.
**Severity:** high (data-loss risk if the analog bug exists — the wallet seed)
**Android files involved:** `registration/` restore flow, `StorageServiceRestore.kt`, `keyvalue/PaymentsValues.kt`.

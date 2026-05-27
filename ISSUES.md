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

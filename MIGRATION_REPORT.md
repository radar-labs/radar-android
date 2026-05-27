# Migration Report — iOS Radar → Android (signal-fork-android)

_Generated 2026-05-27. Source range: iOS `aba8376294`..`e694108c22` (58 commits). Base branch: `mapping-ios-commits` (committed directly)._

## Counts (all 58 iOS commits dispositioned)

| Disposition | Count | Notes |
|---|---|---|
| **Applied** `[x]` | 19 | Real Android changes, each compile-checked |
| **Skipped** `[s]` | 31 | Incl. 8 merge commits + 2 screenshot commits (non-actionable); the rest are already-equivalent, iOS-platform-specific, iOS-rendering-specific, or duplicates |
| **Deferred** `[d]` | 8 | Need assets, backend/SDK verification, or product decisions (see below) |
| **Blocked** `[!]` | 0 | Nothing left in a broken/blocked state |

37 `Mirror iOS <sha>: …` git commits were made (some skips/defers were bundled when tightly related; merge & screenshot commits are recorded in `MIGRATION_PLAN.md` without a dedicated git commit). Full per-commit status: **`MIGRATION_PLAN.md`**.

## Build / test status at HEAD

- **Build:** `JAVA_HOME=<openjdk@17> ./gradlew :Signal-Android:assemblePlayProdDebug` — **GREEN at HEAD: `BUILD SUCCESSFUL in 25s`**, APKs produced for all ABIs (arm64-v8a, armeabi-v7a, x86, x86_64, universal), version 7.68.4. Every applied commit was compile-checked (`:Signal-Android:compilePlayProdDebugKotlin`/`…JavaWithJavac`); full assembles were run at milestones and at HEAD.
- **Tests:** not run. Signal's instrumentation/unit suites weren't exercised; verification was build/compile-level per the agreed strategy (see DECISIONS: "Verification strategy"). **No behavior was verified on a device/emulator** — see risks.
- **JDK:** pinned to JDK 17 via `JAVA_HOME` (project `.tool-versions` is 17; system default is 21).

## What was applied (highlights)

Lightning username editor + display-pref store; receive-screen redesign + on-chain (Lightning/BTC) toggle; Bitcoin Unit picker; "Sats by default"; payment onboarding flow (`PaymentsOnboardingActivity`: intro → add-funds-intro → add-funds → deposit-received → setup-complete, launched post-registration); Breez logs viewer; delete-wallet; BIP-39/Cake seed compatibility; Cake-compatible receive QR; megaphone disable+revert; payment-copy rebrand; balance/currency spacing; hide-amount-in-banner; settings reorder.

## Top 5 risks to review first

1. **BIP-39 seed change** (`85485a8b7d`, DECISIONS "Cake/BIP-39 seed compatibility") — `BreezSdkWrapper.connect` now seeds from `Seed.Mnemonic(bip39EntropyToMnemonic(entropy))` instead of `Seed.Entropy`. **This changes how the wallet is derived from the stored entropy.** Verify on a device that wallets create/restore correctly and the phrase imports into Cake. Highest-impact change.
2. **Backup restore — wallet-seed wipe** (`ae56882371`, ISSUES) — iOS fixed a StorageService manifest-rotation bug that could wipe `paymentsEntropy` on restore. Android already syncs the entropy, but the manifest path differs; **confirm a backup restore preserves the wallet seed** (data-loss risk if the analog exists).
3. **Payment onboarding flow** (`07343eab15`, ISSUES) — not UI-run. Verify the post-registration one-time launch + gating, the deposit-received transition (balance-observe), and that no exit strands the user.
4. **On-chain receive + QR encoding** (`e5f586cbd3`/`4f069a4e30`) — not UI-run. Verify the Lightning/on-chain toggle fetches + shows the right address/QR; decide whether the Lightning QR should be a BOLT11 invoice (iOS) vs the `lightning:<address>` URI shipped here.
5. **Breez SDK version mismatch** (`42e070db35`, ISSUES) — iOS is on Breez **0.14.0**, Android on **0.9.1**. All Breez calls were wired against 0.9.1; bumping is a major jump needing API migration + full build.

## Deferred items — suggested review order

1. **Push relay + disclaimer** (`0e11af806c` #19, `8353e6b3c9` #31) — biggest net-new feature gap; needs an FCM-based relay design + product decision (external `push.radar.chat` service). ISSUES: "Push relay…".
2. **Backup fixes** (`ae56882371` #20, `8ab30cb69e` #29) — payments backup/restore correctness; **review with risk #2 above** (data-loss potential). ISSUES: "Backup restore…", "Fix backup using mobcoin".
3. **Breez SDK 0.14.0 bump** (`42e070db35` #25) — unblocks staying current with iOS; do before further Breez work. ISSUES: "Breez SDK version bump".
4. **Launcher icon + splash** (`b0bd09c807` #12, `1ce180c53e` #24, `f21e3145bc` splash) — convert `radar-logo.svg` → an Android vector and run Image Asset Studio; one asset pass covers all three. ISSUES: "Launcher icon…".

## Pointers

- Per-commit status & rationale: **`MIGRATION_PLAN.md`**
- Decisions (base branch, traversal, localization, verification, per-commit calls): **`DECISIONS.md`**
- Open risks/deferrals with next steps: **`ISSUES.md`**
- Architecture map & translation conventions: **`ANDROID_BASELINE.md`**, **`TRANSLATION_MAP.md`**

> Recommended next action: do the on-device verification pass (risks #1–#4), then schedule the deferred push-relay and Breez-SDK-bump work.

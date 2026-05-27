# Android Baseline

_Snapshot taken 2026-05-27 for the iOS→Android commit-mirroring effort._
_Repo: `signal-fork-android` (Signal fork rebranded "Radar", MobileCoin→Lightning/Breez)._

## Branch situation (important)

- **`mapping-ios-commits`** (base for this migration) = tip `97f461b44e`. It **already contains the entire `add-breez` branch** plus two extra commits:
  - `42fde6304c` — "Add show/hide ability for balance" (eye-icon toggle on payments home).
  - `97f461b44e` — "change seed length to 12" (recovery phrase 24→12 words; matches iOS `fb97d0a9b4` #27).
- `add-breez` (tip `bee753906d`, 2026-03-17) is an **ancestor** of `mapping-ios-commits`, not ahead of it. It did the bulk of the MobileCoin→Lightning migration (`Payments.kt`, `BreezSdkWrapper.kt`, `LightningAddress.kt`, `Bech32.kt`, `ic_bitcoin_lightning_24.xml`).
- ~18 files still reference MobileCoin on both branches (residual; tests, archived-payment/backup compat, config).
- Android `main` = `d88a862e09` "Bump version to 7.68.5" (does **not** contain the Radar/Lightning work).

## Language & build

- **Kotlin + Java hybrid** (~62% Kotlin), actively migrating to Kotlin.
- **Gradle** (Kotlin DSL) with `./gradlew` wrapper, **Gradle 8.11.1**. ~24 modules: `app`, `core-ui` (Compose component lib, pkg `org.signal.core.ui.compose`), `core-models`, `core-util`, `core-util-jvm`, `libsignal-service`, `billing`, `donations`, `contacts`, `qr`, `registration`, `image-editor`, `video`, `device-transfer`, `paging`, `lintchecks`, `build-logic`, etc.
- **Flavors:** distribution = `play` | `website` | `nightly`; environment = `Prod` | `Staging`. Build types include `debug`, `release`, `instrumentation`. Typical debug variant: `playProdDebug`.
- ⚠️ **App module Gradle path is `:Signal-Android`, not `:app`** (`settings.gradle.kts` line 101: `project(":app").name = "Signal-Android"`). Source still lives in `app/`.
- **Verified build command** (JDK 17 pinned inline; no env/repo change):
  - Full build: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home ./gradlew :Signal-Android:assemblePlayProdDebug` — baseline green in ~4m.
  - Per-commit compile check: `… ./gradlew :Signal-Android:compilePlayProdDebugKotlin` (resource processing + R generation run as deps).
- **Env:** Android SDK at `~/Library/Android/sdk` (platforms 24–36.1). JDK 17 at the Homebrew path above (system default is 21; we pin 17 via `JAVA_HOME`).

## UI framework

- **Hybrid:** legacy XML layouts + Fragments/Activities dominate (~2,000 layouts); Jetpack Compose is growing (`MainActivity` is Compose; `core-ui` provides Compose components; `ComposeFragment`/`ComposeDialogFragment` base classes exist).
- Screen classes live under `app/src/main/java/org/thoughtcrime/securesms/<domain>/` (e.g. `conversation/`, `conversationlist/`, `payments/`, `profiles/`, `components/settings/`).
- **Implication for mirroring:** iOS UIKit/SwiftUI `*ViewController` → Android Fragment + XML layout (preferred, matches surrounding payments code which is Fragment+XML) or Compose where the neighbouring screen is already Compose.

## DI

- **Manual service locator**, not Hilt/Dagger. Central object `AppDependencies` (`app/.../dependencies/AppDependencies.kt`), network graph in `NetworkDependenciesModule.kt`. Initialized in `ApplicationContext.onCreate()`.

## Networking & persistence

- **OkHttp** (5.x alpha) + Signal's encrypted `SignalWebSocket`; protobufs via **Wire**. Not Retrofit/Ktor.
- **Persistence:** Signal's custom **SQLCipher** `SignalDatabase` layer (not Room). Tables under `app/.../database/` (`*Table.kt`): `PaymentTable`, `RemoteMegaphoneTable`, `InAppPaymentTable`, etc.

## Payments (Lightning/Breez)

- Package `app/src/main/java/org/thoughtcrime/securesms/payments/`.
- Core: `Payments.kt` (orchestrator; still constructed with `MobileCoinConfig`), `BreezSdkWrapper.kt` (balance/send/getLightningAddress), `LightningAddress.kt`, `Bech32.kt`, `Wallet.java`, `MobileCoinLedgerWrapper.kt` (residual).
- UI under `payments/preferences/`: `PaymentsHomeFragment.java` (balance/history + eye-toggle), `create/` (send flow), `transfer/` (`PaymentsTransferQrScanFragment`), `addmoney/`, `currency/`, `history/`.
- Currency/exchange: `BreezCurrencyConversions` (in `Payments.kt`), `currency/CurrencyExchange.java`.

## Megaphone / promotions

- `app/.../megaphone/`: `Megaphones.java` (local), `RemoteMegaphoneRepository.kt`, `RemoteMegaphoneTable.kt`, display `MegaphoneComponent.kt`.
- Remote fetch job: `app/.../jobs/RetrieveRemoteAnnouncementsJob.kt` (pulls release-notes JSON). **Target for the "disable promotional megaphone" commit + its revert.**

## Username / profile

- `app/.../profiles/` (edit under `profiles/edit/`, username under `profiles/username/`), `UsernameUtil.kt`, `ProfileName.java`, settings under `components/settings/app/profile/`, username link/QR under `components/settings/app/usernamelinks/`.

## Branding & identity

- `app_name` = **"Radar"** already (`app/src/main/res/values/strings.xml`, `translatable=false`).
- Launcher icons: `app/src/main/res/mipmap-*/ic_launcher.png` + `mipmap-anydpi-v26/ic_launcher.xml` (adaptive). `AndroidManifest.xml` ~1,079 lines.
- Many user-facing strings still say "Signal"; some "MobileCoin" references remain.

## Strings & localization

- Default: `app/src/main/res/values/strings.xml` (~2,000+ strings). ~67–81 locale variants `values-<locale>/strings.xml`.
- No dedicated `sat`/`sats` unit strings found (Breez/formatting driven).
- **Practice:** new English strings → default `values/strings.xml` only (translations arrive via the translation platform). Mechanical/identifier substitutions (e.g. "24"→"12") have historically been applied across all locale files (see `97f461b44e`).

## Splash / QR / backup

- **Splash:** Android 12+ splash API on `MainActivity` (no dedicated splash Activity); `util/SplashScreenUtil.java`.
- **QR:** `qr/` module + `qr/QrCodeUtil.java`, `components/qr/QrView.java`, Compose `QrCodeData.kt`; payment scan `payments/preferences/transfer/PaymentsTransferQrScanFragment.java`.
- **Backup:** `app/.../backup/` (`FullBackupExporter/Importer.java`) and `backup/v2/` (`BackupRepository.kt`, Wire-based archivers under `backup/v2/.../Archivers/`).

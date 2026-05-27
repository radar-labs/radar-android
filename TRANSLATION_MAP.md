# iOS → Android Translation Map

_Living document — update as patterns are learned during mirroring._
_Goal: translate **intent**, not syntax. Match Android idioms; never transliterate Swift._

## Platform / framework concepts

| iOS (Radar) | Android (signal-fork-android) |
|---|---|
| UIKit `UIViewController` / SwiftUI `View` | `Fragment` + XML layout (preferred for payments; matches existing code) or `@Composable` where neighbour is Compose |
| `UINavigationController` push/pop | `NavController`/`FragmentManager` transactions; settings use `DSLSettingsFragment`/nav graphs |
| `*ViewController.swift` in `Signal/src/ViewControllers/...` | `*Fragment.(kt|java)` in `app/.../<domain>/` |
| `.xcassets` imageset (SVG/PDF/PNG) | `res/drawable/*.xml` (vector) or `res/mipmap-*` (launcher); density buckets |
| `OWSLocalizedString("KEY", comment:)` | `getString(R.string.KEY)` + entry in `res/values/strings.xml` |
| `Signal/translations/<lang>.lproj/Localizable.strings` (45 files, all edited) | `res/values[-<locale>]/strings.xml` — **edit default only for new strings**; mechanical subs may span locales |
| `Info.plist` `CFBundleDisplayName` | `app_name` in `values/strings.xml` / `AndroidManifest` |
| `AppDelegate` lifecycle | `ApplicationContext` / `AppDependencies` |
| Combine / closures | Kotlin `Flow` / `LiveData` (existing code uses both; match the file) |
| CocoaPods `Podfile` dependency | Gradle dependency in `gradle/libs.versions.toml` + module `build.gradle.kts` |
| `Signal.xcodeproj/project.pbxproj` (file registration) | **N/A** — Gradle discovers sources; never mirrored |

## Domain concepts

| iOS | Android |
|---|---|
| `SignalUI/Payments/BreezSdkExt.swift`, `PaymentsImpl.swift` | `payments/BreezSdkWrapper.kt`, `Payments.kt` |
| `SignalUI/Payments/PaymentsFormat+MobileCoin.swift`, `PaymentsDisplayPreferences.swift` | `payments/MoneyView`, formatting in `Payments.kt`/`MoneyView`; display prefs via `SignalStore.payments()` |
| `PaymentsSettingsViewController` / `PaymentSettingsMenuViewController` | `payments/preferences/PaymentsHomeFragment.java` + settings menu fragments |
| `PaymentsTransferInViewController` (receive / add funds) | `payments/preferences/addmoney/` + `transfer/` fragments |
| `PaymentsTransferOutViewController` / `SendPaymentViewController` | `payments/create/` send flow |
| `RadarUsernameViewController` (payment username) | new fragment under `payments/preferences/` or `profiles/username/` |
| `RegistrationSplash/SetupComplete/SignUpViewController`, `UsernameOnboardingViewController` | `registration/` flow fragments + `RegistrationActivity`; splash via `MainActivity`/`util/SplashScreenUtil` |
| `PaymentsOnboardingCoordinator`, `PaymentsIntro/AddFundsIntro/DepositReceivedViewController` | new onboarding fragments + a coordinator/nav graph under `payments/` |
| `CVComponentArchivedPayment` / `CVComponentPaymentAttachment` (chat bubbles) | `conversation/` payment message components (XML + binder) |
| `ChatListViewController` filter (unread/gesture) crash fixes | `conversationlist/ConversationListFragment` filter logic |
| Megaphone fetch in `AppDelegate` | `jobs/RetrieveRemoteAnnouncementsJob.kt` enqueue / `megaphone/RemoteMegaphoneRepository.kt` |
| `RadarPushRelay.swift` + `PushRegistrationManager` (APNs relay) | FCM path: `FirebaseMessagingService`/push registration under `gcm/` or `notifications/`; **higher-risk translation** |
| `LightningLogsViewController` + `LightningLogger.swift` | new settings fragment + a Breez/Lightning logger util |
| `QRCodeGenerator.swift` (Cake-compatible) | `qr/QrCodeUtil.java` / payment address encoding (`Bech32.kt`) |
| `ci_scripts/test_apns_push.py`, iOS `CLAUDE.md`, `test/Assets/*` | **N/A** — iOS-only tooling/docs, not mirrored |

## Already-present on Android (dedup — do not re-implement)

- **12-word seed** (iOS `fb97d0a9b4` #27) ≈ Android `97f461b44e` "change seed length to 12". Verify parity; mirror only deltas.
- **Balance show/hide** exists on Android (`42fde6304c`); iOS `b35e77bc82` hides balance from *banner/low-balance alert* (related but distinct — apply that intent).
- **`app_name` = Radar** already set; iOS `b0bd09c807` name-change portion is a no-op on Android.

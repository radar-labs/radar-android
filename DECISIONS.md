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

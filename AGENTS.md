# OpenKey Android (`openkey-thanghoang`) — Agent & Contributor Engineering Guide (`AGENTS.md`)

OpenKey Android is a 100% offline, privacy-first Vietnamese + English Input Method Editor (IME) engineered in **Pure Kotlin 2.x + Jetpack Compose** for the **Samsung Galaxy A37** (`6.7"` FHD+ `1080 × 2340`, `19.5:9`, `120Hz` Super AMOLED) with 1:1 Stock Samsung One UI 6/7 Keyboard layout parity.

---

## 1. Repository Layout & Module Architecture

| Path | Target / Module | Description |
| :--- | :--- | :--- |
| `openkey-core/` | `:openkey-core` (Pure JVM Kotlin) | Zero-Android-dependency Vietnamese engine (`VietnameseEngine.kt`, `VietnameseEnginePhonotactics.kt`), bilingual auto-switch (`BilingualAutoSwitchEngine.kt`), live candidate ranking (`AutoWordingEngine.kt`), dictionary & 6-class spatial typo repair (`VietnameseDictionary.kt`), generated corpus (`VietnameseDictionaryCorpus.kt`), Samsung Galaxy A37 layout geometry (`PhoneLayoutMetrics.kt`, `KeyboardLayoutDefinition.kt`), gesture controller (`KeyboardGestureController.kt`), session controller (`KeyboardSessionController.kt`), per-app language store (`SmartSwitchStore.kt`), settings (`OpenKeySettingsStore.kt`), localization (`OpenKeyL10n.kt`), and logger (`OpenKeyLogger.kt`). |
| `app/` | `:app` (Android IME + Compose UI) | Direct-Boot-aware `OpenKeyImeService.kt`, `InputConnectionCommitter.kt`, `SystemSpellCheckerBridge.kt`, `ImeComposeLifecycleOwner.kt`, 120Hz Jetpack Compose keyboard UI (`KeyboardScreen.kt`, `CandidateStripView.kt`, `KeyButton.kt`), `SettingsActivity.kt`, and persistent signing keystore (`app/openkey-debug.keystore`). |
| `shared-data/` | Single-Source Lexicon (`1` entry/line) | Canonical lexical data: `vietnamese_syllables.txt`, `vietnamese_priorities.tsv`, `vietnamese_compound_bigrams.tsv`, `english_words.tsv`, `english_next_word_bigrams.tsv`, and `typo_and_grammar_rules.tsv`. |
| `scripts/sync_shared_lexicon.py` | Lexicon Code Generator | Deterministic generator (`--write`) and CI drift verifier (`--check`) that compiles `shared-data/*` into `VietnameseDictionaryCorpus.kt`. |
| `.github/workflows/android-ci.yml` | Cloud CI/CD & APK Publisher | Runs `sync_shared_lexicon.py --check`, executes all `84` unit tests (`:openkey-core:test` + `:app:testDebugUnitTest`), builds the signed APK (`:app:assembleDebug`), and publishes `OpenKey-Android-A37.apk` to the `latest` GitHub Release on every push to `main`/`master`. |

---

## 2. Build, Lexicon Sync & Test Commands (Local & Remote Linux Agents)

1. **Adding or Fixing a Word, Priority, Bigram, or Typo Rule (`1`-File Edit)**:
   - Never hardcode word lists inside `VietnameseDictionary.kt`. Edit the 1-entry-per-line file under `shared-data/` and regenerate `VietnameseDictionaryCorpus.kt`:
     ```bash
     python3 scripts/sync_shared_lexicon.py --write
     python3 scripts/sync_shared_lexicon.py --check
     ```
2. **Fast Pure-JVM Core Unit Tests (`< 2s`, `70` tests — requires ONLY JDK 21, zero Android SDK or emulator needed)**:
   ```bash
   ./gradlew :openkey-core:test --no-daemon
   ```
3. **Full Android Unit Test Suite + Signed Samsung Galaxy A37 APK Assembly (`84` tests — requires JDK 21 + Android SDK `platforms;android-35`)**:
   ```bash
   ./gradlew :openkey-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
   ```
   - Local Output APK: `app/build/outputs/apk/debug/app-debug.apk`
4. **Cloud Build & Release via GitHub Actions (Zero Local Android SDK Required)**:
   - Pushing to `origin/main` triggers `.github/workflows/android-ci.yml` on `ubuntu-latest` and publishes the updated APK to:
     `https://github.com/1quachuoi9vang/openkey-thanghoang/releases/download/latest/OpenKey-Android-A37.apk`

---

## 3. Core Architectural & Linguistic Invariants (Non-Negotiable)

1. **Zero Network & Zero Telemetry (`REQ-001`, `ADR-001`)**:
   - `app/src/main/AndroidManifest.xml` must **never** declare `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`.
   - `OpenKeyImeService` must remain `android:directBootAware="true"` using device-protected storage (`createDeviceProtectedStorageContext()`).
2. **Persistent APK Signing & Monotonic Version Code (`REQ-022`, `ADR-023`)**:
   - All builds (local and CI) must sign with `app/openkey-debug.keystore` and compute `versionCode` monotonically from `git rev-list --count HEAD` (with `fetch-depth: 0` in CI) so users can install new APKs over existing installs without `"App not installed as package conflicts with an existing package"`.
3. **Zero-Underline Atomic Direct Replacement (`REQ-005`, `ADR-005`)**:
   - Never use `InputConnection.setComposingText` by default. Commit keystroke mutations atomically via `beginBatchEdit()` -> `deleteSurroundingText(backspaceCount, 0)` -> `commitText(committedText, 1)` -> `endBatchEdit()` (`BatchReplacementCoordinator.kt` / `InputConnectionCommitter.kt`).
4. **120Hz Hot-Path Latency, Commit-First Execution & Concurrency Safety (`REQ-011`–`REQ-015`, `REQ-028`, `ADR-011`–`ADR-014`, `ADR-029`)**:
   - On key press, `KeyboardSessionController.onKeyPress` must return `CommitDelta` immediately so `InputConnectionCommitter.applyDelta` runs **before** asynchronous `AutoWordingEngine.computeCandidates` evaluation.
   - `AutoWordingEngine` must read configuration flags lock-free via `@Volatile private var config = AutoWordingConfig()` and capture `languagePriority` + `VietnameseEngineState.shared.lastPreviousWord()` synchronously on the caller thread under `VietnameseEngineState.lock` before dispatching background candidate work.
   - Spacebar trackpad cursor scrubbing must use `BatchReplacementCoordinator.moveCursorByOffset(delta)` (`setSelection`) with **zero** synchronous `getExtractedText` IPC calls during drag events.
5. **Per-App SmartSwitch Isolation (`REQ-028`, `AC-028.1`)**:
   - `KeyboardSessionController.onStartInput` must update only `editorUiState.language` (and never overwrite global `settingsStore.language`) when applying a per-app language override from `SmartSwitchStore`. Only explicit user language toggles (`KeyAction.SwitchLanguage`) may mutate `settingsStore.language`.
6. **Samsung Galaxy A37 One UI 6/7 Layout, Full-Cell Voronoi Touch Targets & 4×4 Numpad (`REQ-007`, `REQ-020`, `REQ-023`, `REQ-025`, `REQ-026`)**:
   - In `KeyButton.kt`, `.pointerInput(key)` MUST be placed **before** visual `.padding(horizontal = gapH, vertical = gapV)` so 100% of every key's grid cell is an active touch target with zero dead-zone seams between keys.
   - `CandidateStripView.kt` must remain clutter-free (3 candidate slots only; zero top settings gear or `"English (US)"` idle header).
   - Numeric fields (`TYPE_CLASS_NUMBER`, `TYPE_CLASS_PHONE`, `TYPE_CLASS_DATETIME`) must render `KeyboardLayer.NUMPAD` (`4×4` Samsung One UI numpad), never the full QWERTY keyboard.
7. **Vietnamese Corpus Purity, `V`/`E` Homograph Disambiguation & 6-Class Spatial Auto-Correct (`REQ-018`, `REQ-026`, `REQ-027`, `ADR-019`, `ADR-027`, `ADR-028`)**:
   - **Pure Bucket Invariant (`AC-027.1`)**: Every word in `stemToWords[stem]` must satisfy `stripVietnameseDiacritics(word) == stem`, and every word in `telexSignatureToWords[sig]` must satisfy `sorted(strip(word)) == sig`. Never add hardcoded non-homophone bucket overrides.
   - **Phonotactic Corpus Sanitization (`AC-027.2`)**: `isPhonotacticallyValidVietnameseSyllable` must reject half-horned `*uơ`+coda strings (`"luờng"`, `"huờng"`, `"cuờng"`, `"tuờng"`, `"phuờng"`, `"thuờng"`, `"vuờn"`, `"suờn"`), orthographic non-words (`"ghuy"`, `"kuy"`, `"byên"`, `"bơng"`, `"bưn"`, `"tưn"`, `"nhĩa"`), and fake `d-` shadow words (`"dất"`, `"dến"`, `"diểm"`, `"dồng"`, `"dứng"`, `"dể"`). Note: `-ơng` filtering must preserve negative lookbehind `(?<![ưứừửữự])[ơớờởỡợ]ng$` so valid `-ương` words (`"lường"`, `"thường"`, `"đường"`) remain valid.
   - **Single-`d` `đ-` Indexing & Class 5 Missing-Key Insertion (`AC-027.3`)**: `stemToneToSyllable` must index single-`d` + vowel-modifier + tone keys for `đ-` syllables (`"daats "` → `"đất "`, `"deens "` → `"đến "`), and `findVietnameseSpatialCandidates` must support all 6 typo classes including Class 5 (`1`-missing-key insertion + `ORTHO_ONSET_PAIRS` for `"ngiax "` → `"nghĩa "`, `"ngiengs "` → `"nghiếng "`) and Class 2 neighbor slips (`"nfhiax "` → `"nghĩa "`).
   - **Context-Ordered `V` / `E` Homographs (`REQ-018`)**: Bilingual homographs (`sex`/`"sẽ"`, `max`/`"mã"`, `six`/`"sĩ"`, `tax`/`"tã"`, `seen`/`"sên"`, `seem`/`"sêm"`, `room`/`"rôm"`, `soon`/`"sôn"`, `door`/`"dổ"`) must reside in `lookback` (`englishLookbackStems`) in `shared-data/english_words.tsv` and never in `priority` (`englishPriorityWords`).
8. **File & Function Modularity Bounds (`REQ-028`, `AC-028.3`, `AC-028.4`)**:
   - `VietnameseDictionary.kt` must remain `< 550` lines.
   - `BilingualAutoSwitchEngine.evaluateWordBreakRestore` must remain `< 120` lines, delegating to single-responsibility helpers (`<= 65` lines each) and `emitHookReplacement`.
9. **Secure Input Redaction in Debug Mode (`REQ-017`, `ADR-017`)**:
   - `OpenKeyLogger.kt` must use lazy closure evaluation (`() -> String`) and never log keystroke values when `isSecureField == true`.

---

## 4. Agent Engineering & Governance Rules

### 4.1 Strict Test-Driven Development (`RULE[tdd]`)
- **Red-Green-Refactor Order**: Never write production code for new behavior or bug fixes without first writing and observing a failing unit test (`RED`) that reproduces the exact missing behavior or defect.
- **Anti-Vacuity & Zero Self-Mocking**: Tests must invoke the real production class/function under test (`VietnameseEngine`, `BilingualAutoSwitchEngine`, `VietnameseDictionary`, `KeyboardSessionController`). Never mock or stub the target unit under test.
- **Bounded Iteration**: Cap implementation attempts at 5 per increment; if still failing, stop and diagnose root causes rather than making speculative edits.
- **Non-Destructive Discipline**: Never run `git reset --hard` or `git clean -fd`.

### 4.2 Attention Guard, Subagent Delegation & Explicit Approval Gating (`RULE[agent-delegation]`)
- **Standalone Explicit Approval Gating**:
  - **Phase 0 Disambiguation**: Clarify ambiguous requirements before planning.
  - **Phase 1 Planning (`implementation_plan.md`)**: For multi-step features or refactors, author `implementation_plan.md` with verification criteria and rollback steps before mutating code.
  - **Human Approval Gate**: Never transition to Phase 2 (Execution) on multi-step plans without explicit human consent (`"Proceed"` or `"Execute"`).
- **Subagent Delegation & 300s Watchdog**:
  - When operating under a thinking model, delegate multi-step code edits, builds, and test runs to `flash` subagents (`invoke_subagent(TypeName="self", Model="flash")`) and arm a 300s watchdog timer (`schedule(DurationSeconds=300, TimerCondition="any")`).
- **Problem Intolerance & Zero Error Suppression (`no-error-suppression`)**:
  - Fail fast: use `set -euo pipefail` in shell scripts; prohibit empty catch blocks (`catch (e: Exception) {}` or `except Exception: pass`) and never mask failures with `|| true`.
- **Epistemic Honesty (`reasoning-quality`)**:
  - Never modify code or state until the root cause is proven with concrete evidence. Keep communication direct, concise, and factual.

### 4.3 3-Way Bug Decision & Peer Review Protocol (`RULE[spec-driven]`, `RULE[ai-review-governance]`)
- **Case 1 (Implementation Bug)**: Add a failing reproducer test, fix via TDD Red → Green, and verify all 84 unit tests pass.
- **Case 2 (Design / Architecture Flaw)** or **Case 3 (Requirement Gap)**: Update the design/specification and obtain human `"Proceed"` before coding.
- **Zero Blocker Suppression**: Any P0 or P1 finding during `/code-review` blocks completion until resolved and verified by tests.

### 4.4 CLI, Token Hygiene (`rtk`) & Memory Persistence (`RULE[rtk]`, `RULE[dev-cli]`, `RULE[openviking-memory]`)
- **Universal RTK Prefixing (`RULE[rtk]`)**:
  - Always prefix shell commands with `rtk` (`rtk git ...`, `rtk gh ...`, `rtk ./gradlew ...`, `rtk python3 ...`).
  - If running on a fresh remote Linux container where `rtk` is not yet installed, bootstrap the static `rtk` binary in `< 2s` (no `sudo` or Rust toolchain required):
    ```bash
    command -v rtk >/dev/null || (curl -fsSL https://raw.githubusercontent.com/rtk-ai/rtk/refs/heads/master/install.sh | sh && export PATH="$HOME/.local/bin:$PATH")
    ```
  - Use `rtk err <cmd>` to silence stdout on success and `rtk test <cmd>` to isolate failing assertions.
- **Official CLI Routing (`RULE[dev-cli]`)**:
  - Always use the official GitHub CLI (`rtk gh ...`) for pull requests, issues, releases, and workflow runs (`rtk gh run list`, `rtk gh run watch`).
- **OpenViking Task Closure (`RULE[openviking-memory]`)**:
  - Upon completing and verifying any non-trivial bug fix or feature milestone, persist the structured summary (`Symptom`, `Root Cause`, `Architecture Pipeline`, `Working Solution`, `Verification`, `Gotchas`) to OpenViking (`viking://resources/openkey/...`).

# OpenKey Android — Samsung Galaxy A37 IME

[![Android CI/CD](https://github.com/1quachuoi9vang/openkey-thanghoang/actions/workflows/android-ci.yml/badge.svg)](https://github.com/1quachuoi9vang/openkey-thanghoang/actions)
[![Download APK](https://img.shields.io/badge/Download-OpenKey--Android--A37.apk-green?logo=android&logoColor=white)](https://github.com/1quachuoi9vang/openkey-thanghoang/releases/download/latest/OpenKey-Android-A37.apk)
[![Agent Guide](https://img.shields.io/badge/Guide-AGENTS.md-blue)](AGENTS.md)

OpenKey Android is a 100% offline, privacy-first Vietnamese + English Input Method Editor (IME) engineered in **Pure Kotlin 2.x + Jetpack Compose** for the **Samsung Galaxy A37** (`6.7"` FHD+ `1080 × 2340`, `19.5:9`, `120Hz` Super AMOLED) with 1:1 Stock Samsung One UI 6/7 Keyboard layout parity.

---

## 📦 Direct APK Download & CI Runs

- **Download Latest Pre-built APK**: [OpenKey-Android-A37.apk](https://github.com/1quachuoi9vang/openkey-thanghoang/releases/download/latest/OpenKey-Android-A37.apk) (signed and packaged automatically on every push)
- **GitHub Actions Pipeline Status**: [View CI/CD Workflow Runs](https://github.com/1quachuoi9vang/openkey-thanghoang/actions)
- **Agent & Engineering Guide**: See [AGENTS.md](AGENTS.md) for full architecture invariants, single-source lexicon editing workflows, and Mac-to-Linux cross-platform test commands.

---

## 🌟 Key Architecture & Invariants

- **100% Offline & Zero Network (`REQ-001`)**: Zero network permissions declared in `AndroidManifest.xml` (`android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` are strictly forbidden). Direct-Boot aware (`android:directBootAware="true"`) using device-protected storage.
- **Samsung Galaxy A37 Ergonomics & Geometry**: Precision keycap and gap metrics matching Samsung One UI Keyboard for 6.7" 19.5:9 120Hz displays.
- **Zero-Underline Atomic Direct Replacement**: Keystroke mutations committed atomically via `deleteSurroundingText` and `commitText` with zero composing underline flicker.
- **Bilingual Auto-Switch & Homograph Disambiguation**: Context-ordered `V` / `E` disambiguation for Telex keystroke collisions (e.g. `sex`/`sẽ`, `max`/`mã`, `six`/`sĩ`).
- **Single-Source Shared Lexicon**: Canonical lexicon datasets in `shared-data/` synchronized to Kotlin corpus via `scripts/sync_shared_lexicon.py`.

---

## 🛠️ Build & Test Commands

### 1. Synchronize / Verify Shared Lexicon
```bash
python3 scripts/sync_shared_lexicon.py --check
```

To regenerate the Kotlin corpus from `shared-data/`:
```bash
python3 scripts/sync_shared_lexicon.py --write
```

### 2. Fast Pure-JVM Core Unit Tests (< 2s, requires only JDK 21)
```bash
./gradlew :openkey-core:test --no-daemon
```

### 3. Run Full Android Unit Tests & Assemble Debug APK
```bash
./gradlew :openkey-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

The resulting signed APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Repository Structure

```text
.
├── .github/workflows/android-ci.yml   # Automated CI/CD, tests, APK build & GitHub release
├── AGENTS.md                          # Comprehensive agent & contributor engineering guide
├── README.md                          # Quick start and project overview
├── app/                               # Android IME application & Jetpack Compose UI
│   ├── src/main/                      # OpenKeyImeService, InputConnectionCommitter, Compose UI
│   ├── src/test/                      # Android unit tests & architectural verification
│   └── openkey-debug.keystore         # Persistent debug keystore for reproducible APK signing
├── openkey-core/                      # Zero-Android-dependency Pure JVM Kotlin linguistic engine
│   ├── src/main/kotlin/               # VietnameseEngine, BilingualAutoSwitchEngine, Lexicon
│   └── src/test/kotlin/               # 70 pure JVM unit tests (< 2s execution)
├── shared-data/                       # Canonical human-readable TSV/TXT linguistic datasets
├── scripts/sync_shared_lexicon.py     # Shared lexicon synchronization script
└── build.gradle.kts / settings...     # Gradle root build definitions
```

---

## 📜 License

Licensed under GNU General Public License v3.0 (GPL-3.0).

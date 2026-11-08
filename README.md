# OpenKey Android — Samsung Galaxy A37 IME

[![Android CI/CD](https://github.com/1quachuoi9vang/openkey-thanghoang/actions/workflows/android-ci.yml/badge.svg)](https://github.com/1quachuoi9vang/openkey-thanghoang/actions/workflows/android-ci.yml)
[![Open in Firebase Studio](https://img.shields.io/badge/Open%20in-Firebase%20Studio-blue?logo=firebase&logoColor=white)](https://studio.firebase.google.com/import?url=https://github.com/1quachuoi9vang/openkey-thanghoang)
[![Download APK](https://img.shields.io/badge/Download-OpenKey--Android--A37.apk-green?logo=android&logoColor=white)](https://github.com/1quachuoi9vang/openkey-thanghoang/releases/download/latest/OpenKey-Android-A37.apk)

OpenKey Android is a 100% offline, privacy-first Vietnamese Input Method Editor (IME) engineered in pure Kotlin 2.x and Jetpack Compose for the **Samsung Galaxy A37** (`6.7"` FHD+ `1080 × 2340`, `19.5:9`, `120Hz` Super AMOLED) with 1:1 Stock Samsung One UI Keyboard layout parity.

---

## ⚡ 1-Click Cloud Workspace

Develop, build, test, and run the Android emulator directly in your browser with **Google Firebase Studio** (powered by Project IDX Nix environment):

[![Open in Firebase Studio](https://img.shields.io/badge/Google%20Firebase%20Studio-Open%20Workspace-orange?style=for-the-badge&logo=firebase)](https://studio.firebase.google.com/import?url=https://github.com/1quachuoi9vang/openkey-thanghoang)

Direct Import URL:
```text
https://studio.firebase.google.com/import?url=https://github.com/1quachuoi9vang/openkey-thanghoang
```

---

## 📦 Direct APK Download

Download the pre-built, signed Samsung Galaxy A37 APK from the latest release:

👉 **[Download OpenKey-Android-A37.apk](https://github.com/1quachuoi9vang/openkey-thanghoang/releases/download/latest/OpenKey-Android-A37.apk)**

---

## 🌟 Key Architecture & Highlights

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

### 2. Run Pure JVM Core Unit Tests (< 2s, requires only JDK 17/21)
```bash
./gradlew :openkey-core:test
```

### 3. Run Full Android Unit Tests & Assemble Debug APK
```bash
./gradlew :openkey-core:test :app:testDebugUnitTest :app:assembleDebug
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
├── .idx/dev.nix                       # Google Firebase Studio (Project IDX) configuration
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

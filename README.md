# 2BAC Limits — Native Android APK

100% native Jetpack Compose & Material 3 Android application for **2BAC PC — Labo des Limites** (`مختبر النهايات اللانهائي`), designed from the ground up for mobile portrait & landscape usage in pure OLED Black Mode (`#000000`).

## Architecture & Highlights

- **100% Native Jetpack Compose Material 3 UI (`MainActivity` + `BacLimitsViewModel`)**:
  - **OLED Black Theme (`OledColors`)** with edge-to-edge black system bars and seamless bilingual **Arabic (RTL) / French BIOF (LTR)** layout switching (`AppStrings`).
  - **4-Tab Bottom Navigation**: `Practice` (adaptive problem workspace), `Progress` (animated accuracy arc & per-technique mastery), `History` (filterable exercise cards with wrapped readable Unicode math via `LatexFormatter`), and `Settings` (account switcher, language, server diagnostics, OTA updater).
  - **Tactile Math Keypad (`MathKeypad`)**: Haptic key grid for roots (`√`, `∛`, `⁴√`), infinities (`+∞`, `-∞`), `π`, trig (`sin`, `cos`, `tan`), fractions, exponents, and cursor-aware editing with live KaTeX preview and optional system keyboard toggle.
- **Zero-Clip Adaptive Math Stage (`KaTeXFormulaView` + `KaTeXStepsView` + `katex_stage.html`)**:
  - Offline bundled **KaTeX 0.16.11** canvas with proportional auto-scaling (`1.0x → 0.72x`), left-anchored horizontal panning for extra-long limits, and a one-tap **Fullscreen Zoom Dialog** (`0.75×` to `2.20×` vector magnification).
  - Step-by-step bilingual hints & solutions render mixed prose + inline `$...$` equations crisply via KaTeX `auto-render`.
- **Online-First Sync + Offline CAS Engine (`LimitsRepository` + `LocalLimitsApi` + `ProgressSyncManager`)**:
  - Syncs directly with the FastAPI + SymPy backend (`http://51.170.138.59`) when online, restoring student progress, history, mastery, and active exercises across devices.
  - Falls back transparently offline using deterministic parametric generation (**`LimitsCasEngine`** across `poly_factor`, `sqrt_conjugate`, `nth_root`, `add_sub_zero`, `trig`, `hybrid`), symbolic equivalence checking (**`MathExprEvaluator`**), SQLite v2 server cache (**`StudentDatabase`**), and replay sync queue.
- **Persistent V1 + V2 + V3 + V4 Signing & In-App OTA Updater (`AppUpdaterManager`)**:
  - Uses `app/keystore/release.jks` (`enableV1Signing` through `enableV4Signing`) for seamless upgrades over prior versions without data loss.
  - Checks GitHub Releases (`Ayoubvvch/2BAC-Limits-APK`), downloads with live progress bar, and installs via `FileProvider`.

## Project Structure

```text
app/
├── keystore/release.jks
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── assets/
    │   │   ├── katex_stage.html        # Offline math canvas & step renderer
    │   │   └── vendor/katex/           # Offline KaTeX 0.16.11 bundle
    │   └── java/com/ayoub/baclimits/
    │       ├── MainActivity.kt
    │       ├── math/
    │       │   ├── MathExprEvaluator.kt
    │       │   └── LimitsCasEngine.kt
    │       ├── data/
    │       │   ├── LimitsModels.kt
    │       │   ├── LatexFormatter.kt
    │       │   ├── StudentDatabase.kt
    │       │   ├── RemoteSyncProtocol.kt
    │       │   ├── LocalLimitsApi.kt
    │       │   ├── ProgressSyncManager.kt
    │       │   ├── LimitsRepository.kt
    │       │   └── AppUpdaterManager.kt
    │       └── ui/
    │           ├── BacLimitsViewModel.kt
    │           ├── i18n/AppStrings.kt
    │           ├── theme/BacLimitsTheme.kt
    │           ├── components/
    │           │   ├── KaTeXMathView.kt
    │           │   ├── MathKeypad.kt
    │           │   └── AppSheets.kt
    │           └── screens/
    │               ├── LoginScreen.kt
    │               ├── PracticeScreen.kt
    │               ├── ProgressScreen.kt
    │               ├── HistoryScreen.kt
    │               └── SettingsScreen.kt
    └── test/java/com/ayoub/baclimits/
        ├── LimitsCasEngineTest.kt
        ├── RemoteSyncProtocolTest.kt
        ├── LatexFormatterTest.kt
        └── LimitsModelsTest.kt
```

## Build & Test Locally

```bash
./gradlew testReleaseUnitTest
./gradlew assembleRelease
```

The signed APK is output to `app/build/outputs/apk/release/app-release.apk`.

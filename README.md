# 2BAC Limits — Native Android APK

Ultra-fast, lightweight, 100% offline-first native Android application for **2BAC PC — Labo des Limites** (`مختبر النهايات اللانهائي`), wrapped in pure OLED Black Mode (`#000000`).

## Architecture & Highlights

- **100% Content & Design Fidelity**: The original `index.html`, `styles.css`, and `app.js` assets are preserved byte-for-byte inside `app/src/main/assets/` without modifying a single character.
- **Pure OLED Black Mode (`#000000`)**: Hardware-accelerated WebView, pure black system status/navigation bars, native top utility strip, and matching dark launcher icon.
- **100% Offline-First Mathematical CAS Engine**:
  - Intercepts `/api/*` calls directly via `WebViewCompat.addDocumentStartJavaScript` + `@JavascriptInterface` bridge (`LocalLimitsApi`) with zero socket overhead.
  - **Deterministic Parametric Generator (`LimitsCasEngine`)**: Generates billions of non-repeating exercises across all 6 techniques (`poly_factor`, `sqrt_conjugate`, `nth_root`, `add_sub_zero`, `trig`, `hybrid`) and 3 difficulty levels with reproducible `2BAC-TECH-L#-XXXXXXXX` seeds.
  - **Symbolic Equivalence Evaluator (`MathExprEvaluator`)**: Recursive-descent expression parser accepting equivalent forms (`0.5`, `1/2`, `sqrt(2)/2`, `1/sqrt(2)`, `√2/2`, `root(x, n)`, `+inf`, `-inf`, `pi`).
  - **Bundled KaTeX 0.16.11**: Served directly from local APK assets via `shouldInterceptRequest` for instant offline rendering.
  - **SQLite Persistence & Auto-Sync (`StudentDatabase` + `ProgressSyncManager`)**: Stores student profiles, mastery, streaks, resume state, and a pending sync queue that automatically uploads when network connectivity is available (when an optional remote sync endpoint is configured).
- **Persistent V1 + V2 + V3 + V4 Signing**: Uses `app/keystore/release.jks` (`enableV1Signing` through `enableV4Signing`) so future updates install cleanly over existing versions without uninstalling.
- **In-App OTA Updater (`InAppUpdaterDialog`)**: Queries GitHub Releases (`Ayoubvvch/2BAC-Limits-APK`), displays a live download progress bar, supports optional personal access tokens if the repository is ever switched to Private, and triggers the Android installer automatically via `FileProvider`.
- **Automated CI/CD**: `.github/workflows/build-release.yml` runs unit tests, builds the signed release APK, verifies the V1–V4 signature, and publishes to GitHub Releases on every push to `main` or `v*` tag.

## Project Structure

```text
app/
├── keystore/release.jks
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── assets/
    │   │   ├── index.html          # Literal untouched source
    │   │   ├── styles.css          # Literal untouched source
    │   │   ├── app.js              # Literal untouched source
    │   │   └── vendor/katex/       # Offline KaTeX 0.16.11 bundle
    │   └── java/com/ayoub/baclimits/
    │       ├── MainActivity.kt
    │       ├── math/
    │       │   ├── MathExprEvaluator.kt
    │       │   └── LimitsCasEngine.kt
    │       ├── data/
    │       │   ├── StudentDatabase.kt
    │       │   ├── LocalLimitsApi.kt
    │       │   └── ProgressSyncManager.kt
    │       └── updater/
    │           └── InAppUpdaterDialog.kt
    └── test/java/com/ayoub/baclimits/
        └── LimitsCasEngineTest.kt
```

## Build & Test Locally

```bash
./gradlew testReleaseUnitTest
./gradlew assembleRelease
```

The signed APK is output to `app/build/outputs/apk/release/app-release.apk`.

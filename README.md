# 2BAC Limits — Native Android APK (Dual Editions: Lite & Full)

100% native Jetpack Compose & Material 3 Android application for **2BAC PC — Labo des Limites** (`مختبر النهايات اللانهائي`), designed from the ground up for mobile portrait & landscape usage in pure OLED Black Mode (`#000000`).

---

## 🎯 Dual Edition Architecture (النسختان الرسميتان)

| الميزة / Feature | النسخة الخفيفة (Lite Edition) | النسخة الكاملة (Full Edition) |
|---|---|---|
| **حجم التطبيق (APK Size)** | **~2.4 MB** (خفيف جداً) | **~25–30 MB** (شامل مكتبات بايثون) |
| **المحرك أوفلاين (Offline Engine)** | محرك Kotlin CAS الداخلي | **Python 3.11 + SymPy 1.13+ CAS** |
| **دقة التوليد أوفلاين** | بارامترية سريعة ودقيقة | **100% مطابقة تامة لخوارزميات السيرفر** |
| **التصحيح الرمزي (Grading)** | فحص رمزي محلي مدمج | **SymPy CAS Symbolic Proof & Equivalence** |
| **سرعة الإقلاع الأول** | فوري ولحظي | ثوانٍ إضافية في أول تشغيل لفك الحزم ثم سريع |
| **المزامنة السحابية (Cloud Sync)** | متزامنة مع خادمنا (`51.170.138.59`) | متزامنة مع خادمنا (`51.170.138.59`) |
| **اسم الحزمة (Application ID)** | `com.ayoub.baclimits` | `com.ayoub.baclimits.full` |
| **اسم ملف الـ APK** | `2BAC-Limits-Lite-v{VER}.apk` | `2BAC-Limits-Full-v{VER}.apk` |

---

## 🏗️ Project Structure

```text
├── keystore/release.jks                # Persistent V1+V2+V3+V4 signing keystore
├── core/                               # Shared Android Library Module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── assets/
│       │   ├── katex_stage.html        # Offline math canvas & step renderer
│       │   └── vendor/katex/           # Offline KaTeX 0.16.11 bundle
│       └── java/com/ayoub/baclimits/
│           ├── CoreMainActivity.kt
│           ├── data/
│           │   ├── OfflineLimitsEngine.kt   # Engine abstraction & holder
│           │   ├── LimitsModels.kt
│           │   ├── LatexFormatter.kt
│           │   ├── StudentDatabase.kt
│           │   ├── RemoteSyncProtocol.kt
│           │   ├── LocalLimitsApi.kt
│           │   ├── ProgressSyncManager.kt
│           │   ├── LimitsRepository.kt
│           │   └── AppUpdaterManager.kt
│           ├── math/
│           │   ├── MathExprEvaluator.kt
│           │   └── LimitsCasEngine.kt       # Pure Kotlin CAS engine
│           └── ui/
│               ├── BacLimitsViewModel.kt
│               ├── i18n/AppStrings.kt
│               ├── theme/BacLimitsTheme.kt
│               ├── components/
│               │   ├── KaTeXMathView.kt
│               │   ├── MathKeypad.kt
│               │   └── AppSheets.kt
│               └── screens/
│                   ├── LoginScreen.kt
│                   ├── PracticeScreen.kt
│                   ├── ProgressScreen.kt
│                   ├── HistoryScreen.kt
│                   └── SettingsScreen.kt
├── app/                                # Lite Application Module (~2.4 MB)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/ayoub/baclimits/
│           └── MainActivity.kt         # Injects LiteKotlinLimitsEngine
└── app-full/                           # Full Application Module (Python + SymPy)
    ├── build.gradle.kts                # Applies Chaquopy plugin with pip sympy>=1.13
    └── src/main/
        ├── AndroidManifest.xml
        ├── python/
        │   ├── engine/                 # 4,619 lines of Moroccan limits procedural code
        │   │   ├── models.py
        │   │   ├── bricks.py
        │   │   ├── selector.py
        │   │   ├── verifier.py
        │   │   └── generators/
        │   └── engine_bridge.py        # Python bridge for generation & grading
        └── java/com/ayoub/baclimits/
            ├── MainActivity.kt         # Injects FullPythonLimitsEngine
            └── full/
                └── FullPythonLimitsEngine.kt
```

---

## 🚀 Build & Test Commands

```bash
# Run unit tests on host JVM
./gradlew :core:testReleaseUnitTest

# Build Lite Release APK (~2.4 MB)
./gradlew :app:assembleRelease

# Build Full Release APK (with Python + SymPy)
./gradlew :app-full:assembleRelease

# Build Both Dual Signed APKs
./gradlew assembleRelease
```

Signed APK outputs:
- **Lite**: `app/build/outputs/apk/release/app-release.apk`
- **Full**: `app-full/build/outputs/apk/release/app-full-release.apk`

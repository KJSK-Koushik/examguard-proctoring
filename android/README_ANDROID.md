# ExamGuard — Native Android App

A native Android port of the ExamGuard proctoring system. It watches the
candidate through the **front camera**, detects proctoring violations on-device,
scores risk in real time, and writes a session report — **fully offline**
(no `INTERNET` permission, nothing leaves the device).

This lives alongside the original Python desktop app (repo root). The Python app
is the reference implementation; the scoring logic here is a faithful Kotlin port.

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose (Material 3) |
| Camera | CameraX 1.3.4 |
| Face detection | ML Kit Face Detection 16.1.7 (bundled, offline) |
| Build | Gradle 8.10.2 + AGP 8.7.2 |
| Min / Target SDK | 24 / 34 |
| JDK | 17+ (built/verified on JDK 21) |

## Architecture

```
app/src/main/java/com/examguard/proctoring/
├── ExamGuardApplication.kt        # app init + crash logging hook
├── core/                          # pure-Kotlin, JVM-unit-testable
│   ├── ProctoringModels.kt        # ViolationType, RiskLevel, AlertEvent, DetectionFrame
│   ├── ProctoringConfig.kt        # tunables (mirrors Python config/settings.py)
│   ├── AlertEngine.kt             # scoring / cooldown / decay (port of alert_engine.py)
│   └── SessionReporter.kt         # writes CSV + summary to filesDir (offline)
├── detect/
│   └── ProctoringAnalyzer.kt      # CameraX analyzer: ML Kit faces + luma motion
└── ui/
    ├── MainActivity.kt            # permission handling + phase routing
    ├── ProctoringViewModel.kt     # StateFlow single source of truth
    ├── CameraPreview.kt           # CameraX preview binding
    ├── Screens.kt                 # Setup / Permission / Dashboard / Report
    └── theme/Theme.kt             # dark palette (matches the desktop UI)
```

Separation of concerns: `core/` has **no Android dependencies** and is unit-tested
on the JVM; `detect/` adapts camera frames into the domain model; `ui/` is pure
presentation driven by a `ViewModel` exposing immutable `StateFlow` state.

## Build

```bash
# from the android/ directory
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests (AlertEngine)
./gradlew connectedDebugAndroidTest   # instrumented UI tests (needs a running emulator/device)
```

`JAVA_HOME` must point at a JDK 17+. `ANDROID_HOME` / `ANDROID_SDK_ROOT` must
point at the Android SDK.

## Run on an emulator

```bash
# 1. start an AVD (API 34 used for verification)
$ANDROID_HOME/emulator/emulator -avd <your_avd> -gpu swiftshader_indirect &

# 2. install + launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.examguard.proctoring/.ui.MainActivity
```

> The emulator's camera is a **synthetic** scene, so live face-detection accuracy
> can't be meaningfully demonstrated there — but build, launch, navigation,
> scoring, persistence and crash-free execution all verify on the emulator.
> For real proctoring accuracy, run on a physical device (front camera).

## Release signing (production)

Release builds are configured to be signed via environment-provided credentials
so **no secrets live in version control**. Create a `keystore.properties`
(git-ignored) or export env vars, then wire a `signingConfig` into the `release`
build type before publishing:

```
storeFile=/secure/path/examguard-release.jks
storePassword=$EXAMGUARD_STORE_PASSWORD
keyAlias=examguard
keyPassword=$EXAMGUARD_KEY_PASSWORD
```

```bash
./gradlew assembleRelease   # R8 minify + resource shrink enabled
```

## CI/CD recommendation

A GitHub Actions workflow should: set up JDK 17, `./gradlew testDebugUnitTest
lintDebug assembleDebug`, and run `connectedDebugAndroidTest` on a
`reactivecircus/android-emulator-runner`. Inject the release keystore from
encrypted secrets for tagged releases only.

## Privacy / security posture

- No `INTERNET` permission — the app cannot exfiltrate data.
- Camera frames are analysed in-memory; only derived events (no images) are
  written, to app-private `filesDir` (not world-readable).
- ML Kit model is bundled — no Play Services model download at runtime.

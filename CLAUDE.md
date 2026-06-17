# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

ExamGuard is a privacy-preserving exam-proctoring system that runs **fully offline** (no cloud, no network). It exists as **two parallel implementations of the same concept**:

- **Python desktop app** (repo root) — the original/reference. OpenCV computer vision + Tkinter dashboard, runs on a laptop webcam.
- **Native Android app** (`android/`) — a Kotlin/Compose port. CameraX + ML Kit face detection on a phone.

The Android scoring logic is a **deliberate, faithful port** of the Python scoring logic. When you change violation weights, cooldowns, frame-confirmation thresholds, risk-level bands, or score decay in one, **change the other to match** (see "Shared scoring contract" below).

## "Emulator" means camera input, not a build target (Python app)

A recurring source of confusion: in the **Python** app, "emulator" refers to using an Android emulator (or any virtual camera) as a **video input source** — the Python app does not run on Android. `tools/emulator_camera.py` (`EmulatorCameraSource`) pulls frames via `adb exec-out screencap` and is a drop-in for `core/camera.py` (`CameraManager`): both expose `start()/read()/stop()`. Run with `python main.py --camera emulator`.

The **Android** app (`android/`) is the actual thing that builds and installs on a device/emulator.

## Shared scoring contract (keep in sync across both apps)

The heart of the system is a stateful alert engine that, per frame: confirms a violation persisted N consecutive frames (anti-flicker) → applies a per-violation cooldown → adds to / decays a running risk score → emits events.

- Python: weights/cooldowns/thresholds in `config/settings.py` (`VIOLATIONS`, `*_FRAME_THRESHOLD`, `RISK_LEVELS`, `SCORE_DECAY_*`); engine in `core/alert_engine.py`.
- Kotlin: same values in `android/app/src/main/java/com/examguard/proctoring/core/ProctoringConfig.kt` + `ProctoringModels.kt` (`ViolationType`, `RiskLevel`); engine in `core/AlertEngine.kt`.

Note the Android frame-confirmation thresholds are intentionally **lower** than Python's (ML Kit analysis runs slower than a 30fps webcam loop), but violation weights, cooldown seconds, and risk bands are identical.

## Python desktop app

Pipeline: `main.py` → `ui/dashboard.py` drives `core/camera.py` → per frame runs `core/motion_detector.py` (MOG2 background subtraction, zone+magnitude) **and** `core/face_detector.py` (MediaPipe BlazeFace or Haar, gives face count + gaze) → `core/alert_engine.py` scores → `ui/report_generator.py` writes CSV + screenshots to `logs/` (git-ignored).

All tunables live in `config/settings.py` (camera, MOG2 sensitivity, motion area thresholds, frame zones, face backend, violation table). `FACE_DETECTOR_BACKEND` switches `"mediapipe"` (default, uses `models/blaze_face_short_range.tflite`) vs `"haar"`.

```bash
pip install -r requirements.txt
python main.py                          # launches dashboard (prompts for student name)
python main.py --student "Harika" --camera 1
python main.py --camera emulator        # use Android emulator screencap as input
python tools/probe_cameras.py --max-index 8   # find a usable camera index

python test_system.py                   # runs the FULL suite (39 tests)
```

Tests use a **custom runner**, not pytest: `test_system.py` registers checks via a `test(name, fn)` helper and prints a `[PASS]/[FAIL]` report. There is **no per-test filter flag** — to run a subset, edit the call list at the bottom of the file. Many tests construct synthetic numpy frames so they run without a real camera.

## Android app (`android/`)

Layered, with the domain layer kept free of Android deps so it unit-tests on the JVM:
- `core/` — pure Kotlin: `AlertEngine`, models/config, `SessionReporter` (writes report to `filesDir`). The shared scoring contract lives here.
- `detect/ProctoringAnalyzer.kt` — CameraX `ImageAnalysis.Analyzer`: ML Kit face count + gaze (head Euler Y) and a cheap luma frame-diff motion level. Produces a `DetectionFrame`.
- `ui/` — Compose. `ProctoringViewModel` is the single source of truth (`StateFlow<ProctoringUiState>`); `MainActivity` routes phases (Setup → Permission → Dashboard → Report); `CameraPreview` binds CameraX.

Offline by design: manifest requests **CAMERA only, never INTERNET**; ML Kit model is bundled; only derived events (no images) are persisted to app-private storage.

### Build / test / run

Gradle is **not on PATH** — always use the wrapper. Requires `JAVA_HOME` (JDK 17+) and `ANDROID_HOME`. Run from the `android/` directory:

```bash
./gradlew assembleDebug            # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # JVM unit tests (AlertEngine)
./gradlew connectedDebugAndroidTest   # instrumented UI tests (needs a running emulator)

# single unit test class / method:
./gradlew testDebugUnitTest --tests "com.examguard.proctoring.core.AlertEngineTest"
./gradlew testDebugUnitTest --tests "com.examguard.proctoring.core.AlertEngineTest.cooldownPreventsImmediateRefire"
```

Run on emulator (AVD `MotionDetection_API34` was used for verification):

```bash
$ANDROID_HOME/emulator/emulator -avd <avd> -gpu swiftshader_indirect &
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.examguard.proctoring/.ui.MainActivity
```

The emulator camera is a **synthetic** scene, so live face-detection accuracy can't be demonstrated there (build/launch/navigation/scoring/persistence can). Under cold-boot + software GPU the emulator may show a "System UI isn't responding" dialog — that's the emulator's SystemUI, not an app crash. Build/tooling versions and release-signing/CI guidance are in `android/README_ANDROID.md`.

## Testing changes to scoring logic

When unit-testing the engine, inject a fake clock — `AlertEngine(clock = { fakeNowMillis })` — and advance it manually. Cooldown (seconds) and the score-decay interval (5s) overlap for some violations, so to isolate cooldown behaviour, use a short-cooldown violation (e.g. `looking_away`, 1s) and stay within the 5s decay window. `reset()` zeroes the score/cooldowns but **preserves the all-time event log** so the end-of-session report stays complete — tests and the ViewModel rely on this.

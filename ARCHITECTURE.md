# ExamGuard System Architecture

ExamGuard is a local desktop proctoring prototype built with Tkinter, OpenCV,
MediaPipe, and lightweight rule-based alerting. The application captures webcam
frames, analyzes face presence and motion, updates a clean dashboard, and writes
session evidence to local logs.

## Runtime Flow

```text
main.py
  -> Tkinter root window
  -> ui.dashboard.ProctoringDashboard
      -> core.camera.CameraManager
      -> core.face_detector.FaceDetector
      -> core.motion_detector.MotionDetector
      -> core.alert_engine.AlertEngine
      -> ui.report_generator.ReportGenerator
```

During a session, the dashboard starts a background processing thread:

```text
Camera frame
  -> resize to configured frame size
  -> motion detection every frame
  -> MediaPipe face detection every third frame
  -> Haar fallback if MediaPipe/model is unavailable
  -> alert evaluation with sustained-frame confirmation
  -> CSV/screenshot logging for fired alerts
  -> clean camera feed + compact status indicators in UI
```

## Main Components

### `main.py`

Creates the Tkinter application, asks for an optional student name, centers the
window, and starts the dashboard event loop.

### `core/camera.py`

Owns webcam lifecycle through `CameraManager`.

- Opens the configured camera index.
- Captures frames on a background thread.
- Resizes frames to `FRAME_WIDTH` x `FRAME_HEIGHT`.
- Waits briefly for the first valid frame during startup.
- Returns thread-safe frame copies to the processing loop.

### `core/face_detector.py`

Produces a `FaceAnalysis` object for each processed frame.

Primary backend:

- MediaPipe Tasks FaceDetector with the local BlazeFace model:
  `models/blaze_face_short_range.tflite`.

Fallback backend:

- OpenCV Haar cascades for frontal and side-profile faces.

Backend selection is configured in `config/settings.py`:

```python
FACE_DETECTOR_BACKEND = "mediapipe"
MEDIAPIPE_FACE_MODEL_PATH = "models/blaze_face_short_range.tflite"
MEDIAPIPE_FACE_MIN_CONFIDENCE = 0.55
```

The MediaPipe detector is the normal path. Haar is only a fallback path when
MediaPipe is unavailable or the local model cannot be loaded.

The detector returns:

- face count
- primary face
- eye visibility estimate
- rough gaze direction
- status: `ok`, `no_face`, `multiple`, or `away`

### `core/motion_detector.py`

Uses OpenCV MOG2 background subtraction to detect meaningful movement.

The detector:

- converts frames to grayscale
- applies blur and background subtraction
- removes shadows and small noise
- classifies motion regions by area and zone
- returns overall motion level: `none`, `low`, `medium`, or `high`

### `core/alert_engine.py`

Converts detector output into proctoring events and score changes.

Important behavior:

- Alerts require sustained evidence across multiple frames.
- Cooldowns prevent repeated spam from the same condition.
- Score decays over time during cleaner periods.
- Reset clears score, cooldowns, and confirmation streaks.

Configured alert types:

- no face detected
- multiple persons detected
- looking away
- suspicious hand/body movement
- student left seat

### `ui/dashboard.py`

Renders the desktop interface.

The current interface intentionally keeps the live camera feed clean:

- no face boxes
- no motion rectangles
- no overlay badges
- no visible alert log
- no visible risk score card

The UI keeps compact operational feedback:

- face count
- eye visibility
- gaze estimate
- motion level
- session stats
- single-line status messages

The internal risk score still exists for alerting and reports, but it is not
shown as a dashboard card.

### `ui/report_generator.py`

Writes local session artifacts under `logs/`.

Outputs:

- timestamped CSV event log
- optional screenshots for high-risk events
- plain-text session summary

`logs/` is ignored by Git except for a placeholder.

## Configuration

All tunable values live in `config/settings.py`.

Key groups:

- camera size, FPS, and camera index
- motion thresholds and zones
- MediaPipe/Haar face detection settings
- sustained-frame alert confirmation thresholds
- violation scores and cooldowns
- screenshot and log paths
- UI colors and font family

## Model Asset

The MediaPipe face detector uses:

```text
models/blaze_face_short_range.tflite
```

This model is small enough to keep in the repository and allows the app to run
without downloading model assets at startup. It was added so multiple-face
detection is more reliable than the older Haar-only approach.

## Testing

Run the full local test suite with:

```powershell
python test_system.py
```

The suite validates imports, dashboard source structure, motion detection, face
detection, alert behavior, reporting, integrated pipeline behavior, and camera
lifecycle.

Current verified test result:

```text
PASSED : 39
FAILED : 0
TOTAL  : 39
```

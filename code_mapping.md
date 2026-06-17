# ExamGuard Code Mapping

## Short Demo Speech

Good morning everyone.

This project is **ExamGuard**, a local exam proctoring system. It uses the webcam to monitor a student during an online exam. The system checks if the student is present, whether multiple faces are visible, whether the student is looking away, and whether there is suspicious movement.

The project has five main working parts.

First, the **Camera Manager** opens the webcam and captures frames continuously. Second, the **Face Detector** uses MediaPipe BlazeFace to detect faces, with Haar Cascade kept as a fallback. Third, the **Motion Detector** uses OpenCV background subtraction to detect meaningful movement. Fourth, the **Alert Engine** decides whether an activity should be treated as a violation, using cooldowns and repeated-frame confirmation to avoid false alerts. Fifth, the **Report Generator** saves CSV logs, screenshots, and a final session summary.

The interface is built with Tkinter. It shows the live camera feed, face status, eye status, gaze status, motion level, and session statistics. The interface is intentionally clean, so it does not show distracting boxes, an alert log panel, or a visible risk score card.

In short, ExamGuard combines webcam capture, MediaPipe face detection, OpenCV motion detection, rule-based alerting, and local reporting into one complete privacy-friendly proctoring system.




## One-Line Explanation of Each Core Module

| File | One-line purpose |
|---|---|
| `main.py` | Starts the app and opens the dashboard |
| `config/settings.py` | Stores all tunable settings |
| `core/camera.py` | Captures webcam frames |
| `core/face_detector.py` | Detects faces using MediaPipe/Haar |
| `core/motion_detector.py` | Detects suspicious movement |
| `core/alert_engine.py` | Converts detections into alerts |
| `ui/dashboard.py` | Shows UI and coordinates runtime processing |
| `ui/report_generator.py` | Saves logs, screenshots, and summaries |
| `test_system.py` | Validates the whole system |

## Key Parameters to Explain in Demo

| Parameter | Meaning |
|---|---|
| `CAMERA_INDEX` | Which webcam to use |
| `FRAME_WIDTH`, `FRAME_HEIGHT` | Processing frame size |
| `FACE_DETECTOR_BACKEND` | Selects MediaPipe or Haar |
| `MEDIAPIPE_FACE_MIN_CONFIDENCE` | Minimum confidence for face detection |
| `MOTION_MIN_AREA_SMALL` | Ignores tiny movement/noise |
| `MOTION_MIN_AREA_MEDIUM` | Threshold for meaningful hand/object movement |
| `MOTION_MIN_AREA_LARGE` | Threshold for large body/full-frame movement |
| `MULTIPLE_FACE_FRAME_THRESHOLD` | Frames required before multiple-face alert |
| `HIGH_MOTION_FRAME_THRESHOLD` | Frames required before high-motion alert |
| `VIOLATIONS` | Scores, cooldowns, labels, and severity |
| `SAVE_SCREENSHOTS` | Enables/disables screenshot saving |
| `SCREENSHOT_MIN_SCORE` | Minimum score needed before screenshots are saved |

## High-Level Runtime Flow

```text
main.py
  -> ui.dashboard.ProctoringDashboard
      -> core.camera.CameraManager
      -> core.face_detector.FaceDetector
      -> core.motion_detector.MotionDetector
      -> core.alert_engine.AlertEngine
      -> ui.report_generator.ReportGenerator
```

Processing flow during a session:

```text
Webcam frame
  -> resize to 640x480
  -> motion detection every frame
  -> face detection every third frame
  -> alert evaluation
  -> UI status update
  -> CSV/screenshot/summary logging
```

## File-by-File Code Map

### `main.py`

**Purpose:** Entry point of the application.

**Main responsibilities:**

- Checks whether required dependencies like OpenCV, NumPy, and Pillow are installed.
- Reads optional command-line argument: `--student "Name"`.
- If no student name is passed, shows a small Tkinter dialog to ask for the name.
- Creates and centers the main Tkinter window.
- Starts `ProctoringDashboard`.

**Important code units:**

- `main()`: Starts the application.
- `_NameDialog`: Small modal dialog for optional student name input.

**Connects to:**

- `ui.dashboard.ProctoringDashboard`

---

### `config/settings.py`

**Purpose:** Central configuration file for the whole system.

**Main responsibilities:**

- Stores camera settings.
- Stores face detection settings.
- Stores motion detection thresholds.
- Stores alert scores and cooldowns.
- Stores logging and screenshot settings.
- Stores UI colors and font settings.

**Important parameter groups:**

**Camera:**

```python
CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
TARGET_FPS = 30
```

**Motion detection:**

```python
MOG2_HISTORY = 500
MOG2_VAR_THRESHOLD = 80
MOTION_MIN_AREA_SMALL = 1200
MOTION_MIN_AREA_MEDIUM = 7000
MOTION_MIN_AREA_LARGE = 22000
```

**Frame zones:**

```python
ZONE_HEAD_BOTTOM = 0.40
ZONE_BODY_TOP = 0.40
ZONE_BODY_BOTTOM = 0.75
ZONE_SEAT_TOP = 0.75
```

**Face detection:**

```python
FACE_DETECTOR_BACKEND = "mediapipe"
MEDIAPIPE_FACE_MODEL_PATH = "models/blaze_face_short_range.tflite"
MEDIAPIPE_FACE_MIN_CONFIDENCE = 0.55
FACE_MIN_SIZE = (45, 45)
```

**Alert confirmation thresholds:**

```python
NO_FACE_FRAME_THRESHOLD = 15
MULTIPLE_FACE_FRAME_THRESHOLD = 4
LOOKING_AWAY_FRAME_THRESHOLD = 12
HIGH_MOTION_FRAME_THRESHOLD = 8
BODY_MOTION_FRAME_THRESHOLD = 12
```

**Violation scores and cooldowns:**

```python
VIOLATIONS = {
    "no_face":        (3,  2, "No face detected", "medium"),
    "multiple_faces": (10, 5, "Multiple persons detected", "high"),
    "looking_away":   (2,  1, "Looking away from screen", "low"),
    "large_motion":   (2,  2, "Suspicious hand movement", "low"),
    "excess_motion":  (5,  3, "Excessive body movement", "medium"),
    "seat_empty":     (8,  5, "Student left their seat", "high"),
}
```

---

### `core/camera.py`

**Purpose:** Handles webcam access.

**Main class:**

- `CameraManager`

**Main responsibilities:**

- Opens the webcam using OpenCV.
- Starts a background capture thread.
- Stores the latest captured frame.
- Resizes frames to the configured resolution.
- Returns a thread-safe copy of the current frame.
- Stops and releases the camera when the session ends.

**Important methods:**

- `start()`: Opens camera and starts capture thread.
- `read()`: Returns latest frame or `None`.
- `stop()`: Stops capture and releases camera.
- `is_running`: Property that tells whether the camera is active.
- `_capture_loop()`: Internal loop that continuously reads frames.

**Important settings used:**

- `CAMERA_INDEX`
- `FRAME_WIDTH`
- `FRAME_HEIGHT`
- `TARGET_FPS`

---

### `core/face_detector.py`

**Purpose:** Detects faces and returns face analysis for each processed frame.

**Main data classes:**

- `FaceResult`
- `FaceAnalysis`

**Main class:**

- `FaceDetector`

**Main responsibilities:**

- Uses MediaPipe BlazeFace as the primary face detector.
- Uses Haar Cascades as fallback if MediaPipe/model is unavailable.
- Counts faces in the frame.
- Selects the largest detected face as the primary face.
- Checks eye visibility using Haar eye detection.
- Estimates rough gaze direction.
- Removes duplicate/overlapping face detections.

**Important methods:**

- `process(frame)`: Main method. Returns `FaceAnalysis`.
- `_detect_faces(frame, gray)`: Chooses MediaPipe first, then Haar fallback.
- `_detect_faces_mediapipe(frame)`: Runs MediaPipe model.
- `_detect_faces_haar(gray)`: Runs Haar frontal/profile face detection.
- `_filter_faces(raw_faces)`: Removes duplicate and tiny false detections.
- `_iou(a, b)`: Calculates overlap between two boxes.
- `_estimate_gaze(face_x, face_w, frame_w, eye_count)`: Returns `left`, `right`, `centre`, or `away`.

**Important outputs:**

```python
FaceAnalysis(
    faces=[],
    face_count=0,
    primary=None,
    eyes_visible=False,
    gaze="unknown",
    status="ok" | "no_face" | "multiple" | "away"
)
```

**Model file used:**

```text
models/blaze_face_short_range.tflite
```

**Important settings used:**

- `FACE_DETECTOR_BACKEND`
- `MEDIAPIPE_FACE_MODEL_PATH`
- `MEDIAPIPE_FACE_MIN_CONFIDENCE`
- `FACE_NMS_IOU_THRESHOLD`
- `FACE_SECONDARY_MIN_AREA_RATIO`
- `EYE_SCALE_FACTOR`
- `EYE_MIN_NEIGHBORS`
- `EYE_MIN_SIZE`

---

### `core/motion_detector.py`

**Purpose:** Detects and classifies movement in the webcam frame.

**Main data class:**

- `MotionRegion`

**Main class:**

- `MotionDetector`

**Main responsibilities:**

- Converts frames to grayscale.
- Applies blur to reduce noise.
- Uses OpenCV MOG2 background subtraction.
- Removes shadows.
- Uses erosion and dilation to clean the foreground mask.
- Finds motion contours.
- Classifies motion by zone and magnitude.
- Returns overall motion level.

**Important methods:**

- `process(frame)`: Returns motion regions, mask, and motion level.
- `reset()`: Reinitializes the background model.
- `_classify_zone(cy, frame_h)`: Maps motion to `head`, `body`, or `seat`.
- `_classify_magnitude(area)`: Maps area to `small`, `medium`, or `large`.
- `_overall_level(regions, frame_area)`: Returns `none`, `low`, `medium`, or `high`.

**Important outputs:**

```python
regions, fg_mask, motion_level = detector.process(frame)
```

**Important settings used:**

- `MOG2_HISTORY`
- `MOG2_VAR_THRESHOLD`
- `MOG2_DETECT_SHADOWS`
- `MOTION_BLUR_KERNEL`
- `MORPH_KERNEL_SIZE`
- `MOTION_MIN_AREA_SMALL`
- `MOTION_MIN_AREA_MEDIUM`
- `MOTION_MIN_AREA_LARGE`
- zone constants from `settings.py`

---

### `core/alert_engine.py`

**Purpose:** Converts face and motion results into alerts and risk score changes.

**Main data classes:**

- `AlertEvent`
- `RiskLevel`

**Main class:**

- `AlertEngine`

**Main responsibilities:**

- Evaluates detection results frame by frame.
- Fires alerts only after suspicious behavior persists for enough frames.
- Applies cooldowns so repeated alerts do not spam.
- Updates internal risk score.
- Decays score over time.
- Stores recent and all-time alert events.
- Resets score, cooldowns, and streaks when Reset Score is clicked.

**Important methods:**

- `evaluate(face_analysis, motion_level, motion_regions, frame=None)`: Main alert evaluation method.
- `reset_score()`: Clears score, cooldowns, and confirmation streaks.
- `get_risk_level()`: Converts numeric score into `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- `recent_events`: Returns latest 50 events.
- `all_events`: Returns all events.
- `_try_fire(key, frame=None)`: Creates an alert if cooldown is over.
- `_confirmed(key, condition, threshold)`: Checks repeated-frame confirmation.
- `_apply_decay()`: Reduces score after clean intervals.

**Alert conditions handled:**

- `no_face`
- `multiple_faces`
- `looking_away`
- `large_motion`
- `excess_motion`
- `seat_empty`

**Important settings used:**

- `VIOLATIONS`
- `RISK_LEVELS`
- `NO_FACE_FRAME_THRESHOLD`
- `MULTIPLE_FACE_FRAME_THRESHOLD`
- `LOOKING_AWAY_FRAME_THRESHOLD`
- `HIGH_MOTION_FRAME_THRESHOLD`
- `BODY_MOTION_FRAME_THRESHOLD`
- `SCORE_DECAY_INTERVAL`
- `SCORE_DECAY_AMOUNT`
- `SAVE_SCREENSHOTS`
- `SCREENSHOT_MIN_SCORE`

---

### `ui/dashboard.py`

**Purpose:** Main Tkinter user interface and runtime coordinator.

**Main class:**

- `ProctoringDashboard`

**Main responsibilities:**

- Builds the UI.
- Shows live camera feed.
- Shows detection status: faces, eyes, gaze, motion.
- Shows session statistics.
- Handles buttons: Start, Pause, Reset Score, Save Report, Quit.
- Starts camera and processing thread.
- Runs face/motion detection pipeline.
- Passes detection results to `AlertEngine`.
- Sends alert events to `ReportGenerator`.
- Keeps the UI clean by not drawing boxes or overlays on the camera feed.

**Important methods:**

- `_build_ui()`: Creates all Tkinter widgets.
- `_draw_placeholder()`: Shows start screen before camera begins.
- `_processing_loop()`: Main background analysis loop.
- `_draw_hud(...)`: Currently returns clean frame without drawing boxes.
- `_poll_queue()`: Pulls processed frames into Tkinter main thread.
- `_apply_result(result)`: Updates UI indicators and stats.
- `_update_canvas(frame)`: Displays camera frame on canvas.
- `_set_status(text, tag)`: Shows one-line status message.
- `_blink_rec()`: Blinking recording indicator.
- `_on_start()`: Starts session.
- `_on_pause()`: Pauses/resumes processing.
- `_on_reset_score()`: Calls `AlertEngine.reset_score()`.
- `_on_save_report()`: Saves summary report.
- `_on_quit()`: Stops camera and closes app.

**UI sections:**

- Top bar: app title, student name, timer, REC indicator.
- Left panel: clean camera feed.
- Right panel: detection status and session stats.
- Bottom controls: Start, Pause, Reset Score, Save Report, Quit.

**Important runtime details:**

- Motion detection runs every frame.
- Face detection runs every third frame using cached `FaceAnalysis`.
- Queue size is limited to 2 to avoid UI lag.
- Report session opens in the processing thread.

---

### `ui/report_generator.py`

**Purpose:** Saves session logs, screenshots, and summary reports.

**Main class:**

- `ReportGenerator`

**Main responsibilities:**

- Creates `logs/` directory.
- Creates screenshot subdirectory for the session.
- Opens CSV session log.
- Writes alert event rows.
- Saves screenshots for qualifying alerts.
- Writes final plain-text summary.

**Important methods:**

- `open_session()`: Creates files and writes CSV header.
- `log_event(event, screenshot_path="")`: Adds alert event to CSV.
- `save_screenshot(frame, event)`: Saves event frame as `.jpg`.
- `close_session(final_score, duration_secs)`: Writes summary `.txt`.

**Important properties:**

- `csv_path`
- `screenshot_dir`
- `session_id`

**Output files:**

```text
logs/session_<timestamp>.csv
logs/summary_<timestamp>.txt
logs/screenshots/<session_id>/*.jpg
```

---

### `test_system.py`

**Purpose:** Full project test suite.

**Main responsibilities:**

- Validates imports and dependencies.
- Checks dashboard source structure.
- Tests motion detector behavior.
- Tests face detector behavior.
- Tests alert engine scoring, cooldowns, and reset.
- Tests report generation.
- Tests integrated synthetic pipeline.
- Tests camera open/read lifecycle.

**Test modules covered:**

- Imports and dependencies
- Dashboard source analysis
- Motion detector
- Face detector
- Alert engine
- Report generator
- Integrated pipeline
- Camera lifecycle

**Current expected result:**

```text
PASSED : 39
FAILED : 0
TOTAL  : 39
```

---

### `models/blaze_face_short_range.tflite`

**Purpose:** Local MediaPipe BlazeFace model file.

**Used by:**

- `core/face_detector.py`

**Why it exists:**

- Allows MediaPipe face detection to work locally.
- Avoids downloading the model at runtime.
- Improves face detection compared with Haar-only detection.

---

### `requirements.txt`

**Purpose:** Python dependency list.

**Dependencies:**

- `opencv-python`: camera access, image processing, motion detection.
- `numpy`: frame arrays and numerical operations.
- `pandas`: session data support.
- `Pillow`: converting frames for Tkinter display.
- `fpdf2`: PDF/report support.
- `mediapipe`: face detection model runtime.

Install command:

```powershell
python -m pip install -r requirements.txt
```

----
## Module Interaction Summary

```text
CameraManager
  captures frame
      |
      v
Dashboard processing loop
  sends frame to MotionDetector and FaceDetector
      |
      v
AlertEngine
  decides whether violations are confirmed
      |
      +--> Dashboard status update
      |
      +--> ReportGenerator CSV/screenshot logging
```


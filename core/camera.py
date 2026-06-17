"""
camera.py — Thread-safe webcam capture manager.

Runs the capture loop in a daemon thread and exposes the latest
frame (plus raw dimensions) to consumers via a thread-safe property.
"""

import os
import threading
import time
import cv2
from config import settings


class CameraManager:
    """
    Manages a background capture thread.

    Usage:
        cam = CameraManager()
        cam.start()
        frame = cam.read()   # always returns the most recent frame or None
        cam.stop()
    """

    def __init__(self, camera_index: int = settings.CAMERA_INDEX, camera_source=None):
        self._source = self._resolve_source(camera_index, camera_source)
        self._cap    = None
        self._frame  = None
        self._lock   = threading.Lock()
        self._running = False
        self._thread  = None

        # Actual dimensions reported by the driver (may differ from requested)
        self.width  = settings.FRAME_WIDTH
        self.height = settings.FRAME_HEIGHT

    # ─── Public API ──────────────────────────────────────────────────────────

    def start(self) -> bool:
        """Open the camera and begin capturing. Returns True on success."""
        self._cap = self._open_capture()
        if not self._cap.isOpened():
            return False

        self._cap.set(cv2.CAP_PROP_FRAME_WIDTH,  settings.FRAME_WIDTH)
        self._cap.set(cv2.CAP_PROP_FRAME_HEIGHT, settings.FRAME_HEIGHT)
        self._cap.set(cv2.CAP_PROP_FPS,          settings.TARGET_FPS)

        # Read back actual dimensions
        self.width  = int(self._cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        self.height = int(self._cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

        self._running = True
        self._thread  = threading.Thread(target=self._capture_loop,
                                         daemon=True, name="CameraThread")
        self._thread.start()

        deadline = time.time() + 1.0
        while time.time() < deadline:
            if self.read() is not None:
                break
            time.sleep(0.03)
        return True

    @property
    def source(self):
        return self._source

    @staticmethod
    def _resolve_source(camera_index, camera_source):
        source = camera_source
        if source is None:
            source = getattr(settings, "CAMERA_SOURCE", None)
        if source is None:
            env_source = os.getenv("EXAMGUARD_CAMERA_SOURCE")
            source = env_source if env_source else camera_index
        if isinstance(source, str):
            stripped = source.strip()
            if stripped.lstrip("-").isdigit():
                return int(stripped)
            return stripped
        return source

    def _open_capture(self):
        if isinstance(self._source, int):
            cap = cv2.VideoCapture(self._source, cv2.CAP_DSHOW)
            if not cap.isOpened():
                cap.release()
                cap = cv2.VideoCapture(self._source)
            return cap

        cap = cv2.VideoCapture(self._source)
        if not cap.isOpened():
            # Fallback: try without backend flag (Linux / macOS)
            cap.release()
            cap = cv2.VideoCapture(self._source, cv2.CAP_FFMPEG)
        return cap

    def read(self):
        """Return the most-recently captured BGR frame, or None."""
        with self._lock:
            if self._frame is None:
                return None
            return self._frame.copy()

    def stop(self):
        """Signal the capture thread to stop and release the device."""
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=2.0)
        if self._cap:
            self._cap.release()
            self._cap = None

    @property
    def is_running(self) -> bool:
        return self._running

    # ─── Internal ────────────────────────────────────────────────────────────

    def _capture_loop(self):
        while self._running:
            ok, frame = self._cap.read()
            if not ok:
                time.sleep(0.01)
                continue
            # Force to target resolution — some cameras ignore set() requests
            h, w = frame.shape[:2]
            if w != settings.FRAME_WIDTH or h != settings.FRAME_HEIGHT:
                import cv2
                frame = cv2.resize(frame, (settings.FRAME_WIDTH, settings.FRAME_HEIGHT))
            with self._lock:
                self._frame = frame

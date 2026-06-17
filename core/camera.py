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
        self._using_emulator = False
        self._emulator_src   = None

        # Actual dimensions reported by the driver (may differ from requested)
        self.width  = settings.FRAME_WIDTH
        self.height = settings.FRAME_HEIGHT

    # ─── Public API ──────────────────────────────────────────────────────────

    def start(self) -> bool:
        """Open the camera and begin capturing. Returns True on success."""
        sources_to_try = [self._source]
        if isinstance(self._source, int):
            # If the requested index fails, try other common camera indices
            for idx in range(9):
                if idx != self._source:
                    sources_to_try.append(idx)

        # Check if user explicitly asked for the emulator source
        if self._source == "emulator":
            return self._start_emulator_source()

        for src in sources_to_try:
            self._source = src
            self._cap = self._open_capture()
            if not self._cap or not self._cap.isOpened():
                if self._cap is not None:
                    self._cap.release()
                    self._cap = None
                continue

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

            # Warmup / check if we get a valid frame
            deadline = time.time() + 1.2
            frame_ok = False
            while time.time() < deadline:
                if self.read() is not None:
                    frame_ok = True
                    break
                time.sleep(0.03)

            if frame_ok:
                print(f"[Camera] Successfully initialized camera source: {self._source}")
                return True
            else:
                print(f"[Camera] Source {self._source} opened but failed to return frames. Trying next...")
                self.stop()

        # ── Emulator fallback ────────────────────────────────────────────────
        if getattr(settings, "EMULATOR_CAMERA_FALLBACK", False):
            print("[Camera] No physical camera found. Trying Android emulator via ADB...")
            return self._start_emulator_source()

        return False

    def _start_emulator_source(self) -> bool:
        """Try to use the Android emulator's screen as a camera source via ADB."""
        try:
            from tools.emulator_camera import EmulatorCameraSource, is_emulator_available
        except ImportError:
            print("[Camera] emulator_camera module not found.")
            return False

        if not is_emulator_available():
            print("[Camera] No Android emulator detected via ADB.")
            return False

        self._emulator_src = EmulatorCameraSource()
        if not self._emulator_src.start():
            print("[Camera] Failed to start emulator camera capture.")
            return False

        # Bridge: poll the emulator source in our own read path
        self._using_emulator = True
        self._running = True
        self.width = self._emulator_src.width
        self.height = self._emulator_src.height
        self._source = self._emulator_src.source

        # Start a thread that copies frames from the emulator source
        self._thread = threading.Thread(
            target=self._emulator_bridge_loop, daemon=True, name="EmulatorBridge"
        )
        self._thread.start()

        print(f"[Camera] Using Android emulator camera ({self._source})")
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
            # Try Windows backends in a robust order for webcams and virtual cameras.
            for backend in (cv2.CAP_DSHOW, cv2.CAP_MSMF, cv2.CAP_ANY):
                cap = cv2.VideoCapture(self._source, backend)
                if cap.isOpened():
                    return cap
                cap.release()
            return cv2.VideoCapture(self._source)

        # For file paths / stream URLs, try the default backend first and then FFMPEG.
        cap = cv2.VideoCapture(self._source)
        if not cap.isOpened():
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
        if self._emulator_src:
            self._emulator_src.stop()
            self._emulator_src = None
            self._using_emulator = False

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

    def _emulator_bridge_loop(self):
        """Bridge loop: copies frames from EmulatorCameraSource into self._frame."""
        while self._running and self._emulator_src:
            frame = self._emulator_src.read()
            if frame is not None:
                with self._lock:
                    self._frame = frame
            time.sleep(0.05)  # ~20 FPS polling

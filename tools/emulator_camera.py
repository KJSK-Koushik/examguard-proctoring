"""
emulator_camera.py — ADB-based camera source for Android emulator.

Captures frames from the running Android emulator via `adb exec-out screencap`
and provides them as an OpenCV-compatible video source. This lets the ExamGuard
proctoring system use the emulator's camera/screen as its input.

Usage from examguard-proctoring root:
    python main.py --camera emulator

This module is also importable:
    from tools.emulator_camera import EmulatorCameraSource
    src = EmulatorCameraSource()
    src.start()
    frame = src.read()
"""

from __future__ import annotations

import subprocess
import threading
import time

import cv2
import numpy as np


class EmulatorCameraSource:
    """
    Captures frames from the Android emulator via ADB screencap.
    Implements the same read()/start()/stop() interface as CameraManager
    so it can be used as a drop-in replacement.
    """

    def __init__(self, serial: str = "emulator-5554", target_fps: float = 10):
        self._serial = serial
        self._target_fps = target_fps
        self._frame = None
        self._lock = threading.Lock()
        self._running = False
        self._thread = None
        self.width = 640
        self.height = 480

    def start(self) -> bool:
        """Start the ADB capture loop. Returns True if emulator is reachable."""
        # Verify emulator is connected
        try:
            result = subprocess.run(
                ["adb", "-s", self._serial, "shell", "echo", "ok"],
                capture_output=True, text=True, timeout=5,
            )
            if result.returncode != 0 or "ok" not in result.stdout:
                print(f"[EmulatorCamera] Emulator {self._serial} not reachable.")
                return False
        except (subprocess.TimeoutExpired, FileNotFoundError):
            print("[EmulatorCamera] ADB not found or emulator not responding.")
            return False

        self._running = True
        self._thread = threading.Thread(
            target=self._capture_loop, daemon=True, name="EmulatorCameraThread"
        )
        self._thread.start()

        # Wait for first frame
        deadline = time.time() + 5.0
        while time.time() < deadline:
            if self.read() is not None:
                print(f"[EmulatorCamera] Capturing from {self._serial}")
                return True
            time.sleep(0.1)

        print("[EmulatorCamera] Timeout waiting for first frame.")
        self.stop()
        return False

    def read(self):
        """Return the latest frame as a BGR numpy array, or None."""
        with self._lock:
            if self._frame is None:
                return None
            return self._frame.copy()

    def stop(self):
        """Stop the capture loop."""
        self._running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=3.0)

    @property
    def is_running(self) -> bool:
        return self._running

    @property
    def source(self):
        return f"emulator:{self._serial}"

    def _capture_loop(self):
        interval = 1.0 / self._target_fps
        while self._running:
            t0 = time.time()
            try:
                # Grab raw RGBA screencap from emulator
                proc = subprocess.run(
                    ["adb", "-s", self._serial, "exec-out", "screencap", "-p"],
                    capture_output=True, timeout=5,
                )
                if proc.returncode != 0 or len(proc.stdout) < 100:
                    time.sleep(0.1)
                    continue

                # Decode PNG bytes → numpy array
                png_data = np.frombuffer(proc.stdout, dtype=np.uint8)
                frame = cv2.imdecode(png_data, cv2.IMREAD_COLOR)
                if frame is None:
                    time.sleep(0.1)
                    continue

                # Smart crop to preserve aspect ratio
                h, w = frame.shape[:2]
                target_aspect = self.width / self.height
                current_aspect = w / h

                if current_aspect > target_aspect:
                    # Too wide, crop width
                    new_w = int(h * target_aspect)
                    x0 = (w - new_w) // 2
                    frame = frame[:, x0:x0+new_w]
                else:
                    # Too tall, crop height
                    new_h = int(w / target_aspect)
                    y0 = (h - new_h) // 2
                    frame = frame[y0:y0+new_h, :]

                # Resize to target dimensions
                frame = cv2.resize(frame, (self.width, self.height))

                with self._lock:
                    self._frame = frame

            except (subprocess.TimeoutExpired, Exception) as e:
                # Skip bad frames
                time.sleep(0.1)
                continue

            # Throttle to target FPS
            elapsed = time.time() - t0
            if elapsed < interval:
                time.sleep(interval - elapsed)


def is_emulator_available(serial: str = "emulator-5554") -> bool:
    """Quick check whether the given emulator serial is connected via ADB."""
    try:
        result = subprocess.run(
            ["adb", "devices"], capture_output=True, text=True, timeout=5
        )
        return serial in result.stdout
    except Exception:
        return False


if __name__ == "__main__":
    # Quick test: capture 30 frames and show in an OpenCV window
    src = EmulatorCameraSource()
    if not src.start():
        print("Failed to connect to emulator.")
        raise SystemExit(1)

    print("Capturing from emulator. Press 'q' to quit.")
    for _ in range(300):
        frame = src.read()
        if frame is not None:
            cv2.imshow("Emulator Camera", frame)
        if cv2.waitKey(100) & 0xFF == ord("q"):
            break

    src.stop()
    cv2.destroyAllWindows()

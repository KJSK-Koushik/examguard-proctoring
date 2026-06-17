"""
Probe local OpenCV camera sources.

Usage:
    python tools/probe_cameras.py
    python tools/probe_cameras.py --max-index 8
"""

from __future__ import annotations

import argparse
import time
import cv2


def probe_index(index: int, warmup_secs: float = 0.25) -> tuple[bool, str]:
    cap = cv2.VideoCapture(index, cv2.CAP_DSHOW)
    backend = "CAP_DSHOW"
    if not cap.isOpened():
        cap.release()
        cap = cv2.VideoCapture(index)
        backend = "default"

    if not cap.isOpened():
        return False, "not available"

    time.sleep(warmup_secs)
    ok, frame = cap.read()
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS)
    cap.release()

    if not ok or frame is None:
        return False, f"opened via {backend}, but no frame returned"

    return True, f"{width}x{height} @ {fps:.1f} fps via {backend}"


def main() -> int:
    parser = argparse.ArgumentParser(description="List camera indexes visible to OpenCV.")
    parser.add_argument("--max-index", type=int, default=6)
    args = parser.parse_args()

    print("ExamGuard camera probe")
    print("----------------------")
    found = False
    for index in range(args.max_index + 1):
        ok, detail = probe_index(index)
        marker = "OK " if ok else "-- "
        print(f"{marker} index {index}: {detail}")
        found = found or ok

    if found:
        print("\nRun ExamGuard with: python main.py --camera <index>")
    else:
        print("\nNo camera source opened. Check Windows camera privacy settings and emulator camera setup.")
    return 0 if found else 1


if __name__ == "__main__":
    raise SystemExit(main())

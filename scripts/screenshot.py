"""Takes a README screenshot from the connected emulator or phone.

Saves docs/screenshots/<name>.png. The notification icons left of the status bar (the emulator shows some
that the system demo mode cannot hide) are covered with the color of the status bar, so every screenshot
shows only the clock, the Wi-Fi and the battery. See docs/screenshots/README.md for the whole procedure.

Usage: python scripts/screenshot.py <name>
Needs adb (ANDROID_HOME, ANDROID_SDK_ROOT, LOCALAPPDATA/Android/Sdk or the PATH) and Pillow.
"""

import io
import os
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "screenshots"

# Notification icons area, as a fraction of the screen: right of the clock, left half of the status bar.
ICONS_LEFT = 0.135
ICONS_RIGHT = 0.55
STATUS_BAR_HEIGHT = 0.03


def adb() -> str:
    for base in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if base and (Path(base) / "platform-tools").exists():
            return str(Path(base) / "platform-tools" / "adb")
    local = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
    if local.exists():
        return str(local)
    found = shutil.which("adb")
    if found:
        return found
    sys.exit("adb not found: set ANDROID_HOME or add platform-tools to the PATH")


def main(name: str) -> None:
    png = subprocess.run([adb(), "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout
    image = Image.open(io.BytesIO(png)).convert("RGB")
    width, height = image.size
    bar = int(height * STATUS_BAR_HEIGHT)
    # The color of the status bar, taken in its empty middle.
    background = image.getpixel((int(width * 0.6), bar // 2))
    ImageDraw.Draw(image).rectangle(
        (int(width * ICONS_LEFT), 0, int(width * ICONS_RIGHT), bar),
        fill=background,
    )
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{name}.png"
    image.save(target, optimize=True)
    print(f"{target.relative_to(ROOT)} ({width}x{height})")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])

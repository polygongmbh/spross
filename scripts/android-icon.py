#!/usr/bin/env python3
"""Derive Android's launcher foreground from the iOS app icon.

    scripts/android-icon.py

The painted icon (`App/Resources/.../icon-1024.png`) is the one original; Android's
adaptive icon is a framing of it, not a second drawing. Two facts make the framing
lossless: the artwork's plate is one flat color, and `ic_launcher_background` is set to
that same color — so whatever a launcher's mask cuts from the foreground's square, the
background layer replaces with the identical color, and the seam cannot be seen.

The art is inset so the FURTHEST painted pixel lands inside the adaptive icon's safe
circle (33 of 108dp): the leaf tips survive a circular mask, and the artwork still fills
its mask about as fully as it fills the square on iOS.
"""
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "App/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
RES = ROOT / "android/src/main/res"

# The safe circle, as a share of the 108dp canvas — everything painted stays inside it.
SAFE_RADIUS = 33 / 108
# One foreground per bucket, at 108dp: nothing else in res/ is density-qualified, but a
# launcher asks for the icon at its own density and upscaling a raster shows.
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def plate_color(image: Image.Image) -> tuple[int, int, int]:
    """The flat plate the art is painted on, read off a corner."""
    return image.convert("RGB").getpixel((1, 1))


def painted_radius(image: Image.Image, plate: tuple[int, int, int]) -> float:
    """Distance from center to the furthest pixel that is not the plate, in pixels."""
    rgb = image.convert("RGB")
    width, height = rgb.size
    center_x, center_y = width / 2, height / 2
    pixels = rgb.load()
    furthest = 0.0
    for y in range(height):
        for x in range(width):
            pixel = pixels[x, y]
            if max(abs(pixel[i] - plate[i]) for i in range(3)) <= 10:
                continue
            radius = ((x - center_x) ** 2 + (y - center_y) ** 2) ** 0.5
            furthest = max(furthest, radius)
    return furthest


def main() -> int:
    source = Image.open(SOURCE).convert("RGBA")
    plate = plate_color(source)
    scale = SAFE_RADIUS * source.width / painted_radius(source, plate)
    print(f"plate #{plate[0]:02X}{plate[1]:02X}{plate[2]:02X}, art inset to {scale:.1%}")

    for bucket, canvas in DENSITIES.items():
        art = source.resize((round(canvas * scale),) * 2, Image.LANCZOS)
        frame = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        offset = (canvas - art.width) // 2
        frame.paste(art, (offset, offset))
        out = RES / f"mipmap-{bucket}/ic_launcher_foreground.webp"
        out.parent.mkdir(parents=True, exist_ok=True)
        frame.save(out, "WEBP", quality=92, method=6)
        print(f"  {out.relative_to(ROOT)} — {out.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())

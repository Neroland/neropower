#!/usr/bin/env python3
"""Write PLACEHOLDER fluid sprites for NeroPower's three fluids.

These are stubs, not art: flat near-white 32x32 noise that the fluid model tints with each fluid's
colour, so tank and pipe GUIs never show the missing texture. Every PNG carries a tEXt chunk
``neropower-placeholder: replace with final fluid art``; the final sprites replace these files in place.

Usage: python tools/gen_fluid_stubs.py [--force]
Writes common/src/main/resources/assets/neropower/textures/block/fluid/<id>_still.png and _flow.png.
Existing files are kept unless --force is given.
"""
import os
import random
import struct
import sys
import zlib

FLUIDS = ("steam", "exhaust_steam", "heavy_water")
SIZE = 32
OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources",
                   "assets", "neropower", "textures", "block", "fluid")
MARK = b"neropower-placeholder\x00replace with final fluid art"


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def png(pixels):
    raw = b"".join(b"\x00" + bytes(row) for row in pixels)
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"tEXt", MARK)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def sprite(name, flowing):
    rng = random.Random(name + ("_flow" if flowing else "_still"))  # deterministic
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            band = 12 if flowing and (x + y) % 8 < 2 else 0
            v = 215 + rng.randint(0, 30) - band
            row += [v, v, v, 220]
        rows.append(row)
    return png(rows)


def main():
    force = "--force" in sys.argv
    os.makedirs(OUT, exist_ok=True)
    for name in FLUIDS:
        for flowing in (False, True):
            path = os.path.join(OUT, f"{name}_{'flow' if flowing else 'still'}.png")
            if os.path.exists(path) and not force:
                continue
            with open(path, "wb") as fh:
                fh.write(sprite(name, flowing))
            print("wrote", os.path.relpath(path))


if __name__ == "__main__":
    main()

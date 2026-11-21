#!/usr/bin/env python3
"""Generate NeroPower's texture set (32x32, amber "power" emissives on NeroTech's alloy body).

The painters live in ``tools/painters/``: ``kit.py`` holds the palette and the shared recipes,
and each family has its own module (``existing.py`` is the 0.3.2 art, ``fluids.py`` the three
fluids, the rest arrive with their blocks). This file only drives them.

* Deterministic: every painter seeds its RNG from the texture name, so re-runs are stable.
* ADDITIVE-ONLY: an existing PNG is kept; ``--force`` repaints, and still leaves alone any file
  whose pixels would not change.
* Pillow optional: without it the script exits 0 with a notice (`pip install pillow` to enable).
* Standalone: paths resolve relative to this file (tools/ -> repo root).

Usage:
    python tools/gen_textures.py                    paint what is missing
    python tools/gen_textures.py --force            repaint everything that changed
    python tools/gen_textures.py --family steam     only that family (repeatable)
    python tools/gen_textures.py --out DIR          write to DIR/textures/... instead of the mod
    python tools/gen_textures.py --list             list the families
    python tools/gen_textures.py --verify           repaint and animate everything into a scratch
                                                    folder and compare it with the mod's textures;
                                                    exits 1 if any shipped texture would change

Animated fronts are painted here as their static frame 0; tools/gen_animations.py turns them into
strips. Run it after this script.
"""
import hashlib
import os
import shutil
import sys
import tempfile

try:
    from PIL import Image
except ModuleNotFoundError:
    print("gen_textures: Pillow not installed; skipping texture generation (pip install pillow).")
    sys.exit(0)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import painters  # noqa: E402
from painters import kit  # noqa: E402

FOLDERS = ("block", "item", "particle")


def _args(flag):
    out = []
    for i, a in enumerate(sys.argv):
        if a == flag and i + 1 < len(sys.argv):
            out.append(sys.argv[i + 1])
    return out


def paint(families):
    kit.kit_sprites()
    for name in families:
        painters.load(name).paint()


def _pngs(folder_dir):
    """PNG paths under a textures folder, relative, with forward slashes."""
    out = set()
    for base, _dirs, files in os.walk(folder_dir):
        for f in files:
            if f.endswith(".png"):
                out.add(os.path.relpath(os.path.join(base, f), folder_dir).replace(os.sep, "/"))
    return out


def check_coverage():
    """Flag any PNG no painter owns: every texture must come from a painter, none hand-made."""
    clean = True
    for folder in FOLDERS:
        d = kit.texture_dir(folder)
        if not os.path.isdir(d):
            continue
        orphans = sorted(_pngs(d) - kit.PAINTED.get(folder, set()))
        if orphans:
            clean = False
            print("NOTICE: existing %s textures WITHOUT a painter (left untouched): %s"
                  % (folder, ", ".join(orphans)))
    if clean:
        print("coverage: every existing block/item/particle PNG has a painter.")


def _sha(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def verify():
    """Repaint + animate everything into a scratch folder and compare with the mod's textures.

    PNGs are compared by decoded pixels (a PNG may carry metadata chunks the painter never
    wrote); ``.mcmeta`` files byte for byte. A texture the mod does not have yet is reported as
    new, not as a failure.
    """
    import gen_animations
    real = kit.assets_dir()
    scratch = tempfile.mkdtemp(prefix="neropower_textures_")
    try:
        kit.configure(assets=scratch, force=True, quiet=True)
        paint(painters.available())
        gen_animations.run(quiet=True)
        same = new = 0
        changed = []
        for folder in FOLDERS:
            d = os.path.join(scratch, "textures", folder)
            if not os.path.isdir(d):
                continue
            for base, _dirs, files in os.walk(d):
                for f in sorted(files):
                    made = os.path.join(base, f)
                    rel = os.path.relpath(made, scratch).replace(os.sep, "/")
                    shipped = os.path.join(real, rel)
                    if not os.path.exists(shipped):
                        new += 1
                        print("new      ", rel)
                    elif f.endswith(".png"):
                        with Image.open(made) as img:
                            if kit.same_pixels(img, shipped):
                                same += 1
                            else:
                                changed.append(rel)
                    elif _sha(made) == _sha(shipped):
                        same += 1
                    else:
                        changed.append(rel)
        for rel in changed:
            print("CHANGED  ", rel)
        print("verify: %d identical, %d new, %d changed" % (same, new, len(changed)))
        return 1 if changed else 0
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def main():
    if "--list" in sys.argv:
        for name in painters.available():
            print(name)
        return 0
    out = _args("--out")
    if out:
        kit.configure(assets=out[-1])
    if "--verify" in sys.argv:
        return verify()
    kit.configure(force="--force" in sys.argv)
    families = _args("--family")
    paint(families or painters.available())
    if not families:
        check_coverage()
    return 0


if __name__ == "__main__":
    sys.exit(main())

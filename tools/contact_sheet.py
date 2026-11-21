#!/usr/bin/env python3
"""Build a review contact sheet for one painter family and print its palette audit.

    python tools/contact_sheet.py --family steam --out some/where/steam.png
    python tools/contact_sheet.py --list

Options: ``--assets DIR`` reads textures, models and blockstates from another assets folder
(default: the mod's); ``--nerotech DIR`` points at NeroTech's block textures for the look-alike row
(default: a sibling ``nerotech`` checkout, skipped when absent); ``--strict`` exits 1 when the
audit has findings.

One row per block or item, repeated on three backgrounds (dark, stone grey, sky):

    faces and item icons at 4x | state sprites at 4x | everything again at 1x |
    black-fill silhouettes of the model (front, three-quarter) at 16 px | grayscale states

Animated strips show four evenly spaced frames. The last row holds the family's look-alikes.

Rows come from the family module's ``SHEET`` list; a family without one gets a row per block id
(textures grouped by the blockstate they belong to) and a row per item. A SHEET row is a dict:

    {"id": "condenser",                      label; also the default model (block/<id>)
     "faces": ["condenser_front", ...],      block textures; "item:name" for an item texture
     "states": ["x_idle", "x_working", ...], state sprites, also shown in grayscale
     "item": "item:condenser_icon",          optional icon
     "model": "block/condenser",             optional model for the silhouettes (None = skip)
     "tint": (r, g, b),                      optional multiply, as a tinting model would apply
     "block": True}                          the row is one block: exactly one cyan tell expected

The tool knows nothing about where sheets are kept: the output path is always an argument.
"""
import json
import math
import os
import shutil
import sys
import tempfile

try:
    from PIL import Image, ImageDraw
except ModuleNotFoundError:
    print("contact_sheet: Pillow not installed (pip install pillow).")
    sys.exit(0)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import painters  # noqa: E402
from painters import kit  # noqa: E402

BACKGROUNDS = [(20, 24, 28), (125, 125, 125), (191, 216, 255)]   # #14181C, stone, sky
SCALE = 4
T = 32
PAD = 6
LABEL_W = 190
CHIP = (230, 230, 230)


def _arg(flag, default=None):
    return sys.argv[sys.argv.index(flag) + 1] if flag in sys.argv else default


# ---------------- textures ----------------

class Source:
    def __init__(self, assets, scratch):
        self.assets = assets
        self.scratch = scratch
        self.missing = []

    def path(self, name):
        folder, _, leaf = name.rpartition(":")
        rel = os.path.join("textures", folder or "block", leaf + ".png")
        for root in (self.assets, self.scratch):
            p = os.path.join(root, rel)
            if os.path.exists(p):
                if root is self.scratch and name not in self.missing:
                    self.missing.append(name)
                return p
        return None

    def frames(self, name, tint=None):
        """Up to four evenly spaced frames of a texture (one for a still), each as RGBA."""
        p = self.path(name)
        if p is None:
            ph = Image.new("RGBA", (T, T), (255, 0, 255, 255))
            ImageDraw.Draw(ph).line((0, 0, T - 1, T - 1), fill=(0, 0, 0, 255))
            return [ph]
        with Image.open(p) as img:
            im = img.convert("RGBA")
        w, h = im.size
        count = h // w if h > w and h % w == 0 else 1
        picks = [0] if count == 1 else sorted({(i * count) // 4 for i in range(4)})
        out = []
        for i in picks:
            fr = im.crop((0, i * w, w, i * w + w)) if count > 1 else im
            if tint:
                px = fr.load()
                for y in range(fr.height):
                    for x in range(fr.width):
                        r, g, b, a = px[x, y]
                        px[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
            out.append(fr)
        return out


# ---------------- model silhouettes ----------------

def _load_model(assets, ref, depth=0):
    """Elements of a model, following ``parent`` inside this mod. None = a plain full cube."""
    if depth > 8 or ref is None:
        return None
    ns, _, rel = ref.partition(":")
    if not rel:
        ns, rel = "minecraft", ns
    if ns != "neropower":
        return None
    path = os.path.join(assets, "models", rel + ".json")
    try:
        with open(path, encoding="utf-8") as fh:
            model = json.load(fh)
    except (OSError, ValueError):
        return None
    if model.get("elements"):
        return model["elements"]
    return _load_model(assets, model.get("parent"), depth + 1)


def _corners(el):
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    pts = [[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    rot = el.get("rotation")
    if rot and rot.get("angle"):
        ox, oy, oz = rot.get("origin", [8, 8, 8])
        a = math.radians(rot["angle"])
        c, s = math.cos(a), math.sin(a)
        for p in pts:
            x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
            if rot["axis"] == "x":
                y, z = y * c - z * s, y * s + z * c
            elif rot["axis"] == "y":
                x, z = x * c + z * s, -x * s + z * c
            else:
                x, y = x * c - y * s, x * s + y * c
            p[0], p[1], p[2] = x + ox, y + oy, z + oz
    return pts


_FACES = ((0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5))


def silhouette(elements, three_quarter):
    """16x16 black-on-transparent fill of the model: front view, or a three-quarter view."""
    big = 64
    img = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(img)
    if elements is None:
        elements = [{"from": [0, 0, 0], "to": [16, 16, 16]}]
    yaw, pitch = math.radians(35.0), math.radians(28.0)
    for el in elements:
        pts = []
        for x, y, z in _corners(el):
            x, y, z = x - 8.0, y - 8.0, z - 8.0
            if three_quarter:
                # Viewer stands north-west-up of a north-facing model; scaled to fit the tile.
                xr = -x * math.cos(yaw) + z * math.sin(yaw)
                zr = x * math.sin(yaw) + z * math.cos(yaw)
                yr = y * math.cos(pitch) + zr * math.sin(pitch)
                sx, sy = xr * 0.66, yr * 0.66
            else:
                sx, sy = -x, y           # looking at the north face: east is on the left
            pts.append(((sx + 8.0) * big / 16.0, (8.0 - sy) * big / 16.0))
        for face in _FACES:
            draw.polygon([pts[i] for i in face], fill=255)
    small = img.resize((16, 16), Image.BOX)
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px, sp = out.load(), small.load()
    for y in range(16):
        for x in range(16):
            if sp[x, y] >= 128:
                px[x, y] = (0, 0, 0, 255)
    return out


# ---------------- rows ----------------

def _blockstate_ids(assets):
    d = os.path.join(assets, "blockstates")
    return sorted((f[:-5] for f in os.listdir(d) if f.endswith(".json")), key=len, reverse=True) \
        if os.path.isdir(d) else []


def auto_rows(assets, painted):
    """A row per block id (textures grouped under the longest matching blockstate id), a row for
    textures that belong to no block, and a row per item."""
    ids = _blockstate_ids(assets)
    groups, loose = {}, []
    for name in sorted(n[:-4] for n in painted.get("block", ())):
        owner = next((i for i in ids if name == i or name.startswith(i + "_")), None)
        if owner:
            groups.setdefault(owner, []).append(name)
        else:
            loose.append(name)
    rows = [{"id": i, "faces": groups[i], "block": True} for i in sorted(groups)]
    for i in range(0, len(loose), 8):
        rows.append({"id": "(no block)", "faces": loose[i:i + 8], "model": None})
    items = sorted(n[:-4] for n in painted.get("item", ()))
    for i in range(0, len(items), 8):
        rows.append({"id": "items", "faces": ["item:" + n for n in items[i:i + 8]], "model": None})
    return rows


def _gray(img):
    g = img.convert("LA").convert("RGBA")
    g.putalpha(img.getchannel("A"))
    return g


def _up(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def render_row(row, src, assets):
    """The row's cells as a list of (kind, image) plus its audit facts. Background-free."""
    tint = row.get("tint")
    cells, audited = [], []
    names = list(row.get("faces", ()))
    if row.get("item"):
        names.append(row["item"])
    for name in names:
        for fr in src.frames(name, tint):
            cells.append(("face", fr))
        audited.append((name, src.frames(name, tint)[0]))
    states = []
    for name in row.get("states", ()):
        for fr in src.frames(name, tint):
            states.append(fr)
        audited.append((name, src.frames(name, tint)[0]))
    sils = []
    model = row.get("model", "block/" + row["id"]) if "model" in row or row.get("block") else None
    if model:
        elements = _load_model(assets, "neropower:" + model)
        if elements is not None or os.path.exists(os.path.join(assets, "models", model + ".json")):
            sils = [silhouette(elements, False), silhouette(elements, True)]
    return cells, states, sils, audited


def row_size(cells, states, sils):
    main = cells + [("state", s) for s in states]
    w = LABEL_W + sum(c[1].width * SCALE + PAD for c in main) + PAD * 3
    w += sum(c[1].width + 2 for c in main) + PAD * 3
    w += len(sils) * (16 * SCALE + PAD) + PAD * 3
    gray = states if states else [c[1] for c in cells]
    w += sum(g.width * 2 + 2 for g in gray) + PAD
    return w, T * SCALE + PAD * 2


def draw_row(sheet, y, bg, label, cells, states, sils, show_label):
    draw = ImageDraw.Draw(sheet)
    draw.rectangle((0, y, sheet.width, y + T * SCALE + PAD * 2 - 1), fill=bg)
    ink = (235, 235, 235) if sum(bg) < 300 else (16, 16, 16)
    if show_label:
        for i, text in enumerate(label):
            draw.text((PAD, y + PAD + i * 12), text[:30], fill=ink)
    x = LABEL_W
    top = y + PAD
    main = [c[1] for c in cells]
    for img in main:
        sheet.paste(_up(img, SCALE), (x, top), _up(img, SCALE))
        x += img.width * SCALE + PAD
    if states:
        draw.line((x, top, x, top + T * SCALE), fill=ink)
        x += PAD
    for img in states:
        sheet.paste(_up(img, SCALE), (x, top), _up(img, SCALE))
        x += img.width * SCALE + PAD
    x += PAD * 2
    for img in main + states:                      # true size
        sheet.paste(img, (x, top), img)
        x += img.width + 2
    x += PAD * 3
    for img in sils:                               # black fill on a light chip, any background
        draw.rectangle((x, top, x + 16 * SCALE - 1, top + 16 * SCALE - 1), fill=CHIP)
        sheet.paste(_up(img, SCALE), (x, top), _up(img, SCALE))
        x += 16 * SCALE + PAD
    x += PAD * 2
    for img in (states if states else main):       # grayscale
        g = _up(_gray(img), 2)
        sheet.paste(g, (x, top), g)
        x += img.width * 2 + 2


def audit_row(row, audited, flags):
    """Print the row's palette facts; return the number of findings."""
    findings = []
    tells = 0
    worst_violet = 0.0
    for name, img in audited:
        a = kit.audit(img, allow_lime=flags.get("allow_lime", False),
                      allow_charge=flags.get("allow_charge", False))
        tells += a["cyan_tell"]
        worst_violet = max(worst_violet, 100.0 * a["violet"] / max(1, a["opaque"]))
        findings.extend("%s: %s" % (name, f) for f in a["findings"])
        if a["cyan_tell"] > 2:
            findings.append("%s: %d cyan tell px on one face (one tick = 2 px)" % (name, a["cyan_tell"]))
    if row.get("block") and tells == 0:
        findings.append("no cyan tell on any face of the block")
    print("  %-34s violet max %4.1f%%  cyan tell %d px  %s"
          % (row["id"], worst_violet, tells, "ok" if not findings else "%d finding(s)" % len(findings)))
    for f in findings:
        print("      - " + f)
    return len(findings)


def main():
    if "--list" in sys.argv:
        for name in painters.available():
            print(name)
        return 0
    family, out = _arg("--family"), _arg("--out")
    if not family or not out:
        print(__doc__)
        return 2
    assets = os.path.abspath(_arg("--assets", kit.DEFAULT_ASSETS))
    nerotech = _arg("--nerotech", os.path.join(kit.ROOT, os.pardir, "nerotech", "common", "src", "main",
                                               "resources", "assets", "nerotech"))
    module = painters.load(family)
    scratch = tempfile.mkdtemp(prefix="neropower_sheet_")
    try:
        # Paint into a scratch folder to learn which textures the family owns (and to have a
        # stand-in for any that are not generated into the assets folder yet).
        kit.configure(assets=scratch, force=True, quiet=True)
        before = {k: set(v) for k, v in kit.PAINTED.items()}
        module.paint()
        painted = {k: kit.PAINTED[k] - before.get(k, set()) for k in kit.PAINTED}
        import gen_animations
        gen_animations.run([family], quiet=True)
        src = Source(assets, scratch)
        rows = list(getattr(module, "SHEET", None) or auto_rows(assets, painted))
        flags = getattr(module, "AUDIT", {})

        rendered = [(row, ) + render_row(row, src, assets) for row in rows]
        look = [("neropower", n, Source(assets, scratch)) for n in getattr(module, "LOOKALIKES", ())]
        nt_src = Source(nerotech, nerotech)
        look += [("nerotech", n, nt_src) for n in getattr(module, "NEROTECH_LOOKALIKES", ())
                 if os.path.isdir(nerotech)]
        width = max([row_size(c, s, sl)[0] for _r, c, s, sl, _a in rendered] + [640])
        width = max(width, LABEL_W + len(look) * (T * SCALE + PAD) + PAD)
        band = T * SCALE + PAD * 2
        height = 22 + len(rendered) * band * len(BACKGROUNDS) + (band + 16 if look else 0)
        sheet = Image.new("RGB", (width, height), (40, 40, 40))
        draw = ImageDraw.Draw(sheet)
        draw.text((PAD, 5), "NeroPower contact sheet: %s   (4x | 1x | silhouettes | grayscale; "
                  "dark / stone / sky)" % family, fill=(240, 240, 240))
        y = 22
        for row, cells, states, sils, _aud in rendered:
            label = [row["id"]] + [n for n in list(row.get("faces", ())) + list(row.get("states", ()))][:8]
            for i, bg in enumerate(BACKGROUNDS):
                draw_row(sheet, y, bg, label, cells, states, sils, i == 0)
                y += band
        if look:
            draw.rectangle((0, y, width, y + band + 16), fill=BACKGROUNDS[1])
            draw.text((PAD, y + PAD), "look-alikes", fill=(16, 16, 16))
            x = LABEL_W
            for origin, name, source in look:
                img = _up(source.frames(name)[0], SCALE)
                sheet.paste(img, (x, y + PAD), img)
                draw.text((x, y + PAD + T * SCALE + 1), ("%s:%s" % (origin[:4], name))[:21], fill=(16, 16, 16))
                x += T * SCALE + PAD
        os.makedirs(os.path.dirname(os.path.abspath(out)) or ".", exist_ok=True)
        sheet.save(out)
        print("contact sheet: %s (%dx%d, %d rows)" % (out, width, height, len(rendered)))
        if src.missing:
            print("  not in the assets folder yet (shown from a scratch paint): " + ", ".join(src.missing))
        print("palette audit:")
        total = sum(audit_row(row, aud, flags) for row, _c, _s, _sl, aud in rendered)
        print("palette audit: %d finding(s)" % total)
        return 1 if total and "--strict" in sys.argv else 0
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())

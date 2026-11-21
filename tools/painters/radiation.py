"""Radiation: Shielding Block, Shielding Glass, Geiger Counter, Dosimeter Badge, Decontaminant and
the contamination mote particle.

The two shielding blocks are plain full cubes with one texture each (no block entity, no lamp, no
emissive). The glass is the only translucent texture in the mod: its pane is painted with real
alpha and its model asks for the translucent layer.

The Geiger Counter has three item-model frames (the dial needle left, upright, right) and the
Dosimeter Badge five (0 to 4 strip cells turned). Rad lime is used here and only for contamination
reads: the top dial band, turned badge cells and the motes.

Models, blockstates, item definitions and the particle definition are written by this module too
(``write_models``).
"""
import json
import math
import os

from PIL import Image

from .kit import *  # noqa: F401,F403
from .kit import _mix

NS = "neropower"
INK = (10, 13, 16, 255)                                   # item outlines
PALE = (214, 236, 255, 255)                               # needle, read-out white
SEAM = _mix(SHIELD[2], A_DARK, 0.55)                      # the joint between two slabs
GLASS_RIM = (150, 78, 14, 150)                            # the pane's thick edge, seen through
GLASS_BODY = (222, 150, 60, 72)                           # warm pane
GLASS_STREAK = (255, 226, 160, 150)                       # the highlight
FOAM = [STEAM_W, STEAM_X, (255, 255, 255, 255)]
RINSE = [_mix(STEAM_W, HW_LIGHT, 0.30), _mix(STEAM_W, HW_LIGHT, 0.55), (244, 249, 252, 255)]
# Dial bands low to high, as the HUD names them (Clear, Trace, Raised, High, Severe).
DIAL_BANDS = [_mix(A_LIGHT, A_DARK, 0.6), A_LIGHT, P_HOT, R_ALARM, RAD_LIME]
GEIGER_FRAMES = 3
BADGE_STEPS = 5


# ---------------- blocks ----------------

def gen_shielding_block():
    """Three stacked slabs, pale to dark, each with a lit upper lip, a shaded lower lip and
    chamfered ends. No rivets, no emissive."""
    name = "shielding_block"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    bands = ((0, 10, SHIELD[1]), (11, 20, SHIELD[0]), (21, 31, SHIELD[2]))
    for (y0, y1, base) in bands:
        light = _mix(base, (255, 255, 255, 255), 0.28)
        shade = _mix(base, A_DARK, 0.30)
        body = [base, base, _mix(base, light, 0.30), _mix(base, shade, 0.30)]
        for y in range(y0, y1 + 1):
            for x in range(S):
                px[x, y] = rng.choice(body)
        for x in range(S):
            px[x, y0] = light
            px[x, y0 + 1] = _mix(light, base, 0.55)
            px[x, y1 - 1] = _mix(shade, base, 0.45)
            px[x, y1] = SEAM
        for y in range(y0 + 1, y1):                       # slab ends
            px[0, y] = _mix(light, base, 0.4)
            px[S - 1, y] = shade
        for (cx, cy) in ((0, y0), (1, y0), (0, y0 + 1), (S - 1, y0), (S - 2, y0), (S - 1, y0 + 1),
                         (0, y1 - 1), (S - 1, y1 - 1), (1, y1), (S - 2, y1)):
            px[cx, cy] = SEAM                             # chamfers
        for x in range(4, S - 6, 9):                      # faint casting marks along the slab
            px[x + (y0 % 5), y0 + 4] = shade
            px[x + (y0 % 5) + 1, y0 + 4] = shade
    tell(px, 15, 20)                                      # the one cyan tick, in the middle joint
    save(img, "block", name)


def gen_shielding_glass():
    """A thick warm pane in a lead-grey frame. The pane is translucent; the frame is solid."""
    name = "shielding_glass"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = min(x, y, S - 1 - x, S - 1 - y)
            if d <= 2:                                    # frame
                px[x, y] = rng.choice([SHIELD[0], SHIELD[0], _mix(SHIELD[0], SHIELD[2], 0.35)])
            elif d <= 4:                                  # the pane's edge, seen through its depth
                px[x, y] = GLASS_RIM
            else:
                px[x, y] = GLASS_BODY
    for i in range(S):
        px[i, 0] = SHIELD[1]
        px[0, i] = SHIELD[1]
        px[i, S - 1] = SEAM
        px[S - 1, i] = SEAM
    for i in range(2, S - 2):                             # inner lip of the frame
        px[i, 2] = SHIELD[2]
        px[2, i] = SHIELD[2]
        px[i, S - 3] = SHIELD[1]
        px[S - 3, i] = SHIELD[1]
    for (cx, cy, sx, sy) in ((3, 3, 1, 1), (S - 4, 3, -1, 1), (3, S - 4, 1, -1), (S - 4, S - 4, -1, -1)):
        px[cx, cy] = SHIELD[0]                            # chamfered inner corners
        px[cx + sx, cy] = SHIELD[2]
        px[cx, cy + sy] = SHIELD[2]
    for (cx, cy) in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        px[cx, cy] = SHIELD[2]                            # one flush pin per corner (not a rivet pair)
    for k in range(9):                                    # two parallel highlight streaks
        px[8 + k, 16 - k] = GLASS_STREAK
        if k < 6:
            px[12 + k, 19 - k] = (GLASS_STREAK[0], GLASS_STREAK[1], GLASS_STREAK[2], 105)
    for k in range(5):
        px[20 + k, 25 - k] = (GLASS_STREAK[0], GLASS_STREAK[1], GLASS_STREAK[2], 90)
    tell(px, 15, S - 2)                                   # the one cyan tick, on the sill
    save(img, "block", name)


# ---------------- items ----------------

def _box(px, rng, x0, y0, x1, y1, pal=None):
    pal = pal or ALLOY
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = rng.choice(pal)
    for x in range(x0, x1 + 1):
        px[x, y0] = A_LIGHT
        px[x, y1] = A_DARK
    for y in range(y0, y1 + 1):
        px[x0, y] = _mix(A_LIGHT, ALLOY[0], 0.4)
        px[x1, y] = A_DARK
    frame(px, x0 - 1, y0 - 1, x1 + 1, y1 + 1, INK)


def _geiger(frame_no):
    """Handheld box with a probe tube on top, a small arc dial and a pistol grip. The needle sits
    left, upright or right by frame; the lamp beside the dial is dark, amber or lime."""
    name = "geiger_counter_%d" % frame_no
    rng = rng_for("geiger_counter")                       # one body for all three frames
    img = new_img()
    px = img.load()
    # pistol grip, leaning back from under the box
    for i, y in enumerate(range(22, 31)):
        x0 = 13 - (i + 1) // 2
        for x in range(x0, x0 + 6):
            px[x, y] = rng.choice(GRAPHITE[:2])
        px[x0, y] = GRAPHITE[2]
        px[x0 + 5, y] = INK
        px[x0 - 1, y] = INK
        px[x0 + 6, y] = INK
        if i % 2 == 1 and i < 8:
            px[x0 + 2, y] = GRAPHITE[2]                   # grip ribs
            px[x0 + 3, y] = GRAPHITE[2]
    for x in range(8, 16):
        px[x, 31] = INK
    px[20, 23] = INK                                      # trigger guard
    px[20, 24] = INK
    px[19, 25] = INK
    px[18, 25] = INK
    px[17, 25] = INK
    px[18, 23] = A_LIGHT                                  # trigger
    # box
    _box(px, rng, 5, 11, 27, 21)
    # probe tube and its two standoffs
    for sx in (9, 22):
        rect(px, sx, 8, sx + 1, 10, _mix(A_DARK, ALLOY[0], 0.5))
        px[sx - 1, 9] = INK
        px[sx + 2, 9] = INK
    for x in range(4, 29):
        px[x, 3] = INK
        px[x, 4] = SILVER[2]
        px[x, 5] = SILVER[0]
        px[x, 6] = SILVER[0]
        px[x, 7] = SILVER[1]
        px[x, 8] = INK
    px[3, 4] = px[3, 5] = px[3, 6] = px[3, 7] = INK
    px[29, 4] = px[29, 5] = px[29, 6] = px[29, 7] = INK
    for x in range(5, 11):                                # the tube's mesh window
        for y in range(5, 7):
            px[x, y] = SOCKET if (x + y) % 2 else SILVER[1]
    rect(px, 26, 4, 28, 7, CINDER[0])                     # end cap
    px[26, 4] = px[27, 4] = px[28, 4] = CINDER[2]
    px[26, 7] = px[27, 7] = px[28, 7] = CINDER[1]
    # dial window
    rect(px, 7, 13, 19, 20, SOCKET)
    frame(px, 7, 13, 19, 20, SOCKET_SHADOW)
    for x in range(8, 19):
        px[x, 20] = _mix(A_LIGHT, A_DARK, 0.55)
    cx, cy = 13.0, 19.5
    for i, col in enumerate(DIAL_BANDS):                  # five bands on a half ring
        for sub in (-11.0, 0.0, 11.0):
            ang = math.radians(162.0 - 36.0 * i + sub)
            x = int(round(cx + 5.0 * math.cos(ang)))
            y = int(round(cy - 5.0 * math.sin(ang)))
            px[x, y] = col
    ang = math.radians((150.0, 90.0, 30.0)[frame_no])
    line(px, 13, 19, int(round(cx + 3.6 * math.cos(ang))), int(round(cy - 3.6 * math.sin(ang))), PALE)
    px[13, 19] = A_LIGHT                                  # hub
    # lamp, knob and speaker slots on the right of the box
    lamp = (P_EMBER, P_HOT, RAD_LIME)[frame_no]
    rect(px, 22, 13, 25, 16, SOCKET_SHADOW)
    rect(px, 23, 14, 24, 15, lamp)
    if frame_no == 2:
        px[23, 14] = (232, 255, 170, 255)
    for y in (18, 20):
        for x in range(22, 26):
            px[x, y] = A_DARK
    save(img, "item", name)


def gen_geiger_counter():
    for f in range(GEIGER_FRAMES):
        _geiger(f)


def _badge(step):
    """Clip-on badge: a spring clip, a pale card and a strip window of four cells. ``step`` cells
    have turned: a turned cell is lime with a dark foot, an unturned one plain white, so the count
    reads without colour."""
    name = "dosimeter_badge_%d" % step
    rng = rng_for("dosimeter_badge")
    img = new_img()
    px = img.load()
    # clip
    rect(px, 12, 2, 19, 8, SILVER[0])
    for y in range(2, 9):
        px[12, y] = SILVER[2]
        px[19, y] = SILVER[1]
    for x in range(12, 20):
        px[x, 2] = SILVER[2]
    frame(px, 11, 1, 20, 9, INK)
    frame(px, 14, 4, 17, 6, INK)                          # the clip's opening
    rect(px, 15, 5, 16, 5, CLEAR)
    # card
    card = [SHIELD[1], SHIELD[1], _mix(SHIELD[1], STEAM_W, 0.5)]
    for y in range(9, 30):
        for x in range(6, 26):
            px[x, y] = rng.choice(card)
    for y in range(9, 30):
        px[6, y] = STEAM_W
        px[25, y] = SHIELD[2]
    for x in range(6, 26):
        px[x, 9] = STEAM_W
        px[x, 29] = SHIELD[2]
    frame(px, 5, 8, 26, 30, INK)
    for (cx, cy) in ((5, 8), (26, 8), (5, 30), (26, 30)):
        px[cx, cy] = CLEAR                                # rounded corners
    for (cx, cy) in ((6, 9), (25, 9), (6, 29), (25, 29)):
        px[cx, cy] = INK
    for x in range(12, 20):
        px[x, 8] = INK
    rect(px, 8, 11, 23, 13, ALLOY[2])                     # header bar
    for x in range(8, 24):
        px[x, 13] = A_DARK
    px[9, 12] = px[10, 12] = A_LIGHT
    px[12, 12] = px[13, 12] = px[14, 12] = _mix(A_LIGHT, ALLOY[2], 0.5)
    # strip window: four cells of 3 x 7
    rect(px, 8, 16, 24, 24, SOCKET)
    frame(px, 8, 16, 24, 24, SOCKET_SHADOW)
    for i, x0 in enumerate((9, 13, 17, 21)):
        turned = i < step
        for y in range(17, 24):
            for x in range(x0, x0 + 3):
                if turned:
                    px[x, y] = RAD_DEEP if y >= 22 else RAD_LIME
                else:
                    px[x, y] = STEAM_W if y < 23 else STEAM_X
        if turned:
            px[x0 + 1, 19] = RAD_DEEP                     # the turned cell's centre mark
    # two print lines under the window
    for x in range(8, 20):
        px[x, 26] = _mix(SHIELD[2], A_DARK, 0.3)
    for x in range(8, 15):
        px[x, 28] = _mix(SHIELD[2], A_DARK, 0.3)
    save(img, "item", name)


def gen_dosimeter_badge():
    for s in range(BADGE_STEPS):
        _badge(s)


def gen_decontaminant():
    """Squat flask with a foam cap and pale blue-white contents."""
    name = "decontaminant"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    spans = {13: (11, 20), 14: (9, 22), 15: (7, 24), 28: (7, 24), 29: (9, 22)}
    for y in range(13, 30):
        x0, x1 = spans.get(y, (6, 25))
        for x in range(x0, x1 + 1):
            if y <= 16:                                   # empty shoulder: clear glass
                px[x, y] = (214, 232, 244, 120)
            else:
                px[x, y] = rng.choice(RINSE[:2]) if (x > x0 + 1) else RINSE[1]
        px[x0, y] = INK
        px[x1, y] = INK
        if y > 16:
            px[x1 - 1, y] = _mix(RINSE[1], HW_BLUE, 0.35)  # shaded side
    for x in range(11, 21):
        px[x, 12] = INK
    for x in range(9, 23):
        px[x, 30] = INK
    for (a, b) in ((9, 13), (10, 13), (21, 13), (22, 13), (7, 14), (8, 14), (23, 14), (24, 14),
                   (6, 15), (25, 15), (6, 28), (25, 28), (7, 29), (8, 29), (23, 29), (24, 29)):
        px[a, b] = INK
    for x in range(7, 25):
        px[x, 17] = RINSE[2]                              # the liquid's surface
    for y in range(18, 26):
        px[9, y] = RINSE[2]                               # one long highlight
    px[10, 19] = px[10, 20] = RINSE[2]
    for (bx, by) in ((14, 20), (19, 19), (16, 26), (21, 26)):
        px[bx, by] = RINSE[2]                             # bubbles
    rect(px, 12, 21, 19, 24, STEAM_W)                     # label
    frame(px, 12, 21, 19, 24, STEAM_X)
    for x in range(14, 18):
        px[x, 22] = ALLOY[2]
    px[14, 23] = px[15, 23] = ALLOY[2]
    # neck collar
    rect(px, 12, 9, 19, 11, SILVER[0])
    for x in range(12, 20):
        px[x, 9] = SILVER[2]
        px[x, 11] = SILVER[1]
    px[11, 9] = px[11, 10] = px[11, 11] = INK
    px[20, 9] = px[20, 10] = px[20, 11] = INK
    # foam cap: two bumps over a wide head
    foam_rows = {2: ((13, 14), (17, 18)), 3: ((12, 19),), 4: ((11, 20),), 5: ((10, 21),),
                 6: ((10, 21),), 7: ((11, 20),), 8: ((11, 20),)}
    for y, runs in foam_rows.items():
        for (x0, x1) in runs:
            for x in range(x0, x1 + 1):
                px[x, y] = FOAM[0] if y < 7 else FOAM[1]
            px[x0 - 1, y] = INK
            px[x1 + 1, y] = INK
    for x in (13, 14, 17, 18):
        px[x, 1] = INK
    px[12, 2] = px[15, 2] = px[16, 2] = px[19, 2] = INK
    px[11, 3] = px[20, 3] = INK
    for (fx, fy) in ((13, 3), (17, 3), (12, 5), (15, 4), (19, 5)):
        px[fx, fy] = FOAM[2]
    for (fx, fy) in ((15, 6), (18, 7), (13, 7)):
        px[fx, fy] = FOAM[1]
    save(img, "item", name)


# ---------------- particle ----------------

MOTE_SHAPES = [
    ["........", "........", "........", "...#....", "........", "........", "........", "........"],
    ["........", "........", "...o....", "..o#o...", "...o....", "........", "........", "........"],
    ["........", "...o....", "...#....", ".o###o..", "...#....", "...o....", "........", "........"],
    ["........", "........", "..o.o...", "...#....", "..o.o...", "........", "........", "........"],
    ["........", "........", "........", "...o....", "........", "........", "........", "........"],
]


def gen_rad_mote():
    """Five 8x8 frames of a lime mote: it kindles, flares to a small cross and gutters out."""
    core = (232, 255, 170, 255)
    for i, rows in enumerate(MOTE_SHAPES):
        img = Image.new("RGBA", (8, 8), CLEAR)
        px = img.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    px[x, y] = core if i == 2 and (x, y) == (3, 3) else RAD_LIME
                elif ch == "o":
                    px[x, y] = (RAD_LIME[0], RAD_LIME[1], RAD_LIME[2], 150) if i < 4 else RAD_DEEP
        save(img, "particle", "rad_mote_%d" % i)


# ---------------- models, blockstates, item definitions ----------------

def _write_json(rel, obj):
    """Write ``rel`` under the active assets folder (CRLF, like the repo); untouched when equal."""
    path = os.path.join(assets_dir(), *rel.split("/"))
    data = (json.dumps(obj, indent=2) + "\n").replace("\n", "\r\n").encode("utf-8")
    try:
        with open(path, "rb") as fh:
            if fh.read() == data:
                return
    except OSError:
        pass
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)


def _flat(texture, parent="minecraft:item/generated"):
    return {"parent": parent, "textures": {"layer0": "%s:item/%s" % (NS, texture)}}


def _stepped(item, count, predicate, parent):
    """The pre-1.21.4 item model: frame 0 plus one override per later frame, keyed on a client item
    property (0..1). A client that has not registered the property shows frame 0."""
    model = _flat("%s_0" % item, parent)
    model["overrides"] = [{"predicate": {"%s:%s" % (NS, predicate): i / (count - 1.0)},
                           "model": "%s:item/%s_%d" % (NS, item, i)} for i in range(1, count)]
    return model


def write_models():
    _write_json("models/block/shielding_block.json",
                {"parent": "minecraft:block/cube_all",
                 "textures": {"all": "%s:block/shielding_block" % NS,
                              "particle": "%s:block/shielding_block" % NS}})
    _write_json("models/block/shielding_glass.json",
                {"parent": "minecraft:block/cube_all",
                 "render_type": "minecraft:translucent",
                 "textures": {"all": "%s:block/shielding_glass" % NS,
                              "particle": "%s:block/shielding_glass" % NS}})
    for block in ("shielding_block", "shielding_glass"):
        _write_json("blockstates/%s.json" % block, {"variants": {"": {"model": "%s:block/%s" % (NS, block)}}})
        _write_json("items/%s.json" % block,
                    {"model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, block)}})
        _write_json("models/item/%s.json" % block, {"parent": "%s:block/%s" % (NS, block)})
    for item, count, predicate, parent in (
            ("geiger_counter", GEIGER_FRAMES, "geiger_frame", "minecraft:item/handheld"),
            ("dosimeter_badge", BADGE_STEPS, "dosimeter_step", "minecraft:item/generated")):
        for i in range(count):
            _write_json("models/item/%s_%d.json" % (item, i), _flat("%s_%d" % (item, i), parent))
        _write_json("models/item/%s.json" % item, _stepped(item, count, predicate, parent))
        # 1.21.4+ item definition: frame 0 until the client range property is bound; the
        # range_dispatch form replaces it then.
        _write_json("items/%s.json" % item,
                    {"model": {"type": "minecraft:model", "model": "%s:item/%s_0" % (NS, item)}})
    _write_json("items/decontaminant.json",
                {"model": {"type": "minecraft:model", "model": "%s:item/decontaminant" % NS}})
    _write_json("models/item/decontaminant.json", _flat("decontaminant"))
    _write_json("particles/rad_mote.json",
                {"textures": ["%s:rad_mote_%d" % (NS, i) for i in range(len(MOTE_SHAPES))]})


def paint():
    gen_shielding_block()
    gen_shielding_glass()
    gen_geiger_counter()
    gen_dosimeter_badge()
    gen_decontaminant()
    gen_rad_mote()
    write_models()


SHEET = [
    {"id": "shielding_block", "block": True, "faces": ["shielding_block"]},
    {"id": "shielding_glass", "block": True, "faces": ["shielding_glass"]},
    {"id": "geiger_counter", "model": None, "faces": [],
     "states": ["item:geiger_counter_%d" % i for i in range(GEIGER_FRAMES)]},
    {"id": "dosimeter_badge", "model": None, "faces": [],
     "states": ["item:dosimeter_badge_%d" % i for i in range(BADGE_STEPS)]},
    {"id": "decontaminant + motes", "model": None,
     "faces": ["item:decontaminant"] + ["particle:rad_mote_%d" % i for i in range(len(MOTE_SHAPES))]},
]

LOOKALIKES = ["fission_casing", "graphite_moderator", "dry_cask_side"]
NEROTECH_LOOKALIKES = ["foundry_brick", "fusion_containment_glass", "blast_foundry_glass"]

# Lime is this family's own colour: the top dial band, turned badge cells and the motes.
AUDIT = {"allow_lime": True, "allow_charge": False}

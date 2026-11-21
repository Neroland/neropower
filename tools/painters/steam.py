"""Steam: Turbine Helm, Shroud, Spindle, Stator, Steam Port, Steam Condenser, the three Vane Sets,
the Governor Module, the steam particles and the sprites the two renderers draw.

The machine blocks are multi-element models, so ``*_front`` / ``*_side`` are VIEWS of the whole
block (one texel = half a model unit, row 0 = the block's top) and ``*_top`` is the plan; elements
without explicit UVs show the part of the view they cover. The models, blockstates and item
definitions are written here too (``write_models``), from the same numbers the views use.

What the block models do not show is drawn by the renderers from synced buckets: the rotor (vane
blades, smear, blur disc), the helm's needle, trip flag and hub cap, the condenser's fan and the
frost / glow on its tubes. Their sprites are white or grey and tinted in code.
"""
import json
import os

from PIL import Image

from .kit import *  # noqa: F401,F403
from .kit import _mix

NS = "neropower"
EDGE = _mix(A_LIGHT, ALLOY[1], 0.35)
DIM = _mix(A_DARK, ALLOY[2], 0.5)
STEEL = [(150, 158, 166, 255), (96, 104, 112, 255), (196, 204, 210, 255)]   # machined steel (alloy lights)
COPPER = [_mix(CINDER[2], P_AMBER, 0.35), _mix(CINDER[1], P_EMBER, 0.3), _mix(CINDER[2], P_GLOW, 0.4)]
TIERS = ("steel", "cindersteel", "superconducting")
TIER_METAL = {"steel": STEEL, "cindersteel": CINDER, "superconducting": SUPER}

# Tachometer geometry (texels on the helm front): pivot, arc radius and sweep. The renderer quotes
# the same numbers for the needle.
TACH_CX, TACH_CY, TACH_R, TACH_SWEEP = 17.5, 15.0, 11.0, 50.0
BOSS = (10, 14, 21, 29)                           # bearing boss, texels (model x5..11, y1..9)


def _plate(px, rng, x0, y0, x1, y1, pal=None, lit=True):
    pal = pal or ALLOY
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = rng.choice(pal)
    if lit and x1 - x0 >= 2 and y1 - y0 >= 2:
        for x in range(x0, x1 + 1):
            px[x, y0] = EDGE
            px[x, y1] = A_DARK
        for y in range(y0, y1 + 1):
            px[x0, y] = EDGE
            px[x1, y] = A_DARK


def _bolt(px, x, y):
    px[x, y] = A_LIGHT
    px[x + 1, y] = _mix(A_LIGHT, A_DARK, 0.45)
    px[x, y + 1] = _mix(A_LIGHT, A_DARK, 0.45)
    px[x + 1, y + 1] = A_DARK


def _dark_base(name):
    img = new_img()
    noise_fill(img, [ALLOY[2], ALLOY[0]], rng_for(name + "/base"))
    return img


# ---------------- Turbine Helm ----------------

def _tach_point(bucket, r):
    a = math.radians(-TACH_SWEEP + 2.0 * TACH_SWEEP * bucket / 24.0)
    return TACH_CX + r * math.sin(a), TACH_CY - r * math.cos(a)


def _helm_front(name, alarm):
    img = front_base(name)
    px = img.load()
    rng = rng_for(name)
    # Tachometer window across the top third: a dark panel with a tick arc, no bar graph.
    recess(px, 8, 2, 28, 10)
    steps = 96
    for i in range(steps + 1):
        b = 24.0 * i / steps
        x, y = _tach_point(b, TACH_R)
        col = P_EMBER
        if 15.0 <= b <= 19.0:
            col = P_AMBER                          # the rated band
        xi, yi = int(round(x)), int(round(y))
        if 9 <= xi <= 27 and 3 <= yi <= 9:
            px[xi, yi] = col
    x, y = _tach_point(18, TACH_R)                 # rated mark
    px[int(round(x)), int(round(y))] = P_HOT
    for b in (0, 6, 12, 18, 24):                   # scale ticks inside the arc
        x, y = _tach_point(b, TACH_R - 2.5)
        xi, yi = int(round(x)), int(round(y))
        if 9 <= xi <= 27 and 3 <= yi <= 9:
            px[xi, yi] = A_LIGHT
    for r in (TACH_R, TACH_R - 1.0, TACH_R - 2.0):  # the trip tick: red, radial, past the band
        x, y = _tach_point(22, r)
        px[int(round(x)), int(round(y))] = R_ALARM
    # Lower face: hazard edge behind the boss (lit = the static "failing" read).
    hazard_edge(px, lit=alarm)
    # Bearing boss, low and off the face centre: an eight-bolt cap with a square hub well.
    x0, y0, x1, y1 = BOSS
    _plate(px, rng, x0 - 2, y0 + 2, x1 + 2, y1 - 2, pal=[ALLOY[1], ALLOY[3]])
    _plate(px, rng, x0, y0, x1, y1, pal=[ALLOY[1], ALLOY[3]])
    for (bx, by) in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2),
                     (x0 - 1, 21), (x1, 21), (15, y0 + 1), (15, y1 - 2)):
        _bolt(px, bx, by)
    recess(px, 12, 18, 19, 25)                     # hub well: the renderer's cap turns in it
    if alarm:
        rect(px, 8, 11, 11, 12, R_ALARM)           # overspeed tell-tale beside the window
    else:
        rect(px, 8, 11, 11, 12, P_EMBER)
    return img


def gen_turbine_helm():
    save(_helm_front("turbine_helm_front", False), "block", "turbine_helm_front")
    save(_helm_front("turbine_helm_front", True), "block", "turbine_helm_front_alarm")
    # Side (also top and bottom): louvred cooling block, the family mark and the block's one tell.
    img = machine_base("turbine_helm_side")
    px = img.load()
    family_mark(px, "steam")
    louvre(px, 6, 15, 25, 26)
    tell(px, 22, 6)
    save(img, "block", "turbine_helm_side")
    # Back: the bearing flange the spindle meets (inside a formed turbine).
    img = machine_base("turbine_helm_back")
    px = img.load()
    _plate(px, rng_for("turbine_helm_back/seat"), 8, 8, 23, 23, pal=[ALLOY[1], ALLOY[3]])
    for (bx, by) in ((9, 9), (21, 9), (9, 21), (21, 21)):
        _bolt(px, bx, by)
    _plate(px, rng_for("turbine_helm_back/axle"), 12, 12, 19, 19, pal=[STEEL[0], STEEL[1]])
    rect(px, 15, 13, 16, 18, A_DARK)
    rect(px, 13, 15, 18, 16, A_DARK)
    save(img, "block", "turbine_helm_back")
    # Hub cap (renderer): a slotted square cap; the off-centre slot makes a slow turn readable.
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for y in range(4, 28):
        for x in range(4, 28):
            cut = min(x - 4, 27 - x) + min(y - 4, 27 - y)
            if cut < 5:
                continue
            px[x, y] = STEEL[0] if (x + y) % 7 else STEEL[2]
    for i in range(4, 28):
        for (x, y) in ((i, 4), (4, i)):
            if px[x, y][3]:
                px[x, y] = STEEL[2]
        for (x, y) in ((i, 27), (27, i)):
            if px[x, y][3]:
                px[x, y] = STEEL[1]
    for (bx, by) in ((8, 8), (22, 8), (8, 22), (22, 22)):
        _bolt(px, bx, by)
    rect(px, 6, 14, 15, 17, A_DARK)                # drive slot to one side only
    rect(px, 6, 13, 15, 13, STEEL[1])
    rect(px, 18, 12, 21, 19, STEEL[1])
    frame(px, 18, 12, 21, 19, A_DARK)
    save(img, "block", "turbine_helm_hub")
    # Trip flag (renderer): the mechanical white-on-red flag that drops over the arc.
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    rect(px, 0, 4, 31, 27, A_DARK)
    rect(px, 1, 5, 30, 26, TRIP_R)
    rect(px, 5, 9, 26, 22, TRIP_W)
    rect(px, 14, 9, 17, 22, TRIP_R)                # a split flag: shape, not only colour
    save(img, "block", "turbine_helm_flag")


# ---------------- Turbine Shroud ----------------

def gen_turbine_shroud():
    img = machine_base("turbine_shroud")
    px = img.load()
    family_mark(px, "steam")
    for x in range(2, S - 2):                      # one panel seam
        px[x, 15] = A_DARK
        px[x, 16] = EDGE
    tell(px, 24, 24)
    save(img, "block", "turbine_shroud")
    # Windowed face: a frame and four slats, open between them (cutout).
    img = machine_base("turbine_shroud_window")
    px = img.load()
    for y in range(4, 28):
        k = (y - 4) % 6
        for x in range(4, 28):
            if k == 0:
                px[x, y] = EDGE
            elif k == 1:
                px[x, y] = ALLOY[1]
            elif k == 2:
                px[x, y] = A_DARK
            else:
                px[x, y] = CLEAR
    for y in range(4, 28):                         # the frame's inner returns
        if px[4, y][3] == 0:
            px[4, y] = A_DARK
            px[27, y] = DIM
    save(img, "block", "turbine_shroud_window")


# ---------------- Turbine Spindle ----------------

def _shaded_across(px, x0, x1, y0, y1, metal, vertical=True):
    """A round bar seen from the side: shaded across its width (x when the bar runs down the tile)."""
    w = max(1, x1 - x0)
    for x in range(x0, x1 + 1):
        t = (x - x0) / float(w)
        if t < 0.15:
            col = metal[1]
        elif t < 0.4:
            col = metal[2]
        elif t < 0.75:
            col = metal[0]
        else:
            col = _mix(metal[1], A_DARK, 0.4)
        for y in range(y0, y1 + 1):
            if vertical:
                px[x, y] = col
            else:
                px[y, x] = col


def _drum_face(px, rng, tier, metal):
    """The vane carrier's end (texels 0..23): a square drum with twelve root slots around a hub."""
    _plate(px, rng, 0, 0, 23, 23, pal=[ALLOY[1], ALLOY[3]])
    for i in range(12):
        a = math.radians(i * 30.0 + 15.0)
        for r in (7.0, 8.0, 9.0, 10.0):
            x = int(round(11.5 + r * math.cos(a)))
            y = int(round(11.5 + r * math.sin(a)))
            if 1 <= x <= 22 and 1 <= y <= 22:
                px[x, y] = metal[0] if r > 8.5 else A_DARK
    recess(px, 8, 8, 15, 15)
    tier_marks(px, tier, 9, 11)


def _drum_rim(px, tier, metal):
    """The drum's rim (texels x24..31, y0..23): one, two or three bands around it."""
    for y in range(24):
        for x in range(24, 32):
            px[x, y] = ALLOY[1] if x not in (24, 31) else (EDGE if x == 24 else A_DARK)
    cols = {0: (27,), 1: (26, 29), 2: (25, 27, 29)}[tier]
    for x in cols:
        for y in range(24):
            px[x, y] = metal[0]
            if x + 1 < 31 and tier < 2:
                px[x + 1, y] = metal[1]
    if tier == 2:                                  # arc-violet edge: the superconducting tell
        for y in range(0, 24, 6):
            px[30, y] = V_ARC
            px[30, y + 1] = V_LIGHT


def _blade(tier, metal):
    """One vane (renderer): root at the bottom, tip at the top. Square / swept / split tips."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for y in range(S):
        t = (31 - y) / 31.0                        # 0 root .. 1 tip
        half = 3.0 + 6.5 * t
        lean = 0.0
        if tier == 1:
            lean = 5.0 * t * t                     # swept
        x0 = int(round(15.5 - half + lean))
        x1 = int(round(15.5 + half + lean))
        if tier == 0 and y < 2:
            pass                                   # square tip: full width to the edge
        if tier == 1 and y < 5:
            x0 += (5 - y)                          # the sweep's raked tip
        for x in range(max(0, x0), min(31, x1) + 1):
            if tier == 2 and y < 9 and abs(x - 15.5) < (9 - y) * 0.45:
                continue                           # split tip
            k = (x - x0) / float(max(1, x1 - x0))
            col = metal[2] if k < 0.2 else (metal[0] if k < 0.7 else metal[1])
            px[x, y] = col
    if tier == 2:
        for y in range(0, 5):
            for x in range(S):
                if px[x, y][3] and (x == 0 or x == 31 or not px[x - 1, y][3] or not px[x + 1, y][3]):
                    px[x, y] = V_LIGHT if y < 2 else V_ARC
    for y in range(26, 32):                        # root platform
        for x in range(S):
            if px[x, y][3]:
                px[x, y] = _mix(metal[1], A_DARK, 0.3)
    return img


def _streaks(size, inner, outer, density, seed):
    """Motion streaks of a spinning vane ring: short arcs, white to grey, cutout."""
    img = Image.new("RGBA", (size, size), CLEAR)
    px = img.load()
    rng = rng_for(seed)
    c = (size - 1) / 2.0
    keep = {}
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d < inner or d > outer:
                continue
            ring = int(d)
            seg = int((math.atan2(y - c, x - c) + math.pi) / (2.0 * math.pi) * (24 + ring))
            key = (ring, seg)
            if key not in keep:
                keep[key] = rng.random() < density
            if not keep[key]:
                continue
            v = 150 + int(90 * (d - inner) / max(1.0, outer - inner)) + rng.randint(-10, 10)
            v = max(0, min(255, v))
            px[x, y] = (v, v, v, 255)
    return img


def gen_turbine_spindle():
    # Axle: the tile's vertical runs along the axis.
    img = _dark_base("turbine_spindle")
    px = img.load()
    _shaded_across(px, 10, 21, 0, 31, STEEL)
    for y in range(3, 32, 8):                      # machining marks
        for x in range(10, 22):
            px[x, y] = _mix(px[x, y], A_DARK, 0.35)
    for y in range(12, 20):
        px[15, y] = A_DARK                         # keyway
    save(img, "block", "turbine_spindle")
    # Collar: a square bearing collar with the axle end in it; carries the block's tell.
    img = _dark_base("turbine_spindle_collar")
    px = img.load()
    rng = rng_for("turbine_spindle_collar")
    _plate(px, rng, 6, 6, 25, 25, pal=[ALLOY[1], ALLOY[3]])
    for (bx, by) in ((7, 7), (23, 7), (7, 23), (23, 23)):
        _bolt(px, bx, by)
    _plate(px, rng, 10, 10, 21, 21, pal=[STEEL[0], STEEL[1]])
    rect(px, 15, 12, 16, 19, A_DARK)
    rect(px, 12, 15, 19, 16, A_DARK)               # cross slot, not a disc
    tell(px, 15, 8)
    save(img, "block", "turbine_spindle_collar")
    for tier, name in enumerate(TIERS):
        metal = TIER_METAL[name]
        img = _dark_base("turbine_vanes_" + name)
        px = img.load()
        _drum_face(px, rng_for("turbine_vanes_" + name), tier, metal)
        _drum_rim(px, tier, metal)
        save(img, "block", "turbine_vanes_" + name)
        save(_blade(tier, metal), "block", "turbine_vane_blade_" + name)
    save(_streaks(64, 10.0, 31.5, 0.62, "turbine_rotor_blur"), "block", "turbine_rotor_blur")
    save(_streaks(64, 10.0, 31.5, 0.16, "turbine_rotor_smear"), "block", "turbine_rotor_smear")


# ---------------- Turbine Stator ----------------

def gen_turbine_stator():
    # Yoke (end plates): a square lamination pack clamped by four tie bolts.
    img = machine_base("turbine_stator")
    px = img.load()
    plate_stack(px, 6, 6, 25, 25, pitch=4, vertical=True)
    frame(px, 5, 5, 26, 26, A_DARK)
    tell(px, 15, 3)
    save(img, "block", "turbine_stator")
    # Core: bare laminations, stacked along the axis (the tile's horizontal).
    img = new_img()
    px = img.load()
    plate_stack(px, 0, 0, 31, 31, pitch=2, face=ALLOY[3], vertical=True)
    for y in (0, 31):
        for x in range(S):
            px[x, y] = A_DARK
    save(img, "block", "turbine_stator_core")
    # Windings: wire turns across the tile. Tier is told by the band COUNT in the model first;
    # the wire is copper for tiers 1 and 2 and superconductor for tier 3.
    for tier in (1, 2, 3):
        metal = COPPER if tier < 3 else SUPER
        img = new_img()
        px = img.load()
        for x in range(S):
            k = x % (3 if tier != 2 else 2)
            col = metal[2] if k == 0 else (metal[0] if k == 1 else metal[1])
            for y in range(S):
                px[x, y] = col
        for x in range(S):
            px[x, 0] = _mix(metal[2], A_LIGHT, 0.3)
            px[x, 31] = _mix(metal[1], A_DARK, 0.5)
        for y in range(4, 28, 8):                  # binding tape
            for x in range(S):
                px[x, y] = A_DARK
        save(img, "block", "turbine_stator_winding_tier%d" % tier)


# ---------------- Turbine Steam Port ----------------

def _port_front(mode, phase):
    name = "turbine_steam_port_" + mode
    img = front_base(name)
    px = img.load()
    ring = STEAM_W if mode == "inlet" else STEAM_X
    pipe_ring(px, 6, 9, 28, ring)
    if mode == "vent":
        louvre(px, 6, 10, 25, 29, pitch=4)
        return img
    flange(px, 6, 10, 25, 29, bolts=True)
    bx0, by0, bx1, by1 = 9, 13, 22, 26
    recess(px, bx0, by0, bx1, by1)                 # a square throat: never an orb
    cx, cy = 15.5, 19.5
    # Two arrowheads in the throat: pointing in and closing (inlet), pointing out and parting (exhaust).
    travel = phase % 4
    for side in (-1, 1):
        if mode == "inlet":
            tip = 15.5 + side * (5.0 - travel)      # tips lead toward the centre
            back = side
        else:
            tip = 15.5 + side * (2.0 + travel)      # tips lead toward the rim
            back = -side
        for depth in range(3):
            x = int(round(tip + back * depth))
            for w in (-depth, depth):
                y = int(round(19.5 + w + (0.5 if w > 0 else -0.5 if w < 0 else 0.5)))
                for yy in ((y, y - 1) if depth == 0 else (y,)):
                    if bx0 < x < bx1 and by0 < yy < by1:
                        px[x, yy] = P_GLOW if depth == 0 else (P_HOT if depth == 1 else P_AMBER)
    return img


def gen_turbine_steam_port():
    for mode in ("inlet", "exhaust"):
        save_strip([_port_front(mode, f) for f in range(8)], "block", "turbine_steam_port_" + mode, 3)
    save(_port_front("vent", 0), "block", "turbine_steam_port_vent")
    img = machine_base("turbine_steam_port_side")
    px = img.load()
    family_mark(px, "steam")
    for y in range(12, 28, 5):                     # pipe seams
        for x in range(4, 28):
            px[x, y] = A_DARK
            px[x, y + 1] = EDGE
    tell(px, 22, 6)
    save(img, "block", "turbine_steam_port_side")


# ---------------- Steam Condenser ----------------

def _condenser_view(name, front):
    img = _dark_base(name)
    px = img.load()
    rng = rng_for(name)
    rect(px, 4, 8, 27, 25, SOCKET_SHADOW)          # the open bay
    for (y0, x0, x1) in ((10, 4, 27), (16, 4, 27)):  # the tube bundle seen through the frame
        for x in range(x0, x1 + 1):
            px[x, y0] = STEEL[2]
            px[x, y0 + 1] = STEEL[0]
            px[x, y0 + 2] = STEEL[0]
            px[x, y0 + 3] = STEEL[1]
            if x % 4 == 0:
                for yy in range(y0 - 1, y0 + 5):
                    px[x, yy] = CINDER[0] if yy in (y0, y0 + 1) else CINDER[1]
    _plate(px, rng, 0, 0, 31, 7)                   # header (model y12..16)
    _plate(px, rng, 0, 8, 3, 25)                   # posts
    _plate(px, rng, 28, 8, 31, 25)
    _plate(px, rng, 0, 26, 31, 31)                 # drip tray (model y0..3)
    pipe_ring(px, 28, 6, 25, HW_BLUE)              # water out: the blue ring on the tray
    if front:
        status_socket(px)
        louvre(px, 12, 2, 27, 5, pitch=2)
    else:
        family_mark(px, "steam")
        _bolt(px, 26, 3)
    return img


def gen_condenser():
    save(_condenser_view("condenser_front", True), "block", "condenser_front")
    side = _condenser_view("condenser_side", False)
    tell(side.load(), 18, 4)
    save(side, "block", "condenser_side")
    # Top: a square fan well with a guard cross; the fan itself is the renderer's.
    img = machine_base("condenser_top")
    px = img.load()
    family_mark(px, "steam")
    for y in range(6, 26):
        for x in range(6, 26):
            cut = min(x - 6, 25 - x) + min(y - 6, 25 - y)
            if cut < 4:
                continue
            px[x, y] = SOCKET_SHADOW if cut == 4 or x == 6 or y == 6 else SOCKET
    save(img, "block", "condenser_top")
    # Tube: a finned cooling tube, the tile's horizontal runs along it (rows 0..5 are one tube).
    img = new_img()
    px = img.load()
    for y in range(S):
        k = y % 6
        col = (STEEL[2], STEEL[0], STEEL[0], STEEL[0], STEEL[1], _mix(STEEL[1], A_DARK, 0.5))[k]
        for x in range(S):
            px[x, y] = col
            if x % 4 == 1:
                px[x, y] = CINDER[2] if k < 2 else (CINDER[0] if k < 4 else CINDER[1])
    save(img, "block", "condenser_tube")
    # Fan (renderer): six raked paddles on a square hub.
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for i in range(6):
        a = math.radians(i * 60.0)
        for r10 in range(40, 150):
            r = r10 / 10.0
            sweep = a + 0.035 * r
            for w in (-1.2, -0.6, 0.0, 0.6, 1.2):
                x = int(round(15.5 + r * math.cos(sweep) - w * math.sin(sweep) * (0.6 + r / 12.0)))
                y = int(round(15.5 + r * math.sin(sweep) + w * math.cos(sweep) * (0.6 + r / 12.0)))
                if 0 <= x < S and 0 <= y < S:
                    px[x, y] = STEEL[2] if w < -0.5 else (STEEL[0] if w < 0.7 else STEEL[1])
    rect(px, 12, 12, 19, 19, STEEL[1])
    frame(px, 12, 12, 19, 19, A_DARK)
    rect(px, 15, 13, 16, 15, A_LIGHT)
    save(img, "block", "condenser_fan")
    # Tube overlay (renderer): rime when cryo-fed (tinted ice white), glow when hot.
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    rng = rng_for("condenser_tube_overlay")
    for y in range(S):
        for x in range(S):
            if rng.random() < 0.55 * (1.0 - (y % 6) / 7.0):
                v = rng.choice((255, 236, 214))
                px[x, y] = (v, v, v, 255)
    save(img, "block", "condenser_tube_overlay")


# ---------------- items ----------------

def _vane_set(tier, name):
    metal = TIER_METAL[name]
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    hub = (9.0, 25.0)
    for i, deg in enumerate((-78.0, -45.0, -12.0)):     # a fan of three vanes on a hub segment
        a = math.radians(deg)
        for r10 in range(40, 215):
            r = r10 / 10.0
            t = (r - 4.0) / 17.5
            half = 1.6 + 2.6 * t
            sweep = a + (0.012 * r if tier == 1 else 0.0)
            for w10 in range(int(-half * 10), int(half * 10) + 1, 4):
                w = w10 / 10.0
                if tier == 2 and t > 0.72 and abs(w) < (t - 0.72) * 5.0:
                    continue                           # split tip
                x = int(round(hub[0] + r * math.cos(sweep) - w * math.sin(sweep)))
                y = int(round(hub[1] + r * math.sin(sweep) + w * math.cos(sweep)))
                if 0 <= x < S and 0 <= y < S:
                    col = metal[2] if w < -half * 0.4 else (metal[0] if w < half * 0.5 else metal[1])
                    if tier == 2 and t > 0.97:
                        col = V_LIGHT if w < 0 else V_ARC
                    px[x, y] = col
    for y in range(19, 31):                            # hub segment
        for x in range(2, 15):
            if math.hypot(x - hub[0], y - hub[1]) < 6.2 and (x - hub[0]) + (hub[1] - y) > -6:
                px[x, y] = ALLOY[3] if (x + y) % 5 else A_LIGHT
    for y in range(19, 31):
        for x in range(2, 15):
            if px[x, y][3] and (y == 30 or not px[x, y + 1][3]):
                px[x, y] = A_DARK
    tier_marks(px, tier, 5, 26)
    # one-pixel dark outline so the icon holds on any background
    edge = []
    for y in range(S):
        for x in range(S):
            if px[x, y][3]:
                continue
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < S and 0 <= yy < S and px[xx, yy][3] and px[xx, yy] != A_DARK:
                    edge.append((x, y))
                    break
    for (x, y) in edge:
        px[x, y] = A_DARK
    return img


def gen_items():
    for tier, name in enumerate(TIERS):
        save(_vane_set(tier, name), "item", name + "_vane_set")
    # Governor Module: a module card with a two-ball governor glyph.
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    rng = rng_for("governor_module")
    _plate(px, rng, 5, 3, 26, 28, pal=[ALLOY[1], ALLOY[3]])
    frame(px, 4, 2, 27, 29, A_DARK)
    for x in range(8, 24, 3):                      # edge contacts
        rect(px, x, 26, x + 1, 27, P_AMBER)
    recess(px, 8, 6, 23, 22)
    rect(px, 15, 8, 16, 20, STEEL[0])              # spindle
    line(px, 15, 9, 10, 14, STEEL[2])              # two arms
    line(px, 16, 9, 21, 14, STEEL[2])
    line(px, 10, 15, 15, 19, STEEL[1])
    line(px, 21, 15, 16, 19, STEEL[1])
    for (bx, by) in ((9, 13), (20, 13)):           # the two fly-balls (square-cut, 3 px)
        rect(px, bx, by, bx + 2, by + 2, P_HOT)
        px[bx, by] = P_GLOW
        px[bx + 2, by + 2] = P_AMBER
    rect(px, 13, 20, 18, 21, STEEL[1])
    save(img, "item", "governor_module")


# ---------------- particles ----------------

def _cloud(name, size, frame_i, frames, lobes, rise):
    img = Image.new("RGBA", (size, size), CLEAR)
    px = img.load()
    rng = rng_for(name)
    t = frame_i / float(max(1, frames - 1))
    c = (size - 1) / 2.0
    blobs = []
    for _ in range(lobes):
        ang = rng.uniform(0, 2 * math.pi)
        dist = rng.uniform(0.0, 0.22) * size * (0.5 + t)
        rad = rng.uniform(0.2, 0.3) * size * (0.75 + 0.5 * t)
        blobs.append((c + dist * math.cos(ang), c + dist * math.sin(ang) - rise * t * size, rad))
    thin = rng_for(name + "/thin%d" % frame_i)
    for y in range(size):
        for x in range(size):
            best = 9.0
            for (bx, by, br) in blobs:
                best = min(best, math.hypot(x - bx, y - by) / br)
            if best > 1.0:
                continue
            if thin.random() < t * 0.75 * (0.35 + best):
                continue                               # it thins out as it ages
            col = STEAM_W if best < 0.55 else (_mix(STEAM_W, STEAM_X, 0.6) if best < 0.85 else STEAM_X)
            px[x, y] = col
    return img


def gen_particles():
    for i in range(6):
        save(_cloud("steam_puff", 16, i, 6, 4, 0.0), "particle", "steam_puff_%d" % i)
    for i in range(8):
        save(_cloud("steam_plume", 16, i, 8, 6, 0.12), "particle", "steam_plume_%d" % i)


# ---------------- models ----------------

_FACES = ("down", "up", "north", "south", "west", "east")


def _el(frm, to, tex, name=None, skip=(), uv=None, rot=None):
    """One element. ``tex`` maps a face (or "*") to a texture variable; a face on the block
    boundary gets its cullface. ``uv`` / ``rot`` map faces to explicit UVs and rotations."""
    on = {"down": frm[1] == 0, "up": to[1] == 16, "north": frm[2] == 0, "south": to[2] == 16,
          "west": frm[0] == 0, "east": to[0] == 16}
    faces = {}
    for f in _FACES:
        if f in skip:
            continue
        face = {"texture": "#" + tex.get(f, tex.get("*"))}
        if uv and (f in uv or "*" in uv):
            face["uv"] = list(uv.get(f, uv.get("*")))
        if rot and rot.get(f):
            face["rotation"] = rot[f]
        if on[f]:
            face["cullface"] = f
        faces[f] = face
    out = {}
    if name:
        out["name"] = name
    out["from"] = list(frm)
    out["to"] = list(to)
    out["faces"] = faces
    return out


def _socket(tex_var):
    """The standard lamp socket (model x13..15, y12..14, half a pixel proud of z 0)."""
    el = _el((13, 12, -0.5), (15, 14, 0), {"*": tex_var}, name="status_socket", skip=("south",))
    for face in el["faces"].values():
        face.pop("cullface", None)
    return el


def _model(textures, elements, cutout=False):
    out = {"parent": "minecraft:block/block"}
    if cutout:
        out["render_type"] = "minecraft:cutout"
    out["textures"] = textures
    out["elements"] = elements
    return out


def _t(**kw):
    return {k: "%s:block/%s" % (NS, v) for k, v in kw.items()}


def _helm_model(front):
    tex = {"*": "side", "north": "front", "south": "back"}
    return _model(_t(front=front, side="turbine_helm_side", back="turbine_helm_back", particle="turbine_helm_side"), [
        _el((0, 3, 2), (16, 16, 16), tex),
        _el((2, 0, 2), (14, 3, 16), tex, skip=("up",)),              # plinth: the lower corners are cut
        _el((0, 10, 0), (16, 16, 2), tex, skip=("south",)),          # tachometer hood
        _el((5, 1, 0), (11, 9, 2), tex, skip=("south",)),            # bearing boss
        _el((4, 2, 0.5), (12, 8, 2), tex, skip=("south",)),
        _socket("front"),
    ])


def _port_model(front):
    tex = {"*": "side", "north": "front"}
    return _model(_t(front=front, side="turbine_steam_port_side", particle="turbine_steam_port_side"), [
        _el((2, 0, 2), (14, 16, 16), tex),                           # body, all four corners cut
        _el((0, 2, 2), (16, 14, 16), tex),
        _el((3, 1, 0), (13, 3, 2), tex, skip=("south",)),            # throat flange, four bars
        _el((3, 9, 0), (13, 11, 2), tex, skip=("south",)),
        _el((3, 3, 0), (5, 9, 2), tex, skip=("south", "up", "down")),
        _el((11, 3, 0), (13, 9, 2), tex, skip=("south", "up", "down")),
        _el((12, 11, 0), (16, 15, 2), tex, skip=("south",)),         # lamp boss
        _socket("front"),
    ])


def _condenser_model():
    tex = {"*": "side", "north": "front", "up": "top", "down": "top"}
    tube = {"*": "tube"}
    long_uv = {"north": (1, 0, 15, 3), "south": (1, 0, 15, 3), "up": (1, 0, 15, 3), "down": (1, 0, 15, 3),
               "west": (0, 0, 3, 3), "east": (0, 0, 3, 3)}
    els = [
        _el((0, 0, 0), (16, 3, 16), tex),                            # drip tray
        _el((0, 12, 0), (16, 16, 16), tex),                          # header and fan deck
        _el((0, 3, 0), (2, 12, 2), tex, skip=("up", "down")),        # posts
        _el((14, 3, 0), (16, 12, 2), tex, skip=("up", "down")),
        _el((0, 3, 14), (2, 12, 16), tex, skip=("up", "down")),
        _el((14, 3, 14), (16, 12, 16), tex, skip=("up", "down")),
    ]
    for (y, z) in CONDENSER_TUBES:
        els.append(_el((1, y, z), (15, y + 3, z + 3), tube, uv=long_uv))
    els.append(_socket("front"))
    return _model(_t(front="condenser_front", side="condenser_side", top="condenser_top",
                     tube="condenser_tube", particle="condenser_side"), els)


CONDENSER_TUBES = ((5, 2), (8.5, 6.5), (5, 11))    # (y, z) of each tube's low corner; the renderer quotes these

_LONG_ROT = {"west": 90, "east": 90}               # the tile's vertical runs along the axis (z)


def _spindle_model(tier_name):
    axle_uv = {"up": (5, 0, 11, 16), "down": (5, 0, 11, 16), "west": (5, 0, 11, 16), "east": (5, 0, 11, 16),
               "north": (5, 5, 11, 11), "south": (5, 5, 11, 11)}
    axle_tex = {"*": "axle", "north": "collar", "south": "collar"}
    els = [_el((5, 5, 0), (11, 11, 16), axle_tex, uv=axle_uv, rot=_LONG_ROT)]
    for z in (2, 11):
        els.append(_el((3, 3, z), (13, 13, z + 3), {"*": "collar"}))
    textures = _t(axle="turbine_spindle", collar="turbine_spindle_collar", particle="turbine_spindle_collar")
    if tier_name:
        rim = (12, 0, 16, 12)
        drum_uv = {"north": (0, 0, 12, 12), "south": (0, 0, 12, 12), "up": rim, "down": rim,
                   "west": rim, "east": rim}
        els.append(_el((2, 2, 6), (14, 14, 10), {"*": "vanes"}, uv=drum_uv, rot={"up": 90, "down": 90}))
        textures.update(_t(vanes="turbine_vanes_" + tier_name))
    return _model(textures, els, cutout=True)


def _stator_model(tier):
    core_rot = {"up": 90, "down": 90}
    els = [
        _el((1, 1, 0), (15, 15, 2), {"*": "yoke"}),
        _el((1, 1, 14), (15, 15, 16), {"*": "yoke"}),
        _el((4, 4, 2), (12, 12, 14), {"*": "core"}, skip=("north", "south"), rot=core_rot),
    ]
    bands = {0: (), 1: ((6, 10),), 2: ((3, 7), (9, 13)), 3: ((2.5, 5.5), (6.5, 9.5), (10.5, 13.5))}[tier]
    for (z0, z1) in bands:
        els.append(_el((2, 2, z0), (14, 14, z1), {"*": "winding"}, rot=core_rot))
    textures = _t(yoke="turbine_stator", core="turbine_stator_core", particle="turbine_stator")
    if tier:
        textures.update(_t(winding="turbine_stator_winding_tier%d" % tier))
    return _model(textures, els, cutout=True)


def _shroud_model():
    tex = {"*": "window", "up": "plate", "down": "plate"}
    return _model(_t(window="turbine_shroud_window", plate="turbine_shroud", particle="turbine_shroud"),
                  [_el((0, 0, 0), (16, 16, 16), tex)], cutout=True)


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


_ROT = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
_AXIS = (("z", {}), ("x", {"y": 90}), ("y", {"x": 90}))    # authored along z


def _ref(model, **extra):
    v = {"model": "%s:block/%s" % (NS, model)}
    v.update({k: val for k, val in extra.items() if val})
    return v


BLOCKS = ("turbine_helm", "turbine_shroud", "turbine_spindle", "turbine_stator", "turbine_steam_port", "condenser")
ITEMS = ("steel_vane_set", "cindersteel_vane_set", "superconducting_vane_set", "governor_module")
WINDINGS = ("none", "tier1", "tier2", "tier3")


def write_models():
    """Models, blockstates, item definitions and the two particle definitions."""
    models = {
        "turbine_helm": _helm_model("turbine_helm_front"),
        "turbine_helm_alarm": _helm_model("turbine_helm_front_alarm"),
        "turbine_shroud": _shroud_model(),
        "turbine_spindle": _spindle_model(None),
        "condenser": _condenser_model(),
    }
    for name in TIERS:
        models["turbine_spindle_" + name] = _spindle_model(name)
    for i, w in enumerate(WINDINGS):
        models["turbine_stator" + ("" if not i else "_" + w)] = _stator_model(i)
    for mode in ("inlet", "exhaust", "vent"):
        models["turbine_steam_port_" + mode] = _port_model("turbine_steam_port_" + mode)
    for name, model in models.items():
        _write_json("models/block/%s.json" % name, model)

    states = {}
    for facing, y in _ROT:
        states["alarm=false,facing=%s" % facing] = _ref("turbine_helm", y=y)
        states["alarm=true,facing=%s" % facing] = _ref("turbine_helm_alarm", y=y)
    _write_json("blockstates/turbine_helm.json", {"variants": states})
    _write_json("blockstates/turbine_shroud.json", {"variants": {"": _ref("turbine_shroud")}})
    states = {}
    for axis, turn in _AXIS:
        states["axis=%s,vanes=none" % axis] = _ref("turbine_spindle", **turn)
        for name in TIERS:
            states["axis=%s,vanes=%s" % (axis, name)] = _ref("turbine_spindle_" + name, **turn)
    _write_json("blockstates/turbine_spindle.json", {"variants": states})
    states = {}
    for axis, turn in _AXIS:
        for i, w in enumerate(WINDINGS):
            states["axis=%s,winding=%s" % (axis, w)] = _ref("turbine_stator" + ("" if not i else "_" + w), **turn)
    _write_json("blockstates/turbine_stator.json", {"variants": states})
    states = {}
    for facing, y in _ROT:
        for mode in ("inlet", "exhaust", "vent"):
            states["facing=%s,port_mode=%s" % (facing, mode)] = _ref("turbine_steam_port_" + mode, y=y)
    _write_json("blockstates/turbine_steam_port.json", {"variants": states})
    _write_json("blockstates/condenser.json",
                {"variants": {"facing=%s" % f: _ref("condenser", y=y) for f, y in _ROT}})

    item_model = {"turbine_steam_port": "turbine_steam_port_inlet", "turbine_spindle": "turbine_spindle"}
    for block in BLOCKS:
        ref = "%s:block/%s" % (NS, item_model.get(block, block))
        _write_json("items/%s.json" % block, {"model": {"type": "minecraft:model", "model": ref}})
        _write_json("models/item/%s.json" % block, {"parent": ref})
    for item in ITEMS:
        _write_json("items/%s.json" % item,
                    {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, item)}})
        _write_json("models/item/%s.json" % item,
                    {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
    _write_json("particles/steam_puff.json", {"textures": ["%s:steam_puff_%d" % (NS, i) for i in range(6)]})
    _write_json("particles/steam_plume.json", {"textures": ["%s:steam_plume_%d" % (NS, i) for i in range(8)]})


def paint():
    gen_turbine_helm()
    gen_turbine_shroud()
    gen_turbine_spindle()
    gen_turbine_stator()
    gen_turbine_steam_port()
    gen_condenser()
    gen_items()
    gen_particles()
    write_models()


# name, frames, frametime(ticks), amplitude, hue window
PULSE = [
    ("turbine_helm_front", 8, 3, 0.25, HUE_AMBER),
    ("turbine_helm_front_alarm", 8, 2, 0.35, HUE_ALARM),
]

# Tints the renderers apply, so the sheet shows what the game shows.
BLUR_TINT = {"steel": (196, 204, 210), "cindersteel": (168, 112, 96), "superconducting": (96, 220, 214)}
FROST = (214, 232, 244)
OVERHEAT = (255, 84, 56)

SHEET = [
    {"id": "turbine_helm", "block": True,
     "faces": ["turbine_helm_front", "turbine_helm_side", "turbine_helm_back", "turbine_helm_hub",
               "turbine_helm_flag"],
     "states": ["turbine_helm_front", "turbine_helm_front_alarm"]},
    {"id": "turbine_shroud", "block": True, "faces": ["turbine_shroud_window", "turbine_shroud"]},
    {"id": "turbine_spindle", "block": True,
     "faces": ["turbine_spindle", "turbine_spindle_collar"]},
    {"id": "turbine_steam_port", "block": True, "model": "block/turbine_steam_port_inlet",
     "faces": ["turbine_steam_port_side"],
     "states": ["turbine_steam_port_inlet", "turbine_steam_port_exhaust", "turbine_steam_port_vent"]},
    {"id": "condenser", "block": True,
     "faces": ["condenser_front", "condenser_side", "condenser_top", "condenser_tube", "condenser_fan"]},
    {"id": "condenser tubes: cryo", "model": None, "faces": ["condenser_tube_overlay"], "tint": FROST},
    {"id": "condenser tubes: hot", "model": None, "faces": ["condenser_tube_overlay"], "tint": OVERHEAT},
    {"id": "stator: bare", "block": True, "model": "block/turbine_stator",
     "faces": ["turbine_stator", "turbine_stator_core"]},
]
SHEET[3:3] = [{"id": "vanes: " + n, "model": "block/turbine_spindle_" + n,
               "faces": ["turbine_vanes_" + n, "turbine_vane_blade_" + n, "item:%s_vane_set" % n]}
              for n in TIERS]
SHEET += [{"id": "stator: tier %d" % i, "model": "block/turbine_stator_tier%d" % i,
           "faces": ["turbine_stator_winding_tier%d" % i]} for i in (1, 2, 3)]
SHEET += [{"id": "rotor blur: " + n, "model": None, "faces": ["turbine_rotor_blur", "turbine_rotor_smear"],
           "tint": BLUR_TINT[n]} for n in ("steel", "superconducting")]
SHEET += [
    {"id": "governor + particles", "model": None,
     "faces": ["item:governor_module"] + ["particle:steam_puff_%d" % i for i in (0, 2, 5)]
     + ["particle:steam_plume_%d" % i for i in (0, 3, 7)]},
]

LOOKALIKES = ["stirling_generator_front", "radioisotope_generator_front", "fission_casing", "stirling_flywheel"]
NEROTECH_LOOKALIKES = ["gas_turbine_front", "wind_turbine_front", "atmospheric_condenser_front", "radiator",
                       "scrubber_fan", "coolant_pump_front"]

AUDIT = {"allow_lime": False, "allow_charge": False}

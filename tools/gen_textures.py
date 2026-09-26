#!/usr/bin/env python3
"""Generate the 32x32 amber/"power" texture set for NeroPower.

Ported from NeroTech's tools/gen_textures.py so the add-on reads as the same family: every block
face is the shared machine-face recipe — `noise_fill(ALLOY)` + 2px `bevel(A_LIGHT, A_DARK)` +
corner rivets — but the emissive ramp is the amber POWER ramp (P_EMBER -> P_AMBER -> P_HOT ->
P_GLOW) instead of NeroTech's teal T_* ramp. Every emissive pixel uses ONLY the P_* ramp (plus
R_ALARM on the fission alarm face) so the PULSE animator's HSV hue-window in gen_animations.py
picks it up; the one T_CYAN "family tell" per block sits outside that window and stays static.
Hazard striping is reserved for the Fission Core shell edges and the Fission Casing corners;
battery charge windows use the green -> amber -> red CH_* ramp (never animated).

* Deterministic: every painter seeds its RNG from the texture name, so re-runs are stable.
* ADDITIVE-ONLY: save() skips any PNG that already exists; pass --force to replace the whole set.
* Pillow optional: without it the script exits 0 with a notice (`pip install pillow` to enable).
* Standalone: paths resolve relative to this file (tools/ -> repo root), no target helper.

Outputs into common/src/main/resources/assets/neropower/textures/{block,item}. Animated fronts
are painted here as their static 32x32 frame 0; tools/gen_animations.py turns them into strips.
"""
import hashlib
import math
import os
import random
import sys

try:
    from PIL import Image
except ModuleNotFoundError:
    print("gen_textures: Pillow not installed; skipping texture generation (pip install pillow).")
    sys.exit(0)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "neropower")
BLOCK_DIR = os.path.join(ASSETS, "textures", "block")
ITEM_DIR = os.path.join(ASSETS, "textures", "item")
os.makedirs(BLOCK_DIR, exist_ok=True)
os.makedirs(ITEM_DIR, exist_ok=True)

S = 32  # texture size (NeroTech's 32x recipes, 2px bevels)
FORCE = "--force" in sys.argv

# ---- Palette (RGBA) — single source of truth is tools/README-textures.md; keep in lockstep ----
CLEAR = (0, 0, 0, 0)
A_DARK = (24, 30, 36, 255)                      # alloy shadow          (== NeroTech)
ALLOY = [(40, 50, 58, 255), (48, 60, 68, 255),  # alloy base ramp       (== NeroTech)
         (34, 42, 50, 255), (58, 72, 82, 255)]
A_LIGHT = (132, 156, 172, 255)                  # alloy highlight       (== NeroTech)
P_EMBER = (64, 24, 6, 255)                      # power deep ember      (replaces T_DEEP)
P_AMBER = (204, 112, 18, 255)                   # power amber           (replaces T_TEAL)
P_HOT = (255, 160, 40, 255)                     # power hot orange      (replaces T_CYAN/T_PLASMA)
P_GLOW = (255, 226, 160, 255)                   # power white-gold peak (replaces T_GLOW)
T_CYAN = (36, 208, 222, 255)                    # NeroTech's cyan — the ONE family tell per block
R_ALARM = (255, 56, 36, 255)                    # fission alarm red (alarm face only, pulses)
HAZ_Y = (255, 214, 30, 255)                     # hazard stripe — hue sits ABOVE the pulse window
HAZ_K = (24, 24, 30, 255)
CH_GREEN = (72, 214, 96, 255)                   # battery charge window: full
CH_AMBER = (255, 160, 40, 255)                  # battery charge window: mid
CH_RED = (236, 64, 40, 255)                     # battery charge window: low
CH_OFF = (14, 18, 22, 255)                      # battery charge window: unlit segment
POWER_RAMP = [P_EMBER, P_AMBER, P_HOT, P_GLOW]

# Metal hues for the item art.
SILVER = [(150, 154, 162, 255), (118, 122, 130, 255), (180, 184, 192, 255)]
URANIUM = [(106, 128, 92, 255), (78, 96, 70, 255), (150, 172, 128, 255)]
REPROC = [(148, 160, 140, 255), (116, 128, 110, 255), (188, 198, 176, 255)]
SPENT = [(70, 66, 62, 255), (52, 48, 46, 255), (96, 90, 84, 255)]

# Coverage ledger: every save() records its name so main() can flag any pre-existing PNG that
# no painter owns (a --force run must replace the WHOLE set — no orphans left on the old art).
PAINTED = {"block": set(), "item": set()}


# ---------------- helpers ----------------

def rng_for(name):
    """Deterministic per-name seed — stable across runs and machines."""
    return random.Random(int(hashlib.md5(name.encode()).hexdigest(), 16) & 0xffffffff)


def new_img():
    return Image.new("RGBA", (S, S), CLEAR)


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def noise_fill(img, palette, rng):
    px = img.load()
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice(palette)
    return img


def bevel(img, light=A_LIGHT, dark=A_DARK):
    """2px bevel: highlight top/left, shadow bottom/right."""
    px = img.load()
    light2 = _mix(light, ALLOY[0], 0.45)
    dark2 = _mix(dark, ALLOY[0], 0.35)
    for i in range(S):
        px[i, 0] = light
        px[0, i] = light
        px[i, S - 1] = dark
        px[S - 1, i] = dark
    for i in range(1, S - 1):
        px[i, 1] = light2
        px[1, i] = light2
        px[i, S - 2] = dark2
        px[S - 2, i] = dark2


def rivets(img, pts=((4, 4), (27, 4), (4, 27), (27, 27))):
    """2x2 corner rivets."""
    px = img.load()
    half = _mix(A_LIGHT, A_DARK, 0.45)
    for (rx, ry) in pts:
        px[rx, ry] = A_LIGHT
        px[rx + 1, ry] = half
        px[rx, ry + 1] = half
        px[rx + 1, ry + 1] = A_DARK


def machine_base(name):
    """The shared machine-face recipe: alloy noise + 2px bevel + corner rivets."""
    img = new_img()
    noise_fill(img, ALLOY, rng_for(name))
    bevel(img)
    rivets(img)
    return img


def recess(px, x0, y0, x1, y1, fill=(12, 16, 20, 255)):
    """Sunken dark panel: fill + inner shadow (top/left) + catch-light on the lower lip."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = fill
    for x in range(x0, x1 + 1):
        px[x, y0] = (6, 9, 12, 255)
    for y in range(y0, y1 + 1):
        px[x0, y] = (6, 9, 12, 255)
    for x in range(x0 + 1, x1 + 1):
        px[x, y1] = _mix(A_LIGHT, A_DARK, 0.55)


def led(px, x, y, col=P_HOT, core=None):
    """4x4 ember socket with a 2x2 emissive core at (x, y) — P_* only, so PULSE catches it."""
    for yy in range(y - 1, y + 3):
        for xx in range(x - 1, x + 3):
            px[xx, yy] = P_EMBER
    for yy in range(y, y + 2):
        for xx in range(x, x + 2):
            px[xx, yy] = col
    if core:
        px[x, y] = core


def tell(px, x, y, vertical=False):
    """The thin T_CYAN family tell: a 1x2 (or 2x1) cyan tick on a dark socket. ONE per block."""
    if vertical:
        px[x, y - 1] = A_DARK
        px[x, y] = T_CYAN
        px[x, y + 1] = T_CYAN
        px[x, y + 2] = A_DARK
    else:
        px[x - 1, y] = A_DARK
        px[x, y] = T_CYAN
        px[x + 1, y] = T_CYAN
        px[x + 2, y] = A_DARK


def haz_band(px, y0, y1, x0=2, x1=S - 3):
    """Diagonal hazard striping — Fission Core shell edges / Fission Casing corners ONLY."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = HAZ_Y if ((x + y) // 3) % 2 == 0 else HAZ_K


def haz_band_v(px, x0, x1, y0=2, y1=S - 3):
    """Vertical hazard band (the shell-edge variant)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = HAZ_Y if ((x + y) // 3) % 2 == 0 else HAZ_K


def ramp_at(t, ramp=POWER_RAMP):
    """Nearest ramp colour for 0..1 (0 = ember, 1 = white-gold)."""
    i = min(len(ramp) - 1, max(0, int(round(t * (len(ramp) - 1)))))
    return ramp[i]


def radial(px, fn, cx=15.5, cy=15.5):
    """Call fn(d, ang, x, y) for every pixel; paint the colour it returns (None = leave)."""
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            ang = math.atan2(y - cy, x - cx)
            col = fn(d, ang, x, y)
            if col is not None:
                px[x, y] = col


def save(img, folder, name):
    # ADDITIVE-ONLY: never clobber an existing asset (pass --force to override).
    d = {"block": BLOCK_DIR, "item": ITEM_DIR}[folder]
    PAINTED[folder].add(name + ".png")
    path = os.path.join(d, name + ".png")
    if os.path.exists(path) and not FORCE:
        print("skip (exists)", os.path.relpath(path, ROOT))
        return
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


# ---------------- fission ----------------

def _fission_viewport(px, core_ramp, ring_col):
    """Round viewport ring over a glowing core (the fusion_reactor_front recipe, re-ramped)."""
    def paint(d, ang, x, y):
        if d <= 3.0:
            return core_ramp[3] if d <= 1.6 else core_ramp[2]
        if d <= 5.5:
            return core_ramp[2] if (x + y) % 4 == 0 else core_ramp[1]
        if d <= 7.5:
            return core_ramp[1] if (x * 2 + y) % 5 == 0 else _mix(core_ramp[0], core_ramp[1], 0.5)
        if d <= 9.0:
            return core_ramp[0]
        if d <= 12.0:
            if d >= 11.2 or d <= 9.8:
                return A_DARK
            return ring_col if (x + y) % 2 == 0 else ALLOY[1]
        return None
    radial(px, paint)
    for k in range(8):                          # viewport ring bolts
        ang = k * math.pi / 4
        px[int(round(15.5 + 10.5 * math.cos(ang))),
           int(round(15.5 + 10.5 * math.sin(ang)))] = A_LIGHT


def gen_fission_core():
    # side: hazard-striped shell EDGES (vertical bands) + heavy plate seams + an ember vent row
    img = machine_base("fission_core")
    px = img.load()
    haz_band_v(px, 3, 6)
    haz_band_v(px, 25, 28)
    for y in range(2, 30):
        px[2, y] = A_DARK
        px[7, y] = A_DARK
        px[24, y] = A_DARK
        px[29, y] = A_DARK
    for y in (10, 21):                          # heavy plate seams
        for x in range(9, 23):
            px[x, y] = A_DARK
    for x in (10, 15, 21):                      # plate bolts
        px[x, 5] = A_LIGHT
        px[x, 6] = A_DARK
        px[x, 26] = A_LIGHT
        px[x, 27] = A_DARK
    for y in (14, 17):                          # ember vent slots between the seams
        for x in range(10, 22):
            px[x, y] = P_AMBER if x in (15, 16) else P_EMBER
            px[x, y + 1] = A_DARK
    tell(px, 15, 24)
    save(img, "block", "fission_core")

    # top: chamfered lid (hazard on the chamfers) + rod-port ring with four lit ports
    img = machine_base("fission_core_top")
    px = img.load()
    for y in range(2, 30):
        for x in range(2, 30):
            m = min(x, 31 - x) + min(y, 31 - y)
            if m < 6:
                px[x, y] = A_DARK
            elif m < 8:
                px[x, y] = HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K

    def lid(d, ang, x, y):
        if 8.0 <= d <= 11.5:
            seg = int((ang + math.pi) / (math.pi / 6)) % 2
            return ALLOY[3] if seg else A_DARK
        if d < 3.5:
            return P_AMBER if d < 1.6 else P_EMBER
        return None
    radial(px, lid)
    for k in range(4):                          # the four rod ports, amber-lit
        ang = k * math.pi / 2 + math.pi / 4
        rx = int(round(15.5 + 6.2 * math.cos(ang)))
        ry = int(round(15.5 + 6.2 * math.sin(ang)))
        led(px, rx, ry, P_AMBER, P_HOT)
    tell(px, 15, 4)
    save(img, "block", "fission_core_top")

    # front: viewport over the amber core + a status LED row (PULSE target — P_* only)
    img = machine_base("fission_core_front")
    px = img.load()
    _fission_viewport(px, POWER_RAMP, ALLOY[3])
    for i, x in enumerate((6, 10, 22, 26)):     # status LEDs flanking the port
        led(px, x, 28, P_AMBER if i % 2 else P_HOT)
    tell(px, 15, 3)
    save(img, "block", "fission_core_front")

    # front (alarm): the same viewport over-driven to red, red LEDs, hazard corner ticks
    img = machine_base("fission_core_front_alarm")
    px = img.load()
    alarm_ramp = [_mix(P_EMBER, R_ALARM, 0.35), _mix(P_AMBER, R_ALARM, 0.6), R_ALARM,
                  _mix(R_ALARM, P_GLOW, 0.55)]
    _fission_viewport(px, alarm_ramp, ALLOY[3])
    for x in (6, 10, 22, 26):
        led(px, x, 28, R_ALARM, _mix(R_ALARM, P_GLOW, 0.5))
    for (cx, cy) in ((2, 2), (27, 2), (2, 27), (27, 27)):   # hazard ticks in the corners
        for i in range(3):
            px[cx + i, cy] = HAZ_Y if i % 2 == 0 else HAZ_K
            px[cx, cy + i] = HAZ_Y if i % 2 == 0 else HAZ_K
    tell(px, 15, 3)
    save(img, "block", "fission_core_front_alarm")

    # port: the rod-port inset panel — recessed collar around a dark bore with ember deep inside
    img = machine_base("fission_core_port")
    px = img.load()
    recess(px, 4, 4, 27, 27, (10, 13, 16, 255))

    def bore(d, ang, x, y):
        if d <= 3.2:
            return P_AMBER if d <= 1.4 else P_EMBER
        if d <= 5.0:
            return (6, 9, 12, 255)
        if d <= 7.0:
            return A_DARK if (x + y) % 3 else ALLOY[2]
        if d <= 9.0:
            return ALLOY[3] if (x + y) % 2 == 0 else ALLOY[1]
        if d <= 9.8:
            return A_LIGHT if y < 15 else A_DARK
        return None
    radial(px, bore)
    for (bx, by) in ((7, 7), (23, 7), (7, 23), (23, 23)):    # clamp bolts
        px[bx, by] = A_LIGHT
        px[bx + 1, by + 1] = A_DARK
    tell(px, 15, 26)
    save(img, "block", "fission_core_port")

    # glow: BER emissive core — soft radial amber-white wisp on transparent (PULSE breathes it)
    img = new_img()
    px = img.load()

    def wisp(d, ang, x, y):
        if d > 15.5:
            return None
        g = max(0.0, 1.0 - d / 15.0)
        swirl = 0.5 + 0.5 * math.sin(3.0 * ang + d * 0.85)
        a = (g ** 1.5) * (0.45 + 0.55 * swirl)
        if a < 0.06:
            return None
        if a > 0.85:
            col = P_GLOW
        elif a > 0.62:
            col = P_HOT
        elif a > 0.32:
            col = P_AMBER
        else:
            col = P_EMBER
        return col[:3] + (min(255, int(90 + 165 * a)),)
    radial(px, wisp)
    save(img, "block", "fission_core_glow")


def gen_fission_casing():
    # multiblock shell plate: dashed ember conduit ring + plate seam cross + hazard CORNERS
    img = machine_base("fission_casing")
    px = img.load()
    for i in range(3, 29):                      # dashed conduit ring just inside the bevel
        c = P_EMBER if i % 4 < 2 else _mix(P_EMBER, P_AMBER, 0.5)
        px[i, 2] = c
        px[i, 29] = c
        px[2, i] = c
        px[29, i] = c
    for i in (8, 16, 24):                       # conduit junction pips
        px[i, 2] = P_AMBER
        px[i, 29] = P_AMBER
        px[2, i] = P_AMBER
        px[29, i] = P_AMBER
    for x in range(6, 26):                      # heavy plate seam cross
        px[x, 15] = A_DARK
        px[x, 16] = ALLOY[2]
    for y in range(6, 26):
        px[15, y] = A_DARK
        px[16, y] = ALLOY[2]
    for (cx, cy, sx, sy) in ((3, 3, 1, 1), (28, 3, -1, 1), (3, 28, 1, -1), (28, 28, -1, -1)):
        for i in range(5):                      # hazard corner chevrons
            for j in range(5 - i):
                px[cx + sx * j, cy + sy * i] = HAZ_Y if ((i + j) // 2) % 2 == 0 else HAZ_K
    rivets(img, ((9, 9), (22, 9), (9, 22), (22, 22)))   # inner rivet square
    px[15, 15] = A_LIGHT                        # hub bolt where the seams cross
    px[16, 16] = A_DARK
    tell(px, 15, 25)
    save(img, "block", "fission_casing")


def gen_control_rod_assembly():
    # side: three rod channels running top to bottom, silver-capped, ember at the tips
    img = machine_base("control_rod_assembly")
    px = img.load()
    for cx in (8, 15, 22):
        for y in range(4, 28):                  # channel groove
            px[cx - 2, y] = A_DARK
            px[cx + 3, y] = ALLOY[3]
            for x in range(cx - 1, cx + 3):
                px[x, y] = (12, 16, 20, 255) if x in (cx - 1, cx + 2) else (18, 22, 26, 255)
        for (cy0, cy1) in ((5, 7), (24, 26)):   # silver caps
            for y in range(cy0, cy1 + 1):
                for x in range(cx - 1, cx + 3):
                    px[x, y] = SILVER[0] if y > cy0 else SILVER[2]
            for x in range(cx - 1, cx + 3):
                px[x, cy1] = SILVER[1]
        for x in range(cx, cx + 2):             # ember at the rod tip
            px[x, 8] = P_EMBER
            px[x, 23] = P_EMBER
        px[cx, 8] = P_AMBER
    for x in range(4, 28):                      # yoke bar across the rods
        px[x, 15] = A_LIGHT if x % 3 else ALLOY[3]
        px[x, 16] = A_DARK
    tell(px, 26, 11, vertical=True)
    save(img, "block", "control_rod_assembly")

    # top: 3x3 rod-port grid — dark bores with silver collars, centre bore amber-lit
    img = machine_base("control_rod_assembly_top")
    px = img.load()
    recess(px, 4, 4, 27, 27, (16, 20, 24, 255))
    for gy in (8, 15, 22):
        for gx in (8, 15, 22):
            for yy in range(gy - 2, gy + 4):
                for xx in range(gx - 2, gx + 4):
                    d = math.hypot(xx - gx - 0.5, yy - gy - 0.5)
                    if d <= 1.6:
                        px[xx, yy] = (8, 11, 14, 255)
                    elif d <= 2.8:
                        px[xx, yy] = SILVER[1] if yy > gy else SILVER[0]
            if (gx, gy) == (15, 15):
                px[15, 15] = P_AMBER
                px[16, 15] = P_HOT
                px[15, 16] = P_EMBER
                px[16, 16] = P_AMBER
    tell(px, 5, 15, vertical=True)
    save(img, "block", "control_rod_assembly_top")


# ---------------- batteries ----------------

TIERS = ("basic", "advanced", "elite")


def _tier_ticks(px, tier, x, y):
    """1/2/3 amber tier ticks stacked at (x, y) — the only tier difference a player must read."""
    for k in range(tier + 1):
        px[x, y - k * 3] = P_AMBER
        px[x + 1, y - k * 3] = P_AMBER
        px[x, y - k * 3 + 1] = A_DARK
        px[x + 1, y - k * 3 + 1] = A_DARK


def _tier_trim(px, tier):
    """Advanced gets ember conduit ridges, elite adds a white-gold trim line inside the bevel."""
    if tier >= 1:
        for cx in (3, 28):
            for y in range(5, 27):
                px[cx, y] = P_EMBER if y % 4 < 2 else _mix(P_EMBER, P_AMBER, 0.6)
    if tier >= 2:
        for i in range(4, 28):
            px[i, 2] = _mix(P_GLOW, ALLOY[3], 0.55) if i % 2 == 0 else ALLOY[3]
            px[i, 29] = _mix(P_GLOW, A_DARK, 0.6) if i % 2 == 0 else A_DARK


def gen_battery_cell(tier):
    name = "battery_cell_%s" % TIERS[tier]

    # side: stacked cell casing with cooling bands + terminal cap + tier ticks
    img = machine_base(name + "_side")
    px = img.load()
    _tier_trim(px, tier)
    for i, ry in enumerate((6, 14, 22)):
        recess(px, 6, ry, 25, ry + 5, (10, 14, 18, 255))
        for y in range(ry + 1, ry + 5):
            for x in range(7, 24):
                px[x, y] = ALLOY[1] if (x + y) % 2 else ALLOY[2]
            px[24, y] = SILVER[2]               # the terminal cap
            px[25, y] = SILVER[1]
        for x in range(8, 23, 4):               # cooling band slots
            px[x, ry + 3] = A_DARK
            px[x + 1, ry + 3] = A_DARK
        px[9 + i * 4, ry + 2] = P_EMBER         # a cell-status pip per row
    _tier_ticks(px, tier, 25, 25)
    tell(px, 8, 4)
    save(img, "block", name + "_side")

    # top: twin terminal posts with amber busbars between them + tier ticks
    img = machine_base(name + "_top")
    px = img.load()
    _tier_trim(px, tier)
    for tx in (7, 24):                          # the terminal posts
        for y in range(11, 20):
            for x in range(tx - 3, tx + 4):
                px[x, y] = ALLOY[2] if (x + y) % 2 else ALLOY[0]
        for x in range(tx - 3, tx + 4):
            px[x, 11] = A_LIGHT
            px[x, 19] = A_DARK
        px[tx, 15] = SILVER[2]
    for by in (11, 15, 19):                     # the busbars
        for x in range(11, 21):
            px[x, by] = P_EMBER if (x // 2) % 2 == 0 else P_AMBER
            px[x, by + 1] = A_DARK
    for x in (12, 16, 20):
        px[x, 15] = P_HOT
    _tier_ticks(px, tier, 25, 26)
    tell(px, 4, 5)
    save(img, "block", name + "_top")

    # front_0..4: recessed charge window with four stacked segments; lit count == level, colour
    # green (full) -> amber (mid) -> red (low). Static: the CH_* hues sit outside the pulse window.
    for level in range(5):
        img = machine_base("%s_front_%d" % (name, level))
        px = img.load()
        _tier_trim(px, tier)
        recess(px, 9, 5, 22, 26, (8, 11, 14, 255))
        if level >= 3:
            col = CH_GREEN
        elif level == 2:
            col = CH_AMBER
        else:
            col = CH_RED
        for seg in range(4):                    # segment 0 is the bottom one
            sy = 22 - seg * 5
            lit = seg < level
            for y in range(sy, sy + 4):
                for x in range(10, 22):
                    if lit:
                        px[x, y] = _mix(col, P_GLOW, 0.35) if (y == sy and x % 3 == 0) else col
                    else:
                        px[x, y] = CH_OFF if (x + y) % 2 else (10, 13, 16, 255)
            for x in range(10, 22):
                px[x, sy + 4] = (6, 9, 12, 255)
        if level == 0:                          # an "empty" pip so a dead cell still reads
            px[15, 24] = CH_RED
            px[16, 24] = CH_RED
        led(px, 5, 7, P_AMBER if level else P_EMBER)    # the master charge LED
        led(px, 25, 7, P_HOT if level == 4 else P_EMBER)
        _tier_ticks(px, tier, 25, 26)
        tell(px, 5, 26)
        save(img, "block", "%s_front_%d" % (name, level))


def gen_battery_cell_bar():
    # BER charge strip: a horizontal red -> amber -> green gradient bar on transparent, framed.
    # The renderer crops the u-range to the charge fraction, so LEFT is empty and RIGHT is full.
    img = new_img()
    px = img.load()
    for x in range(S):
        t = x / (S - 1)
        col = _mix(CH_RED, CH_AMBER, t * 2) if t < 0.5 else _mix(CH_AMBER, CH_GREEN, (t - 0.5) * 2)
        for y in range(11, 21):
            if y in (11, 20):
                px[x, y] = A_DARK
            elif y == 12:
                px[x, y] = _mix(col, P_GLOW, 0.45)
            elif y >= 18:
                px[x, y] = _mix(col, A_DARK, 0.4)
            else:
                px[x, y] = col
    save(img, "block", "battery_cell_bar")


def gen_battery_bank_controller():
    # side: a conduit run across the housing with junction pips and a pair of link LEDs
    img = machine_base("battery_bank_controller_side")
    px = img.load()
    for x in range(3, 29):                      # the conduit run
        px[x, 14] = A_DARK
        px[x, 15] = P_EMBER if x % 4 < 2 else _mix(P_EMBER, P_AMBER, 0.6)
        px[x, 16] = A_DARK
    for x in (8, 16, 24):                       # conduit junction pips
        px[x, 15] = P_AMBER
    for x in (6, 15, 24):                       # conduit clamps
        px[x, 13] = A_LIGHT
        px[x, 17] = A_DARK
    for y in (22, 24, 26):                      # vent slats low on the housing
        for x in range(8, 24):
            px[x, y] = A_DARK
    led(px, 8, 6, P_AMBER)
    led(px, 22, 6, P_HOT)
    tell(px, 15, 6)
    save(img, "block", "battery_bank_controller_side")

    # top: dispatch lines fanning out from the hub LED
    img = machine_base("battery_bank_controller_top")
    px = img.load()
    for k in range(8):
        ang = k * math.pi / 4
        for r in range(5, 13):
            px[int(round(15.5 + r * math.cos(ang))),
               int(round(15.5 + r * math.sin(ang)))] = P_AMBER if r % 3 else P_EMBER
        px[int(round(15.5 + 12.0 * math.cos(ang))),
           int(round(15.5 + 12.0 * math.sin(ang)))] = P_HOT
    for y in range(11, 21):                     # the hub plate
        for x in range(11, 21):
            if math.hypot(x - 15.5, y - 15.5) <= 4.0:
                px[x, y] = A_DARK if (x + y) % 2 else ALLOY[2]
    led(px, 15, 15, P_HOT, P_GLOW)
    tell(px, 4, 15, vertical=True)
    save(img, "block", "battery_bank_controller_top")

    # front: the bank screen — a stepped charge bar-graph under a hot threshold rule (PULSE)
    img = machine_base("battery_bank_controller_front")
    px = img.load()
    recess(px, 4, 5, 27, 26, (8, 11, 14, 255))
    for i, h in enumerate((5, 9, 14, 11, 17, 13, 8)):
        bx = 6 + i * 3
        for y in range(25 - h, 25):
            c = P_HOT if y < 14 else P_AMBER
            px[bx, y] = c
            px[bx + 1, y] = c
    for x in range(5, 27):                      # the threshold rule across the graph
        px[x, 14] = P_GLOW if x % 3 else P_EMBER
    for x in range(6, 27):                      # graph baseline
        px[x, 25] = A_DARK
    tell(px, 24, 7)
    save(img, "block", "battery_bank_controller_front")


# ---------------- beam network ----------------

def _pylon_side(name, rings, tip=P_HOT):
    """A slim pylon carrying stacked emitter rings (wireless_node recipe, amber)."""
    img = machine_base(name)
    px = img.load()
    for y in range(4, 28):                      # the pylon
        for x in range(13, 19):
            px[x, y] = ALLOY[3] if (x + y) % 3 else ALLOY[1]
        px[13, y] = A_LIGHT
        px[18, y] = A_DARK
    for ry in rings:                            # emitter rings (P_* only)
        for x in range(6, 26):
            px[x, ry] = P_EMBER
            px[x, ry + 1] = P_AMBER if (x // 2) % 2 == 0 else P_EMBER
            px[x, ry + 2] = P_EMBER
        px[6, ry + 1] = A_LIGHT
        px[25, ry + 1] = A_DARK
        px[15, ry + 1] = P_HOT
        px[16, ry + 1] = P_HOT
    px[15, 5] = tip                             # mast tip pip
    return img


def _aperture_top(name, lit):
    """Dish aperture with four alignment ticks; `lit` picks a hot or ember mouth."""
    img = machine_base(name)
    px = img.load()

    def paint(d, ang, x, y):
        if d <= 2.2:
            if lit:
                return P_GLOW if d <= 1.0 else P_HOT
            return P_AMBER if d <= 1.0 else P_EMBER
        if d <= 5.0:
            return P_AMBER if (lit and (x + y) % 3) else P_EMBER
        if d <= 7.0:
            return A_DARK
        if d <= 9.4:
            return ALLOY[3] if (x + y) % 2 == 0 else ALLOY[1]
        return None
    radial(px, paint)
    for (tx, ty) in ((15, 4), (15, 27), (4, 15), (27, 15)):   # alignment ticks
        px[tx, ty] = A_LIGHT
        px[tx + 1, ty] = A_LIGHT
    tell(px, 15, 27)
    return img


def _ring_front(name, lit, core=True):
    """Concentric transmission rings around a beacon core; unlinked faces stay on ember only."""
    img = machine_base(name)
    px = img.load()
    recess(px, 4, 4, 27, 27, (10, 13, 16, 255))

    def paint(d, ang, x, y):
        if d > 10.8 or d < 0 or x < 5 or x > 26 or y < 5 or y > 26:
            return None
        if d <= 1.6 and core:
            return P_GLOW if lit else P_AMBER
        if lit:
            return (P_HOT, P_AMBER, P_EMBER)[int(d) % 3]
        return P_EMBER if int(d) % 3 == 2 else _mix(P_EMBER, A_DARK, 0.5)
    radial(px, paint)
    for k in range(4):                          # antenna spurs breaking the rings
        ang = k * math.pi / 2 + math.pi / 4
        for r in (8, 9, 10):
            px[int(round(15.5 + r * math.cos(ang))),
               int(round(15.5 + r * math.sin(ang)))] = A_DARK
    tell(px, 5, 26)
    return img


def gen_beam_transmitter():
    img = _pylon_side("beam_transmitter", (10, 20))
    tell(img.load(), 26, 6, vertical=True)
    save(img, "block", "beam_transmitter")
    save(_aperture_top("beam_transmitter_top", True), "block", "beam_transmitter_top")
    save(_ring_front("beam_transmitter_front", False), "block", "beam_transmitter_front")
    save(_ring_front("beam_transmitter_linked_front", True), "block",
         "beam_transmitter_linked_front")


def gen_beam_receiver():
    # side: collector housing with heat-sink fins and a single catch ring
    img = _pylon_side("beam_receiver", (15,), tip=P_AMBER)
    px = img.load()
    for x in range(4, 12, 2):                   # heat-sink fins either side of the pylon
        for y in range(6, 27):
            px[x, y] = A_DARK
            px[x + 1, y] = ALLOY[3]
            px[31 - x, y] = A_DARK
            px[30 - x, y] = ALLOY[3]
    for x in range(6, 26):                      # (re-lay the catch ring over the fins)
        px[x, 15] = P_EMBER
        px[x, 16] = P_AMBER if (x // 2) % 2 == 0 else P_EMBER
        px[x, 17] = P_EMBER
    tell(px, 26, 6, vertical=True)
    save(img, "block", "beam_receiver")
    save(_aperture_top("beam_receiver_top", False), "block", "beam_receiver_top")

    # front: the catcher dish — a bowl of rings drawing inward to an ember-warm collector
    img = machine_base("beam_receiver_front")
    px = img.load()
    recess(px, 4, 4, 27, 27, (10, 13, 16, 255))

    def bowl(d, ang, x, y):
        if d > 10.8:
            return None
        if d <= 2.4:
            return P_HOT if d <= 1.2 else P_AMBER
        if d <= 4.0:
            return P_EMBER
        ring = int(d) % 2
        return (ALLOY[3] if ring else A_DARK) if (x + y) % 2 else (ALLOY[1] if ring else A_DARK)
    radial(px, bowl)
    for k in range(6):                          # dish struts
        ang = k * math.pi / 3
        for r in range(5, 11):
            px[int(round(15.5 + r * math.cos(ang))),
               int(round(15.5 + r * math.sin(ang)))] = A_LIGHT if r % 2 else ALLOY[3]
    tell(px, 5, 26)
    save(img, "block", "beam_receiver_front")


def gen_beam_relay():
    img = _pylon_side("beam_relay", (8, 15, 22))
    tell(img.load(), 26, 6, vertical=True)
    save(img, "block", "beam_relay")
    save(_aperture_top("beam_relay_top", True), "block", "beam_relay_top")

    # front: twin apertures (in + out) side by side; the linked face lights both
    for lit, name in ((False, "beam_relay_front"), (True, "beam_relay_linked_front")):
        img = machine_base(name)
        px = img.load()
        recess(px, 3, 7, 28, 24, (10, 13, 16, 255))
        for cx in (9.5, 21.5):
            def eye(d, ang, x, y, cx=cx):
                if d > 6.4:
                    return None
                if d <= 1.4:
                    return P_GLOW if lit else P_AMBER
                if lit:
                    return (P_HOT, P_AMBER, P_EMBER)[int(d) % 3]
                return P_EMBER if int(d) % 3 == 2 else _mix(P_EMBER, A_DARK, 0.5)
            radial(px, eye, cx, 15.5)
        for y in range(9, 23):                  # the bridge bar between the eyes
            px[15, y] = A_DARK if y % 2 else ALLOY[3]
            px[16, y] = A_DARK if y % 2 else ALLOY[3]
        px[15, 15] = P_HOT if lit else P_EMBER
        px[16, 16] = P_HOT if lit else P_EMBER
        tell(px, 15, 27)
        save(img, "block", name)


def gen_orbital_receiver():
    # side: heavy pedestal with a tall tracking mast and stacked cable clamps
    img = machine_base("orbital_receiver")
    px = img.load()
    for y in range(20, 28):                     # pedestal plinth
        for x in range(4, 28):
            px[x, y] = ALLOY[2] if (x + y) % 3 else A_DARK
    for x in range(4, 28):
        px[x, 20] = A_LIGHT
        px[x, 27] = A_DARK
    for y in range(4, 20):                      # the mast
        for x in range(14, 18):
            px[x, y] = ALLOY[3] if (x + y) % 2 else ALLOY[1]
        px[14, y] = A_LIGHT
        px[17, y] = A_DARK
    for y in (7, 12, 17):                       # cable clamps + ember cable
        px[13, y] = A_LIGHT
        px[18, y] = A_DARK
        px[12, y] = P_EMBER
        px[19, y] = P_EMBER
    for x in range(6, 26):                      # ember bus along the plinth
        px[x, 23] = P_EMBER if x % 4 < 2 else _mix(P_EMBER, P_AMBER, 0.6)
    led(px, 15, 4, P_HOT)
    tell(px, 8, 24)
    save(img, "block", "orbital_receiver")

    # top: the big dish bowl with a star-tracker ring and a hot feed horn
    img = machine_base("orbital_receiver_top")
    px = img.load()

    def dish(d, ang, x, y):
        if d > 13.5:
            return None
        if d <= 1.8:
            return P_GLOW if d <= 0.9 else P_HOT
        if d <= 3.5:
            return P_AMBER if (x + y) % 2 else P_EMBER
        if 11.5 <= d <= 13.5:
            seg = int((ang + math.pi) / (math.pi / 8)) % 2
            return ALLOY[3] if seg else A_DARK
        return _mix(A_DARK, ALLOY[3], 0.25 + 0.75 * (d / 11.5)) if (x + y) % 2 else \
            _mix(A_DARK, ALLOY[1], 0.25 + 0.75 * (d / 11.5))
    radial(px, dish)
    for k in range(4):                          # feed-horn struts
        ang = k * math.pi / 2 + math.pi / 4
        for r in range(4, 12):
            px[int(round(15.5 + r * math.cos(ang))),
               int(round(15.5 + r * math.sin(ang)))] = A_LIGHT if r % 2 else ALLOY[2]
    tell(px, 15, 29)
    save(img, "block", "orbital_receiver_top")

    # front: the uplink screen — an amber orbit arc over a dark sky with a lit satellite pip
    img = machine_base("orbital_receiver_front")
    px = img.load()
    recess(px, 4, 5, 27, 26, (8, 11, 14, 255))
    rng = rng_for("orbital_receiver_front_stars")
    for k in range(14):                         # faint stars
        px[rng.randrange(5, 27), rng.randrange(6, 26)] = ALLOY[3]
    for x in range(5, 27):                      # the orbit arc
        y = int(round(23 - 9.0 * math.sin(math.pi * (x - 4) / 23)))
        if 6 <= y <= 25:
            px[x, y] = P_AMBER if x % 3 else P_EMBER
    for x in range(5, 27):                      # horizon rule
        px[x, 25] = P_EMBER if x % 2 else A_DARK
    led(px, 15, 12, P_HOT, P_GLOW)              # the satellite
    tell(px, 24, 7)
    save(img, "block", "orbital_receiver_front")


def gen_beam_ber_sprites():
    # beam_dish — the BER dish disc on transparent: concentric alloy rings, hot emitter core.
    img = new_img()
    px = img.load()

    def disc(d, ang, x, y):
        if d > 15.2:
            return None
        if d <= 2.0:
            return P_GLOW if d <= 0.9 else P_HOT
        if d <= 4.0:
            return P_AMBER if (x + y) % 2 else P_EMBER
        ring = int(d - 4.0) % 3
        shade = 0.2 + 0.8 * (d / 15.2)
        if ring == 2:
            return A_DARK
        return _mix(A_DARK, ALLOY[3] if ring else A_LIGHT, shade)
    radial(px, disc)
    for k in range(8):                          # dish ribs
        ang = k * math.pi / 4
        for r in range(4, 15):
            px[int(round(15.5 + r * math.cos(ang))),
               int(round(15.5 + r * math.sin(ang)))] = ALLOY[1] if r % 2 else A_DARK
    for (rx, ry) in ((15, 1), (16, 1)):         # the cyan rim tick (the tell)
        px[rx, ry] = T_CYAN
    save(img, "block", "beam_dish")

    # beam_ray — frame 0 of the BER beam: a bright amber core running top-to-bottom with soft
    # (alpha) edges and a dash pattern so the SCROLL animator visibly travels it. Tileable in y.
    img = new_img()
    px = img.load()
    for y in range(S):
        dash = 0.75 + 0.25 * math.cos(2 * math.pi * y / 8.0)
        for x in range(S):
            dx = abs(x - 15.5)
            if dx > 9.5:
                continue
            g = max(0.0, 1.0 - dx / 9.5) * dash
            if g > 0.82:
                col = P_GLOW
            elif g > 0.6:
                col = P_HOT
            elif g > 0.32:
                col = P_AMBER
            else:
                col = P_EMBER
            px[x, y] = col[:3] + (int(30 + 225 * (g ** 1.4)),)
    save(img, "block", "beam_ray")


# ---------------- generators ----------------

def gen_radioisotope_generator():
    # side: canister with vertical heat fins, ember glow bleeding between them low down
    img = machine_base("radioisotope_generator")
    px = img.load()
    for x in range(4, 28):
        fin = (x - 4) % 4
        for y in range(5, 27):
            if fin == 0:
                px[x, y] = A_LIGHT if y < 8 else ALLOY[3]
            elif fin == 1:
                px[x, y] = ALLOY[1]
            elif fin == 2:
                px[x, y] = A_DARK
            else:
                px[x, y] = P_EMBER if y > 18 else (14, 18, 22, 255)
                if y > 24:
                    px[x, y] = _mix(P_EMBER, P_AMBER, 0.5)
    for x in range(4, 28):                      # fin caps
        px[x, 4] = A_LIGHT
        px[x, 27] = A_DARK
    tell(px, 15, 2)
    save(img, "block", "radioisotope_generator")

    # top: round cap carrying the radiation trefoil on a dark disc
    img = machine_base("radioisotope_generator_top")
    px = img.load()

    def cap(d, ang, x, y):
        if d > 11.0:
            return None
        if 10.0 <= d <= 11.0:
            return A_LIGHT if y < 15 else A_DARK
        if d <= 1.4:
            return HAZ_K
        if 2.2 <= d <= 8.5:
            a = (ang + math.pi / 2) % (2 * math.pi / 3)
            if a < math.pi / 3:
                return HAZ_Y
        return HAZ_K if (x + y) % 2 else (30, 30, 36, 255)
    radial(px, cap)
    tell(px, 15, 29)
    save(img, "block", "radioisotope_generator_top")

    # front: the pellet window — warm amber glow behind a grille (PULSE target)
    img = machine_base("radioisotope_generator_front")
    px = img.load()
    recess(px, 5, 5, 26, 26, (10, 13, 16, 255))

    def glow(d, ang, x, y):
        if d > 9.5:
            return None
        if d <= 2.2:
            return P_GLOW
        if d <= 4.5:
            return P_HOT
        if d <= 7.0:
            return P_AMBER if (x + y) % 3 else P_HOT
        return P_EMBER if (x * 2 + y) % 5 else P_AMBER
    radial(px, glow)
    for y in range(6, 26, 3):                   # grille bars over the window
        for x in range(6, 26):
            if math.hypot(x - 15.5, y - 15.5) <= 9.8:
                px[x, y] = A_DARK if x % 2 else ALLOY[2]
    for (bx, by) in ((6, 6), (25, 6), (6, 25), (25, 25)):
        px[bx, by] = A_LIGHT
    tell(px, 15, 28)
    save(img, "block", "radioisotope_generator_front")

    # rtg_fin — BER fin plate on transparent: a vertical alloy plate, ember-hot at its root
    # (bottom rows) fading to plain alloy at the tip.
    img = new_img()
    px = img.load()
    rng = rng_for("rtg_fin")
    for y in range(1, 31):
        heat = max(0.0, (y - 14) / 16.0)
        for x in range(10, 22):
            base = rng.choice(ALLOY)
            if x == 10:
                col = A_LIGHT
            elif x == 21:
                col = A_DARK
            else:
                col = _mix(base, P_AMBER if heat > 0.7 else P_EMBER, heat * 0.85)
            px[x, y] = col
    for x in range(10, 22):
        px[x, 1] = A_LIGHT
        px[x, 30] = _mix(P_AMBER, A_DARK, 0.3)
    for y in range(4, 28, 6):                   # rib grooves
        for x in range(11, 21):
            px[x, y] = _mix(px[x, y], A_DARK, 0.5)
    save(img, "block", "rtg_fin")


def gen_stirling_generator():
    # side: cylinder body — cold end with cooling fins (right), hot end glowing (left), piston seam
    img = machine_base("stirling_generator")
    px = img.load()
    for y in range(8, 24):                      # the cylinder
        for x in range(4, 28):
            v = math.sin(math.pi * (y - 7.5) / 16)
            px[x, y] = _mix(A_DARK, ALLOY[3], 0.2 + 0.8 * v) if (x + y) % 2 else \
                _mix(A_DARK, ALLOY[1], 0.2 + 0.8 * v)
    for x in range(4, 28):
        px[x, 8] = A_LIGHT
        px[x, 23] = A_DARK
    for x in range(17, 28, 2):                  # cooling fins on the cold end
        for y in range(9, 23):
            px[x, y] = A_DARK
    for x in range(5, 12):                      # the hot end
        for y in range(9, 23):
            h = 1.0 - (x - 5) / 7.0
            px[x, y] = _mix(px[x, y], P_AMBER if h > 0.7 else P_EMBER, h * 0.8)
    for y in range(11, 21):                     # hot-end cap seam
        px[12, y] = A_DARK
        px[13, y] = A_LIGHT
    for x in (8, 22):                           # mount bolts
        px[x, 5] = A_LIGHT
        px[x, 26] = A_LIGHT
    tell(px, 15, 5)
    save(img, "block", "stirling_generator")

    # top: bolted plate with the flywheel axle hub and a belt slot
    img = machine_base("stirling_generator_top")
    px = img.load()

    def hub(d, ang, x, y):
        if d > 7.0:
            return None
        if d <= 1.5:
            return A_LIGHT
        if d <= 3.2:
            return A_DARK if (x + y) % 2 else ALLOY[2]
        if d <= 5.5:
            return ALLOY[3] if (x + y) % 2 == 0 else ALLOY[1]
        return A_DARK
    radial(px, hub)
    for y in range(13, 19):                     # belt slot across to the wheel side
        for x in range(23, 29):
            px[x, y] = A_DARK if y in (13, 18) else (12, 16, 20, 255)
    for x in range(4, 10):                      # exhaust vent on the hot side
        for y in (11, 14, 17, 20):
            px[x, y] = P_EMBER if x % 2 else A_DARK
    tell(px, 15, 4)
    save(img, "block", "stirling_generator_top")

    # front: the firebox door — a grille over a flame glow, dogged shut (PULSE target)
    img = machine_base("stirling_generator_front")
    px = img.load()
    recess(px, 5, 7, 26, 26, (10, 13, 16, 255))
    for y in range(9, 25):                      # flame glow: hottest low and centred
        for x in range(7, 25):
            t = (1.0 - abs(x - 15.5) / 9.0) * (0.35 + 0.65 * (y - 9) / 15.0)
            if t > 0.78:
                px[x, y] = P_GLOW
            elif t > 0.56:
                px[x, y] = P_HOT
            elif t > 0.34:
                px[x, y] = P_AMBER
            elif t > 0.16:
                px[x, y] = P_EMBER
            else:
                px[x, y] = (12, 16, 20, 255)
    for y in range(9, 25, 3):                   # grille slats
        for x in range(7, 25):
            px[x, y] = A_DARK if x % 2 else ALLOY[2]
    for x in range(6, 26):                      # door frame
        px[x, 8] = A_LIGHT
        px[x, 25] = A_DARK
    for (bx, by) in ((6, 9), (25, 9), (6, 24), (25, 24)):   # door dogs
        px[bx, by] = A_LIGHT
    led(px, 8, 4, P_AMBER)
    tell(px, 22, 4)
    save(img, "block", "stirling_generator_front")

    # stirling_flywheel — BER spoked wheel on transparent: rim + six spokes + hub, one cyan
    # balance mark on the rim so the spin reads.
    img = new_img()
    px = img.load()

    def wheel(d, ang, x, y):
        if d > 15.2:
            return None
        if d >= 12.8:
            return A_LIGHT if (d < 13.6 and ang < 0) else (ALLOY[3] if (x + y) % 2 else ALLOY[1])
        if d <= 1.5:
            return A_LIGHT
        if d <= 3.6:
            return A_DARK if (x + y) % 2 else ALLOY[2]
        a = (ang % (math.pi / 3)) / (math.pi / 3)
        w = 0.16 - d * 0.004
        if a < w or a > 1.0 - w:
            return ALLOY[3] if a < w * 0.5 or a > 1.0 - w * 0.5 else ALLOY[1]
        return None
    radial(px, wheel)
    px[15, 2] = T_CYAN
    px[16, 2] = T_CYAN
    save(img, "block", "stirling_flywheel")


# ---------------- items ----------------

def _rod(name, shell, cap, x0=13, x1=18):
    """Vertical rod silhouette: noisy shell between silver caps, lit left edge, shaded right."""
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    for y in range(4, 28):
        for x in range(x0, x1 + 1):
            px[x, y] = rng.choice(shell)
    for (cy0, cy1) in ((4, 6), (25, 27)):       # end caps
        for y in range(cy0, cy1 + 1):
            for x in range(x0, x1 + 1):
                px[x, y] = cap[0]
        for x in range(x0, x1 + 1):
            px[x, cy0] = cap[2] if cy0 == 4 else cap[0]
            px[x, cy1] = cap[1]
    for y in range(4, 28):                      # shading
        px[x0, y] = _mix(px[x0, y], A_LIGHT, 0.35)
        px[x1, y] = _mix(px[x1, y], A_DARK, 0.55)
    return img


def gen_control_rod():
    img = _rod("control_rod", [A_DARK, ALLOY[2], ALLOY[0]], SILVER)
    px = img.load()
    for y in range(8, 24, 4):                   # absorber bands
        for x in range(13, 19):
            px[x, y] = _mix(A_DARK, A_LIGHT, 0.25)
    for x in range(14, 18):                     # the cyan tell band under the top cap
        px[x, 7] = T_CYAN
    px[13, 7] = A_DARK
    px[18, 7] = A_DARK
    save(img, "item", "control_rod")


def _fuel_rod(name, shell, window, frame):
    img = _rod(name, shell, SILVER)
    px = img.load()
    for wy in (8, 13, 18):                      # pellet windows
        for y in range(wy, wy + 4):
            for x in range(14, 18):
                px[x, y] = window[1] if (y == wy or y == wy + 3) else window[0]
        px[15, wy + 1] = window[2]
        px[16, wy + 2] = window[2]
        for y in range(wy, wy + 4):
            px[13, y] = frame
            px[18, y] = frame
    save(img, "item", name)


def gen_fuel_rod():
    _fuel_rod("fuel_rod", SILVER, (P_AMBER, P_EMBER, P_HOT), A_DARK)


def gen_spent_fuel_rod():
    _fuel_rod("spent_fuel_rod", SPENT, ((30, 26, 24, 255), (20, 18, 16, 255), (56, 34, 20, 255)),
              (36, 34, 32, 255))


def _pellet(name, ramp, glint, r=8.0):
    """Disc pellet: noisy fill, light upper-left rim, dark lower-right rim, one glint."""
    rng = rng_for(name)
    img = new_img()
    px = img.load()

    def disc(d, ang, x, y):
        if d > r:
            return None
        if d > r - 1.2:
            return ramp[2] if ang < -0.4 or ang > 2.7 else ramp[1]
        return rng.choice(ramp) if rng.random() < 0.7 else ramp[0]
    radial(px, disc)
    for y in range(int(15.5 - r), int(15.5 + r) + 1):    # a subtle chord across the disc
        x = int(round(15.5 + (y - 15.5) * 0.35))
        if math.hypot(x - 15.5, y - 15.5) <= r - 1.6:
            px[x, y] = _mix(px[x, y], A_DARK, 0.3)
    if glint:
        px[12, 12] = glint
        px[13, 12] = _mix(glint, ramp[2], 0.5)
        px[12, 13] = _mix(glint, ramp[2], 0.5)
    save(img, "item", name)


def gen_uranium_pellet():
    _pellet("uranium_pellet", URANIUM, P_HOT)


def gen_reprocessed_pellet():
    _pellet("reprocessed_pellet", REPROC, P_AMBER, r=7.5)


def _capsule(name, shell, window, lit):
    """Vertical capsule with rounded silver ends and a pellet window in the middle."""
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    for y in range(6, 26):
        for x in range(11, 21):
            edge = (y < 8 or y > 23) and (x < 13 or x > 18)
            if edge:
                continue
            px[x, y] = rng.choice(shell)
    for y in range(6, 26):
        if 8 <= y <= 23:
            px[11, y] = _mix(shell[2], A_LIGHT, 0.4)
            px[20, y] = _mix(shell[1], A_DARK, 0.5)
    for x in range(13, 19):
        px[x, 6] = shell[2]
        px[x, 25] = _mix(shell[1], A_DARK, 0.5)
    for y in range(11, 21):                     # the window
        for x in range(13, 19):
            d = math.hypot(x - 15.5, y - 15.5)
            if lit:
                px[x, y] = window[3] if d <= 1.3 else (window[2] if d <= 2.6 else
                                                        (window[1] if d <= 4.2 else window[0]))
            else:
                px[x, y] = window[1] if d <= 2.0 else window[0]
        px[12, y] = A_DARK
        px[19, y] = A_DARK
    for x in range(12, 20):
        px[x, 10] = A_DARK
        px[x, 21] = A_DARK
    save(img, "item", name)


def gen_isotope_pellet():
    _capsule("isotope_pellet", SILVER, POWER_RAMP, True)


def gen_spent_isotope_pellet():
    _capsule("spent_isotope_pellet", SPENT, ((18, 16, 14, 255), (44, 28, 16, 255)), False)


# ---------------- main ----------------

def check_coverage():
    """Flag any pre-existing PNG no painter owns — after a --force run NOTHING may be left on
    the old placeholder art, and every model-referenced name must resolve."""
    clean = True
    for folder, d in (("block", BLOCK_DIR), ("item", ITEM_DIR)):
        existing = {f for f in os.listdir(d) if f.endswith(".png")}
        orphans = sorted(existing - PAINTED[folder])
        if orphans:
            clean = False
            print("NOTICE: existing %s textures WITHOUT a painter (left untouched): %s"
                  % (folder, ", ".join(orphans)))
    if clean:
        print("coverage: every existing block/item PNG has a painter.")


def main():
    # fission
    gen_fission_core()
    gen_fission_casing()
    gen_control_rod_assembly()
    # batteries
    for tier in range(3):
        gen_battery_cell(tier)
    gen_battery_cell_bar()
    gen_battery_bank_controller()
    # beam network
    gen_beam_transmitter()
    gen_beam_receiver()
    gen_beam_relay()
    gen_orbital_receiver()
    gen_beam_ber_sprites()
    # generators
    gen_radioisotope_generator()
    gen_stirling_generator()
    # items
    gen_control_rod()
    gen_fuel_rod()
    gen_spent_fuel_rod()
    gen_uranium_pellet()
    gen_reprocessed_pellet()
    gen_isotope_pellet()
    gen_spent_isotope_pellet()
    check_coverage()


if __name__ == "__main__":
    main()

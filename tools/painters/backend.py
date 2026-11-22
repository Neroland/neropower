"""Back end and generation: Spent Fuel Rack, Vitrifier, Dry Cask, Hydrogen Fuel Cell, Betavoltaic
Cell, and the waste items (fission residue, vitrified waste, beta capsules).

Every block here is a multi-element model, so its textures are painted as VIEWS of the whole block:
``*_side`` / ``*_front`` are elevations (one texel = half a model unit, row 0 = the block's top) and
``*_top`` is the plan. The models leave UVs out, so each element face shows the part of the view it
covers. The models are written by this module too (``write_models``), from the same numbers the
views are painted with, so the two cannot drift apart.

The status socket is its own small element textured with ``backend_socket``; no view bakes a lamp.
"""
import json
import os

from PIL import Image, ImageDraw

from .kit import *  # noqa: F401,F403
from .kit import _mix

NS = "neropower"
GLASS = [(34, 42, 50, 255), (22, 28, 34, 255), (58, 72, 82, 255)]       # vitrified glass (alloy darks)
SLAG = [(38, 36, 36, 255), (26, 24, 26, 255), (60, 56, 54, 255)]        # fission residue
WAFER = [(20, 24, 30, 255), (30, 36, 44, 255)]                          # betavoltaic wafer
EDGE = _mix(A_LIGHT, ALLOY[1], 0.35)
DIM = _mix(A_DARK, ALLOY[2], 0.5)


def _plate(px, rng, x0, y0, x1, y1, pal=None, lit=True):
    """A noisy alloy panel with a one-pixel lit top/left and dark bottom/right edge."""
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


def _clear_base(name):
    """A dark, fully opaque tile: parts of a view no element shows still must not be transparent."""
    img = new_img()
    noise_fill(img, [ALLOY[2], ALLOY[0]], rng_for(name + "/base"))
    return img


# ---------------- shared ----------------

def gen_socket():
    """The lamp socket element's face: a dark well in a collar. The renderer draws the lamp over it."""
    img = new_img()
    px = img.load()
    collar = _mix(A_LIGHT, A_DARK, 0.5)
    rect(px, 0, 0, S - 1, S - 1, SOCKET)
    for i in range(S):
        for k in range(4):
            px[i, k] = px[k, i] = collar
    for i in range(S):
        for k in range(4):
            px[i, S - 1 - k] = px[S - 1 - k, i] = _mix(collar, A_DARK, 0.5)
    for i in range(4, S - 4):
        for k in range(4, 8):
            px[i, k] = SOCKET_SHADOW
            px[k, i] = SOCKET_SHADOW
    save(img, "block", "backend_socket")


# ---------------- Spent Fuel Rack (id spent_fuel_pool) ----------------
# tray y0..4, walls y4..12 (2 thick), cross dividers y4..11, bail posts y12..14, bail bar y14..16.

def _pool_side(name, alarm):
    rng = rng_for("spent_fuel_pool_side")
    img = _clear_base("spent_fuel_pool_side")
    px = img.load()
    _plate(px, rng, 0, 0, 31, 3, [ALLOY[3], ALLOY[1]])              # bail bar
    for (a, b) in ((0, 3), (14, 17), (28, 31)):                     # bail posts (front and end-on)
        _plate(px, rng, a, 4, b, 7, [ALLOY[3], ALLOY[1]], lit=False)
        for y in range(4, 8):
            px[a, y] = EDGE
            px[b, y] = A_DARK
    _plate(px, rng, 0, 8, 31, 23)                                   # wall
    for x in (1, 2, 29, 30):                                        # corner posts read as thicker
        for y in range(9, 23):
            px[x, y] = ALLOY[3]
    # the rack motif: a 2x2 block of square sockets, 12x12 px
    for (sx, sy) in ((10, 10), (17, 10), (10, 17), (17, 17)):
        recess(px, sx, sy, sx + 4, sy + 4)
    _plate(px, rng, 0, 24, 31, 31, [ALLOY[0], ALLOY[2]])            # tray
    _bolt(px, 3, 25)
    _bolt(px, 27, 25)
    hazard_edge(px, lit=alarm, y0=28, y1=30)
    save(img, "block", name)


def gen_spent_fuel_pool():
    _pool_side("spent_fuel_pool_side", False)
    _pool_side("spent_fuel_pool_side_alarm", True)
    name = "spent_fuel_pool_top"
    rng = rng_for(name)
    img = _clear_base(name)
    px = img.load()
    _plate(px, rng, 0, 0, 31, 31)                                   # rim
    for i in range(4, 28):
        px[i, 3] = A_DARK
        px[3, i] = A_DARK
        px[i, 28] = EDGE
        px[28, i] = EDGE
    _plate(px, rng, 4, 4, 27, 27, [ALLOY[1], ALLOY[3]], lit=False)  # dividers (cells cut below)
    for (cx, cy) in ((4, 4), (18, 4), (4, 18), (18, 18)):           # four socket cells
        rect(px, cx, cy, cx + 9, cy + 9, SOCKET)
        for i in range(10):
            px[cx + i, cy] = SOCKET_SHADOW
            px[cx, cy + i] = SOCKET_SHADOW
            px[cx + i, cy + 9] = _mix(A_LIGHT, A_DARK, 0.7)
        for (dx, dy) in ((2, 2), (7, 2), (2, 7), (7, 7)):           # rod guide corners
            px[cx + dx, cy + dy] = DIM
    for i in range(4, 28):
        if not 14 <= i <= 17:
            px[i, 14] = EDGE
            px[i, 17] = A_DARK
            px[14, i] = EDGE
            px[17, i] = A_DARK
    rib(px, 5, 15, 12, 16, 3)                                       # family mark: rib rows
    tell(px, 15, 1)                                                 # on the rim (the bail hides none of it)
    for (bx, by) in ((1, 1), (29, 1), (1, 29), (29, 29)):
        _bolt(px, bx, by)
    save(img, "block", name)

    # Rod head (renderer sprite, tinted by cool step): a pale square cap with a lifting eye.
    name = "spent_fuel_pool_rod_head"
    img = new_img()
    px = img.load()
    cap = [(206, 208, 212, 255), (188, 190, 196, 255), (222, 224, 228, 255)]
    r2 = rng_for(name)
    for y in range(S):
        for x in range(S):
            px[x, y] = r2.choice(cap)
    for i in range(S):
        px[i, 0] = px[0, i] = (246, 246, 248, 255)
        px[i, 1] = px[1, i] = (234, 236, 238, 255)
    for i in range(S):
        px[i, S - 1] = px[S - 1, i] = (110, 112, 118, 255)
    for i in range(1, S - 1):
        px[i, S - 2] = px[S - 2, i] = (140, 142, 148, 255)
    rect(px, 5, 13, 26, 18, (240, 240, 242, 255))                   # lifting bar across the cap
    for i in range(5, 27):
        px[i, 12] = (250, 250, 252, 255)
        px[i, 19] = (120, 122, 128, 255)
    for (a, b) in ((7, 9), (22, 24)):                               # two grapple notches
        rect(px, a, 14, b, 17, (96, 98, 104, 255))
    save(img, "block", name)


# ---------------- Vitrifier ----------------
# body y0..9 with a mould slot x4..12, y2..7, z0..5; hopper steps y9..11 (x3..13), y11..13
# (x1.5..14.5) and an open rim y13..16.

def _vit_profile(px, rng):
    """The stepped hopper both elevations share."""
    _plate(px, rng, 0, 0, 31, 5, [ALLOY[3], ALLOY[1]])              # rim
    for x in range(2, 30, 4):
        px[x, 2] = A_DARK
        px[x, 3] = A_DARK
    _plate(px, rng, 3, 6, 28, 9, [ALLOY[1], ALLOY[0]])              # upper step
    _plate(px, rng, 6, 10, 25, 13, [ALLOY[0], ALLOY[2]])            # throat neck
    for x in range(6, 26):
        px[x, 10] = A_DARK                                          # shadow under the step
    for x in range(3, 29):
        px[x, 6] = A_DARK
    _plate(px, rng, 0, 14, 31, 31)                                  # body


def gen_vitrifier():
    name = "vitrifier_front"
    rng = rng_for(name)
    img = _clear_base(name)
    px = img.load()
    _vit_profile(px, rng)
    recess(px, 8, 18, 23, 27)                                       # mould slot (cavity back wall)
    for x in range(9, 23):                                          # heater strip under the lintel
        px[x, 19] = P_AMBER if x % 3 else P_HOT
    for x in range(8, 24):
        px[x, 17] = A_LIGHT                                         # lintel lip
    for y in range(20, 27):                                         # mould side rails
        px[10, y] = DIM
        px[21, y] = DIM
    _bolt(px, 3, 27)
    _bolt(px, 27, 27)
    save(img, "block", name)

    name = "vitrifier_side"
    rng = rng_for(name)
    img = _clear_base(name)
    px = img.load()
    _vit_profile(px, rng)
    louvre(px, 5, 17, 16, 25)                                       # cooling vent
    flange(px, 19, 17, 27, 25)                                      # item port
    tell(px, 22, 28)
    _bolt(px, 3, 27)
    save(img, "block", name)

    name = "vitrifier_top"
    rng = rng_for(name)
    img = _clear_base(name)
    px = img.load()
    _plate(px, rng, 0, 0, 31, 31, [ALLOY[3], ALLOY[1]])             # rim
    rect(px, 4, 4, 27, 27, SOCKET)                                  # throat floor
    for i in range(4, 28):
        px[i, 4] = SOCKET_SHADOW
        px[4, i] = SOCKET_SHADOW
        px[i, 27] = _mix(A_LIGHT, A_DARK, 0.7)
        px[27, i] = _mix(A_LIGHT, A_DARK, 0.7)
    for k in range(6):                                              # hopper slope creases
        px[5 + k, 5 + k] = DIM
        px[26 - k, 5 + k] = DIM
        px[5 + k, 26 - k] = DIM
        px[26 - k, 26 - k] = DIM
    rect(px, 11, 14, 20, 17, SOCKET_SHADOW)                         # pour slot (a slit, not a hole)
    rib(px, 1, 10, 2, 21, 3, vertical=False)                        # family mark on the rim
    for (bx, by) in ((1, 1), (29, 1), (1, 29), (29, 29)):
        _bolt(px, bx, by)
    save(img, "block", name)

    # Renderer sprites. The melt is light the animator breathes; the log is pale glass the
    # renderer tints from hot amber down to cold dark glass.
    name = "vitrifier_melt"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            r = rng.random()
            px[x, y] = P_AMBER if r < 0.62 else (P_HOT if r < 0.92 else P_GLOW)
    for i in range(S):
        px[i, 0] = px[0, i] = px[i, S - 1] = px[S - 1, i] = _mix(P_AMBER, P_EMBER, 0.6)
    rect(px, 10, 14, 21, 17, _mix(P_AMBER, P_EMBER, 0.75))          # the pour slot shows through
    save(img, "block", name)

    name = "vitrifier_log"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    pale = [(196, 200, 204, 255), (180, 184, 190, 255), (210, 214, 218, 255)]
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice(pale)
    for x in range(6, 26, 4):                                       # faint casting seams
        for y in range(12, 28):
            px[x, y] = (164, 168, 174, 255)
    for i in range(S):
        px[i, 0] = px[0, i] = (250, 250, 252, 255)
    for i in range(S):
        px[i, S - 1] = px[S - 1, i] = (104, 108, 114, 255)
    for i in range(1, S - 1):
        px[i, S - 2] = px[S - 2, i] = (140, 144, 150, 255)
    for k in range(9):                                              # one highlight, top left
        px[4 + k, 5] = (255, 255, 255, 255)
        px[4, 5 + min(k, 5)] = (255, 255, 255, 255)
    save(img, "block", name)


# ---------------- Dry Cask ----------------
# foot y0..2 (x1..15), body y2..13 (10 wide), lid plate y13..14, cap y14..15, lugs y11..13.

# Leaking: the lid plate and cap stand one unit higher on a short neck, so the seal is visibly open.

def _cask_side(name, state):
    """``state``: ``idle`` (sealed, cold), ``working`` (sealed, warm: the rib vents glow) or
    ``leaking`` (lid lifted off its seat, seal band broken, lime seep down the body, vents blown
    open, hazard stripes lit on the foot). The three differ in shape, not only colour."""
    rng = rng_for("dry_cask_side")
    img = _clear_base("dry_cask_side")
    px = img.load()
    _plate(px, rng, 8, 2, 23, 3, [ALLOY[3], ALLOY[1]], lit=False)   # cap
    _plate(px, rng, 5, 4, 26, 5, [ALLOY[3], ALLOY[1]], lit=False)   # lid plate
    for x in range(8, 24):
        px[x, 2] = EDGE
    for x in range(5, 27):
        px[x, 5] = A_DARK
    for x in (6, 11, 16, 21, 25):
        px[x, 4] = A_LIGHT                                          # lid bolts, edge on
    can(px, 6, 6, 25, 27)                                           # body
    for y in (10, 15, 20, 24):                                      # cooling ribs round the body
        for x in range(6, 26):
            px[x, y] = _mix(px[x, y], A_LIGHT, 0.35)
            px[x, y + 1] = _mix(px[x, y + 1], A_DARK, 0.55)
    for x in range(6, 26):                                          # one cindersteel seal band
        px[x, 7] = CINDER[2] if x < 12 else CINDER[0]
        px[x, 8] = CINDER[1]
    for y in range(6, 28):                                          # the upright rib (centre)
        px[14, y] = A_LIGHT
        px[15, y] = ALLOY[3]
        px[16, y] = ALLOY[3]
        px[17, y] = A_DARK
    for y in range(12, 24, 4):                                      # rib vents
        for yy in (y, y + 1):
            px[15, yy] = SOCKET_SHADOW
            px[16, yy] = SOCKET_SHADOW
    for (a, b) in ((2, 5), (26, 29)):                               # lifting lugs
        _plate(px, rng, a, 6, b, 9, [ALLOY[3], ALLOY[1]])
        px[a + 1, 7] = SOCKET_SHADOW
        px[a + 2, 7] = SOCKET_SHADOW
    _plate(px, rng, 2, 28, 29, 31, [ALLOY[0], ALLOY[2]])            # foot
    if state == "working":
        for y in range(12, 24, 4):                                  # heat seen through the rib vents
            for yy in (y, y + 1):
                px[15, yy] = P_HOT
                px[16, yy] = P_AMBER
        for x in list(range(7, 14)) + list(range(18, 25)):          # a warm line under the seal band
            px[x, 9] = P_EMBER
    elif state == "leaking":
        fresh = _clear_base("dry_cask_side").load()
        for y in (0, 1, 2, 3):                                      # lid plate and cap, one unit up
            for x in range(S):
                px[x, y] = px[x, y + 2]
        for y in (4, 5):                                            # the gap, and the neck in it
            for x in range(S):
                px[x, y] = fresh[x, y]
            for x in range(10, 22):
                px[x, y] = SOCKET_SHADOW
        for x in range(10, 22, 2):
            px[x, 5] = RAD_LIME
            px[x + 1, 5] = RAD_DEEP
        for (x0, x1, drop) in ((10, 12, 17), (19, 21, 21)):         # seal band broken in two places
            for x in range(x0, x1 + 1):
                px[x, 7] = SOCKET_SHADOW
                px[x, 8] = SOCKET_SHADOW
            for y in range(7, drop):                                # seep running down from the break
                px[x0 + 1, y] = RAD_LIME if y < drop - 3 else RAD_DEEP
                if y % 3 == 0:
                    px[x0, y] = RAD_DEEP
        for y in range(12, 24, 4):                                  # rib vents blown open
            for yy in (y - 1, y, y + 1, y + 2):
                px[14, yy] = RAD_DEEP
                px[17, yy] = RAD_DEEP
                px[15, yy] = RAD_LIME if yy in (y, y + 1) else RAD_DEEP
                px[16, yy] = RAD_LIME if yy in (y, y + 1) else RAD_DEEP
        hazard_edge(px, lit=True, y0=28, y1=30)                     # the foot's hazard stripes
    save(img, "block", name)


def _cask_top(name, leaking):
    rng = rng_for("dry_cask_top")
    img = _clear_base("dry_cask_top")
    px = img.load()
    _plate(px, rng, 2, 2, 29, 29, [ALLOY[0], ALLOY[2]])             # foot
    _plate(px, rng, 5, 5, 26, 26, [ALLOY[1], ALLOY[3]])             # lid plate
    for (bx, by) in ((6, 6), (15, 6), (24, 6), (6, 15), (24, 15), (6, 24), (15, 24), (24, 24)):
        _bolt(px, bx, by)                                           # eight lid bolts
    _plate(px, rng, 8, 8, 23, 23, [ALLOY[3], ALLOY[1]])             # cap
    rib(px, 10, 10, 20, 15, 3)                                      # family mark
    tell(px, 15, 19)
    for (a, b) in ((2, 5), (26, 29)):                               # lugs from above
        _plate(px, rng, a, 14, b, 17, [ALLOY[3], ALLOY[1]])
    for y in (4, 27):                                               # rib tops (north / south)
        for x in range(14, 18):
            px[x, y] = ALLOY[3]
    if leaking:
        for (bx, by) in ((6, 6), (24, 6), (6, 24), (24, 24)):       # four corner bolts gone
            rect(px, bx, by, bx + 1, by + 1, SOCKET_SHADOW)
            px[bx, by] = RAD_LIME
        for x in range(9, 23):                                      # seep along two lid edges only
            for y in (6, 25):
                if x not in (15, 16):
                    px[x, y] = RAD_LIME if x % 3 else RAD_DEEP
        for y in (3, 28):                                           # blown rib vents from above
            for x in (15, 16):
                px[x, y] = RAD_LIME
    save(img, "block", name)


def gen_dry_cask():
    _cask_side("dry_cask_side", "idle")
    _cask_side("dry_cask_side_working", "working")
    _cask_side("dry_cask_side_leaking", "leaking")
    _cask_top("dry_cask_top", False)
    _cask_top("dry_cask_top_leaking", True)


# ---------------- Hydrogen Fuel Cell (id fuel_cell) ----------------
# end plates x0..3 and x13..16 (y1..15); nine plates between: five raised (1 unit thick) and four
# recessed on the core; tie rods on top; two gas nipples on the back.

RAISED = (7, 11, 15, 19, 23)     # left texel of each raised plate (front view)
RECESSED = (9, 13, 17, 21)


def gen_fuel_cell():
    name = "fuel_cell_front"
    rng = rng_for(name)
    img = _clear_base(name)
    px = img.load()
    for (a, b) in ((0, 5), (26, 31)):                               # end plates
        _plate(px, rng, a, 2, b, 29, [ALLOY[3], ALLOY[1]])
        for y in (4, 26):
            _bolt(px, a + 2, y)
    rect(px, 6, 2, 25, 29, A_DARK)                                  # the gaps between plates
    for x in RAISED:
        for y in range(4, 28):
            px[x, y] = A_LIGHT if y > 4 else EDGE
            px[x + 1, y] = ALLOY[3]
        px[x, 27] = ALLOY[1]
        px[x + 1, 27] = A_DARK
    for x in RECESSED:
        for y in range(6, 26):
            px[x, y] = ALLOY[1]
            px[x + 1, y] = ALLOY[2]
        px[x, 6] = EDGE
    for x in range(6, 26):                                          # tie rods, top and bottom
        px[x, 2] = ALLOY[3]
        px[x, 3] = ALLOY[2]
        px[x, 28] = ALLOY[3]
        px[x, 29] = ALLOY[2]
    save(img, "block", name)

    name = "fuel_cell_side"
    rng = rng_for(name)
    img = _clear_base(name)
    px = img.load()
    _plate(px, rng, 2, 2, 29, 29, [ALLOY[3], ALLOY[1]])             # the clamping end plate
    for (bx, by) in ((4, 4), (26, 4), (4, 26), (26, 26)):           # tie-rod nuts
        rect(px, bx - 1, by - 1, bx + 2, by + 2, A_DARK)
        _bolt(px, bx, by)
    rib(px, 9, 7, 22, 11, 3)                                        # stiffening ribs
    flange(px, 11, 14, 20, 23)                                      # gas / water service port
    tell(px, 15, 26)
    save(img, "block", name)

    # The plate wave (renderer sprite, tinted): a solid light bar with a dithered fringe.
    name = "fuel_cell_plate"
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            d = abs(x - 15.5)
            if d < 7:
                px[x, y] = (255, 255, 255, 255)
            elif d < 12 and (x + y) % 2 == 0:
                px[x, y] = (226, 226, 226, 255)
    save(img, "block", name)


# ---------------- Betavoltaic Cell ----------------
# half height: base y0..2, core y2..6 (x3..13), four fins a side y2..5, window frame y6..8 (x5..11).

# Spent: the capsule's pull tab stands up out of the window (x7..9, y8..9.5, z7..9).

def _beta_side(name, spent):
    rng = rng_for("betavoltaic_cell_side")
    img = _clear_base("betavoltaic_cell_side")
    px = img.load()
    _plate(px, rng, 10, 16, 21, 19, [ALLOY[3], ALLOY[1]])           # window frame
    _plate(px, rng, 6, 20, 25, 27)                                  # core
    for y in range(22, 28):                                         # fin rows (front and end-on)
        for x in list(range(0, 6)) + list(range(26, 32)):
            px[x, y] = ALLOY[1] if y % 2 else ALLOY[3]
        for x in (3, 11, 19, 27):
            px[x, y] = A_LIGHT
            px[x + 1, y] = ALLOY[3]
            px[x + 2, y] = A_DARK
    for x in list(range(0, 6)) + list(range(26, 32)):
        px[x, 22] = EDGE
    _plate(px, rng, 0, 28, 31, 31, [ALLOY[0], ALLOY[2]])            # base
    _bolt(px, 2, 29)
    _bolt(px, 28, 29)
    if spent:
        for y in (13, 14, 15):                                      # the raised tab, edge on
            px[14, y] = SPENT[2]
            px[15, y] = SPENT[0]
            px[16, y] = SPENT[0]
            px[17, y] = SPENT[1]
        for x in range(14, 18):
            px[x, 13] = SPENT[2]
    save(img, "block", name)


def _beta_top(name, state):
    """``working``: one steady amber row under the glass. ``idle``: the same wafer, dark.
    ``spent``: dark, and the spent capsule's square pull tab stands up in the window."""
    rng = rng_for("betavoltaic_cell_top")
    img = _clear_base("betavoltaic_cell_top")
    px = img.load()
    _plate(px, rng, 0, 0, 31, 31, [ALLOY[0], ALLOY[2]])             # base
    for z in (3, 11, 19, 27):                                       # fins from above, both flanks
        for x in list(range(0, 6)) + list(range(26, 32)):
            px[x, z] = A_LIGHT
            px[x, z + 1] = ALLOY[3]
            px[x, z + 2] = A_DARK
    _plate(px, rng, 6, 6, 25, 25)                                   # core top
    rib(px, 7, 7, 9, 24, 3, vertical=False)                         # family mark (left flank)
    _plate(px, rng, 10, 10, 21, 21, [ALLOY[3], ALLOY[1]])           # window frame
    rect(px, 12, 12, 19, 19, WAFER[0])                              # the wafer under glass
    for i in range(12, 20):
        px[i, 12] = SOCKET_SHADOW
        px[12, i] = SOCKET_SHADOW
    for i in range(13, 20, 2):
        px[i, 18] = WAFER[1]
        px[i, 14] = WAFER[1]
    if state == "working":
        for x in range(13, 19):                                     # one steady amber row: never pulsed
            px[x, 16] = P_AMBER
        px[15, 16] = P_HOT
    elif state == "spent":
        rect(px, 14, 14, 17, 17, SPENT[0])                          # the tab, from above
        for i in range(14, 18):
            px[i, 14] = SPENT[2]
            px[14, i] = SPENT[2]
            px[i, 17] = SPENT[1]
            px[17, i] = SPENT[1]
        px[15, 15] = A_DARK                                         # its pull hole
        px[16, 16] = A_DARK
        for i in range(15, 19):                                     # and its shadow on the wafer
            px[i, 18] = SOCKET_SHADOW
            px[18, i] = SOCKET_SHADOW
    px[19, 13] = _mix(A_LIGHT, WAFER[0], 0.3)                       # glass glint
    tell(px, 23, 14, vertical=True)
    save(img, "block", name)


def gen_betavoltaic_cell():
    _beta_side("betavoltaic_cell_side", False)
    _beta_side("betavoltaic_cell_side_spent", True)
    _beta_top("betavoltaic_cell_top", "working")
    _beta_top("betavoltaic_cell_top_idle", "idle")
    _beta_top("betavoltaic_cell_top_spent", "spent")


# ---------------- items ----------------

def _outline(px, pts, col):
    for (ax, ay), (bx, by) in zip(pts, pts[1:] + pts[:1]):
        line(px, ax, ay, bx, by, col)


def _fill_poly(img, pts, pal, rng):
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    px, mp = img.load(), mask.load()
    for y in range(S):
        for x in range(S):
            if mp[x, y]:
                px[x, y] = rng.choice(pal)
    return mask


def gen_fission_residue():
    """Angular dark slag chip with one lime fleck."""
    name = "fission_residue"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    pts = [(6, 20), (9, 10), (16, 6), (24, 9), (27, 17), (22, 25), (12, 26)]
    mask = _fill_poly(img, pts, SLAG[:2], rng)
    mp = mask.load()
    for y in range(S):
        for x in range(S):
            if mp[x, y] and x < 16 and y < 15 and (x + y) % 2:
                px[x, y] = SLAG[2]                                   # the lit facet
    for (a, b, c, d) in ((9, 10, 16, 15), (16, 6, 16, 15), (16, 15, 27, 17), (16, 15, 12, 26)):
        line(px, a, b, c, d, SLAG[1])                                # fracture facets
    _outline(px, pts, (14, 13, 15, 255))
    line(px, 10, 10, 16, 7, SLAG[2])
    px[19, 19] = RAD_LIME                                            # the one fleck
    px[20, 19] = RAD_LIME
    px[19, 20] = RAD_DEEP
    px[20, 20] = RAD_DEEP
    save(img, "item", name)


def gen_vitrified_waste():
    """Smooth dark glass log, square section, one highlight."""
    name = "vitrified_waste"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    top = [(5, 12), (11, 6), (27, 12), (21, 18)]                     # top face (a slanted square)
    left = [(5, 12), (21, 18), (21, 27), (5, 21)]
    right = [(21, 18), (27, 12), (27, 21), (21, 27)]
    _fill_poly(img, left, [GLASS[0], GLASS[1]], rng)
    _fill_poly(img, right, [GLASS[1]], rng)
    _fill_poly(img, top, [GLASS[2], GLASS[0]], rng)
    for pts in (left, right, top):
        _outline(px, pts, (10, 13, 16, 255))
    line(px, 7, 15, 7, 20, A_LIGHT)                                  # the one highlight
    line(px, 8, 16, 8, 18, _mix(A_LIGHT, GLASS[0], 0.5))
    save(img, "item", name)


def _beta_capsule(name, lit):
    """Short flat-ended capsule lying on its side, cindersteel end caps, a square window."""
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    body = SILVER if lit else SPENT
    for y in range(11, 22):
        for x in range(4, 28):
            px[x, y] = rng.choice(body[:2])
    for x in range(4, 28):
        px[x, 11] = body[2]
        px[x, 12] = _mix(body[2], body[0], 0.5)
        px[x, 21] = _mix(body[1], A_DARK, 0.6)
    for (a, b) in ((4, 8), (23, 27)):                               # flat end caps
        for y in range(10, 23):
            for x in range(a, b + 1):
                px[x, y] = CINDER[0] if lit else SPENT[1]
            px[a, y] = CINDER[2] if lit else SPENT[2]
            px[b, y] = CINDER[1] if lit else A_DARK
        for x in range(a, b + 1):
            px[x, 10] = CINDER[2] if lit else SPENT[2]
            px[x, 22] = A_DARK
    frame(px, 11, 13, 20, 19, A_DARK)                               # window
    if lit:
        rect(px, 12, 14, 19, 18, P_AMBER)
        rect(px, 13, 15, 18, 17, P_HOT)
        px[15, 16] = P_GLOW
        px[16, 16] = P_GLOW
        for x in range(12, 20):
            px[x, 14] = _mix(P_AMBER, P_EMBER, 0.5)
    else:
        rect(px, 12, 14, 19, 18, (58, 60, 64, 255))
        for x in range(12, 20):
            px[x, 14] = (38, 40, 44, 255)
        px[18, 15] = (96, 98, 104, 255)
    save(img, "item", name)


def gen_beta_capsules():
    _beta_capsule("beta_capsule", True)
    _beta_capsule("spent_beta_capsule", False)


# ---------------- particle ----------------

def gen_pool_bubble():
    """Three 8x8 bubble frames (small, medium, burst) for ``particles/pool_bubble.json``."""
    white = (232, 240, 246, 255)
    soft = (176, 206, 228, 255)
    shapes = [
        ["........", "........", "...##...", "..#..#..", "..#..#..", "...##...", "........", "........"],
        ["........", "..####..", ".#o...#.", ".#o...#.", ".#....#.", ".#....#.", "..####..", "........"],
        ["#..#..#.", "........", "#......#", "........", "........", "#......#", "........", ".#..#..#"],
    ]
    for i, rows in enumerate(shapes):
        img = Image.new("RGBA", (8, 8), CLEAR)
        px = img.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    px[x, y] = soft
                elif ch == "o":
                    px[x, y] = white
        save(img, "particle", "pool_bubble_%d" % i)


# ---------------- models, blockstates, item definitions ----------------

_DEFAULT_VARS = {"north": "front", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}


def _el(frm, to, tex=None, name=None, skip=()):
    """One element with auto-UV faces; a face lying on the block boundary gets its cullface."""
    tex = dict(_DEFAULT_VARS, **(tex or {}))
    on = {"down": frm[1] == 0, "up": to[1] == 16, "north": frm[2] == 0, "south": to[2] == 16,
          "west": frm[0] == 0, "east": to[0] == 16}
    faces = {}
    for f in ("down", "up", "north", "south", "west", "east"):
        if f in skip:
            continue
        face = {"texture": "#" + tex[f]}
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


def _socket(frm, to):
    full = {f: "socket" for f in _DEFAULT_VARS}
    el = _el(frm, to, full, name="status_socket", skip=("south",))
    for face in el["faces"].values():
        face["uv"] = [0, 0, 16, 16]
        face.pop("cullface", None)
    return el


def _model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def _t(**kw):
    return {k: "%s:block/%s" % (NS, v) for k, v in kw.items()}


SOCKETS = {   # status_socket boxes (model pixels); the renderers quote these
    "spent_fuel_pool": ((13, 8, -0.5), (15, 10, 0)),
    "vitrifier": ((13, 5, -0.5), (15, 7, 0)),
    "dry_cask": ((10, 9, 2.5), (12, 11, 3)),
    "fuel_cell": ((13, 11, 0.5), (15, 13, 1)),
    "betavoltaic_cell": ((10, 3, 2.5), (12, 5, 3)),
}
ALL_SIDE = {"north": "side"}


def _pool_model(side):
    s = ALL_SIDE
    return _model(_t(side=side, top="spent_fuel_pool_top", socket="backend_socket", particle=side), [
        _el((0, 0, 0), (16, 4, 16), s),                    # tray
        _el((0, 4, 0), (16, 12, 2), s, skip=("down",)),    # walls
        _el((0, 4, 14), (16, 12, 16), s, skip=("down",)),
        _el((0, 4, 2), (2, 12, 14), s, skip=("down", "north", "south")),
        _el((14, 4, 2), (16, 12, 14), s, skip=("down", "north", "south")),
        _el((7, 4, 2), (9, 11, 14), s, skip=("down", "north", "south")),      # cross dividers
        _el((2, 4, 7), (7, 11, 9), s, skip=("down", "west", "east")),
        _el((9, 4, 7), (14, 11, 9), s, skip=("down", "west", "east")),
        _el((0, 12, 7), (2, 14, 9), s, skip=("down", "up")),                  # bail
        _el((14, 12, 7), (16, 14, 9), s, skip=("down", "up")),
        _el((0, 14, 7), (16, 16, 9), s),
        _socket(*SOCKETS["spent_fuel_pool"]),
    ])


def _vitrifier_model():
    s = ALL_SIDE
    return _model(_t(front="vitrifier_front", side="vitrifier_side", top="vitrifier_top",
                     socket="backend_socket", particle="vitrifier_side"), [
        _el((0, 0, 0), (16, 2, 16)),                       # floor
        _el((0, 2, 5), (16, 9, 16), skip=("down",)),       # back body (its north face is the cavity wall)
        _el((0, 2, 0), (4, 9, 5), skip=("down", "south")),                    # slot pillars
        _el((12, 2, 0), (16, 9, 5), skip=("down", "south")),
        _el((4, 7, 0), (12, 9, 5), skip=("south", "west", "east")),           # lintel
        _el((3, 9, 3), (13, 11, 13), skip=("down", "up")),                    # throat neck
        _el((1.5, 11, 1.5), (14.5, 13, 14.5)),             # upper step; its top is the throat floor
        _el((0, 13, 0), (16, 16, 2), skip=("down",)),      # rim
        _el((0, 13, 14), (16, 16, 16), s, skip=("down",)),
        _el((0, 13, 2), (2, 16, 14), s, skip=("down", "north", "south")),
        _el((14, 13, 2), (16, 16, 14), s, skip=("down", "north", "south")),
        _socket(*SOCKETS["vitrifier"]),
    ])


def _cask_model(side="dry_cask_side", top="dry_cask_top", leaking=False):
    s = ALL_SIDE
    lift = 1 if leaking else 0
    elements = [
        _el((1, 0, 1), (15, 2, 15), s),                    # foot
        _el((3, 2, 4), (13, 13, 12), s, skip=("down", "up")),                 # body (two crossed slabs)
        _el((4, 2, 3), (12, 13, 13), s, skip=("down", "up")),
        _el((7, 2, 2), (9, 13, 3), s, skip=("down", "south")),                # upright ribs
        _el((7, 2, 13), (9, 13, 14), s, skip=("down", "north")),
        _el((2, 2, 7), (3, 13, 9), s, skip=("down", "east")),
        _el((13, 2, 7), (14, 13, 9), s, skip=("down", "west")),
        _el((2.5, 13 + lift, 2.5), (13.5, 14 + lift, 13.5), s),               # lid plate
        _el((4, 14 + lift, 4), (12, 15 + lift, 12), s, skip=("down",)),       # cap
        _el((1, 11, 7), (2, 13, 9), s, skip=("east",)),    # lifting lugs
        _el((14, 11, 7), (15, 13, 9), s, skip=("west",)),
    ]
    if leaking:
        elements.append(_el((5, 13, 5), (11, 14, 11), s, skip=("down", "up")))   # the neck in the gap
        for el in elements:                                # a lifted cap is never culled by a neighbour
            el["faces"].get("up", {}).pop("cullface", None)
    elements.append(_socket(*SOCKETS["dry_cask"]))
    return _model(_t(side=side, top=top, socket="backend_socket", particle=side), elements)


def _fuel_cell_model():
    f = {"south": "front", "up": "front", "down": "front"}
    n = {"north": "side", "up": "side", "down": "side"}
    elements = [
        _el((1, 0, 2), (15, 1, 14), n),                    # skid
        _el((0, 1, 1), (3, 15, 15), f),                    # end plates
        _el((13, 1, 1), (16, 15, 15), f),
        _el((3, 3, 3), (13, 13, 13), f, skip=("west", "east")),               # core (recessed plates)
    ]
    for x in (3.5, 5.5, 7.5, 9.5, 11.5):                   # raised plates
        elements.append(_el((x, 2, 2), (x + 1, 14, 14), f))
    elements += [
        _el((3, 14, 4), (13, 15, 5), f, skip=("west", "east")),               # tie rods
        _el((3, 14, 11), (13, 15, 12), f, skip=("west", "east")),
        _el((4, 8, 14), (6, 10, 16), n, skip=("north",)),  # gas nipples (back)
        _el((10, 8, 14), (12, 10, 16), n, skip=("north",)),
        _socket(*SOCKETS["fuel_cell"]),
    ]
    return _model(_t(front="fuel_cell_front", side="fuel_cell_side", socket="backend_socket",
                     particle="fuel_cell_side"), elements)


def _betavoltaic_model(side="betavoltaic_cell_side", top="betavoltaic_cell_top", spent=False):
    s = ALL_SIDE
    elements = [
        _el((0, 0, 0), (16, 2, 16), s),                    # base
        _el((3, 2, 3), (13, 6, 13), s, skip=("down",)),    # core
    ]
    for z in (1.5, 5.5, 9.5, 13.5):                        # fins, four a side
        elements.append(_el((0, 2, z), (3, 5, z + 1), s, skip=("down", "east")))
        elements.append(_el((13, 2, z), (16, 5, z + 1), s, skip=("down", "west")))
    elements.append(_el((5, 6, 5), (11, 8, 11), s, skip=("down",)))          # window frame
    if spent:
        elements.append(_el((7, 8, 7), (9, 9.5, 9), s, skip=("down",)))      # the raised pull tab
    elements.append(_socket(*SOCKETS["betavoltaic_cell"]))
    return _model(_t(side=side, top=top, socket="backend_socket", particle=side), elements)


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


def _variants(model_for):
    """Blockstate variants for ``facing`` (plus whatever else ``model_for`` puts in its keys)."""
    out = {}
    for facing, y in _ROT:
        for key, model in model_for(facing):
            v = {"model": "%s:block/%s" % (NS, model)}
            if y:
                v["y"] = y
            out[key] = v
    return {"variants": out}


BLOCKS = ("spent_fuel_pool", "vitrifier", "dry_cask", "fuel_cell", "betavoltaic_cell")
STATUS = ("idle", "working", "starved", "tripped")
# Model per status. Cask: idle = sealed and cold, working = sealed and warm, tripped = leaking
# (starved is not a state a cask has; it shows the sealed model). Betavoltaic Cell: the block's own
# model is the working look (it is also the item); idle = dark, starved = spent capsule (tripped
# shows the same).
STATUS_MODELS = {
    "dry_cask": {"idle": "dry_cask", "working": "dry_cask_working", "starved": "dry_cask",
                 "tripped": "dry_cask_leaking"},
    "betavoltaic_cell": {"idle": "betavoltaic_cell_idle", "working": "betavoltaic_cell",
                         "starved": "betavoltaic_cell_spent", "tripped": "betavoltaic_cell_spent"},
}
ITEMS = ("fission_residue", "vitrified_waste", "beta_capsule", "spent_beta_capsule")


def write_models():
    """Models, blockstates, item definitions and the bubble particle definition."""
    models = {
        "spent_fuel_pool": _pool_model("spent_fuel_pool_side"),
        "spent_fuel_pool_alarm": _pool_model("spent_fuel_pool_side_alarm"),
        "vitrifier": _vitrifier_model(),
        "dry_cask": _cask_model(),
        "dry_cask_working": _cask_model(side="dry_cask_side_working"),
        "dry_cask_leaking": _cask_model("dry_cask_side_leaking", "dry_cask_top_leaking", leaking=True),
        "fuel_cell": _fuel_cell_model(),
        "betavoltaic_cell": _betavoltaic_model(),
        "betavoltaic_cell_idle": _betavoltaic_model(top="betavoltaic_cell_top_idle"),
        "betavoltaic_cell_spent": _betavoltaic_model("betavoltaic_cell_side_spent", "betavoltaic_cell_top_spent",
                                                     spent=True),
    }
    for name, model in models.items():
        _write_json("models/block/%s.json" % name, model)
    _write_json("blockstates/spent_fuel_pool.json", _variants(lambda f: [
        ("alarm=false,facing=%s" % f, "spent_fuel_pool"), ("alarm=true,facing=%s" % f, "spent_fuel_pool_alarm")]))
    for block in ("vitrifier", "fuel_cell"):
        _write_json("blockstates/%s.json" % block, _variants(lambda f, b=block: [("facing=%s" % f, b)]))
    # The cask and the betavoltaic cell have no renderer, so they carry the `status` block state
    # (idle / working / starved / tripped, set on a change only) and each status has its own model.
    for block, looks in STATUS_MODELS.items():
        _write_json("blockstates/%s.json" % block, _variants(lambda f, lk=looks: [
            ("facing=%s,status=%s" % (f, s), lk[s]) for s in STATUS]))
    for block in BLOCKS:
        _write_json("items/%s.json" % block,
                    {"model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, block)}})
        _write_json("models/item/%s.json" % block, {"parent": "%s:block/%s" % (NS, block)})
    for item in ITEMS:
        _write_json("items/%s.json" % item,
                    {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, item)}})
        _write_json("models/item/%s.json" % item,
                    {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, item)}})
    _write_json("particles/pool_bubble.json",
                {"textures": ["%s:pool_bubble_%d" % (NS, i) for i in range(3)]})


def paint():
    gen_socket()
    gen_spent_fuel_pool()
    gen_vitrifier()
    gen_dry_cask()
    gen_fuel_cell()
    gen_betavoltaic_cell()
    gen_fission_residue()
    gen_vitrified_waste()
    gen_beta_capsules()
    gen_pool_bubble()
    write_models()


# name, frames, frametime(ticks), amplitude, hue window
PULSE = [
    ("vitrifier_front", 8, 3, 0.30, HUE_AMBER),
    ("vitrifier_melt", 8, 3, 0.26, HUE_AMBER),
]

# The tints the renderers apply (kept here so the contact sheet shows what the game shows).
ROD_TINTS = {4: (255, 226, 160), 3: (255, 160, 40), 2: (204, 112, 18), 1: (74, 74, 72)}
LOG_HOT = (255, 160, 40)
LOG_COLD = (58, 74, 84)

SHEET = [
    {"id": "spent_fuel_pool", "block": True,
     "faces": ["spent_fuel_pool_top", "spent_fuel_pool_side"],
     "states": ["spent_fuel_pool_side", "spent_fuel_pool_side_alarm"]},
    {"id": "rod head 4 / 3", "model": None, "faces": [], "states": []},
    {"id": "vitrifier", "block": True,
     "faces": ["vitrifier_front", "vitrifier_side", "vitrifier_top"],
     "states": ["vitrifier_top", "vitrifier_melt"]},
    {"id": "dry_cask", "block": True, "faces": ["dry_cask_top"],
     "states": ["dry_cask_side", "dry_cask_side_working", "dry_cask_side_leaking"]},
    {"id": "dry_cask: leaking", "model": "block/dry_cask_leaking", "faces": [],
     "states": ["dry_cask_top", "dry_cask_top_leaking"]},
    {"id": "fuel_cell", "block": True, "faces": ["fuel_cell_front", "fuel_cell_side"]},
    {"id": "betavoltaic_cell", "block": True, "faces": ["betavoltaic_cell_side"],
     "states": ["betavoltaic_cell_top_idle", "betavoltaic_cell_top", "betavoltaic_cell_top_spent"]},
    {"id": "betavoltaic_cell: spent", "model": "block/betavoltaic_cell_spent", "faces": [],
     "states": ["betavoltaic_cell_side", "betavoltaic_cell_side_spent"]},
    {"id": "socket + items", "model": None,
     "faces": ["backend_socket", "item:fission_residue", "item:vitrified_waste", "item:beta_capsule",
               "item:spent_beta_capsule", "particle:pool_bubble_0", "particle:pool_bubble_1",
               "particle:pool_bubble_2"]},
]
SHEET[1:2] = [{"id": "rod head: step %d" % k, "model": None, "faces": ["spent_fuel_pool_rod_head"],
               "tint": ROD_TINTS[k]} for k in (4, 3, 2, 1)]
SHEET += [
    {"id": "glass log: hot", "model": None, "faces": ["vitrifier_log"], "tint": LOG_HOT},
    {"id": "glass log: set", "model": None, "faces": ["vitrifier_log"], "tint": LOG_COLD},
    {"id": "plate wave", "model": None, "faces": ["fuel_cell_plate"], "tint": (255, 160, 40)},
]

LOOKALIKES = ["radioisotope_generator_front", "stirling_generator_front", "battery_bank_controller_front",
              "control_rod_assembly_top", "fission_casing"]
NEROTECH_LOOKALIKES = ["plasma_crucible_front", "plasma_crucible_top", "process_tank", "centrifuge_front",
                       "chemical_processor_front", "fuel_injector_front", "nero_generator_front"]

# The residue chip carries one lime fleck (a contamination trace mark).
AUDIT = {"allow_lime": True, "allow_charge": False}

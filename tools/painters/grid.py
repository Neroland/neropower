"""Beam and grid family: Beam Splitter, Phased Array Emitter, Circuit Breaker, Load Bank, Power
Monitor, the Beam Alignment Scope and the SCRAM Key, and the renderer sprites they need.

Reads, one per block (none is a centred orb, a ring target, a bar graph or a spiral):

* Beam Splitter: a square prism hung in a yoke over a slab, a square lens collar behind it and four
  short barrels fanned out in front. The prism, the lens pane, the fans and the lit barrel caps are
  renderer sprites.
* Phased Array Emitter: an upright panel of 4 x 4 raised square tiles on a short mast. The phase wave
  is a renderer sprite strip (white, tinted arc violet in code); the baked panel carries only violet
  pips and corner marks.
* Circuit Breaker: an inline box with a bus bar in at the back and out at the front, a T handle on
  top and a flag window. Handle up and an amber window: closed. Handle down and a white-on-red flag:
  tripped.
* Load Bank: five zig-zag resistor elements behind a mesh in an open frame, an exhaust cowl on top.
  ``load_bank_grid_0`` .. ``_4`` glow from dark to hot.
* Power Monitor: a wall plate two units deep with a wide screen and three keys. The traces are drawn
  by the renderer.
* Beam Alignment Scope: a short monocular with a level vial. SCRAM Key: a chunky key with a red
  striped bow.

This module also writes the family's models, blockstates and item definitions
(``write_models()``). Run::

    python -B -c "import sys; sys.path.insert(0, 'tools'); from painters import grid; grid.write_models()"
"""
import json
import os

from .kit import *  # noqa: F401,F403
from .kit import _mix

GLASS = [(206, 214, 220, 255), (150, 162, 172, 255), (238, 242, 245, 255)]   # prism: base, shade, light
MESH = (30, 37, 43, 255)

# Phased array: texel origins of the sixteen 4x4 tiles on the panel sheet (and on the wave strip).
# The panel is model x 2..14, y 4..16: texels 4..27 across, 0..23 down.
PA_XS = (5, 11, 17, 23)
PA_YS = (1, 7, 13, 19)
# Circuit breaker flag window on the front sheet (interior), and the unlit copy the renderer blinks.
BW_X, BW_Y, BW_W, BW_H = 11, 13, 11, 9
# Power monitor screen opening on the bezel and screen sheets (texels, inclusive).
PM_X0, PM_Y0, PM_X1, PM_Y1 = 4, 4, 27, 23


def _plain(name, bevelled=True):
    img = new_img()
    noise_fill(img, ALLOY, rng_for(name))
    if bevelled:
        bevel(img)
    return img


def _well(px, x, y):
    """The dark lamp well without a collar (for sockets on a narrow member)."""
    rect(px, x, y, x + 3, y + 3, SOCKET)
    for i in range(4):
        px[x + i, y] = SOCKET_SHADOW
        px[x, y + i] = SOCKET_SHADOW


def _cinder_bar(px, x0, y0, x1, y1):
    rect(px, x0, y0, x1, y1, CINDER[0])
    for x in range(x0, x1 + 1):
        px[x, y0] = CINDER[2]
        px[x, y1] = CINDER[1]


# ---------------------------------------------------------------- Beam Splitter

def beam_splitter_base():
    """Front elevation of the slab, yoke posts and top bar (those faces take their own part of it)."""
    img = _plain("beam_splitter_base")
    px = img.load()
    for x in range(S):                                       # slab rows 26..31
        px[x, 26] = A_LIGHT
        px[x, 31] = A_DARK
    _well(px, 2, 27)
    tell(px, 15, 29)
    for x0 in (2, 26):                                       # yoke posts
        for y in range(8, 26):
            px[x0, y] = A_LIGHT
            px[x0 + 3, y] = A_DARK
        px[x0 + 1, 12] = CINDER[2]                           # trunnion pin
        px[x0 + 2, 12] = CINDER[0]
        px[x0 + 1, 13] = CINDER[0]
        px[x0 + 2, 13] = CINDER[1]
    for x in range(2, 30):                                   # top bar rows 4..7
        px[x, 4] = A_LIGHT
        px[x, 7] = A_DARK
    for x in (6, 15, 16, 25):
        px[x, 5] = _mix(A_LIGHT, A_DARK, 0.45)
    save(img, "block", "beam_splitter_base")


def beam_splitter_top():
    img = machine_base("beam_splitter_top")
    px = img.load()
    family_mark(px, "beam")
    for x0 in (2, 26):                                       # post seats
        frame(px, x0, 12, x0 + 3, 19, A_DARK)
    recess(px, 9, 11, 22, 20)                                # the well under the prism
    for x in (12, 19):
        for y in range(13, 19):
            px[x, y] = _mix(SOCKET, ALLOY[0], 0.5)
    save(img, "block", "beam_splitter_top")


def beam_splitter_barrel():
    """Barrel patches (top left) on a plain plate that the manifold and lens collar take by position."""
    img = _plain("beam_splitter_barrel", bevelled=False)
    px = img.load()
    tube = (_mix(ALLOY[3], A_LIGHT, 0.5), ALLOY[3], ALLOY[1], A_DARK)
    for x in range(6):                                       # patch A: tube lying along x, muzzle at left
        for y in range(4):
            px[x, y] = tube[y]
    for y in range(4):
        px[0, y] = CINDER[2] if y == 0 else CINDER[0]
        px[1, y] = CINDER[1]
        px[4, y] = A_DARK
    for y in range(6):                                       # patch B: tube standing, muzzle at top
        for x in range(4):
            px[8 + x, y] = tube[x]
    for x in range(4):
        px[8 + x, 0] = CINDER[2] if x == 0 else CINDER[0]
        px[8 + x, 1] = CINDER[1]
        px[8 + x, 4] = A_DARK
    rect(px, 12, 0, 15, 3, CINDER[1])                        # patch C: muzzle, unlit cap in a collar
    rect(px, 13, 1, 14, 2, P_EMBER)
    px[12, 0] = CINDER[2]
    for y in (16, 23):                                       # manifold seam rows
        for x in range(4, 28):
            px[x, y] = A_LIGHT if y == 16 else A_DARK
    for x in (7, 13, 19, 25):
        px[x, 19] = _mix(A_LIGHT, A_DARK, 0.45)
    save(img, "block", "beam_splitter_barrel")


def beam_splitter_prism():
    """Renderer sprite: one face of the glass prism. Two facets split on the diagonal, one glint."""
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            px[x, y] = GLASS[0] if x + y < S else GLASS[1]
    frame(px, 0, 0, S - 1, S - 1, A_LIGHT)
    frame(px, 1, 1, S - 2, S - 2, GLASS[2])
    for i in range(3, S - 3):
        px[i, S - 1 - i] = GLASS[2]
    for i in range(6, 14):                                   # refracted glint, off centre
        px[i, i + 10] = P_HOT if i % 2 else P_AMBER
    px[14, 24] = P_GLOW
    save(img, "block", "beam_splitter_prism")


def beam_splitter_optics():
    """Renderer sheet, white and tinted in code: the fan wedge (rows 0..15), the lens pane
    (8x8 at 0,16) and the lit barrel cap (4x4 at 12,16)."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    white = (255, 255, 255, 255)
    soft = (214, 214, 214, 255)
    for y in range(16):                                      # wedge: wide at the top, narrow below
        half = 15.5 - y * 0.8
        for x in range(S):
            d = abs(x - 15.5)
            if d <= half * 0.45:
                px[x, y] = white
            elif d <= half and (x + y) % 2 == 0:
                px[x, y] = soft
    rect(px, 0, 16, 7, 23, (176, 188, 198, 255))             # lens pane
    frame(px, 0, 16, 7, 23, (236, 240, 244, 255))
    for i in range(1, 7):
        px[i, 23 - i] = (236, 240, 244, 255)
    rect(px, 12, 16, 15, 19, white)                          # cap
    save(img, "block", "beam_splitter_optics")


# ---------------------------------------------------------------- Phased Array Emitter

def _pa_tile(px, x0, y0):
    rect(px, x0, y0, x0 + 3, y0 + 3, ALLOY[3])
    for i in range(4):
        px[x0 + i, y0] = _mix(A_LIGHT, ALLOY[3], 0.3)
        px[x0, y0 + i] = _mix(A_LIGHT, ALLOY[3], 0.5)
    for i in range(4):
        px[x0 + i, y0 + 3] = A_DARK
        px[x0 + 3, y0 + i] = ALLOY[0]
    px[x0 + 1, y0 + 2] = V_ARC                               # the field pip


def phased_array_emitter_panel():
    """Baked face: the plate, sixteen raised tiles each with one violet pip, and the foot below."""
    img = new_img()
    px = img.load()
    rng = rng_for("phased_array_emitter_panel")
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice(ALLOY)
    rect(px, 4, 0, 27, 23, SOCKET)
    frame(px, 4, 0, 27, 23, ALLOY[1])
    for x in range(4, 28):
        px[x, 0] = A_LIGHT
    for y in range(24):
        px[4, y] = _mix(A_LIGHT, ALLOY[1], 0.4)
        px[27, y] = A_DARK
    for (cx, cy) in ((4, 0), (27, 0), (4, 23), (27, 23)):    # violet corner marks
        px[cx, cy] = V_ARC
    for y0 in PA_YS:
        for x0 in PA_XS:
            _pa_tile(px, x0, y0)
    for x in range(6, 26):                                   # foot front: rows 28..31
        px[x, 28] = A_LIGHT
        px[x, 31] = A_DARK
    _well(px, 7, 28)
    save(img, "block", "phased_array_emitter_panel")


def phased_array_emitter_side():
    """Panel back, mast, bracket and foot: ribbed plate with the feed lines to the tiles."""
    img = machine_base("phased_array_emitter_side")
    px = img.load()
    rect(px, 4, 1, 9, 2, SOCKET)                             # lens notch (beam family mark)
    px[6, 3] = SOCKET
    px[7, 3] = SOCKET
    px[6, 1] = P_HOT
    for x0 in PA_XS:                                         # four feed lines down the back
        for y in range(7, 26):
            px[x0 + 1, y] = SUPER[1] if y % 6 else A_DARK
        px[x0 + 1, 6] = A_LIGHT
    for y in (12, 19):
        for x in range(3, 29):
            if px[x, y] not in (SUPER[1],):
                px[x, y] = A_DARK
            px[x, y + 1] = _mix(A_LIGHT, ALLOY[2], 0.5) if px[x, y + 1] != SUPER[1] else SUPER[1]
    tell(px, 15, 28)
    save(img, "block", "phased_array_emitter_side")


def phased_array_emitter_face():
    """Renderer strip, white and tinted arc violet in code: a diagonal phase wave over the sixteen
    tiles, top left to bottom right, eight frames. A lit tile is a 2x2 core in a dim 4x4; the trailing diagonal is a
    faint pair. The renderer mirrors it to run toward the served receiver."""
    white = (255, 255, 255, 255)
    dim = (150, 150, 150, 255)
    frames = []
    for f in range(8):
        img = Image.new("RGBA", (S, S), CLEAR)
        px = img.load()
        for r, y0 in enumerate(PA_YS):
            for c, x0 in enumerate(PA_XS):
                d = c + r
                if d == f:
                    rect(px, x0, y0, x0 + 3, y0 + 3, dim)
                    rect(px, x0 + 1, y0 + 1, x0 + 2, y0 + 2, white)
                elif d == f - 1:
                    px[x0 + 1, y0 + 2] = dim
                    px[x0 + 2, y0 + 1] = dim
        frames.append(img)
    save_strip(frames, "block", "phased_array_emitter_face", 3)


# ---------------------------------------------------------------- Circuit Breaker

def _breaker_window_closed(px):
    frame(px, BW_X - 1, BW_Y - 1, BW_X + BW_W, BW_Y + BW_H, A_DARK)
    rect(px, BW_X, BW_Y, BW_X + BW_W - 1, BW_Y + BW_H - 1, P_AMBER)
    frame(px, BW_X, BW_Y, BW_X + BW_W - 1, BW_Y + BW_H - 1, _mix(P_AMBER, P_EMBER, 0.5))
    for y in range(BW_Y + 1, BW_Y + BW_H - 1):               # the closed blade: one upright bar
        px[BW_X + 5, y] = P_HOT
        px[BW_X + 4, y] = P_EMBER
        px[BW_X + 6, y] = P_EMBER
    px[BW_X + 5, BW_Y + 2] = P_GLOW
    px[BW_X + 5, BW_Y + 3] = P_GLOW


def _breaker_window_dark(px, x, y):
    rect(px, x, y, x + BW_W - 1, y + BW_H - 1, P_EMBER)
    frame(px, x, y, x + BW_W - 1, y + BW_H - 1, _mix(P_EMBER, A_DARK, 0.5))
    for yy in range(y + 1, y + BW_H - 1):
        px[x + 5, yy] = _mix(P_AMBER, P_EMBER, 0.6)


def circuit_breaker_front(tripped):
    name = "circuit_breaker_front_tripped" if tripped else "circuit_breaker_front"
    img = _plain("circuit_breaker_front")
    px = img.load()
    for x in range(4, 28):                                   # body face: texels 4..27 x 10..31
        px[x, 10] = A_LIGHT
        px[x, 31] = A_DARK
    for y in range(10, S):
        px[4, y] = A_LIGHT
        px[27, y] = A_DARK
    status_socket(px, 5, 12)
    if tripped:
        trip_flag(px, BW_X, BW_Y, True, w=BW_W, h=BW_H)
    else:
        _breaker_window_closed(px)
    for x in (6, 8, 23, 25):                                 # terminal bolts either side of the bar
        px[x, 26] = _mix(A_LIGHT, A_DARK, 0.4)
        px[x, 27] = A_DARK
    save(img, "block", name)


def circuit_breaker_window():
    """Renderer sprite: the window unlit, for the off beat of the warning blink (tile-filling)."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    _breaker_window_dark(px, 0, 0)
    save(img, "block", "circuit_breaker_window")


def circuit_breaker_side():
    img = machine_base("circuit_breaker_side")
    px = img.load()
    _cinder_bar(px, 2, 22, 29, 25)                           # the bus running through, back to front
    for x in (5, 15, 25):
        px[x, 23] = A_LIGHT
        px[x + 1, 23] = A_DARK
    louvre(px, 8, 12, 23, 17)
    tell(px, 15, 28)
    for x in range(2, 30):
        px[x, 10] = A_LIGHT
    save(img, "block", "circuit_breaker_side")


def circuit_breaker_top():
    img = machine_base("circuit_breaker_top")
    px = img.load()
    _cinder_bar(px, 6, 5, 13, 7)                             # bus bar: the grid family mark
    recess(px, 14, 4, 17, 27)                                # the slot the handle throws along
    for y in (9, 22):
        px[12, y] = A_LIGHT
        px[19, y] = A_LIGHT
    save(img, "block", "circuit_breaker_top")


def circuit_breaker_handle():
    """Handle grip, arm and the two bus stubs: cindersteel with a bright top edge."""
    img = new_img()
    px = img.load()
    rng = rng_for("circuit_breaker_handle")
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice((CINDER[0], CINDER[0], CINDER[1]))
    for i in range(S):
        px[i, 0] = CINDER[2]
        px[0, i] = CINDER[2]
        px[i, S - 1] = CINDER[1]
        px[S - 1, i] = CINDER[1]
    for y in range(4, S - 2, 4):                             # grip knurling
        for x in range(2, S - 2):
            px[x, y] = CINDER[1]
            px[x, y + 1] = CINDER[2] if x % 2 else CINDER[0]
    save(img, "block", "circuit_breaker_handle")


# ---------------------------------------------------------------- Load Bank

HEAT_COLS = (GRAPHITE[2], _mix(P_EMBER, P_AMBER, 0.35), P_AMBER, _mix(P_AMBER, P_HOT, 0.6), P_HOT)


def load_bank_grid(step):
    """Five zig-zag elements behind a mesh. Step 0 dark, 1..4 ember to hot."""
    name = "load_bank_grid_%d" % step
    img = new_img()
    px = img.load()
    rect(px, 0, 0, S - 1, S - 1, SOCKET_SHADOW)
    col = HEAT_COLS[step]
    under = _mix(col, SOCKET_SHADOW, 0.6)
    zig = (-1, 0, 1, 0)
    for cx in (6, 11, 16, 21, 26):
        for y in range(4, S):
            x = cx + zig[y % 4]
            px[x - 1, y] = col
            px[x, y] = under if step else GRAPHITE[1]
    if step == 4:                                            # at most four peak pixels on the face
        for (gx, gy) in ((10, 14), (15, 22), (20, 14), (25, 22)):
            px[gx, gy] = P_GLOW
    for y in range(S):                                       # the mesh: dotted wires every fourth texel
        for x in range(S):
            if (x % 4 == 1 and y % 2 == 0) or (y % 4 == 1 and x % 2 == 0):
                px[x, y] = MESH
    for x in range(S):                                       # top and bottom element bus
        for y in (10, 27):
            px[x, y] = A_DARK
    save(img, "block", name)


def load_bank_side():
    img = front_base("load_bank_side")
    px = img.load()
    louvre(px, 9, 12, 26, 23)
    for x in range(2, 30):
        px[x, 8] = A_DARK
        px[x, 9] = _mix(A_LIGHT, ALLOY[2], 0.5)
    tell(px, 15, 27)
    save(img, "block", "load_bank_side")


def load_bank_top():
    """Cowl top: exhaust slats, with the bus bar mark."""
    img = machine_base("load_bank_top")
    px = img.load()
    louvre(px, 6, 10, 25, 25, pitch=4)
    _cinder_bar(px, 6, 4, 15, 6)
    save(img, "block", "load_bank_top")


# ---------------------------------------------------------------- Power Monitor

def power_monitor_bezel():
    img = machine_base("power_monitor_bezel")
    px = img.load()
    rect(px, PM_X0, PM_Y0, PM_X1, PM_Y1, SOCKET)
    frame(px, PM_X0 - 1, PM_Y0 - 1, PM_X1 + 1, PM_Y1 + 1, A_DARK)
    for x in range(PM_X0 - 1, PM_X1 + 2):
        px[x, PM_Y1 + 1] = _mix(A_LIGHT, A_DARK, 0.5)
    status_socket(px, 2, 26)
    for kx in (12, 17, 22):                                  # three keys
        rect(px, kx, 27, kx + 2, 28, ALLOY[3])
        for i in range(3):
            px[kx + i, 26] = A_LIGHT
            px[kx + i, 29] = A_DARK
    tell(px, 27, 28, vertical=True)
    save(img, "block", "power_monitor_bezel")


def power_monitor_screen():
    """The dark screen with a dotted graticule. The traces are the renderer's."""
    img = new_img()
    px = img.load()
    rect(px, 0, 0, S - 1, S - 1, A_DARK)
    rect(px, PM_X0, PM_Y0, PM_X1, PM_Y1, SOCKET)
    dot = _mix(SOCKET, ALLOY[1], 0.55)
    for y in range(PM_Y0 + 1, PM_Y1, 4):
        for x in range(PM_X0 + 1, PM_X1, 2):
            px[x, y] = dot
    for x in range(PM_X0 + 5, PM_X1, 6):
        for y in range(PM_Y0 + 1, PM_Y1, 2):
            px[x, y] = dot
    for x in range(PM_X0, PM_X1 + 1):
        px[x, PM_Y0] = SOCKET_SHADOW
    save(img, "block", "power_monitor_screen")


# ---------------------------------------------------------------- beam effects

def beam_sheath():
    """Renderer sprite: the soft outer sheath of a beam, white (tinted in code). Two columns in five
    are lit, so it reads as a 40 % veil without a translucent pass, and the columns run the length
    of the tile, so one quad can stretch it along a whole beam. The outer columns are broken."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for x in range(4, 28):
        if x % 5 not in (0, 2):
            continue
        edge = min(x - 4, 27 - x)
        for y in range(S):
            if edge <= 3 and (y // 4) % 2:
                continue
            px[x, y] = (255, 255, 255, 255) if edge > 5 else (200, 200, 200, 255)
    save(img, "block", "beam_sheath")


def beam_flare():
    """Renderer sprite: the small flare where a beam meets a block. A four-point splinter, white."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for i in range(4, 28):
        w = 1 if abs(i - 15.5) > 6 else 2
        for k in range(-w + 1, w):
            px[i, 15 + k] = (255, 255, 255, 255)
            px[15 + k, i] = (255, 255, 255, 255)
    for i in range(9, 23):
        if i % 2:
            px[i, i] = (210, 210, 210, 255)
            px[i, S - 1 - i] = (210, 210, 210, 255)
    save(img, "block", "beam_flare")


def beam_scope_line():
    """Scope overlay sprite: eight dashes along y, four texels wide, white (tinted white or red)."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for y in range(S):
        if (y // 2) % 2 == 0:
            for x in range(14, 18):
                px[x, y] = (255, 255, 255, 255)
    save(img, "block", "beam_scope_line")


def beam_scope_box():
    """Scope overlay sprite: a two-texel outline with corner ticks, for the blocking block."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    for i in range(S):
        if (i // 4) % 2 == 0 or i < 6 or i > 25:
            for k in (0, 1):
                px[i, k] = px[i, S - 1 - k] = px[k, i] = px[S - 1 - k, i] = (255, 255, 255, 255)
    save(img, "block", "beam_scope_box")


# ---------------------------------------------------------------- items

def _outline(px, mask):
    """A_DARK outline round the set of opaque texels in ``mask``."""
    for (x, y) in list(mask):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in mask and 0 <= p[0] < S and 0 <= p[1] < S:
                px[p[0], p[1]] = A_DARK


def beam_alignment_scope():
    """A short monocular, leaning right: eyecup low left, objective high right with a lens notch,
    a level vial on the tube (the cyan tick is its bubble)."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    mask = set()

    def put(x, y, col):
        if 0 <= x < S and 0 <= y < S:
            px[x, y] = col
            mask.add((x, y))

    for i in range(0, 17):                                   # the tube, five texels thick
        x, y = 8 + i, 23 - i
        cols = (A_LIGHT, ALLOY[3], ALLOY[2], ALLOY[1], ALLOY[0])
        for k, col in enumerate(cols):
            put(x + k - 1, y + k - 1, col)
            put(x + k, y + k - 1, col)
    for i in range(0, 4):                                    # eyecup, cindersteel, wider
        x, y = 5 + i, 26 - i
        for k in range(-2, 5):
            put(x + k, y + k, CINDER[2] if k == -2 else (CINDER[1] if k >= 3 else CINDER[0]))
            put(x + k + 1, y + k, CINDER[1] if k >= 3 else CINDER[0])
    for i in range(0, 4):                                    # objective bell
        x, y = 23 + i, 8 - i
        for k in range(-3, 5):
            put(x + k, y + k, A_LIGHT if k == -3 else (A_DARK if k == 4 else ALLOY[3]))
            put(x + k + 1, y + k, ALLOY[2] if k < 4 else A_DARK)
    for k in range(-2, 4):                                   # the glass: a slanted sliver, not a disc
        put(27 + k, 5 + k, P_AMBER if k in (-2, 3) else P_HOT)
    put(27, 5, P_GLOW)
    put(24, 2, SOCKET)                                       # lens notch on the rim
    put(25, 2, SOCKET)
    for i in range(5):                                       # level vial on the upper flank
        put(12 + i, 16 - i, SOCKET)
        put(12 + i, 15 - i, A_LIGHT if i in (0, 4) else SOCKET)
    px[14, 14] = T_CYAN
    px[15, 13] = T_CYAN
    _outline(px, mask)
    save(img, "item", "beam_alignment_scope")


def scram_key():
    """A chunky key: a square red-striped bow low left, a thick blade with three teeth up right."""
    img = Image.new("RGBA", (S, S), CLEAR)
    px = img.load()
    mask = set()

    def put(x, y, col):
        if 0 <= x < S and 0 <= y < S:
            px[x, y] = col
            mask.add((x, y))

    for y in range(17, 29):                                  # bow: 12x12 with a 4x4 eye
        for x in range(3, 15):
            if 21 <= y <= 24 and 7 <= x <= 10:
                continue
            stripe = ((x + y) // 3) % 2 == 0
            put(x, y, R_ALARM if stripe else ALLOY[1])
    for i in range(12):                                      # lit top edge, shaded foot
        px[3 + i, 17] = _mix(R_ALARM, TRIP_W, 0.35) if ((3 + i + 17) // 3) % 2 == 0 else ALLOY[3]
        px[3 + i, 28] = _mix(R_ALARM, A_DARK, 0.45) if ((3 + i + 28) // 3) % 2 == 0 else ALLOY[0]
    for i in range(0, 14):                                   # blade, four texels thick, up to the right
        x, y = 14 + i, 17 - i
        for k, col in enumerate((A_LIGHT, SILVER[0], SILVER[1], A_DARK)):
            put(x + k - 1, y + k, col)
            put(x + k, y + k, col)
    for i in (5, 8, 11):                                     # three teeth on the lower flank
        x, y = 14 + i, 17 - i
        put(x + 3, y + 4, SILVER[1])
        put(x + 4, y + 4, SILVER[1])
        put(x + 4, y + 5, A_DARK)
    _outline(px, mask)
    save(img, "item", "scram_key")


def paint():
    beam_splitter_base()
    beam_splitter_top()
    beam_splitter_barrel()
    beam_splitter_prism()
    beam_splitter_optics()
    phased_array_emitter_panel()
    phased_array_emitter_side()
    phased_array_emitter_face()
    circuit_breaker_front(False)
    circuit_breaker_front(True)
    circuit_breaker_window()
    circuit_breaker_side()
    circuit_breaker_top()
    circuit_breaker_handle()
    for step in range(5):
        load_bank_grid(step)
    load_bank_side()
    load_bank_top()
    power_monitor_bezel()
    power_monitor_screen()
    beam_sheath()
    beam_flare()
    beam_scope_line()
    beam_scope_box()
    beam_alignment_scope()
    scram_key()


SHEET = [
    {"id": "beam_splitter", "block": True,
     "faces": ["beam_splitter_base", "beam_splitter_top", "beam_splitter_barrel", "beam_splitter_prism",
               "beam_splitter_optics"]},
    {"id": "phased_array_emitter", "block": True,
     "faces": ["phased_array_emitter_panel", "phased_array_emitter_side"],
     "states": ["phased_array_emitter_face"]},
    {"id": "circuit_breaker", "block": True,
     "faces": ["circuit_breaker_side", "circuit_breaker_top", "circuit_breaker_handle",
               "circuit_breaker_window"],
     "states": ["circuit_breaker_front", "circuit_breaker_front_tripped"]},
    {"id": "circuit_breaker (tripped)", "faces": [], "model": "block/circuit_breaker_tripped"},
    {"id": "load_bank", "block": True, "faces": ["load_bank_side", "load_bank_top"],
     "states": ["load_bank_grid_%d" % i for i in range(5)]},
    {"id": "power_monitor", "block": True, "faces": ["power_monitor_bezel", "power_monitor_screen"]},
    {"id": "beam effects", "model": None,
     "faces": ["beam_sheath", "beam_flare", "beam_scope_line", "beam_scope_box"]},
    {"id": "tools", "model": None, "faces": ["item:beam_alignment_scope", "item:scram_key"]},
]
LOOKALIKES = ["beam_transmitter_front", "beam_relay_front", "beam_dish", "beam_ray",
              "battery_bank_controller_front"]
NEROTECH_LOOKALIKES = ["analytics_terminal_front", "solar_array", "radiator", "grid_controller_front"]
AUDIT = {"allow_lime": False, "allow_charge": False}


# ---------------------------------------------------------------- models

def _tex(u0, v0, u1, v1):
    """A texel rectangle (x0, y0 inclusive; x1, y1 exclusive) as model UV (16 = the whole tile)."""
    return [u0 / 2.0, v0 / 2.0, u1 / 2.0, v1 / 2.0]


def _box(frm, to, tex, cull=(), name=None, uv=None, rot=None, turn=None):
    """One element. ``tex``: a texture key for every face, or {face: key}. ``cull``: faces lying on
    the block boundary. ``uv``: {face: [u0, v0, u1, v1]} for faces that do not use automatic UV;
    ``rot``: {face: degrees}; ``turn``: (origin, axis, angle) element rotation."""
    faces = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        key = tex.get(face) if isinstance(tex, dict) else tex
        if key is None:
            continue
        body = {"texture": "#" + key}
        if uv and face in uv:
            body["uv"] = uv[face]
        if rot and face in rot:
            body["rotation"] = rot[face]
        if face in cull:
            body["cullface"] = face
        faces[face] = body
    el = {}
    if name:
        el["name"] = name
    el["from"] = list(frm)
    el["to"] = list(to)
    if turn:
        el["rotation"] = {"origin": list(turn[0]), "axis": turn[1], "angle": turn[2]}
    el["faces"] = faces
    return el


_ALL = ("north", "south", "west", "east", "up", "down")
_SIDES4 = ("north", "south", "west", "east")


def _write_json(rel, obj):
    path = os.path.join(assets_dir(), *rel.split("/"))
    data = (json.dumps(obj, indent=2) + "\n").replace("\n", "\r\n").encode("utf-8")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    try:
        with open(path, "rb") as fh:
            if fh.read() == data:
                return
    except OSError:
        pass
    with open(path, "wb") as fh:
        fh.write(data)
    print("wrote", rel)


def _model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def _facing(model, extra=""):
    out = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        body = {"model": model}
        if y:
            body["y"] = y
        out[extra + "facing=" + facing] = body
    return out


def beam_splitter_model():
    b, t, k = "base", "top", "barrel"
    tube_w = _tex(0, 0, 6, 4)
    tube_e = [tube_w[2], tube_w[1], tube_w[0], tube_w[3]]
    tube_u = _tex(8, 0, 12, 6)
    tube_d = [tube_u[0], tube_u[3], tube_u[2], tube_u[1]]
    muzzle = _tex(12, 0, 16, 4)
    els = [
        _box((0, 0, 0), (16, 3, 16), {"north": b, "south": b, "west": b, "east": b, "up": t, "down": t},
             cull=("down", "north", "south", "west", "east")),
        _box((1, 3, 6), (3, 12, 10), b),                     # yoke posts
        _box((13, 3, 6), (15, 12, 10), b),
        _box((1, 12, 6), (15, 14, 10), b),                   # top bar
        _box((7, 3, 12), (9, 4, 13), k),                     # lens collar: stem and four bars
        _box((5, 4, 12), (11, 5.5, 13), k),
        _box((5, 9.5, 12), (11, 11, 13), k),
        _box((5, 5.5, 12), (6, 9.5, 13), k),
        _box((10, 5.5, 12), (11, 9.5, 13), k),
        _box((2, 4, 3), (14, 8, 5), k),                      # barrel manifold
    ]
    tube_faces = {"west": k, "east": k, "up": k, "down": k, "north": k}
    tube_uv = {"west": tube_w, "east": tube_e, "up": tube_u, "down": tube_d, "north": muzzle}
    for x in (5.5, 8.5):                                     # the two inner barrels
        els.append(_box((x, 5, 0), (x + 2, 7, 3), tube_faces, uv=tube_uv, cull=("north",)))
    els.append(_box((2.5, 5, 0.5), (4.5, 7, 3.5), tube_faces, uv=tube_uv,
                    turn=((3.5, 6, 3.5), "y", 22.5)))        # the two outer barrels, swung out
    els.append(_box((11.5, 5, 0.5), (13.5, 7, 3.5), tube_faces, uv=tube_uv,
                    turn=((12.5, 6, 3.5), "y", -22.5)))
    els.append(_box((13, 0.5, -0.5), (15, 2.5, 0), b, name="status_socket",
                    uv={f: _tex(2, 27, 6, 31) for f in _ALL}))
    return els


def phased_array_emitter_model():
    p, s = "panel", "side"
    edge = _tex(12, 26, 16, 28)                              # a plain strip of the side sheet for tile flanks
    els = [
        _box((3, 0, 2), (13, 2, 13), {"north": p, "south": s, "west": s, "east": s, "up": s, "down": s},
             cull=("down",)),                                # foot
        _box((6, 2, 5), (10, 11, 9), s),                     # mast, seen as a neck under the panel
        _box((2, 4, 3), (14, 16, 5), {"north": p, "south": s, "west": s, "east": s, "up": s, "down": s},
             cull=("up",)),                                  # panel
    ]
    for y in (4.5, 7.5, 10.5, 13.5):
        for x in (2.5, 5.5, 8.5, 11.5):
            els.append(_box((x, y, 2), (x + 2, y + 2, 3),
                            {"north": p, "west": s, "east": s, "up": s, "down": s},
                            uv={f: edge for f in ("west", "east", "up", "down")}))
    els.append(_box((10.5, 0, 1.5), (12.5, 2, 2), p, name="status_socket",
                    uv={f: _tex(7, 28, 11, 32) for f in _ALL}))
    return els


def circuit_breaker_model(tripped):
    f, s, t, h = "front", "side", "top", "handle"
    els = [
        _box((2, 0, 1), (14, 11, 15), {"north": f, "south": s, "west": s, "east": s, "up": t, "down": t},
             cull=("down",)),
        _box((0, 0, 2), (2, 2, 14), s, cull=("down", "west")),      # feet
        _box((14, 0, 2), (16, 2, 14), s, cull=("down", "east")),
        _box((5, 1, 0), (11, 4, 1), h, cull=("north",)),            # bus bar out (front)
        _box((5, 1, 15), (11, 4, 16), h, cull=("south",)),          # bus bar in (back)
        _box((6, 11, 6), (10, 12, 10), s),                          # pivot block
    ]
    if tripped:
        els.append(_box((7, 12, 3), (9, 13.5, 8), s))               # arm thrown flat toward the front
        els.append(_box((4, 11, 1.5), (12, 13, 3), h))              # grip down on the body
    else:
        els.append(_box((7, 12, 7), (9, 15, 9), s))                 # arm upright
        els.append(_box((4, 15, 7), (12, 16, 9), h, cull=("up",)))  # T grip
    els.append(_box((11.5, 8, 0.5), (13.5, 10, 1), f, name="status_socket",
                    uv={k: _tex(5, 12, 9, 16) for k in _ALL}))
    return els


def load_bank_model():
    g, s, t = "grid", "side", "top"
    return [
        _box((1, 0, 2), (15, 10, 16), {"north": g, "south": s, "west": s, "east": s, "up": s, "down": s},
             cull=("down", "south")),
        _box((0, 0, 0), (2, 11, 2), s, cull=("down", "north", "west")),      # frame posts
        _box((14, 0, 0), (16, 11, 2), s, cull=("down", "north", "east")),
        _box((2, 0, 0), (14, 2, 2), s, cull=("down", "north")),              # sill
        _box((2, 9, 0), (14, 11, 2), s, cull=("north",)),                    # lintel
        _box((5, 10, 5), (11, 13, 13), s),                                   # cowl throat
        _box((2.5, 13, 3), (13.5, 15, 15), {"north": s, "south": s, "west": s, "east": s, "up": t, "down": s}),
        _box((14, 8.5, -0.5), (16, 10.5, 0), s, name="status_socket",
             uv={k: _tex(2, 4, 6, 8) for k in _ALL}),
    ]


def power_monitor_model():
    z, c = "bezel", "screen"
    return [
        _box((0, 0, 15), (16, 16, 16), {"north": c, "south": z, "west": z, "east": z, "up": z, "down": z},
             cull=("south", "west", "east", "up", "down")),
        _box((0, 14, 14), (16, 16, 15), z, cull=("west", "east", "up")),     # bezel bars
        _box((0, 0, 14), (16, 4, 15), z, cull=("west", "east", "down")),
        _box((0, 4, 14), (2, 14, 15), z, cull=("west",)),
        _box((14, 4, 14), (16, 14, 15), z, cull=("east",)),
        _box((13, 1, 13.5), (15, 3, 14), z, name="status_socket",
             uv={k: _tex(2, 26, 6, 30) for k in _ALL}),
    ]


def write_models():
    """Write every model, blockstate and item definition of the family (CRLF, like the repo)."""
    ns = "neropower:block/"
    _write_json("models/block/beam_splitter.json", _model({
        "particle": ns + "beam_splitter_base", "base": ns + "beam_splitter_base",
        "top": ns + "beam_splitter_top", "barrel": ns + "beam_splitter_barrel"}, beam_splitter_model()))
    _write_json("blockstates/beam_splitter.json", {"variants": _facing(ns + "beam_splitter")})

    _write_json("models/block/phased_array_emitter.json", _model({
        "particle": ns + "phased_array_emitter_side", "panel": ns + "phased_array_emitter_panel",
        "side": ns + "phased_array_emitter_side"}, phased_array_emitter_model()))
    _write_json("blockstates/phased_array_emitter.json", {"variants": _facing(ns + "phased_array_emitter")})

    tex = {"particle": ns + "circuit_breaker_side", "front": ns + "circuit_breaker_front",
           "side": ns + "circuit_breaker_side", "top": ns + "circuit_breaker_top",
           "handle": ns + "circuit_breaker_handle"}
    _write_json("models/block/circuit_breaker.json", _model(tex, circuit_breaker_model(False)))
    tripped = dict(tex, front=ns + "circuit_breaker_front_tripped")
    _write_json("models/block/circuit_breaker_tripped.json", _model(tripped, circuit_breaker_model(True)))
    variants = {}
    for alarm in ("false", "true"):                          # the alarm reads through the lamp and window
        variants.update(_facing(ns + "circuit_breaker", "alarm=%s,tripped=false," % alarm))
        variants.update(_facing(ns + "circuit_breaker_tripped", "alarm=%s,tripped=true," % alarm))
    _write_json("blockstates/circuit_breaker.json", {"variants": variants})

    _write_json("models/block/load_bank.json", _model({
        "particle": ns + "load_bank_side", "grid": ns + "load_bank_grid_0",
        "side": ns + "load_bank_side", "top": ns + "load_bank_top"}, load_bank_model()))
    variants = {}
    for step in range(5):
        ref = ns + "load_bank" if step == 0 else ns + "load_bank_heat%d" % step
        if step:
            _write_json("models/block/load_bank_heat%d.json" % step,
                        {"parent": ns + "load_bank", "textures": {"grid": ns + "load_bank_grid_%d" % step}})
        variants.update(_facing(ref, "heat=%d," % step))
    _write_json("blockstates/load_bank.json", {"variants": variants})

    _write_json("models/block/power_monitor.json", _model({
        "particle": ns + "power_monitor_bezel", "bezel": ns + "power_monitor_bezel",
        "screen": ns + "power_monitor_screen"}, power_monitor_model()))
    _write_json("blockstates/power_monitor.json", {"variants": _facing(ns + "power_monitor")})

    for block in ("beam_splitter", "phased_array_emitter", "circuit_breaker", "load_bank", "power_monitor"):
        _write_json("models/item/%s.json" % block, {"parent": ns + block})
        _write_json("items/%s.json" % block, {"model": {"type": "minecraft:model", "model": ns + block}})
    for item in ("beam_alignment_scope", "scram_key"):
        _write_json("models/item/%s.json" % item,
                    {"parent": "minecraft:item/handheld", "textures": {"layer0": "neropower:item/" + item}})
        _write_json("items/%s.json" % item,
                    {"model": {"type": "minecraft:model", "model": "neropower:item/" + item}})

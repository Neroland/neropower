"""Storage family: Surge Bank, Momentum Wheel, Storage Ring Controller and Coil, the three Power
Cells, the renderer sprites they need and the quench vapour particle.

Reads, one per block (none is a centred orb, a ring target or a bar graph):

* Surge Bank: two stepped rows of three upright cans between two low bus bars. Charge = how many caps glow
  (``surge_bank_front_0`` .. ``_6``; front row fills first, left to right).
* Momentum Wheel: a rim wheel on a cross axle inside a guard cage. The rim, its blur twin and the
  tread are renderer sprites; the one cyan tick is the rim's balance mark.
* Storage Ring Controller: a square gauge of sixteen sockets round a flag window, two frosted
  cryo pipes carried clear of the cabinet. The renderer lights the sockets (arc violet) and shows the flag.
* Storage Ring Coil: a wound tube in a cindersteel cradle, with a faint static violet seam.
* Power Cells: a hand canister with a carry loop; 1 / 2 / 3 bands, then alloy / cindersteel /
  violet inlay.

This module also writes the family's models, blockstates and item definitions
(``write_models()``): the Surge Bank maps each can to its own part of the front sheet, which is
easier to keep right from code than by hand. Run::

    python -B -c "import sys; sys.path.insert(0, 'tools'); from painters import storage; storage.write_models()"
"""
import json
import os

from .kit import *  # noqa: F401,F403
from .kit import _mix

ICE = [(214, 232, 244, 255), (176, 206, 228, 255), (236, 244, 250, 255)]
STEEL = [(96, 108, 118, 255), (62, 72, 82, 255), (150, 164, 176, 255)]      # wheel rim: base, shade, light

# Surge Bank can columns on the front sheet (texel x of the 6-wide body), left to right as seen.
CAN_COLS = (3, 13, 23)
# Rows on the front sheet: back caps, back upper bodies, front caps, shared can body, plinth.
ROW_BACK_CAP, ROW_BACK_BODY, ROW_FRONT_CAP, ROW_BODY, ROW_PLINTH = 2, 4, 10, 12, 26
# Storage Ring gauge: sixteen 2x2 sockets on the rim of a 5x5 grid, clockwise from the top left.
GAUGE_X, GAUGE_Y, GAUGE_PITCH = 9, 11, 3


def gauge_cells():
    """Texel origins of the sixteen gauge sockets, clockwise from the top-left corner."""
    cells = [(i, 0) for i in range(5)] + [(4, j) for j in range(1, 5)]
    cells += [(i, 4) for i in range(3, -1, -1)] + [(0, j) for j in range(3, 0, -1)]
    return [(GAUGE_X + i * GAUGE_PITCH, GAUGE_Y + j * GAUGE_PITCH) for i, j in cells]


# ---------------------------------------------------------------- Surge Bank

def _cap(px, x0, y0, lit):
    """A domed can cap, 4x2 texels. Columns are uniform top to bottom so it can stretch on a top face."""
    if lit:
        cols = (P_AMBER, P_GLOW, P_HOT, P_AMBER)
    else:
        cols = (ALLOY[3], A_LIGHT, ALLOY[1], A_DARK)
    for i, col in enumerate(cols):
        px[x0 + i, y0] = col
        px[x0 + i, y0 + 1] = col


def _can_band(px, x0, y, lit):
    for x in range(x0, x0 + 6):
        edge = x in (x0, x0 + 5)
        px[x, y] = (P_AMBER if not edge else _mix(P_AMBER, A_DARK, 0.5)) if lit else P_EMBER
        px[x, y + 1] = (_mix(P_AMBER, A_DARK, 0.55) if lit else A_DARK)


def surge_bank_front(n):
    name = "surge_bank_front_%d" % n
    img = new_img()
    px = img.load()
    rng = rng_for("surge_bank_front")
    # Plinth (rows 26..31) with the lamp well; the rails' edges at the far left and right.
    for y in range(ROW_PLINTH, S):
        for x in range(S):
            px[x, y] = rng.choice(ALLOY)
    for x in range(S):
        px[x, ROW_PLINTH] = A_LIGHT
        px[x, S - 1] = A_DARK
    for y in range(20, ROW_PLINTH):
        for x, col in ((0, CINDER[2]), (1, CINDER[1]), (30, CINDER[0]), (31, CINDER[1])):
            px[x, y] = col
    rect(px, 2, 27, 5, 30, SOCKET)
    px[2, 27] = px[3, 27] = px[4, 27] = px[5, 27] = SOCKET_SHADOW
    tell(px, 15, 29)
    for i, cx in enumerate(CAN_COLS):
        front_lit = n > i
        back_lit = n > i + 3
        # Back row: upper body and cap, seen over the front row.
        can(px, cx, ROW_BACK_BODY, cx + 5, ROW_BACK_BODY + 5, body=ALLOY[2])
        _can_band(px, cx, ROW_BACK_BODY, back_lit)
        _cap(px, cx + 1, ROW_BACK_CAP, back_lit)
        # Front row: body with one cindersteel clamp band, and its cap.
        can(px, cx, ROW_BODY, cx + 5, ROW_PLINTH - 1, body=ALLOY[1], band=CINDER[0])
        _can_band(px, cx, ROW_BODY, front_lit)
        _cap(px, cx + 1, ROW_FRONT_CAP, front_lit)
    save(img, "block", name)
    return img


def surge_bank_side():
    img = machine_base("surge_bank_side")
    px = img.load()
    # The bus-bar rail (rows 20..25): a low cindersteel bar with a bright top edge and three bolts.
    rect(px, 2, 20, 29, 25, CINDER[0])
    for x in range(2, 30):
        px[x, 20] = CINDER[2]
        px[x, 25] = CINDER[1]
    for bx in (6, 15, 24):
        px[bx, 22] = A_LIGHT
        px[bx + 1, 22] = A_DARK
    for x in range(S):
        px[x, ROW_PLINTH] = A_LIGHT
    save(img, "block", "surge_bank_side")


def surge_bank_top():
    img = machine_base("surge_bank_top")
    px = img.load()
    family_mark(px, "storage")
    for cz in (4, 20):                      # the six can seats
        for cx in CAN_COLS:
            frame(px, cx, cz, cx + 5, cz + 7, A_DARK)
            rect(px, cx + 1, cz + 1, cx + 4, cz + 6, SOCKET)
    save(img, "block", "surge_bank_top")


def surge_bank_spark():
    """Renderer sprite: a jagged discharge between the rails. Cutout, drawn for a few ticks."""
    img = new_img()
    px = img.load()
    pts = [(0, 17), (5, 12), (9, 19), (14, 10), (18, 20), (23, 13), (27, 18), (31, 14)]
    trace(px, pts, col=P_GLOW, halo=P_HOT)
    for (x, y) in ((9, 20), (14, 9), (18, 21)):
        px[x, y] = P_AMBER
    save(img, "block", "surge_bank_spark")


# ---------------------------------------------------------------- Momentum Wheel

def momentum_wheel_side():
    img = front_base("momentum_wheel_side")
    px = img.load()
    # Pillow-block bearing housing where the block's side faces land: a bolted square flange.
    flange(px, 11, 13, 20, 22)
    rib(px, 8, 24, 23, 29, 3, vertical=False)
    for x in range(2, 30):
        px[x, 30] = A_DARK
    save(img, "block", "momentum_wheel_side")


def momentum_wheel_cage():
    img = new_img()
    px = img.load()
    rng = rng_for("momentum_wheel_cage")
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice((ALLOY[2], ALLOY[2], ALLOY[0]))
    for i in range(S):
        px[i, 0] = px[0, i] = _mix(A_LIGHT, ALLOY[0], 0.3)
        px[i, 1] = px[1, i] = _mix(A_LIGHT, ALLOY[0], 0.7)
        px[i, S - 1] = px[S - 1, i] = A_DARK
    for y in range(4, 28, 6):               # slotted guard: short dark slots in two columns
        for x0 in (6, 20):
            rect(px, x0, y, x0 + 5, y + 1, SOCKET_SHADOW)
            for x in range(x0, x0 + 6):
                px[x, y + 2] = _mix(A_LIGHT, A_DARK, 0.55)
    save(img, "block", "momentum_wheel_cage")


def _wheel(name, blur):
    img = new_img()
    px = img.load()
    rng = rng_for(name)

    def fn(d, ang, x, y):
        if 11.0 <= d <= 15.6:               # the heavy rim
            if d > 14.6:
                return STEEL[1]
            if d < 12.0:
                return A_DARK
            if blur:
                return STEEL[2] if int((ang + math.pi) * 9.0) % 3 == 0 else STEEL[0]
            lit = math.cos(ang + 2.2)
            return STEEL[2] if lit > 0.55 else (STEEL[1] if lit < -0.55 else STEEL[0])
        if d <= 2.6:                        # axle boss, unlit
            return ALLOY[3] if d < 1.6 else A_DARK
        if blur:
            if 3.0 < d < 11.0 and (x + y) % 2 == 0 and rng.random() < 0.55:
                return _mix(STEEL[0], A_DARK, 0.35)
            return None
        for k in range(3):                  # three slim spokes
            a = k * 2.0 * math.pi / 3.0 - math.pi / 2.0
            dist = abs(math.sin(ang - a)) * d
            if math.cos(ang - a) > 0 and dist < 1.0:
                return STEEL[2] if dist < 0.45 else STEEL[1]
        return None

    radial(px, fn)
    if not blur:                            # the balance mark: the block's one cyan tick
        px[15, 3] = T_CYAN
        px[16, 3] = T_CYAN
        px[15, 2] = px[16, 2] = A_DARK
    save(img, "block", name)


def momentum_wheel_tread():
    """Renderer sprite for the rim's tread, seen edge-on. Left half: plain grooved steel. Right
    half: the same with the balance weight, used on one tread segment so a slow turn can be read."""
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            k = x % 16
            col = STEEL[1] if k in (0, 15) else (STEEL[2] if k in (3, 4) else STEEL[0])
            if k in (7, 8):
                col = A_DARK                # centre groove
            px[x, y] = col
        if y % 8 == 0:
            for x in range(S):
                px[x, y] = _mix(px[x, y], A_DARK, 0.5)
    rect(px, 18, 12, 29, 19, A_LIGHT)
    frame(px, 18, 12, 29, 19, A_DARK)
    save(img, "block", "momentum_wheel_tread")


# ---------------------------------------------------------------- Storage Ring

def _cinder_strip(px, y0, y1, rng):
    for y in range(y0, y1 + 1):
        for x in range(S):
            px[x, y] = rng.choice((CINDER[0], CINDER[0], CINDER[1]))
    for x in range(S):
        px[x, y0] = CINDER[2]
        px[x, y1] = CINDER[1]


def smes_controller_front(alarm):
    name = "smes_controller_front_alarm" if alarm else "smes_controller_front"
    img = machine_base("smes_controller_front")
    px = img.load()
    for x in range(2, 30):                  # the bezel's top edge (rows 10..25 are the bezel)
        px[x, 9] = A_DARK
        px[x, 10] = A_LIGHT
    status_socket(px, 2, 12)                # the lamp sits on the bezel, below the pipes
    _cinder_strip(px, 26, 31, rng_for("smes_controller_base"))
    hazard_edge(px, lit=alarm)
    # The square gauge: sixteen dark sockets (the renderer lights the outer half of each).
    for (gx, gy) in gauge_cells():
        rect(px, gx, gy, gx + 1, gy + 1, SOCKET)
        px[gx, gy] = SOCKET_SHADOW
    trip_flag(px, 13, 16, shown=False)
    tell(px, 26, 23)
    save(img, "block", name)
    return img


def smes_controller_side():
    img = machine_base("smes_controller_side")
    px = img.load()
    _cinder_strip(px, 26, 31, rng_for("smes_controller_base"))
    louvre(px, 5, 11, 26, 16)
    rib(px, 5, 18, 26, 24, 4)
    save(img, "block", "smes_controller_side")


def smes_controller_top():
    img = machine_base("smes_controller_top")
    px = img.load()
    family_mark(px, "storage")
    recess(px, 12, 12, 19, 19)
    save(img, "block", "smes_controller_top")


def smes_controller_pipe():
    """A frosted cryo pipe running across the tile. Banded every six rows so any face of a
    three-unit pipe shows a whole pipe, whatever rows its automatic UV lands on."""
    img = new_img()
    px = img.load()
    rng = rng_for("smes_controller_pipe")
    band = (_mix(ALLOY[1], A_DARK, 0.5), ALLOY[1], _mix(ALLOY[3], A_LIGHT, 0.5), ALLOY[3], ALLOY[0], A_DARK)
    for y in range(S):
        for x in range(S):
            px[x, y] = band[y % 6]
            if x % 16 in (5, 6):
                px[x, y] = _mix(ICE[1], A_DARK, 0.25 if x % 16 == 5 else 0.5)   # identification ring
            elif rng.random() < 0.34:
                px[x, y] = rng.choice(ICE)
    save(img, "block", "smes_controller_pipe")


def smes_controller_frost():
    """Renderer sprite: rime on a cold coil's back, sparse cutout dither, tinted by the renderer."""
    img = new_img()
    px = img.load()
    rng = rng_for("smes_controller_frost")
    for y in range(S):
        for x in range(S):
            t = abs(y - 15.5) / 15.5
            if rng.random() < 0.55 * (1.0 - t) ** 1.2:
                px[x, y] = rng.choice(ICE)
    save(img, "block", "smes_controller_frost")


def smes_controller_field():
    """Renderer sprite: the field ribbon, white (the renderer tints it arc violet). A ragged band
    across the middle of the tile, open at both ends so neighbouring segments join."""
    img = new_img()
    px = img.load()
    rng = rng_for("smes_controller_field")
    for x in range(S):
        top = 11 + (1 if rng.random() < 0.3 else 0)
        bot = 20 - (1 if rng.random() < 0.3 else 0)
        for y in range(top, bot + 1):
            core = 14 <= y <= 17
            px[x, y] = (255, 255, 255, 255) if core else (196, 196, 196, 255)
        if x % 5 == 2:
            px[x, 9] = (150, 150, 150, 255)
            px[x, 22] = (150, 150, 150, 255)
    save(img, "block", "smes_controller_field")


def smes_coil():
    img = new_img()
    px = img.load()
    turn = (A_DARK, ALLOY[2], ALLOY[3], ALLOY[1])          # one turn of the winding, four texels
    for y in range(S):
        for x in range(S):
            col = turn[x % 4]
            if x % 16 in (0, 1):
                col = CINDER[0] if x % 16 == 0 else CINDER[1]   # clamp band
            px[x, y] = col
    for x in range(S):                                      # the faint static seam
        if x % 16 not in (0, 1):
            px[x, 14] = _mix(V_ARC, A_DARK, 0.30)
    save(img, "block", "smes_coil")


def smes_coil_cradle():
    img = new_img()
    px = img.load()
    rng = rng_for("smes_coil_cradle")
    for y in range(S):
        for x in range(S):
            px[x, y] = rng.choice((CINDER[0], CINDER[0], CINDER[1]))
    bevel(img, light=CINDER[2], dark=_mix(CINDER[1], A_DARK, 0.5))
    rivets(img)
    for x in range(6, 26):
        px[x, 20] = CINDER[1]
        px[x, 21] = CINDER[2]
    tell(px, 15, 28)
    save(img, "block", "smes_coil_cradle")


# ---------------------------------------------------------------- items, particle

def power_cell(tier, key):
    img = new_img()
    px = img.load()
    x0, x1 = 10, 21
    can(px, x0, 9, x1, 29, body=ALLOY[1] if tier < 2 else ALLOY[0])
    rect(px, x0 + 2, 7, x1 - 2, 8, ALLOY[3])                # neck
    for x in range(x0 + 2, x1 - 1):
        px[x, 7] = A_LIGHT
    px[15, 8] = P_HOT                                       # terminal
    px[16, 8] = P_AMBER
    # Carry loop: a squared hoop over the neck.
    for x in range(x0 + 2, x1 - 1):
        px[x, 2] = A_LIGHT
        px[x, 3] = ALLOY[3]
    for y in range(3, 7):
        px[x0 + 1, y] = A_LIGHT
        px[x0 + 2, y] = ALLOY[3]
        px[x1 - 1, y] = ALLOY[3]
        px[x1 - 2, y] = A_LIGHT
    rows = {0: (18,), 1: (14, 22), 2: (12, 18, 24)}[tier]
    trim = (ALLOY[3], CINDER[0], V_ARC)[tier]
    for y in rows:                                          # bands: count first, colour second
        for x in range(x0, x1 + 1):
            px[x, y] = A_LIGHT if x0 < x < x1 else ALLOY[3]
            px[x, y + 1] = A_DARK
        for x in range(13, 19):
            px[x, y + 1] = trim
    for y in range(9, 30):                                  # outline
        px[x0 - 1, y] = A_DARK
        px[x1 + 1, y] = A_DARK
    for x in range(x0, x1 + 1):
        px[x, 30] = A_DARK
    save(img, "item", "power_cell_" + key)


def quench_vapour(i):
    """Particle frame ``i`` of 4: a cold puff that spreads and thins."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    rng = rng_for("quench_vapour_%d" % i)
    radius = 3.0 + 1.5 * i
    keep = 0.95 - 0.2 * i
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5 + 0.3 * i)
            if d <= radius and rng.random() < keep * (1.0 - 0.45 * d / radius):
                px[x, y] = ICE[2] if d < radius * 0.45 else rng.choice(ICE)
    save(img, "particle", "quench_vapour_%d" % i)


def paint():
    for n in range(7):
        surge_bank_front(n)
    surge_bank_side()
    surge_bank_top()
    surge_bank_spark()
    momentum_wheel_side()
    momentum_wheel_cage()
    _wheel("momentum_wheel_rim", False)
    _wheel("momentum_wheel_rim_blur", True)
    momentum_wheel_tread()
    smes_controller_front(False)
    smes_controller_front(True)
    smes_controller_side()
    smes_controller_top()
    smes_controller_pipe()
    smes_controller_frost()
    smes_controller_field()
    smes_coil()
    smes_coil_cradle()
    for tier, key in enumerate(("basic", "advanced", "elite")):
        power_cell(tier, key)
    for i in range(4):
        quench_vapour(i)


SHEET = [
    {"id": "surge_bank", "block": True, "faces": ["surge_bank_side", "surge_bank_top", "surge_bank_spark"],
     "states": ["surge_bank_front_%d" % n for n in range(7)]},
    {"id": "momentum_wheel", "block": True,
     "faces": ["momentum_wheel_side", "momentum_wheel_cage", "momentum_wheel_rim", "momentum_wheel_rim_blur",
               "momentum_wheel_tread"]},
    {"id": "smes_controller", "block": True,
     "faces": ["smes_controller_side", "smes_controller_top", "smes_controller_pipe", "smes_controller_frost",
               "smes_controller_field"],
     "states": ["smes_controller_front", "smes_controller_front_alarm"]},
    {"id": "smes_coil", "block": True, "faces": ["smes_coil", "smes_coil_cradle"]},
    {"id": "smes_coil (corner)", "faces": [], "model": "block/smes_coil_corner"},
    {"id": "power cells", "model": None,
     "faces": ["item:power_cell_basic", "item:power_cell_advanced", "item:power_cell_elite"]},
    {"id": "quench_vapour", "model": None, "faces": ["particle:quench_vapour_%d" % i for i in range(4)]},
]
LOOKALIKES = ["battery_bank_controller_front", "battery_cell_basic_front_2", "battery_cell_elite_front_4",
              "stirling_flywheel"]
NEROTECH_LOOKALIKES = ["battery_bank_front", "battery_bank_top", "accelerator_coil"]
AUDIT = {"allow_lime": False, "allow_charge": True}       # the storage family mark is a charge tick


# ---------------------------------------------------------------- models

def _tex(u0, v0, u1, v1):
    """A texel rectangle (x0, y0 inclusive; x1, y1 exclusive) as model UV (16 = the whole tile)."""
    return [u0 / 2.0, v0 / 2.0, u1 / 2.0, v1 / 2.0]


def _box(frm, to, tex, cull=(), name=None, uv=None, rot=None):
    """One element. ``tex``: a texture key for every face, or {face: key}. ``cull``: faces lying on
    the block boundary. ``uv``: {face: [u0, v0, u1, v1]} for faces that do not use automatic UV;
    ``rot``: {face: degrees}."""
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
    el["faces"] = faces
    return el


_ALL = ("north", "south", "west", "east", "up", "down")


def _socket(x0=13, y0=12, tex="front"):
    return _box((x0, y0, -0.5), (x0 + 2, y0 + 2, 0), tex, name="status_socket",
                uv={f: _tex(2, 4, 6, 8) for f in _ALL})


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


def surge_bank_model():
    """Plinth, two rails, three short cans in front and three tall ones behind, each can and cap
    mapped to its own column of the front sheet so the lit caps follow the ``cans`` state."""
    ns = ("north", "south", "west", "east")
    els = [
        _box((0, 0, 0), (16, 3, 16), {"north": "front", "south": "side", "west": "side", "east": "side",
                                      "up": "top", "down": "top"}, cull=("down", "north", "south", "west", "east")),
        _box((0, 3, 1), (1, 6, 15), "side", cull=("west",)),
        _box((15, 3, 1), (16, 6, 15), "side", cull=("east",)),
    ]
    xs = (11.5, 6.5, 1.5)                                    # model x of the columns, left to right as seen
    for i, cx in enumerate(CAN_COLS):
        x = xs[i]
        body = _tex(cx, ROW_BODY, cx + 6, ROW_PLINTH)
        plain = _tex(cx, ROW_BODY + 4, cx + 6, ROW_BODY + 10)
        fcap = _tex(cx + 1, ROW_FRONT_CAP, cx + 5, ROW_FRONT_CAP + 2)
        upper = _tex(cx, ROW_BACK_BODY, cx + 6, ROW_BACK_BODY + 6)
        bcap = _tex(cx + 1, ROW_BACK_CAP, cx + 5, ROW_BACK_CAP + 2)
        faces = {f: "front" for f in ns + ("up",)}
        els.append(_box((x, 3, 3), (x + 3, 10, 6), faces, uv=dict({f: body for f in ns}, up=plain)))
        els.append(_box((x + 0.5, 10, 3.5), (x + 2.5, 11, 5.5), faces, uv={f: fcap for f in ns + ("up",)}))
        els.append(_box((x, 3, 10), (x + 3, 10, 13), {f: "front" for f in ns}, uv={f: body for f in ns}))
        els.append(_box((x, 10, 10), (x + 3, 13, 13), faces, uv=dict({f: upper for f in ns}, up=plain)))
        els.append(_box((x + 0.5, 13, 10.5), (x + 2.5, 14, 12.5), faces, uv={f: bcap for f in ns + ("up",)}))
    els.append(_box((13, 0.5, -0.5), (15, 2.5, 0), "front", name="status_socket",
                    uv={f: _tex(2, 27, 6, 31) for f in _ALL}))
    return els


def momentum_wheel_model():
    s, c = "side", "cage"
    return [
        _box((0, 0, 0), (16, 1, 16), s, cull=("down", "north", "south", "west", "east")),
        _box((0, 1, 0), (1, 16, 1), c, cull=("north", "west", "up")),
        _box((15, 1, 0), (16, 16, 1), c, cull=("north", "east", "up")),
        _box((0, 1, 15), (1, 16, 16), c, cull=("south", "west", "up")),
        _box((15, 1, 15), (16, 16, 16), c, cull=("south", "east", "up")),
        _box((1, 15, 0), (15, 16, 1), c, cull=("north", "up")),
        _box((1, 15, 15), (15, 16, 16), c, cull=("south", "up")),
        _box((0, 15, 1), (1, 16, 15), c, cull=("west", "up")),
        _box((15, 15, 1), (16, 16, 15), c, cull=("east", "up")),
        _box((1, 1, 6), (4, 10, 10), s),                    # pillow blocks
        _box((12, 1, 6), (15, 10, 10), s),
        _box((4, 7, 7), (12, 9, 9), s),                     # axle
        _box((6, 5, 5), (10, 11, 11), c),                   # hub drum (the rim is the renderer's)
        _box((11, 11, 0), (15, 15, 1), s, cull=("north",)),  # gusset plate carrying the lamp
        _socket(tex="side"),
    ]


def smes_controller_model():
    base = {"north": "front", "south": "side", "west": "side", "east": "side", "up": "top", "down": "top"}
    pipe_uv = {"west": _tex(0, 0, 6, 6), "east": _tex(0, 0, 6, 6)}
    return [
        _box((0, 0, 0), (16, 3, 16), base, cull=("down", "north", "south", "west", "east")),
        _box((2, 3, 1), (14, 11, 14), {"south": "side", "west": "side", "east": "side", "up": "top"}),
        _box((1, 3, 0), (15, 11, 1), {"north": "front", "south": "side", "west": "side", "east": "side",
                                      "up": "top"}, cull=("north",)),
        _box((0, 13, 3), (16, 16, 6), "pipe", cull=("west", "east", "up"), uv=pipe_uv),
        _box((0, 13, 10), (16, 16, 13), "pipe", cull=("west", "east", "up"), uv=pipe_uv),
        _box((6, 11, 3), (10, 13, 13), {"north": "top", "south": "top", "west": "top", "east": "top"}),
        _box((0, 5, 4), (2, 11, 12), "coil", cull=("west",), rot={"up": 90, "down": 90}),
        _box((14, 5, 4), (16, 11, 12), "coil", cull=("east",), rot={"up": 90, "down": 90}),
        _box((4, 5, 14), (12, 11, 16), "coil", cull=("south",)),
        _box((13, 8, -0.5), (15, 10, 0), "front", name="status_socket", uv={f: _tex(2, 12, 6, 16) for f in _ALL}),
    ]


def smes_coil_models():
    cr, co = "cradle", "coil"
    straight = [
        _box((0, 0, 2), (16, 3, 14), cr, cull=("down", "west", "east")),
        _box((2, 3, 3), (4, 8, 13), cr),
        _box((12, 3, 3), (14, 8, 13), cr),
        _box((0, 5, 4), (16, 13, 12), co, cull=("west", "east")),
    ]
    corner = [                                               # joins north and east
        _box((2, 0, 0), (16, 3, 14), cr, cull=("down", "north", "east")),
        _box((3, 3, 2), (13, 8, 4), cr),
        _box((12, 3, 3), (14, 8, 13), cr),
        _box((4, 5, 0), (12, 13, 12), co, cull=("north",), rot={"up": 90, "down": 90}),
        _box((12, 5, 4), (16, 13, 12), co, cull=("east",)),
    ]
    return straight, corner


def write_models():
    """Write every model, blockstate and item definition of the family (CRLF, like the repo)."""
    ns = "neropower:block/"
    els = surge_bank_model()                                # one geometry, seven front sheets
    _write_json("models/block/surge_bank.json", _model({
        "particle": ns + "surge_bank_side", "front": ns + "surge_bank_front_0",
        "side": ns + "surge_bank_side", "top": ns + "surge_bank_top"}, els))
    variants = {}
    for n in range(7):
        ref = ns + "surge_bank" if n == 0 else ns + "surge_bank_cans%d" % n
        if n:
            _write_json("models/block/surge_bank_cans%d.json" % n,
                        {"parent": ns + "surge_bank", "textures": {"front": ns + "surge_bank_front_%d" % n}})
        variants.update(_facing(ref, "cans=%d," % n))
    _write_json("blockstates/surge_bank.json", {"variants": variants})

    _write_json("models/block/momentum_wheel.json", _model({
        "particle": ns + "momentum_wheel_side", "side": ns + "momentum_wheel_side",
        "cage": ns + "momentum_wheel_cage"}, momentum_wheel_model()))
    _write_json("blockstates/momentum_wheel.json", {"variants": _facing(ns + "momentum_wheel")})

    _write_json("models/block/smes_controller.json", _model({
        "particle": ns + "smes_controller_side", "front": ns + "smes_controller_front",
        "side": ns + "smes_controller_side", "top": ns + "smes_controller_top",
        "pipe": ns + "smes_controller_pipe", "coil": ns + "smes_coil"}, smes_controller_model()))
    _write_json("models/block/smes_controller_alarm.json",
                {"parent": ns + "smes_controller", "textures": {"front": ns + "smes_controller_front_alarm"}})
    variants = _facing(ns + "smes_controller", "alarm=false,")
    variants.update(_facing(ns + "smes_controller_alarm", "alarm=true,"))
    _write_json("blockstates/smes_controller.json", {"variants": variants})

    straight, corner = smes_coil_models()
    tex = {"particle": ns + "smes_coil_cradle", "coil": ns + "smes_coil", "cradle": ns + "smes_coil_cradle"}
    _write_json("models/block/smes_coil.json", _model(tex, straight))
    _write_json("models/block/smes_coil_corner.json", _model(tex, corner))
    _write_json("blockstates/smes_coil.json", {"variants": {
        "shape=straight_x": {"model": ns + "smes_coil"},
        "shape=straight_z": {"model": ns + "smes_coil", "y": 90},
        "shape=corner_ne": {"model": ns + "smes_coil_corner"},
        "shape=corner_se": {"model": ns + "smes_coil_corner", "y": 90},
        "shape=corner_sw": {"model": ns + "smes_coil_corner", "y": 180},
        "shape=corner_nw": {"model": ns + "smes_coil_corner", "y": 270}}})

    for block in ("surge_bank", "momentum_wheel", "smes_controller", "smes_coil"):
        _write_json("models/item/%s.json" % block, {"parent": ns + block})
        _write_json("items/%s.json" % block, {"model": {"type": "minecraft:model", "model": ns + block}})
    for key in ("basic", "advanced", "elite"):
        item = "power_cell_" + key
        _write_json("models/item/%s.json" % item,
                    {"parent": "minecraft:item/generated", "textures": {"layer0": "neropower:item/" + item}})
        _write_json("items/%s.json" % item,
                    {"model": {"type": "minecraft:model", "model": "neropower:item/" + item}})
    _write_json("particles/quench_vapour.json",
                {"textures": ["neropower:quench_vapour_%d" % i for i in range(4)]})

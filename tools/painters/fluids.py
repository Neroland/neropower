"""Fluid sprites: steam, exhaust steam and heavy water.

Each fluid has ``fluid/<id>_still`` (32x512: 16 frames, frametime 2) and ``fluid/<id>_flow``
(one 32x32 tile, tileable both ways; whatever draws it scrolls it). They tile seamlessly in x, y
and time: the art is periodic value noise on a wrapped lattice.

The fluid models multiply the sprite by the fluid's tint, so the sprites are painted
TINT-RELATIVE: the wanted on-screen colour divided by the tint (white = the tint itself). A tint
is a ceiling, so heavy water's caustic lines top out at its base blue and the body sits below it.
Set ``TINTED = False`` to paint true colours instead, should the models ever stop tinting.
"""
from .kit import *  # noqa: F401,F403
from .kit import _mix

FRAMES = 16
FRAMETIME = 2
TINTED = True

# The tints the fluid models apply (they are the fluids' registered colours).
TINT = {"steam": STEAM_W, "exhaust_steam": STEAM_X, "heavy_water": HW_BLUE}

_EXHAUST_DIM = (146, 156, 164, 255)


def _lattice(rng, nx, ny, nt):
    return [[[rng.random() for _x in range(nx)] for _y in range(ny)] for _t in range(nt)]


def _smooth(t):
    return t * t * (3.0 - 2.0 * t)


def _sample(lat, x, y, t):
    """Trilinear, smoothstepped, wrapping on every axis."""
    nt, ny, nx = len(lat), len(lat[0]), len(lat[0][0])
    x0, y0, t0 = int(math.floor(x)), int(math.floor(y)), int(math.floor(t))
    fx, fy, ft = _smooth(x - x0), _smooth(y - y0), _smooth(t - t0)
    acc = 0.0
    for dt, wt in ((0, 1.0 - ft), (1, ft)):
        for dy, wy in ((0, 1.0 - fy), (1, fy)):
            row = lat[(t0 + dt) % nt][(y0 + dy) % ny]
            acc += wt * wy * ((1.0 - fx) * row[x0 % nx] + fx * row[(x0 + 1) % nx])
    return acc


def field(name, cells, tcells, frames, octaves=2):
    """``frames`` SxS grids of 0..1 noise. ``cells`` = (across, down) lattice cells per tile,
    ``tcells`` = cells per loop; each octave doubles them at half the weight."""
    rng = rng_for(name)
    lats = []
    for o in range(octaves):
        k = 2 ** o
        lats.append((_lattice(rng, cells[0] * k, cells[1] * k, max(1, tcells * k)), k, 0.5 ** o))
    total = sum(w for _l, _k, w in lats)
    out = []
    for f in range(frames):
        grid = []
        for y in range(S):
            row = []
            for x in range(S):
                v = 0.0
                for lat, k, w in lats:
                    v += w * _sample(lat, x * cells[0] * k / float(S), y * cells[1] * k / float(S),
                                     f * max(1, tcells * k) / float(frames))
                row.append(v / total)
            grid.append(row)
        out.append(grid)
    lo = min(min(min(r) for r in g) for g in out)
    hi = max(max(max(r) for r in g) for g in out)
    span = (hi - lo) or 1.0
    return [[[(v - lo) / span for v in r] for r in g] for g in out]


def _relative(col, tint, alpha):
    """The sprite pixel that shows as ``col`` once the model multiplies it by ``tint``."""
    if not TINTED:
        return col[:3] + (alpha,)
    return tuple(min(255, int(round(255.0 * col[i] / max(1, tint[i])))) for i in range(3)) + (alpha,)


def _steam_px(v, tint, ramp, alphas):
    i = min(len(ramp) - 1, int(v * len(ramp)))
    return _relative(ramp[i], tint, alphas[i])


STEAM_LOOK = ([_mix(STEAM_W, STEAM_X, 0.80), _mix(STEAM_W, STEAM_X, 0.50), _mix(STEAM_W, STEAM_X, 0.22),
               STEAM_W, STEAM_W],
              [188, 204, 220, 234, 244])
EXHAUST_LOOK = ([_mix(STEAM_X, _EXHAUST_DIM, 0.70), _mix(STEAM_X, _EXHAUST_DIM, 0.40),
                 _mix(STEAM_X, _EXHAUST_DIM, 0.18), STEAM_X],
                [168, 180, 192, 204])


def _water_px(n1, n2, body, tint):
    """Heavy water: caustic lines run where two drifting noise fields cross (a net of thin
    meandering ridges, never closed rings); ``body`` is the slow depth underneath."""
    gap = abs(n1 - n2)
    if gap < 0.035:
        col = HW_LIGHT
    elif gap < 0.075:
        col = HW_BLUE
    else:
        step = min(3, int(body * 4))
        col = _mix(HW_SHADE, HW_BLUE, 0.16 + 0.13 * step)
    return _relative(col, tint, 236)


def _gas(fluid, look, cells, flow_cells, tcells):
    tint = TINT[fluid]
    ramp, alphas = look
    frames = []
    for grid in field(fluid + "_still", cells, tcells, FRAMES):
        img = new_img()
        px = img.load()
        for y in range(S):
            for x in range(S):
                px[x, y] = _steam_px(grid[y][x], tint, ramp, alphas)
        frames.append(img)
    save_strip(frames, "block", "fluid/%s_still" % fluid, FRAMETIME)
    grid = field(fluid + "_flow", flow_cells, 1, 1)[0]
    img = new_img()
    px = img.load()
    for y in range(S):
        for x in range(S):
            px[x, y] = _steam_px(grid[y][x], tint, ramp, alphas)
    save(img, "block", "fluid/%s_flow" % fluid)


def gen_steam():
    """Soft white billow, large slow cells; the flow tile is streaked along its travel."""
    _gas("steam", STEAM_LOOK, (3, 3), (5, 2), 2)


def gen_exhaust_steam():
    """Greyer, flatter, smaller cells, lower contrast."""
    _gas("exhaust_steam", EXHAUST_LOOK, (5, 5), (7, 3), 2)


def _water(name, cells, frames):
    tint = TINT["heavy_water"]
    a = field(name + "_a", cells, 2, frames, octaves=2)
    b = field(name + "_b", cells, 2, frames, octaves=2)
    depth = field(name + "_depth", (2, 2), 1, frames, octaves=2)
    out = []
    for f in range(frames):
        img = new_img()
        px = img.load()
        for y in range(S):
            for x in range(S):
                px[x, y] = _water_px(a[f][y][x], b[f][y][x], depth[f][y][x], tint)
        out.append(img)
    return out


def gen_heavy_water():
    """Deep blue with slow, wide caustics (water's rhythm at half speed)."""
    save_strip(_water("heavy_water_still", (3, 3), FRAMES), "block", "fluid/heavy_water_still", FRAMETIME)
    save(_water("heavy_water_flow", (4, 2), 1)[0], "block", "fluid/heavy_water_flow")


def paint():
    gen_steam()
    gen_exhaust_steam()
    gen_heavy_water()


# Contact-sheet rows: the tint is applied for the preview, as the fluid model does in game.
SHEET = [
    {"id": fluid, "faces": ["fluid/%s_still" % fluid, "fluid/%s_flow" % fluid],
     "tint": TINT[fluid][:3] if TINTED else None}
    for fluid in ("steam", "exhaust_steam", "heavy_water")
]

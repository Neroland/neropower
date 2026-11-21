"""Fission family (0.4.0): reactor interior blocks, the shell ports, the steam-mode core front and
the fuel-line items.

Blocks: graphite_moderator, moderator_tank, breeder_blanket, europium_control_rod_assembly,
reactor_steam_port, reactor_control_computer. The three interior blocks are plain cubes; the
europium assembly is an open frame like the control rod assembly; the two shell ports are machine
models whose textures are laid out for the models' automatic UVs (see the notes on each painter).

State sprites (the ``_idle`` / ``_working`` / ``_starved`` / ``_backed_up`` / ``_tripped`` /
``_alarm`` fronts) differ in SHAPE, not only colour: hollow rings, a turned key, a flag, lit hazard.
"""
from .kit import *  # noqa: F401,F403
from .kit import _mix
from . import existing as _old
from .fluids import field

# Europium: a pale, slightly warm silver [base, shade, light]. Saturation stays under the audit's
# colour threshold, so it reads as metal, never as light.
EURO = [(214, 208, 196, 255), (170, 164, 154, 255), (240, 236, 226, 255)]
# Pallurite: slate with a violet cast, kept under the saturation threshold (it is a material, not
# the arc-violet field colour).
SLATE = [(98, 92, 120, 255), (70, 66, 90, 255), (134, 128, 156, 255)]
SLATE_FLECK = (206, 202, 220, 255)
FERTILE = [(150, 154, 148, 255), (116, 120, 114, 255), (184, 188, 180, 255)]


def _cinder_fill(px, rng, x0, y0, x1, y1):
    """Cindersteel plate: noisy body, lit top/left edge, shaded bottom/right edge."""
    body = [CINDER[0], CINDER[0], _mix(CINDER[0], CINDER[1], 0.45)]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = rng.choice(body)
    for x in range(x0, x1 + 1):
        px[x, y0] = CINDER[2]
        px[x, y1] = CINDER[1]
    for y in range(y0, y1 + 1):
        px[x0, y] = CINDER[2]
        px[x1, y] = CINDER[1]


# ---------------- interior cubes ----------------

def gen_graphite_moderator():
    """Matte black hex-brick courses. No alloy, no bevel highlight, nothing emissive."""
    rng = rng_for("graphite_moderator")
    img = new_img()
    px = img.load()
    centres = [(cx * 16 + (8 if j % 2 else 0), j * 8) for j in range(-1, 6) for cx in range(-1, 4)]
    fill = [GRAPHITE[1], GRAPHITE[1], _mix(GRAPHITE[1], GRAPHITE[0], 0.5), _mix(GRAPHITE[1], GRAPHITE[2], 0.25)]
    for y in range(S):
        for x in range(S):
            best = sorted((math.hypot((x + 0.5 - cx) * 0.62, (y + 0.5 - cy) * 1.0), cx, cy)
                          for cx, cy in centres)[:2]
            gap = best[1][0] - best[0][0]
            cy = best[0][2]
            if gap < 0.85:
                px[x, y] = GRAPHITE[0]
            elif gap < 1.9 and (y + 0.5) < cy:
                px[x, y] = GRAPHITE[2]                 # the course's upper lip, not a bevel
            else:
                px[x, y] = rng.choice(fill)
    tell(px, 15, 16)
    save(img, "block", "graphite_moderator")


def _tank_liquid(a, b, body):
    gap = abs(a - b)
    if gap < 0.035:
        return HW_LIGHT
    if gap < 0.08:
        return HW_BLUE
    return _mix(HW_SHADE, HW_BLUE, 0.14 + 0.13 * min(3, int(body * 4)))


def family_mark_inset(px, x0=5, y0=5):
    """The fission hazard corner, moved inside a frame."""
    for y in range(6):
        for x in range(6 - y):
            px[x0 + x, y0 + y] = HAZ_Y if ((x + y) // 2) % 2 == 0 else HAZ_K


def gen_moderator_tank():
    """Cindersteel frame, ribbed window, heavy water with a slow caustic (8 painted frames)."""
    frames_n = 8
    a = field("moderator_tank_a", (3, 3), 1, frames_n)
    b = field("moderator_tank_b", (3, 3), 1, frames_n)
    depth = field("moderator_tank_depth", (2, 2), 1, frames_n)
    frames = []
    for f in range(frames_n):
        img = new_img()
        px = img.load()
        _cinder_fill(px, rng_for("moderator_tank_window"), 0, 0, S - 1, S - 1)
        for y in range(4, 28):
            for x in range(4, 28):
                px[x, y] = _tank_liquid(a[f][y][x], b[f][y][x], depth[f][y][x])
        for x in range(4, 28):                          # inner shadow under the top rail, lip below
            px[x, 4] = _mix(HW_SHADE, A_DARK, 0.6)
            px[x, 27] = _mix(HW_SHADE, A_DARK, 0.35)
        for y in range(4, 28):
            px[4, y] = _mix(HW_SHADE, A_DARK, 0.6)
        for rx in (11, 20):                             # two ribs across the pane
            for y in range(3, 29):
                px[rx, y] = CINDER[2]
                px[rx + 1, y] = CINDER[1]
        for (bx, by) in ((1, 1), (29, 1), (1, 29), (29, 29)):   # frame bolts
            px[bx, by] = A_LIGHT
            px[bx + 1, by + 1] = A_DARK
        frames.append(img)
    save_strip(frames, "block", "moderator_tank_window", 6)

    img = new_img()
    px = img.load()
    rng = rng_for("moderator_tank_frame")
    _cinder_fill(px, rng, 0, 0, S - 1, S - 1)
    for y in range(4, 28):                              # alloy lid plate inside the frame
        for x in range(4, 28):
            px[x, y] = rng.choice(ALLOY)
    for i in range(4, 28):
        px[i, 4] = A_DARK
        px[4, i] = A_DARK
        px[i, 27] = _mix(A_LIGHT, A_DARK, 0.5)
        px[27, i] = _mix(A_LIGHT, A_DARK, 0.5)
    recess(px, 12, 12, 19, 19)                          # square filler cap
    rect(px, 14, 14, 17, 17, ALLOY[3])
    px[14, 14] = A_LIGHT
    px[17, 17] = A_DARK
    pipe_ring(px, 22, 12, 19, HW_BLUE)                  # what is inside
    for (bx, by) in ((1, 1), (29, 1), (1, 29), (29, 29)):
        px[bx, by] = A_LIGHT
        px[bx + 1, by + 1] = A_DARK
    family_mark_inset(px)
    tell(px, 15, 8)
    save(img, "block", "moderator_tank_frame")


def gen_breeder_blanket():
    """Quilted 3x3 pad panels, a rivet at each crossing, one fertile-grey inlay strip."""
    rng = rng_for("breeder_blanket")
    img = new_img()
    px = img.load()
    edges = [(0, 9), (11, 20), (22, 31)]
    for y in range(S):
        for x in range(S):
            px[x, y] = A_DARK                           # the stitched seams
    for (py0, py1) in edges:
        for (px0, px1) in edges:
            w, h = px1 - px0, py1 - py0
            for y in range(py0, py1 + 1):
                for x in range(px0, px1 + 1):
                    u = (x - px0) / float(w)
                    v = (y - py0) / float(h)
                    puff = 1.0 - max(abs(u - 0.5), abs(v - 0.5)) * 2.0      # 1 centre .. 0 edge
                    col = rng.choice(ALLOY)
                    if puff < 0.24:
                        col = _mix(A_LIGHT, ALLOY[1], 0.45) if (u + v) < 1.0 else _mix(A_DARK, ALLOY[2], 0.4)
                    elif puff > 0.62:
                        col = _mix(col, A_LIGHT, 0.16)
                    px[x, y] = col
    for x in range(1, 31):                              # fertile inlay across the middle course
        if px[x, 15] != A_DARK:
            px[x, 14] = FERTILE[1]
            px[x, 15] = FERTILE[0]
            px[x, 16] = FERTILE[2] if x % 4 else FERTILE[0]
            px[x, 17] = FERTILE[1]
    half = _mix(A_LIGHT, A_DARK, 0.45)
    for ry in (10, 21):
        for rx in (10, 21):
            px[rx, ry] = A_LIGHT
            px[rx + 1, ry] = half
            px[rx, ry + 1] = half
            px[rx + 1, ry + 1] = A_DARK
    tell(px, 4, 26)
    save(img, "block", "breeder_blanket")


# ---------------- europium control rod assembly ----------------

def _euro_rod_column(px, x, y0, y1):
    """A thin (2 px) absorber rod with a pale europium cap at each end."""
    for y in range(y0, y1 + 1):
        px[x, y] = _mix(A_DARK, ALLOY[2], 0.5)
        px[x + 1, y] = SOCKET
    for y in (y0, y0 + 1, y0 + 2):
        px[x, y] = EURO[2] if y == y0 else EURO[0]
        px[x + 1, y] = EURO[0] if y < y0 + 2 else EURO[1]
    for y in (y1 - 1, y1):
        px[x, y] = EURO[0]
        px[x + 1, y] = EURO[1]


def gen_europium_control_rod_assembly():
    """Side: two end plates, three thin pale-capped rods showing, a cindersteel collar at the waist.
    Laid out for the model's automatic UVs: plates rows 0-3 / 28-31, rods at px 6, 15 and 24,
    collar cols 8-23 x rows 10-21."""
    name = "europium_control_rod_assembly_side"
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    noise_fill(img, [SOCKET, SOCKET_SHADOW, (16, 20, 24, 255)], rng)      # the open bay behind
    for (y0, y1) in ((0, 3), (28, 31)):                                   # end plates
        for y in range(y0, y1 + 1):
            for x in range(S):
                px[x, y] = rng.choice(ALLOY)
        for x in range(S):
            px[x, y0] = A_LIGHT
            px[x, y1] = A_DARK
    for cx in (6, 15, 24):
        _euro_rod_column(px, cx, 4, 27)
    _cinder_fill(px, rng, 8, 10, 23, 21)                                  # the collar
    for y in (13, 18):                                                    # collar clamp grooves
        for x in range(9, 23):
            px[x, y] = CINDER[1]
    for (bx, by) in ((10, 11), (20, 11), (10, 19), (20, 19)):
        px[bx, by] = CINDER[2]
        px[bx + 1, by + 1] = A_DARK
    save(img, "block", name)

    name = "europium_control_rod_assembly_top"
    img = machine_base(name)
    px = img.load()
    rng = rng_for(name)
    _cinder_fill(px, rng, 8, 8, 23, 23)                                   # collar seen from above
    recess(px, 12, 12, 19, 19)
    for (rx, ry) in ((15, 5), (24, 15), (15, 24), (5, 15)):               # four rod heads, a diamond
        rect(px, rx - 1, ry - 1, rx + 2, ry + 2, A_DARK)
        px[rx, ry] = EURO[2]
        px[rx + 1, ry] = EURO[0]
        px[rx, ry + 1] = EURO[0]
        px[rx + 1, ry + 1] = EURO[1]
    for (rx, ry) in ((10, 10), (20, 10), (10, 20), (20, 20)):
        px[rx, ry] = CINDER[2]
        px[rx + 1, ry + 1] = A_DARK
    tell(px, 15, 15)
    save(img, "block", name)


# ---------------- reactor steam port ----------------

def _ring(px, box, col, hollow):
    """A one-pixel identification ring round a flange bore, with a shade line under it. Hollow
    keeps only the four corners: the starved / backed-up read."""
    x0, y0, x1, y1 = box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1
    shade = _mix(col, A_DARK, 0.5)
    for x in range(x0, x1 + 1):
        for y in (y0, y1):
            if hollow and x0 + 1 < x < x1 - 1:
                px[x, y] = SOCKET_SHADOW
            else:
                px[x, y] = col if y == y0 else shade
    for y in range(y0, y1 + 1):
        for x in (x0, x1):
            if hollow and y0 + 1 < y < y1 - 1:
                px[x, y] = SOCKET_SHADOW
            else:
                px[x, y] = col if x == x0 else shade


def _steam_port_front(name, steam, water, steam_hollow=False, water_hollow=False, flowing=False):
    """Viewer's left: the lamp rail (px 0-7). Right: the recessed shell with two bolted flanges,
    steam (white ring) above coolant (blue ring). Flange faces sit at px 12-27 x 2-13 and 18-29."""
    rng = rng_for("reactor_steam_port_front")
    img = new_img()
    px = img.load()
    noise_fill(img, ALLOY, rng)
    for y in range(S):                                  # the recessed shell face is darker
        for x in range(9, S):
            px[x, y] = _mix(px[x, y], A_DARK, 0.38)
    bevel(img)
    for y in range(2, 30):
        px[8, y] = A_DARK
        px[7, y] = _mix(A_LIGHT, ALLOY[0], 0.45)
    status_socket(px)
    rivets(img, pts=((3, 26),))
    haz_band_v(px, 3, 4, 17, 23)
    tell(px, 4, 11, vertical=True)
    top = flange(px, 12, 2, 27, 13)
    low = flange(px, 12, 18, 27, 29)
    _ring(px, top, steam, steam_hollow)
    _ring(px, low, water, water_hollow)
    if flowing:
        for (x, y, c) in ((top[0] + 1, top[1] + 1, STEAM_X), (top[0] + 3, top[1] + 2, STEAM_W),
                          (top[0] + 5, top[1] + 1, STEAM_W), (top[0] + 6, top[1] + 2, STEAM_X)):
            px[x, y] = c
        for x in range(low[0] + 1, low[2]):
            px[x, low[3] - 1] = HW_SHADE
            px[x, low[3]] = HW_BLUE if x % 3 else HW_LIGHT
    save(img, "block", name)


def gen_reactor_steam_port():
    dim_w = _mix(STEAM_W, A_DARK, 0.42)
    dim_b = _mix(HW_BLUE, A_DARK, 0.30)
    _steam_port_front("reactor_steam_port_front", dim_w, dim_b)                        # idle (baked)
    _steam_port_front("reactor_steam_port_front_working", STEAM_W, HW_LIGHT, flowing=True)
    _steam_port_front("reactor_steam_port_front_starved", dim_w, HW_BLUE, water_hollow=True)
    _steam_port_front("reactor_steam_port_front_backed_up", STEAM_W, dim_b, steam_hollow=True)

    # side: shell plate with the casing's seam cross and the two service marks
    img = machine_base("reactor_steam_port_side")
    px = img.load()
    for x in range(6, 26):
        px[x, 15] = A_DARK
        px[x, 16] = ALLOY[2]
    for y in range(6, 26):
        px[15, y] = A_DARK
        px[16, y] = ALLOY[2]
    recess(px, 10, 9, 21, 22)
    pipe_ring(px, 12, 12, 19, STEAM_W)
    pipe_ring(px, 18, 12, 19, HW_BLUE)
    for (cx, cy, sx, sy) in ((3, 3, 1, 1), (28, 3, -1, 1), (3, 28, 1, -1), (28, 28, -1, -1)):
        for i in range(4):
            for j in range(4 - i):
                px[cx + sx * j, cy + sy * i] = HAZ_Y if ((i + j) // 2) % 2 == 0 else HAZ_K
    save(img, "block", "reactor_steam_port_side")


# ---------------- reactor control computer ----------------

def _lamp(px, y, col, core):
    for x in (25, 26, 27):
        px[x, y] = col
        px[x, y + 1] = col
    if core:
        px[26, y] = core


def _computer_front(name, lamps, flag=False, key_turned=False, hazard=False):
    """Viewer's left: the lamp pillar (px 0-7). Under the hood (rows 0-3): flag window, key switch,
    a readout slot, and on the right the four-lamp column (stage, output, fuel, heat from the top).
    Rows 25-31 are the console keys, mapped onto the sloped desk by the model.
    ``lamps`` = four (colour, core) pairs from the top."""
    rng = rng_for("reactor_control_computer_front")
    img = new_img()
    px = img.load()
    noise_fill(img, ALLOY, rng)
    bevel(img)
    for y in range(2, 22):
        px[8, y] = A_DARK
        px[7, y] = _mix(A_LIGHT, ALLOY[0], 0.45)
    status_socket(px)
    tell(px, 4, 12, vertical=True)
    rivets(img, pts=((3, 18),))
    # hood strip (rows 0-3): the hazard edge lives here, dim unless failing
    for x in range(9, 31):
        for y in (1, 2):
            on = ((x + y) // 3) % 2 == 0
            if hazard:
                px[x, y] = HAZ_Y if on else HAZ_K
            else:
                px[x, y] = _mix(HAZ_Y, A_DARK, 0.90) if on else HAZ_K
        px[x, 3] = A_DARK
    trip_flag(px, 11, 6, shown=flag, w=8, h=4)
    # key switch: escutcheon, barrel, slot (vertical = armed, horizontal = turned)
    rect(px, 9, 11, 14, 18, SOCKET)
    frame(px, 9, 11, 14, 18, A_DARK)
    if key_turned:
        rect(px, 9, 13, 14, 16, SILVER[0])
        for x in range(9, 15):
            px[x, 13] = SILVER[2]
            px[x, 16] = SILVER[1]
        for x in range(10, 14):
            px[x, 14] = A_DARK
    else:
        rect(px, 10, 12, 13, 17, SILVER[0])
        for y in range(12, 18):
            px[10, y] = SILVER[2]
            px[13, y] = SILVER[1]
        for y in range(13, 17):
            px[11, y] = A_DARK
    # readout slot: one short trace, never a bar graph
    recess(px, 16, 12, 22, 17)
    if lamps[0][0] != P_EMBER:
        col = lamps[0][0]
        for (x, y) in ((17, 15), (18, 14), (19, 15), (20, 14), (21, 14)):
            px[x, y] = col
    else:
        for x in (17, 19, 21):
            px[x, 15] = P_EMBER
    # lamp column
    rect(px, 24, 5, 28, 20, SOCKET)
    frame(px, 24, 5, 28, 20, A_DARK)
    for y in range(5, 21):
        px[29, y] = _mix(A_LIGHT, A_DARK, 0.55)
    for i, (col, core) in enumerate(lamps):
        _lamp(px, 7 + i * 3 + (i > 1), col, core)
    # console keys (rows 25-31)
    rect(px, 2, 25, 29, 31, SOCKET)
    for ky in (26, 29):
        for kx in range(3, 28, 3):
            px[kx, ky] = A_LIGHT
            px[kx + 1, ky] = ALLOY[3]
            px[kx, ky + 1] = ALLOY[3]
            px[kx + 1, ky + 1] = A_DARK
    for kx in (24, 25):                                 # the wide enter key
        px[kx, 29] = lamps[3][0]
        px[kx, 30] = lamps[3][0]
    save(img, "block", name)


def gen_reactor_control_computer():
    off = (P_EMBER, None)
    on = (P_AMBER, P_HOT)
    red = (R_ALARM, _mix(R_ALARM, P_GLOW, 0.5))
    _computer_front("reactor_control_computer_front", [on, on, on, on])                # working (baked)
    _computer_front("reactor_control_computer_front_idle", [off, off, off, off])
    _computer_front("reactor_control_computer_front_tripped", [off, off, off, on], flag=True, key_turned=True)
    _computer_front("reactor_control_computer_front_alarm", [red, red, red, red], hazard=True)

    img = machine_base("reactor_control_computer_side")
    px = img.load()
    louvre(px, 8, 6, 23, 14)
    box = flange(px, 11, 18, 20, 27)
    rect(px, box[0], box[1], box[2], box[3], SOCKET)
    px[box[0] + 1, box[1] + 1] = P_EMBER
    save(img, "block", "reactor_control_computer_side")

    img = machine_base("reactor_control_computer_top")
    px = img.load()
    family_mark(px, "fission")
    recess(px, 10, 12, 25, 25)
    for x in range(12, 24, 3):                          # hatch ribs
        for y in range(14, 24):
            px[x, y] = ALLOY[2]
    for (bx, by) in ((11, 13), (24, 13), (11, 24), (24, 24)):
        px[bx, by] = A_LIGHT
    save(img, "block", "reactor_control_computer_top")


# ---------------- fission core, steam mode ----------------

def gen_fission_core_steam():
    """The shipped core front with its viewport collar turned steam white, and the same collar alone
    on a clear tile for the renderer to lay over the baked front while the core runs in steam mode."""
    img = machine_base("fission_core_front")            # same seed: the shipped face, unchanged
    px = img.load()
    _old._fission_viewport(px, POWER_RAMP, ALLOY[3])
    for i, x in enumerate((6, 10, 22, 26)):
        led(px, x, 28, P_AMBER if i % 2 else P_HOT)
    tell(px, 15, 3)
    ring = new_img()
    rp = ring.load()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 15.5, y - 15.5)
            if 9.8 < d < 11.2:
                col = STEAM_W if (x + y) < 31 else STEAM_X
                px[x, y] = col
                rp[x, y] = col
            elif 11.2 <= d <= 12.0:
                rp[x, y] = A_DARK
    for k in range(8):                                  # the collar bolts stay
        ang = k * math.pi / 4
        bx, by = int(round(15.5 + 10.5 * math.cos(ang))), int(round(15.5 + 10.5 * math.sin(ang)))
        px[bx, by] = A_DARK
        rp[bx, by] = A_DARK
    save(img, "block", "fission_core_front_steam")
    save(ring, "block", "fission_core_steam_ring")


# ---------------- items ----------------

def gen_europium_control_rod():
    """Thin rod (4 px; the plain control rod is 6), pale caps, a cindersteel collar."""
    img = _old._rod("europium_control_rod", [A_DARK, ALLOY[2], ALLOY[0]], EURO, x0=14, x1=17)
    px = img.load()
    for y in range(11, 15):
        for x in range(13, 19):
            px[x, y] = CINDER[0]
        px[13, y] = CINDER[2]
        px[18, y] = CINDER[1]
    for x in range(13, 19):
        px[x, 11] = CINDER[2]
        px[x, 14] = CINDER[1]
    for x in (15, 16):
        px[x, 8] = T_CYAN
    save(img, "item", "europium_control_rod")


def gen_hot_spent_fuel_rod():
    """The spent rod with glowing bands and a heat haze above it. Four painted frames: the glow
    climbs the rod and the haze drifts, so it reads "do not carry"."""
    band = [P_AMBER, P_HOT, P_GLOW, P_HOT]
    frames = []
    for f in range(4):
        img = _old._rod("hot_spent_fuel_rod", SPENT, SILVER)
        px = img.load()
        for i, wy in enumerate((9, 14, 19)):
            core = band[(f + i) % 4]
            for y in range(wy, wy + 3):
                px[13, y] = P_EMBER
                px[18, y] = P_EMBER
                for x in range(14, 18):
                    px[x, y] = core if y == wy + 1 else P_AMBER
        haze = _mix(P_GLOW, STEAM_W, 0.6)
        for x in range(11, 21):
            if (x + f) % 4 == 0:
                px[x, 2] = haze[:3] + (150,)
            if (x + f + 2) % 4 == 0:
                px[x, 1] = haze[:3] + (110,)
            if (x + 2 * f + 1) % 5 == 0:
                px[x, 0] = haze[:3] + (70,)
        frames.append(img)
    save_strip(frames, "item", "hot_spent_fuel_rod", 4)


def _hexagon(name, r, fill, core=False):
    """Hexagonal pellet (the uranium pellet is round): points left and right, a faceted face."""
    rng = rng_for(name)
    img = new_img()
    px = img.load()
    lim = 0.866 * r
    for y in range(S):
        for x in range(S):
            dx, dy = x - 15.5, y - 15.5
            ax, ay = abs(dx), abs(dy)
            e = max(ay, 0.866 * ax + 0.5 * ay)
            if e > lim:
                continue
            if e > lim - 1.3:
                px[x, y] = fill[2] if (dy < -0.2 * ax) else fill[1]
            elif e > lim - 2.4 and dy > 0:
                px[x, y] = _mix(fill[0], fill[1], 0.5)
            else:
                px[x, y] = rng.choice([fill[0], fill[0], _mix(fill[0], fill[2], 0.3)])
    for x in range(10, 22):                             # facet line across the face
        if px[x, 12][3]:
            px[x, 12] = _mix(px[x, 12], fill[2], 0.35)
    px[11, 11] = SLATE_FLECK
    px[12, 11] = _mix(SLATE_FLECK, fill[0], 0.5)
    if core:
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - 15.5, y - 16.5)
                if d <= 1.2:
                    px[x, y] = P_HOT
                elif d <= 2.4:
                    px[x, y] = P_AMBER
                elif d <= 3.4:
                    px[x, y] = P_EMBER
        trace(px, [(17, 15), (19, 13), (19, 11), (21, 10)], P_AMBER)     # the hairline crack
        px[21, 10] = P_EMBER
    return img


def gen_pallurite():
    img = new_img()
    px = img.load()
    rng = rng_for("pallurite_dust")
    for x in range(5, 27):                              # a low mound
        h = int(round(7.5 * math.exp(-((x - 15.5) / 6.5) ** 2) + 1.5))
        for y in range(24 - h, 25):
            if y == 24 - h:
                px[x, y] = SLATE[2]
            elif y == 24:
                px[x, y] = SLATE[1]
            else:
                px[x, y] = rng.choice([SLATE[0], SLATE[0], SLATE[1], SLATE[2]])
            if 24 - h < y < 24 and rng.random() < 0.09:
                px[x, y] = SLATE_FLECK
    for (x, y) in ((4, 24), (27, 24), (7, 22), (25, 21)):                 # stray grains
        px[x, y] = SLATE[0]
    save(img, "item", "pallurite_dust")
    save(_hexagon("pallurite_pellet", 9.0, SLATE), "item", "pallurite_pellet")
    save(_hexagon("kindled_pellet", 9.0, SLATE, core=True), "item", "kindled_pellet")


def gen_braided_fuel_rod():
    """Two strands, amber and pale, wound round each other between silver caps."""
    img = _old._rod("braided_fuel_rod", SILVER, SILVER)
    px = img.load()
    for y in range(7, 25):
        px[13, y] = A_DARK
        px[18, y] = A_DARK
        for x in range(14, 18):
            px[x, y] = SOCKET
        ph = (y - 7) * math.pi / 4.0
        xa = int(round(15.5 + 1.5 * math.sin(ph)))
        xb = int(round(15.5 - 1.5 * math.sin(ph)))
        if math.cos(ph) >= 0:
            px[xb, y] = EURO[1]
            px[xa, y] = P_HOT if y % 2 else P_AMBER
        else:
            px[xa, y] = P_AMBER if y % 2 else P_EMBER
            px[xb, y] = EURO[2]
    save(img, "item", "braided_fuel_rod")


def gen_fuel_economizer():
    """Module card: a notched plate with edge contacts and a long-bar / short-bar glyph."""
    rng = rng_for("fuel_economizer")
    img = new_img()
    px = img.load()
    for y in range(4, 28):
        for x in range(7, 25):
            if x > 21 and y < 7 and (x - 21) + (7 - y) > 3:
                continue                                # clipped upper-right corner
            px[x, y] = rng.choice(ALLOY)
    for y in range(4, 28):
        if px[7, y][3]:
            px[7, y] = A_LIGHT
        if px[24, y][3]:
            px[24, y] = A_DARK
    for x in range(7, 25):
        if px[x, 4][3]:
            px[x, 4] = A_LIGHT
        px[x, 27] = A_DARK
    for x in range(9, 23, 2):                           # edge contacts
        px[x, 25] = CINDER[2]
        px[x, 26] = CINDER[0]
    recess(px, 9, 8, 22, 20)
    for x in range(11, 21):                             # long bar: what you get
        px[x, 11] = P_HOT
        px[x, 12] = P_AMBER
    for x in range(11, 15):                             # short bar: what it costs
        px[x, 16] = P_AMBER
        px[x, 17] = P_EMBER
    px[19, 16] = T_CYAN
    px[20, 16] = T_CYAN
    save(img, "item", "fuel_economizer")


def paint():
    gen_graphite_moderator()
    gen_moderator_tank()
    gen_breeder_blanket()
    gen_europium_control_rod_assembly()
    gen_reactor_steam_port()
    gen_reactor_control_computer()
    gen_fission_core_steam()
    gen_europium_control_rod()
    gen_hot_spent_fuel_rod()
    gen_pallurite()
    gen_braided_fuel_rod()
    gen_fuel_economizer()


PULSE = [
    ("reactor_control_computer_front",        8, 3, 0.30, HUE_AMBER),
    ("reactor_control_computer_front_alarm",  8, 3, 0.40, HUE_ALARM),
    ("fission_core_front_steam",              8, 3, 0.32, HUE_AMBER),
]

SHEET = [
    {"id": "graphite_moderator", "block": True, "faces": ["graphite_moderator"]},
    {"id": "moderator_tank", "block": True, "faces": ["moderator_tank_frame", "moderator_tank_window"]},
    {"id": "breeder_blanket", "block": True, "faces": ["breeder_blanket"]},
    {"id": "europium_control_rod_assembly", "block": True,
     "faces": ["europium_control_rod_assembly_side", "europium_control_rod_assembly_top"],
     "item": "item:europium_control_rod"},
    {"id": "reactor_steam_port", "block": True, "faces": ["reactor_steam_port_side"],
     "states": ["reactor_steam_port_front", "reactor_steam_port_front_working",
                "reactor_steam_port_front_starved", "reactor_steam_port_front_backed_up"]},
    {"id": "reactor_control_computer", "block": True,
     "faces": ["reactor_control_computer_side", "reactor_control_computer_top"],
     "states": ["reactor_control_computer_front_idle", "reactor_control_computer_front",
                "reactor_control_computer_front_tripped", "reactor_control_computer_front_alarm"]},
    {"id": "fission_core (steam)", "model": "block/fission_core_steam",
     "faces": ["fission_core_front", "fission_core_front_steam", "fission_core_steam_ring"]},
    {"id": "fuel line", "model": None,
     "faces": ["item:pallurite_dust", "item:pallurite_pellet", "item:kindled_pellet",
               "item:braided_fuel_rod", "item:hot_spent_fuel_rod", "item:fuel_economizer"]},
]

LOOKALIKES = ["fission_core_front", "fission_casing", "control_rod_assembly", "control_rod_assembly_top"]
NEROTECH_LOOKALIKES = ["annunciator_panel_front", "process_tank", "fusion_casing", "foundry_brick"]
AUDIT = {"allow_lime": False, "allow_charge": False}

"""Back-end and generation sounds: the Vitrifier's pour, the Dry Cask's seal, the boiling Spent Fuel
Rack and the Hydrogen Fuel Cell's breath. All synthesised from the kit's oscillators, noise and
envelopes.
"""


def pour(k, rng):
    """Three viscous glugs falling in pitch, then a ringing glass tone as the log sets."""
    glugs = k.silence(0.75)
    for i, (off, f0) in enumerate(((0.0, 210.0), (0.2, 180.0), (0.42, 150.0))):
        g = k.env(k.sine((f0, f0 * 0.55), 0.2), [(0.0, 0.0), (0.15, 1.0), (1.0, 0.0)])
        start = int(off * k.RATE)
        for j, v in enumerate(g):
            glugs[start + j] += v * (0.8 - 0.12 * i)
    slosh = k.env(k.lowpass(k.noise(rng, 0.7), 700.0), [(0.0, 0.0), (0.2, 0.35), (1.0, 0.0)])
    ring = k.decay(k.fm(1320.0, 2.41, lambda p: 2.2 * (1.0 - p), 1.2), 0.22)
    return k.fade(k.mix(glugs, slosh, k.at(k.gain(ring, 0.5), 0.62)), 0.005, 0.05)


def seal(k, rng):
    """Three bolt ratchets, each a short burst of clicks, and a dull thud."""
    parts = k.silence(0.95)
    for b in range(3):
        for n in range(5):
            click = k.decay(k.bandpass(k.noise(rng, 0.012), 1500.0, 5200.0), 0.002)
            start = int((0.02 + b * 0.2 + n * 0.028) * k.RATE)
            for j, v in enumerate(click):
                parts[start + j] += v * (0.45 + 0.08 * n)
    thud = k.decay(k.mix(k.sine((95.0, 48.0), 0.3), k.gain(k.lowpass(k.noise(rng, 0.3), 260.0), 0.6)), 0.06)
    return k.fade(k.mix(parts, k.at(k.gain(thud, 0.95), 0.64)), 0.002, 0.03)


def boil(k, rng):
    """Bubbling: random low sine blips over a soft churn. A 2 s loop."""
    dur = 2.0
    bed = k.gain(k.lowpass(k.noise(rng, dur + 0.15), 420.0), 0.35)
    out = k.silence(dur + 0.15)
    for _ in range(34):
        f0 = rng.uniform(140.0, 420.0)
        length = rng.uniform(0.035, 0.08)
        blip = k.env(k.sine((f0, f0 * 1.7), length), [(0.0, 0.0), (0.3, 1.0), (1.0, 0.0)])
        start = int(rng.uniform(0.0, dur + 0.15 - length - 0.01) * k.RATE)
        g = rng.uniform(0.25, 0.7)
        for j, v in enumerate(blip):
            out[start + j] += v * g
    return k.loopable(k.mix(bed, out), 0.15)


def hiss(k, rng):
    """A faint gas hiss with two drips. A 2 s loop, very quiet."""
    dur = 2.0
    gas = k.tremolo(k.bandpass(k.noise(rng, dur + 0.15), 3200.0, 9000.0), 0.5, 0.25)
    loop = k.loopable(k.gain(gas, 0.5), 0.15)
    out = list(loop)
    for off, f0 in ((0.55, 1450.0), (1.4, 1180.0)):
        drip = k.decay(k.sine((f0, f0 * 1.5), 0.07), 0.012)
        start = int(off * k.RATE)
        for j, v in enumerate(drip):
            if start + j < len(out):
                out[start + j] += v * 0.45
    return out


SOUNDS = [
    ("vitrifier.pour", pour, {"subtitle": "Waste glass sets"}),
    ("cask.seal", seal, {"subtitle": "Cask seals"}),
    ("pool.boil", boil, {"subtitle": "Fuel rack boils", "loop": True, "peak": 0.6}),
    ("fuel_cell.hiss", hiss, {"subtitle": "Fuel cell breathes", "loop": True, "peak": 0.3}),
]

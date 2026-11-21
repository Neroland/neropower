"""Radiation sounds: the Geiger Counter's single click (three variants), its chatter at high levels
and the Decontaminant's foam spray. All synthesised from the kit's oscillators, noise and envelopes.
"""


def _click(k, rng, tone, level=1.0):
    """One counter click: a 2 ms noise tick ringing through a small box (two short resonances)."""
    tick = k.decay(k.highpass(k.noise(rng, 0.004), 1800.0), 0.0007)
    ring = k.decay(k.sine((tone, tone * 0.94), 0.022), 0.0035)
    box = k.decay(k.sine((tone * 0.31, tone * 0.27), 0.03), 0.006)
    return k.gain(k.mix(tick, k.gain(ring, 0.55), k.gain(box, 0.35)), level)


def click(k, rng):
    """A single click; each variant gets its own box tone so repeats never machine-gun."""
    return _click(k, rng, rng.uniform(2300.0, 3100.0))


def chatter(k, rng):
    """One second of dense, irregular clicks (about 24 a second) that loops without a seam."""
    dur = 1.15
    out = k.silence(dur)
    t = 0.0
    while True:
        t += rng.expovariate(24.0)
        if t >= dur - 0.04:
            break
        c = _click(k, rng, rng.uniform(2300.0, 3100.0), rng.uniform(0.55, 1.0))
        start = int(t * k.RATE)
        for j, v in enumerate(c):
            out[start + j] += v
    return k.loopable(out, 0.15)


def spray(k, rng):
    """A short foam hiss: a bright burst that thickens, fizzes and dies away."""
    dur = 0.7
    hiss = k.env(k.bandpass(k.noise(rng, dur), 2600.0, 9500.0),
                 [(0.0, 0.0), (0.04, 1.0), (0.35, 0.7), (1.0, 0.0)])
    foam = k.env(k.tremolo(k.bandpass(k.noise(rng, dur), 700.0, 2400.0), 31.0, 0.6),
                 [(0.0, 0.0), (0.2, 0.2), (0.5, 0.6), (1.0, 0.0)])
    out = k.mix(hiss, k.gain(foam, 0.7))
    for _ in range(14):                                   # fizz: tiny bubbles popping in the tail
        f0 = rng.uniform(1800.0, 4200.0)
        pop = k.decay(k.sine((f0, f0 * 1.4), 0.012), 0.002)
        start = int(rng.uniform(0.25, dur - 0.03) * k.RATE)
        g = rng.uniform(0.15, 0.4)
        for j, v in enumerate(pop):
            out[start + j] += v * g
    return k.fade(out, 0.004, 0.06)


SOUNDS = [
    ("geiger.click", click, {"subtitle": "Geiger counter clicks", "variants": 3, "peak": 0.6}),
    ("geiger.chatter", chatter, {"subtitle": "Geiger counter chatters", "loop": True, "peak": 0.6}),
    ("decontaminant.spray", spray, {"subtitle": "Decontaminant sprays", "peak": 0.6}),
]

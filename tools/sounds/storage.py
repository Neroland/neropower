"""Storage sounds: the Storage Ring's hum, warming and quench, the Momentum Wheel's whirr and the
Surge Bank's snap. All synthesised from the kit's oscillators, noise and envelopes.
"""


def hum(k, rng):
    """A low pure tone with a slow beat: two sines 0.67 Hz apart, whole cycles of both in 1.5 s."""
    dur = 1.5
    a = k.sine(82.0 / dur, dur)
    b = k.sine(83.0 / dur, dur)
    octave = k.gain(k.sine(164.0 / dur, dur), 0.18)
    return k.mix(k.gain(a, 0.5), k.gain(b, 0.5), octave)


def warn(k, rng):
    """The hum with a wavering pitch and ice crackle over it."""
    dur = 2.0
    waver = lambda p: 55.0 + 2.5 * k.math.sin(k.TAU * 3.0 * p)     # three whole wavers per loop
    tone = k.mix(k.gain(k.sine(waver, dur), 0.6), k.gain(k.sine(lambda p: 2.0 * waver(p), dur), 0.15))
    crackle = k.silence(dur)
    for _ in range(14):
        tick = k.decay(k.highpass(k.noise(rng, 0.012), 3000.0), 0.002)
        off = int(rng.uniform(0.05, dur - 0.25) * k.RATE)
        g = rng.uniform(0.25, 0.6)
        for i, v in enumerate(tick):
            crackle[off + i] += v * g
    return k.loopable(k.mix(tone, crackle), 0.1)


def quench(k, rng):
    """Rising squeal, a loud vapour burst, then a falling ring-out. 2.5 s."""
    squeal = k.env(k.mix(k.sine((320.0, 2400.0), 0.7), k.gain(k.saw((322.0, 2410.0), 0.7), 0.25)),
                   [(0.0, 0.0), (0.2, 0.35), (1.0, 0.9)])
    burst = k.env(k.bandpass(k.noise(rng, 1.3), 500.0, (7000.0, 1200.0)),
                  [(0.0, 1.0), (0.08, 0.8), (0.5, 0.3), (1.0, 0.0)])
    thump = k.decay(k.sine((110.0, 45.0), 0.5), 0.09)
    ring = k.decay(k.fm(196.0, 2.76, lambda p: 3.0 * (1.0 - p), 1.8), 0.32)
    return k.mix(squeal, k.at(burst, 0.68), k.at(k.gain(thump, 0.9), 0.68), k.at(k.gain(ring, 0.45), 0.7))


def spin(k, rng):
    """A soft whirr with one tick per turn (the loop is one turn; pitch follows the charge)."""
    dur = 1.0
    whirr = k.tremolo(k.lowpass(k.noise(rng, dur + 0.15), 900.0), 6.0, 0.35)
    body = k.gain(k.sine(96.0, dur + 0.15), 0.25)
    loop = k.loopable(k.mix(k.gain(whirr, 0.8), body), 0.15)
    tick = k.decay(k.bandpass(k.noise(rng, 0.03), 1200.0, 4200.0), 0.005)
    return k.mix(loop, k.at(k.gain(tick, 0.7), 0.5))


def snap(k, rng):
    """A short electric snap: a click, a bright crackle tail and a small body thump."""
    click = k.decay(k.highpass(k.noise(rng, 0.02), 2500.0), 0.0025)
    tail = k.decay(k.tremolo(k.bandpass(k.noise(rng, 0.22), 1800.0, 9000.0), 140.0, 0.9), 0.035)
    body = k.decay(k.sine((420.0, 140.0), 0.12), 0.02)
    return k.mix(click, k.at(k.gain(tail, 0.6), 0.004), k.gain(body, 0.5))


SOUNDS = [
    ("smes.hum", hum, {"subtitle": "Storage ring hums", "loop": True, "peak": 0.55}),
    ("smes.warn", warn, {"subtitle": "Storage ring warms", "loop": True, "peak": 0.6}),
    ("smes.quench", quench, {"subtitle": "Storage ring quenches", "attenuation": 24}),
    ("momentum_wheel.spin", spin, {"subtitle": "Momentum wheel whirs", "loop": True, "peak": 0.5}),
    ("surge_bank.snap", snap, {"subtitle": "Surge bank discharges", "peak": 0.7}),
]

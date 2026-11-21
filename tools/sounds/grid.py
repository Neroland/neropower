"""Beam and grid sounds: the Circuit Breaker's trip, reset and strain, the Load Bank's sizzle, the Beam
Splitter's shimmer, the Phased Array's retarget step and the SCRAM Key's turn. All synthesised from the
kit's oscillators, noise and envelopes.
"""


def _clack(k, rng, body=180.0, bright=3200.0, weight=0.7):
    """A hard contact: a few milliseconds of bright noise on a short falling thump."""
    click = k.decay(k.highpass(k.noise(rng, 0.03), bright), 0.004)
    knock = k.decay(k.sine((body * 1.6, body * 0.6), 0.09), 0.012)
    return k.mix(click, k.gain(knock, weight))


def trip(k, rng):
    """A sharp double clack, 60 ms apart, then a buzz that falls away as the contacts open."""
    buzz = k.mix(k.gain(k.saw((120.0, 55.0), 0.45), 0.5), k.gain(k.sine((240.0, 110.0), 0.45), 0.3))
    buzz = k.env(k.lowpass(buzz, (2400.0, 500.0)), [(0.0, 0.0), (0.04, 0.8), (0.3, 0.35), (1.0, 0.0)])
    spit = k.decay(k.bandpass(k.noise(rng, 0.12), 2500.0, 9000.0), 0.02)
    return k.mix(_clack(k, rng), k.at(_clack(k, rng, body=140.0), 0.06), k.at(k.gain(buzz, 0.6), 0.07),
                 k.at(k.gain(spit, 0.35), 0.065))


def reset(k, rng):
    """One ratchet clack with a rising spring note: the handle going back up."""
    ratchet = k.silence(0.12)
    for i in range(4):
        tick = k.decay(k.highpass(k.noise(rng, 0.012), 2800.0), 0.0025)
        off = int((0.012 + 0.024 * i) * k.RATE)
        for j, v in enumerate(tick):
            ratchet[off + j] += v * (0.35 + 0.1 * i)
    spring = k.decay(k.sine((260.0, 520.0), 0.16), 0.035)
    return k.mix(ratchet, k.at(_clack(k, rng, body=220.0, weight=0.9), 0.11), k.at(k.gain(spring, 0.3), 0.11))


def strain(k, rng):
    """A 100 Hz mains buzz with a rough edge. One second: whole cycles of every part."""
    dur = 1.0
    hum = k.mix(k.gain(k.sine(100.0, dur), 0.55), k.gain(k.saw(100.0, dur), 0.25),
                k.gain(k.sine(300.0, dur), 0.12))
    rattle = k.tremolo(k.bandpass(k.noise(rng, dur + 0.15), 900.0, 3200.0), 100.0, 0.95)
    return k.mix(k.tremolo(hum, 4.0, 0.15), k.gain(k.loopable(rattle, 0.15), 0.18))


def sizzle(k, rng):
    """Low filtered noise with a slow crackle over it: hot elements in moving air."""
    dur = 1.8
    bed = k.tremolo(k.lowpass(k.noise(rng, dur + 0.2), 700.0), 1.0 / 0.9, 0.3)
    hiss = k.gain(k.bandpass(k.noise(rng, dur + 0.2), 3000.0, 8000.0), 0.12)
    loop = k.loopable(k.mix(k.gain(bed, 0.9), hiss), 0.2)
    crackle = k.silence(dur)
    for _ in range(11):
        tick = k.decay(k.highpass(k.noise(rng, 0.015), 2200.0), 0.003)
        off = int(rng.uniform(0.05, dur - 0.1) * k.RATE)
        g = rng.uniform(0.2, 0.55)
        for i, v in enumerate(tick):
            crackle[off + i] += v * g
    return k.mix(loop, crackle)


def split(k, rng):
    """A thin glassy shimmer: three high partials beating slowly. Whole cycles in 1.5 s."""
    dur = 1.5
    parts = []
    for cycles, g in ((3141, 0.4), (3147, 0.4), (4713, 0.2), (6282, 0.1)):
        parts.append(k.gain(k.sine(cycles / dur, dur), g))
    air = k.gain(k.loopable(k.highpass(k.noise(rng, dur + 0.15), 6000.0), 0.15), 0.06)
    return k.mix(k.tremolo(k.mix(*parts), 2.0, 0.35), air)


def step(k, rng):
    """A soft relay tick and a short rising pitch blip: the emitter moving to its next target."""
    tick = k.decay(k.bandpass(k.noise(rng, 0.02), 1500.0, 5000.0), 0.003)
    blip = k.env(k.sine((880.0, 1320.0), 0.09), [(0.0, 0.0), (0.15, 1.0), (1.0, 0.0)])
    under = k.decay(k.sine(440.0, 0.06), 0.012)
    return k.mix(k.gain(tick, 0.7), k.at(k.gain(blip, 0.45), 0.012), k.at(k.gain(under, 0.25), 0.012))


def turn(k, rng):
    """A key click, the barrel turning, and a heavy thunk as the SCRAM latches."""
    click = k.decay(k.highpass(k.noise(rng, 0.015), 3500.0), 0.002)
    scrape = k.env(k.bandpass(k.noise(rng, 0.12), 1200.0, 3600.0), [(0.0, 0.0), (0.3, 0.5), (1.0, 0.0)])
    thunk = k.mix(k.decay(k.sine((130.0, 52.0), 0.3), 0.05),
                  k.decay(k.lowpass(k.noise(rng, 0.08), 900.0), 0.012))
    return k.mix(click, k.at(k.gain(scrape, 0.3), 0.02), k.at(k.gain(thunk, 0.95), 0.15))


SOUNDS = [
    ("breaker.trip", trip, {"subtitle": "Breaker trips", "peak": 0.8}),
    ("breaker.reset", reset, {"subtitle": "Breaker resets", "peak": 0.7}),
    ("breaker.strain", strain, {"subtitle": "Breaker strains", "loop": True, "peak": 0.5}),
    ("load_bank.sizzle", sizzle, {"subtitle": "Load bank sizzles", "loop": True, "peak": 0.45}),
    ("beam.split", split, {"subtitle": "Beam splits", "loop": True, "peak": 0.3}),
    ("phased_array.step", step, {"subtitle": "Emitter retargets", "peak": 0.55}),
    ("scram_key.turn", turn, {"subtitle": "SCRAM key turns", "peak": 0.75}),
]

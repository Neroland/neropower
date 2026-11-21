"""Steam family sounds: the turbine (spool, run, coast, overspeed, trip), the steam vent and the
condenser. Every sound is synthesised from the kit's oscillators, noise, filters and envelopes.
"""


def _whine(k, dur, base):
    """Two detuned saws through a low-pass: the rotor's whine."""
    a = k.saw(base, dur)
    b = k.saw(base * 1.007, dur, phase=0.31)
    return k.lowpass(k.mix(k.gain(a, 0.5), k.gain(b, 0.5)), 900.0)


def spool(k, rng):
    dur = 3.0
    whoosh = k.lowpass(k.noise(rng, dur), (300.0, 2600.0))
    whoosh = k.env(whoosh, [(0.0, 0.0), (0.15, 0.25), (0.8, 1.0), (1.0, 0.7)])
    sweep = k.env(k.sine((60.0, 240.0), dur), [(0.0, 0.0), (0.1, 0.6), (0.9, 1.0), (1.0, 0.6)])
    rise = k.lowpass(k.saw((45.0, 180.0), dur), (400.0, 1200.0))
    rise = k.env(rise, [(0.0, 0.0), (0.3, 0.3), (1.0, 0.7)])
    return k.mix(k.gain(whoosh, 0.5), k.gain(sweep, 0.6), k.gain(rise, 0.35))


def run(k, rng):
    dur = 2.15
    whine = _whine(k, dur, 180.0)
    flutter = k.tremolo(k.bandpass(k.noise(rng, dur), 500.0, 1800.0), 14.0, 0.7)
    body = k.sine(60.0, dur)
    return k.loopable(k.mix(k.gain(whine, 0.7), k.gain(flutter, 0.18), k.gain(body, 0.3)))


def coast(k, rng):
    dur = 3.0
    whoosh = k.lowpass(k.noise(rng, dur), (2400.0, 250.0))
    whoosh = k.env(whoosh, [(0.0, 0.8), (0.2, 1.0), (0.85, 0.2), (1.0, 0.0)])
    sweep = k.env(k.sine((240.0, 55.0), dur), [(0.0, 0.8), (0.7, 0.6), (1.0, 0.0)])
    rumble = k.lowpass(k.noise(rng, dur, "brown"), 120.0)
    rumble = k.env(k.tremolo(rumble, 9.0, 0.6), [(0.0, 0.2), (0.6, 1.0), (1.0, 0.0)])
    return k.mix(k.gain(whoosh, 0.45), k.gain(sweep, 0.55), k.gain(rumble, 0.8))


def overspeed(k, rng):
    dur = 2.15
    whine = k.tremolo(_whine(k, dur, 262.0), 7.0, 0.55)
    rattle = k.tremolo(k.bandpass(k.noise(rng, dur), 1500.0, 4200.0), 28.0, 0.9)
    return k.loopable(k.mix(k.gain(whine, 0.75), k.gain(rattle, 0.3)))


def trip(k, rng):
    slam = k.decay(k.lowpass(k.noise(rng, 0.25), 1800.0), 0.03)
    thump = k.decay(k.sine((95.0, 60.0), 0.5), 0.09)
    release = k.bandpass(k.noise(rng, 1.4), 1200.0, 6000.0)
    release = k.env(release, [(0.0, 0.0), (0.05, 1.0), (0.3, 0.5), (1.0, 0.0)])
    return k.mix(k.gain(slam, 1.0), k.gain(thump, 0.9), k.at(k.gain(release, 0.45), 0.08))


def vent(k, rng):
    dur = 1.5
    hiss = k.bandpass(k.noise(rng, dur), 1800.0, 7000.0)
    hiss = k.env(hiss, [(0.0, 0.0), (0.04, 1.0), (0.35, 0.6), (1.0, 0.0)])
    breath = k.env(k.lowpass(k.noise(rng, dur), 600.0), [(0.0, 0.0), (0.1, 0.5), (1.0, 0.0)])
    return k.mix(hiss, k.gain(breath, 0.4))


def drip(k, rng):
    dur = 2.15
    hum = k.mix(k.gain(k.sine(110.0, dur), 0.25), k.gain(k.lowpass(k.noise(rng, dur), 500.0), 0.2))
    tracks = [hum]
    for _ in range(4):                                 # sparse plinks, kept clear of the loop seam
        where = rng.uniform(0.25, 1.75)
        pitch = rng.uniform(900.0, 1500.0)
        plink = k.decay(k.sine((pitch, pitch * 1.6), 0.12), 0.02)
        tracks.append(k.at(k.gain(plink, 0.6), where))
    out = k.mix(*tracks)
    return k.loopable(out[:k.seconds(dur)])


SOUNDS = [
    ("turbine.spool", spool, {"subtitle": "Turbine spools up"}),
    ("turbine.run", run, {"subtitle": "Turbine hums", "loop": True}),
    ("turbine.coast", coast, {"subtitle": "Turbine winds down"}),
    ("turbine.overspeed", overspeed, {"subtitle": "Turbine overspeeds", "loop": True}),
    ("turbine.trip", trip, {"subtitle": "Turbine trips"}),
    ("steam.vent", vent, {"subtitle": "Steam vents"}),
    ("condenser.drip", drip, {"subtitle": "Condenser drips", "loop": True, "peak": 0.45}),
]

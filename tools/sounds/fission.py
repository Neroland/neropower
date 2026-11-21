"""Fission family sounds: the reactor's switch to steam mode and the control computer's alarm.
Synthesised from the kit only (oscillators, noise, filters, envelopes)."""


def steam_mode(k, rng):
    """A valve wheel turned (three ratchet ticks and a low clunk), then steam rushing into the line."""
    ticks = []
    for i in range(3):
        tick = k.decay(k.bandpass(k.noise(rng, 0.05), 900.0, 3200.0), 0.008)
        ticks.append(k.at(k.gain(tick, 0.55), 0.09 * i))
    clunk = k.at(k.decay(k.mix(k.sine((150.0, 70.0), 0.22), k.gain(k.lowpass(k.noise(rng, 0.22), 400.0), 0.5)),
                         0.05), 0.30)
    rush = k.bandpass(k.noise(rng, 1.25), 700.0, (2600.0, 5200.0))
    rush = k.env(rush, [(0.0, 0.0), (0.12, 1.0), (0.45, 0.7), (1.0, 0.0)])
    body = k.env(k.lowpass(k.noise(rng, 1.25, "brown"), 300.0), [(0.0, 0.0), (0.2, 0.6), (1.0, 0.0)])
    out = k.mix(*ticks, clunk, k.at(k.gain(rush, 0.6), 0.36), k.at(k.gain(body, 0.5), 0.36))
    return k.fade(k.normalise(out, 0.7), 0.004, 0.05)


def alarm(k, rng):
    """Two-tone beeper at 2 Hz: a high and a low square-ish beep, each a quarter second, with a
    short gap. One second, so it loops on the beat."""
    def beep(freq):
        tone = k.mix(k.osc("square", freq, 0.19), k.gain(k.sine(freq * 2.0, 0.19), 0.3))
        tone = k.lowpass(tone, 2600.0)
        return k.concat(k.fade(tone, 0.006, 0.02), k.silence(0.06))
    out = k.concat(beep(880.0), beep(660.0), beep(880.0), beep(660.0))
    return k.normalise(out, 0.6)


SOUNDS = [
    ("reactor.steam_mode", steam_mode, {"subtitle": "Reactor routes to steam"}),
    ("control_computer.alarm", alarm, {"subtitle": "Reactor alarm sounds", "loop": True, "attenuation": 24,
                                       "peak": 0.6}),
]

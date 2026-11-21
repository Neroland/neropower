#!/usr/bin/env python3
"""Synthesise NeroPower's sound effects. No samples and no third-party audio: every sound is built
here from oscillators, noise and envelopes in pure Python (standard library only).

Pipeline: recipe -> mono 44.1 kHz float samples -> 16-bit WAV -> OGG Vorbis through ``ffmpeg``.

* Recipes live in ``tools/sounds/<family>.py``. Each module exposes::

      SOUNDS = [
          ("breaker.trip", trip, {"subtitle": "Breaker trips"}),
          ("turbine.run", run, {"subtitle": "Turbine hums", "loop": True}),
          ("geiger.click", click, {"subtitle": "Counter clicks", "variants": 3}),
          ("control_computer.alarm", alarm, {"subtitle": "Reactor alarm sounds", "loop": True,
                                             "attenuation": 24}),
      ]

  where ``recipe(k, rng)`` returns a list of floats in -1..1. ``k`` is this module (the synthesis
  kit below) and ``rng`` is a ``random.Random`` seeded from the event id, so output is stable.
* Event ``a.b`` is written to ``assets/neropower/sounds/a/b.ogg``; its subtitle key is
  ``subtitles.neropower.a.b``. Loops are at most 2 s and are made seamless by ``loopable()``.
* ``variants: n`` renders the recipe n times with different seeds into ``b_1.ogg`` .. ``b_n.ogg``
  under one event, so a repeated sound (a counter click) never machine-guns.
* Sounds are heard over 16 blocks unless the row sets ``attenuation`` (alarms use 24).
* ADDITIVE-ONLY like the texture tools: an existing .ogg is skipped; ``--force`` replaces it.
* Without ``ffmpeg`` on PATH nothing is written unless ``--wav-dir DIR`` is given (WAVs only).

Usage:
    python tools/gen_sounds.py                 generate missing sounds
    python tools/gen_sounds.py --force         regenerate all
    python tools/gen_sounds.py --list          print the event table
    python tools/gen_sounds.py --family NAME   only the recipes in tools/sounds/NAME.py
    python tools/gen_sounds.py --out DIR       write the .ogg files under DIR instead of the mod
    python tools/gen_sounds.py --manifest F    also write a sounds.json-shaped fragment and the
                                               subtitle keys to file F (for integration)
    python tools/gen_sounds.py --wav-dir DIR   keep the intermediate WAVs in DIR
"""
import hashlib
import importlib
import json
import math
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import wave

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ID = "neropower"
SOUND_DIR = os.path.join(ROOT, "common", "src", "main", "resources", "assets", MOD_ID, "sounds")

RATE = 44100
TAU = 2.0 * math.pi
MAX_LOOP_SECONDS = 2.0
ATTENUATION_BLOCKS = 16


# ================================ synthesis kit (passed to recipes as k) ================================

def seconds(n):
    """Sample count for ``n`` seconds."""
    return int(round(n * RATE))


def silence(dur):
    return [0.0] * seconds(dur)


def _freq_at(freq, i, n):
    """``freq`` may be a number, a (start, end) pair (exponential glide) or a function of 0..1."""
    if callable(freq):
        return freq(i / max(1, n - 1))
    if isinstance(freq, (tuple, list)):
        a, b = freq
        return a * (b / a) ** (i / max(1, n - 1))
    return freq


def osc(shape, freq, dur, phase=0.0):
    """Oscillator: ``sine``, ``saw``, ``square`` or ``tri``; phase-accumulating, so glides are clean."""
    n = seconds(dur)
    out = [0.0] * n
    ph = phase
    for i in range(n):
        if shape == "sine":
            out[i] = math.sin(TAU * ph)
        elif shape == "saw":
            out[i] = 2.0 * (ph % 1.0) - 1.0
        elif shape == "square":
            out[i] = 1.0 if (ph % 1.0) < 0.5 else -1.0
        else:
            out[i] = 4.0 * abs((ph % 1.0) - 0.5) - 1.0
        ph += _freq_at(freq, i, n) / RATE
    return out


def sine(freq, dur, phase=0.0):
    return osc("sine", freq, dur, phase)


def saw(freq, dur, phase=0.0):
    return osc("saw", freq, dur, phase)


def fm(carrier, ratio, index, dur):
    """Two-operator FM (bell and glass tones). ``index`` may be a number or a function of 0..1."""
    n = seconds(dur)
    out = [0.0] * n
    for i in range(n):
        t = i / RATE
        depth = index(i / max(1, n - 1)) if callable(index) else index
        out[i] = math.sin(TAU * carrier * t + depth * math.sin(TAU * carrier * ratio * t))
    return out


def noise(rng, dur, colour="white"):
    """White or brown (integrated, leak-limited) noise."""
    n = seconds(dur)
    if colour == "white":
        return [rng.uniform(-1.0, 1.0) for _ in range(n)]
    out = [0.0] * n
    acc = 0.0
    for i in range(n):
        acc = (acc + rng.uniform(-1.0, 1.0) * 0.06) * 0.998
        out[i] = max(-1.0, min(1.0, acc * 3.5))
    return out


def lowpass(x, cutoff):
    """One-pole low-pass. ``cutoff`` in Hz: a number, a (start, end) pair or a function of 0..1."""
    n = len(x)
    out = [0.0] * n
    y = 0.0
    for i in range(n):
        a = 1.0 - math.exp(-TAU * _freq_at(cutoff, i, n) / RATE)
        y += a * (x[i] - y)
        out[i] = y
    return out


def highpass(x, cutoff):
    low = lowpass(x, cutoff)
    return [a - b for a, b in zip(x, low)]


def bandpass(x, low, high):
    return highpass(lowpass(x, high), low)


def env(x, points):
    """Piecewise-linear envelope: ``points`` = [(position 0..1, gain), ...], sorted by position."""
    n = len(x)
    out = [0.0] * n
    j = 0
    for i in range(n):
        p = i / max(1, n - 1)
        while j + 1 < len(points) - 1 and p > points[j + 1][0]:
            j += 1
        (p0, g0), (p1, g1) = points[j], points[min(j + 1, len(points) - 1)]
        g = g0 if p1 <= p0 else g0 + (g1 - g0) * min(1.0, max(0.0, (p - p0) / (p1 - p0)))
        out[i] = x[i] * g
    return out


def decay(x, half_life):
    """Exponential decay with the given half-life in seconds (plucks, bells, ticks)."""
    k = math.log(2.0) / (half_life * RATE)
    return [v * math.exp(-k * i) for i, v in enumerate(x)]


def fade(x, attack=0.005, release=0.01):
    """Short linear fades so a one-shot never starts or ends on a click."""
    n = len(x)
    a, r = min(n, seconds(attack)), min(n, seconds(release))
    out = list(x)
    for i in range(a):
        out[i] *= i / a
    for i in range(r):
        out[n - 1 - i] *= i / r
    return out


def tremolo(x, rate, depth):
    return [v * (1.0 - depth * 0.5 * (1.0 + math.sin(TAU * rate * i / RATE))) for i, v in enumerate(x)]


def gain(x, g):
    return [v * g for v in x]


def mix(*tracks):
    """Sum tracks sample by sample (the result is as long as the longest)."""
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, v in enumerate(t):
            out[i] += v
    return out


def concat(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def at(x, offset):
    """Delay a track by ``offset`` seconds (for use inside ``mix``)."""
    return silence(offset) + list(x)


def loopable(x, overlap=0.15):
    """Make a loop seamless: cross-fade the tail into the head (equal power) and drop the tail."""
    k = min(len(x) // 2, seconds(overlap))
    body = len(x) - k
    out = list(x[:body])
    for i in range(k):
        t = i / k
        out[i] = x[i] * math.sin(0.5 * math.pi * t) + x[body + i] * math.cos(0.5 * math.pi * t)
    return out


def normalise(x, peak=0.8):
    top = max((abs(v) for v in x), default=0.0)
    return list(x) if top <= 0.0 else [v * peak / top for v in x]


# ============================================ driver ============================================

def seed_for(event):
    """Deterministic per-event seed, the same scheme as gen_textures.rng_for."""
    return int(hashlib.md5(event.encode()).hexdigest(), 16) & 0xffffffff


def load_recipes(family=None):
    """Every ``SOUNDS`` row from tools/sounds/*.py (or one family's), in file order. Duplicate ids
    are an error."""
    here = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sounds")
    rows, seen = [], {}
    if not os.path.isdir(here):
        return rows
    for fname in sorted(os.listdir(here)):
        if not fname.endswith(".py") or fname == "__init__.py":
            continue
        if family and fname[:-3] != family:
            continue
        module = importlib.import_module("sounds." + fname[:-3])
        for event, recipe, meta in getattr(module, "SOUNDS", ()):
            if event in seen:
                raise SystemExit("gen_sounds: %s defined in both %s and %s" % (event, seen[event], fname))
            seen[event] = fname
            rows.append((event, recipe, dict(meta)))
    return rows


def variants_of(event, meta):
    """[(seed key, file path relative to the sounds folder without extension), ...]."""
    base = event.replace(".", "/")
    n = int(meta.get("variants", 1))
    if n <= 1:
        return [(event, base)]
    return [("%s#%d" % (event, i), "%s_%d" % (base, i)) for i in range(1, n + 1)]


def render(event, recipe, meta, seed_key=None):
    kit = sys.modules[__name__]
    samples = recipe(kit, random.Random(seed_for(seed_key or event)))
    if meta.get("loop"):
        if len(samples) > seconds(MAX_LOOP_SECONDS) + 1:
            raise SystemExit("gen_sounds: loop %s is %.2f s (max %.1f s after loopable())"
                             % (event, len(samples) / RATE, MAX_LOOP_SECONDS))
    else:
        samples = fade(samples)
    return normalise(samples, meta.get("peak", 0.8))


def write_wav(path, samples):
    frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32767)) for v in samples)
    with wave.open(path, "wb") as fh:
        fh.setnchannels(1)
        fh.setsampwidth(2)
        fh.setframerate(RATE)
        fh.writeframes(frames)


def encode_ogg(ffmpeg, wav_path, ogg_path):
    # -bitexact + a fixed stream serial keep the file byte-stable across runs of one ffmpeg build.
    cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", wav_path,
           "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
           "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", "-ar", str(RATE),
           "-serial_offset", "1", ogg_path]
    subprocess.run(cmd, check=True)


def manifest(rows):
    """A sounds.json-shaped fragment plus the subtitle lang keys (integration merges both)."""
    sounds, lang = {}, {}
    for event, _recipe, meta in rows:
        key = "subtitles.%s.%s" % (MOD_ID, event)
        entries = []
        for _seed, rel in variants_of(event, meta):
            entry = {"name": "%s:%s" % (MOD_ID, rel),
                     "attenuation_distance": int(meta.get("attenuation", ATTENUATION_BLOCKS))}
            if meta.get("loop"):
                entry["stream"] = False
            entries.append(entry)
        sounds[event] = {"subtitle": key, "sounds": entries}
        lang[key] = meta["subtitle"]
    return {"sounds.json": sounds, "lang": lang,
            "loops": sorted(e for e, _r, m in rows if m.get("loop"))}


def _arg(flag):
    return sys.argv[sys.argv.index(flag) + 1] if flag in sys.argv else None


def main():
    # Run as a script this module is "__main__"; alias it so a recipe that imports gen_sounds
    # gets this instance.
    sys.modules.setdefault("gen_sounds", sys.modules[__name__])
    rows = load_recipes(_arg("--family"))
    if "--list" in sys.argv:
        for event, _recipe, meta in rows:
            print("%-28s %-5s %s" % (event, "loop" if meta.get("loop") else "", meta["subtitle"]))
        return
    man = _arg("--manifest")
    if man:
        with open(man, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(manifest(rows), fh, indent=2)
            fh.write("\n")
        print("gen_sounds: manifest ->", man)

    out_dir = _arg("--out") or SOUND_DIR
    print("gen_sounds: %d recipe(s) -> %s" % (len(rows), os.path.relpath(out_dir, ROOT)
                                              if not _arg("--out") else out_dir))
    force = "--force" in sys.argv
    wav_dir = _arg("--wav-dir")
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        print("gen_sounds: ffmpeg not found on PATH; no .ogg written"
              + ("" if wav_dir else " (pass --wav-dir DIR to keep the WAVs)."))
        if not wav_dir:
            return
    tmp = wav_dir or tempfile.mkdtemp(prefix="neropower_sounds_")
    os.makedirs(tmp, exist_ok=True)
    try:
        for event, recipe, meta in rows:
            for seed_key, base in variants_of(event, meta):
                rel = base + ".ogg"
                ogg = os.path.join(out_dir, rel)
                if os.path.exists(ogg) and not force and not wav_dir:
                    print("skip (exists)", rel)
                    continue
                samples = render(event, recipe, meta, seed_key)
                wav = os.path.join(tmp, base.replace("/", ".") + ".wav")
                write_wav(wav, samples)
                if ffmpeg is None or (os.path.exists(ogg) and not force):
                    print("wav only", rel)
                    continue
                os.makedirs(os.path.dirname(ogg), exist_ok=True)
                encode_ogg(ffmpeg, wav, ogg)
                print("wrote %s (%.2f s%s)" % (rel, len(samples) / RATE, ", loop" if meta.get("loop") else ""))
    finally:
        if not wav_dir:
            shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    main()

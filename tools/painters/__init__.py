"""NeroPower texture painters, one module per family.

``kit``       palette, output handling and the shared painters. Not a family.
``existing``  the 0.3.2 art, kept pixel-identical.
``fluids``    steam, exhaust steam, heavy water.
The 0.4.0 families add ``fission``, ``steam``, ``backend``, ``storage``, ``grid`` and ``radiation``.

A family module exposes:

    paint()                 required: paints every texture the family owns through kit.save()
    PULSE = [(name, frames, frametime, amplitude, hue_window), ...]      optional (breathing)
    SCROLL = [(name, frames, frametime), ...]                            optional (travelling)
    SHEET = [{"id": ..., "faces": [...], "states": [...], ...}, ...]     optional (contact sheet)
    LOOKALIKES / NEROTECH_LOOKALIKES = [texture names]                   optional (contact sheet)
    AUDIT = {"allow_lime": bool, "allow_charge": bool}                   optional (palette audit)

The drivers (gen_textures.py, gen_animations.py, contact_sheet.py) find families by file name, so
adding a family is adding a file here; no driver changes.
"""
import importlib
import os

ORDER = ["existing", "fluids", "fission", "steam", "backend", "storage", "grid", "radiation"]
_SKIP = {"__init__", "kit"}


def available():
    """Family names present on disk: the known ones in ``ORDER`` first, then any others by name."""
    here = os.path.dirname(os.path.abspath(__file__))
    found = {f[:-3] for f in os.listdir(here) if f.endswith(".py") and f[:-3] not in _SKIP}
    return [n for n in ORDER if n in found] + sorted(found - set(ORDER))


def load(name):
    if name not in available():
        raise SystemExit("painters: no family '%s' (have: %s)" % (name, ", ".join(available())))
    return importlib.import_module("painters." + name)

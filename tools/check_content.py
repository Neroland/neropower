#!/usr/bin/env python3
"""Content-completeness check for NeroPower (stdlib only).

Reads every ``*Content.java`` under the common source tree plus ``registry/ModItems.java``,
extracts the block and item ids they register, and verifies that each one has the full set of
resources a player-facing block or item needs:

* blocks: blockstate JSON, every model it references (recursively through ``parent``),
  an item definition (``assets/neropower/items/<id>.json``), an item model, a block loot table,
  a ``block.neropower.<id>`` lang key, at least one recipe whose result is ``neropower:<id>`` and
  membership of ``#minecraft:mineable/pickaxe``;
* items: item definition, item model, ``item.neropower.<id>`` lang key and a recipe result
  (except ids in ``ALLOW_NO_RECIPE``, which are by-products). Block items need the ``item.`` key
  too: NeroPower's block items are plain ``BlockItem``s whose description id is
  ``item.neropower.<id>`` (as NeroTech's), so without it the creative tab and tooltips show the
  raw key even though ``block.neropower.<id>`` names the placed block;
* every texture referenced by any model resolves to a PNG on disk;
* every blockstate lists a model for every combination of its properties (and, for the 0.4.0
  blocks, for exactly the properties and values the block declares);
* the 0.4.0 machine blocks are multi-element models (3 to 24 elements) with a ``status_socket``
  element and a ``particle`` texture;
* every PNG under ``textures/`` is a square or a strip of square frames, a strip has a
  ``.png.mcmeta`` and a ``.png.mcmeta`` has a strip, and no placeholder sprite is left;
* every ``sounds.json`` event has its ``.ogg`` files and a subtitle lang key, every ``.ogg`` is
  listed, and every recipe in ``tools/sounds`` has an event;
* every fluid has its still and flowing sprites and its lang keys; every particle definition's
  sprites exist;
* every JSON file under the resource tree parses;
* every lang key the Java code uses (``Component.translatable("...")`` and string literals
  starting with ``container.neropower.``, ``gui.neropower.``, ``command.neropower.`` or
  ``neropower.``) exists in ``en_us.json``. Keys built from a prefix plus a runtime value are
  matched by prefix.

Prints a summary and exits non-zero when anything is missing. Run from anywhere:

    python3 tools/check_content.py
    python3 tools/check_content.py --family steam     only the gaps that name that family's ids
"""

from __future__ import annotations

import itertools
import json
import os
import re
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
MOD_ID = "neropower"
JAVA_ROOT = os.path.join(ROOT, "common", "src", "main", "java", "za", "co", "neroland", MOD_ID)
RES = os.path.join(ROOT, "common", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", MOD_ID)
DATA = os.path.join(RES, "data", MOD_ID)
LANG = os.path.join(ASSETS, "lang", "en_us.json")
PICKAXE_TAG = os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json")

# Items that are only ever produced as by-products of a machine (never crafted).
ALLOW_NO_RECIPE = {"spent_fuel_rod", "spent_isotope_pellet",
                   # 0.4.0 machine by-products: burnt rods, the breeder's output, the Vitrifier's log,
                   # the betavoltaic cell's used capsule.
                   "hot_spent_fuel_rod", "kindled_pellet", "vitrified_waste", "spent_beta_capsule"}
# Ids registered from a loop with a computed suffix: the prefix stands for these ids.
DYNAMIC_IDS = {"power_cell_": ("power_cell_basic", "power_cell_advanced", "power_cell_elite")}

# ---- 0.4.0 content: families, declared block states, machine blocks --------------------------
FAMILIES = {
    "fission": {"graphite_moderator", "moderator_tank", "breeder_blanket", "europium_control_rod_assembly",
                "reactor_steam_port", "reactor_control_computer", "europium_control_rod",
                "hot_spent_fuel_rod", "pallurite_dust", "pallurite_pellet", "kindled_pellet",
                "braided_fuel_rod", "fuel_economizer"},
    "steam": {"turbine_helm", "turbine_shroud", "turbine_spindle", "turbine_stator", "turbine_steam_port",
              "condenser", "steel_vane_set", "cindersteel_vane_set", "superconducting_vane_set",
              "governor_module"},
    "backend": {"spent_fuel_pool", "vitrifier", "dry_cask", "fuel_cell", "betavoltaic_cell",
                "fission_residue", "vitrified_waste", "beta_capsule", "spent_beta_capsule"},
    "storage": {"surge_bank", "momentum_wheel", "smes_controller", "smes_coil", "power_cell_basic",
                "power_cell_advanced", "power_cell_elite"},
    "grid": {"beam_splitter", "phased_array_emitter", "circuit_breaker", "load_bank", "power_monitor",
             "beam_alignment_scope", "scram_key"},
    "radiation": {"shielding_block", "shielding_glass", "geiger_counter", "dosimeter_badge",
                  "decontaminant"},
    "fluids": {"steam", "exhaust_steam", "heavy_water"},
}
# Sound groups (the part of an event id before the dot) by family.
SOUND_GROUPS = {
    "fission": {"reactor", "control_computer"},
    "steam": {"turbine", "steam", "condenser"},
    "backend": {"vitrifier", "cask", "pool", "fuel_cell"},
    "storage": {"smes", "momentum_wheel", "surge_bank"},
    "grid": {"breaker", "load_bank", "beam", "phased_array", "scram_key"},
    "radiation": {"geiger", "decontaminant"},
}

_FACING = ("north", "east", "south", "west")
_BOOL = ("false", "true")
_AXIS = ("x", "y", "z")
# Machine blocks: horizontal ``facing``; the second set also carries ``alarm``.
MACHINES = {"reactor_steam_port", "turbine_steam_port", "condenser", "vitrifier", "dry_cask", "fuel_cell",
            "betavoltaic_cell", "surge_bank", "momentum_wheel", "beam_splitter", "phased_array_emitter",
            "load_bank", "power_monitor"}
ALARM_MACHINES = {"reactor_control_computer", "turbine_helm", "spent_fuel_pool", "smes_controller",
                  "circuit_breaker"}
EXTRA_STATES = {
    "turbine_spindle": {"axis": _AXIS, "vanes": ("none", "steel", "cindersteel", "superconducting")},
    "turbine_stator": {"axis": _AXIS, "winding": ("none", "tier1", "tier2", "tier3")},
    "turbine_steam_port": {"port_mode": ("inlet", "exhaust", "vent")},
    "surge_bank": {"cans": tuple(str(i) for i in range(7))},
    "smes_coil": {"shape": ("straight_x", "straight_z", "corner_ne", "corner_nw", "corner_se", "corner_sw")},
    "circuit_breaker": {"tripped": _BOOL},
    "load_bank": {"heat": tuple(str(i) for i in range(5))},
    # Wall machines and BER-less machines with state art carry `status` (decision 012 addendum).
    "reactor_steam_port": {"status": ("idle", "working", "starved", "tripped")},
    "reactor_control_computer": {"status": ("idle", "working", "starved", "tripped")},
    "dry_cask": {"status": ("idle", "working", "starved", "tripped")},
    "betavoltaic_cell": {"status": ("idle", "working", "starved", "tripped")},
}
# Full cubes with no block entity: declared states are exactly EXTRA_STATES (or none).
PLAIN_BLOCKS = {"graphite_moderator", "moderator_tank", "breeder_blanket", "europium_control_rod_assembly",
                "turbine_shroud", "turbine_spindle", "turbine_stator", "smes_coil", "shielding_block",
                "shielding_glass"}
PLACEHOLDER_MARK = b"neropower-placeholder"


def declared_states(block: str) -> dict[str, tuple[str, ...]] | None:
    """Properties and values a 0.4.0 block declares; None for blocks this table does not cover."""
    if block not in MACHINES and block not in ALARM_MACHINES and block not in PLAIN_BLOCKS:
        return None
    props: dict[str, tuple[str, ...]] = {}
    if block in MACHINES or block in ALARM_MACHINES:
        props["facing"] = _FACING
    if block in ALARM_MACHINES:
        props["alarm"] = _BOOL
    props.update(EXTRA_STATES.get(block, {}))
    return props


# Lang-key prefixes that Java completes with a runtime value (enum name, status id, ...).
# Each prefix must have at least one key in en_us.json.
DYNAMIC_PREFIX_OK = True

REGISTER_RE = re.compile(r'\bregister\(\s*"([a-z0-9_/.]+)"')
TRANSLATABLE_RE = re.compile(r'Component\.translatable\(\s*"([^"]+)"')
LITERAL_RE = re.compile(r'"((?:container|gui|block|item|command|advancement|advancements)\.' + MOD_ID +
                        r'\.[A-Za-z0-9_.]*|' + MOD_ID + r'\.[A-Za-z0-9_.]*)"\s*\+?')


def read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def load_json(path: str):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def walk(root: str, suffix: str):
    for base, _dirs, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(suffix):
                yield os.path.join(base, name)


class Report:
    def __init__(self) -> None:
        self.gaps: list[str] = []
        self.checked = 0

    def gap(self, message: str) -> None:
        self.gaps.append(message)

    def ok(self) -> None:
        self.checked += 1


# ---------------------------------------------------------------------------------------------
# Registrations
# ---------------------------------------------------------------------------------------------

def collect_registrations() -> tuple[set[str], set[str]]:
    """Return (block ids, item ids) from *Content.java and registry/ModItems.java."""
    blocks: set[str] = set()
    items: set[str] = set()
    sources = list(walk(JAVA_ROOT, "Content.java"))
    sources.append(os.path.join(JAVA_ROOT, "registry", "ModItems.java"))
    for src in sources:
        if not os.path.isfile(src):
            continue
        text = read(src)
        # Strip comments so javadoc examples such as MenuScreens.register(...) are ignored.
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        text = re.sub(r"//[^\n]*", "", text)
        # A register( call whose id is on the next line counts as one line.
        text = re.sub(r'register\(\s*\n\s*"', 'register("', text)
        for line_no, line in enumerate(text.splitlines(), 1):
            for match in REGISTER_RE.finditer(line):
                name = match.group(1)
                # Look back a couple of lines for the provider the call went through.
                window = "\n".join(text.splitlines()[max(0, line_no - 3):line_no])
                if re.search(r"ModBlocks\.(BLOCKS\.)?register\(", window):
                    blocks.add(name)
                elif re.search(r"ModItems\.ITEMS\.register\(|\bITEMS\.register\(|\bitem\(|\bblockItem\(", window) \
                        or re.search(r"\bITEMS\.register\(", line):
                    items.add(name)
                elif re.search(r"BLOCK_ENTITIES\.register\(|MENUS\.register\(|COMPONENTS\.register\(", window):
                    pass
                else:
                    # Helper methods (private static ... item(String) / blockItem(...)) appear as
                    # register("name") calls only through their callers, which are matched above.
                    pass
        # Helper calls: item("x"), blockItem("x", ...) declared in the same file.
        for match in re.finditer(r'\b(?:item|blockItem)\(\s*"([a-z0-9_]+)"', text):
            items.add(match.group(1))
    for prefix, ids in DYNAMIC_IDS.items():
        if prefix in items:
            items.discard(prefix)
            items.update(ids)
    return blocks, items


# ---------------------------------------------------------------------------------------------
# Model / texture resolution
# ---------------------------------------------------------------------------------------------

def model_path(ref: str) -> str | None:
    """assets path for a model reference such as ``neropower:block/fission_core``."""
    ns, _, rel = ref.partition(":")
    if not rel:
        ns, rel = "minecraft", ns
    if ns != MOD_ID:
        return None  # vanilla / other-namespace parents are not on disk here
    return os.path.join(ASSETS, "models", rel + ".json")


def texture_path(ref: str) -> str | None:
    ns, _, rel = ref.partition(":")
    if not rel:
        ns, rel = "minecraft", ns
    if ns != MOD_ID:
        return None
    return os.path.join(ASSETS, "textures", rel + ".png")


def check_model(ref: str, report: Report, seen: set[str], context: str) -> None:
    if ref in seen:
        return
    seen.add(ref)
    path = model_path(ref)
    if path is None:
        return
    if not os.path.isfile(path):
        report.gap(f"{context}: model {ref} missing ({os.path.relpath(path, ROOT)})")
        return
    try:
        model = load_json(path)
    except (OSError, ValueError) as err:
        report.gap(f"{context}: model {ref} unreadable: {err}")
        return
    report.ok()
    parent = model.get("parent")
    if isinstance(parent, str):
        check_model(parent, report, seen, context)
    for key, tex in (model.get("textures") or {}).items():
        if not isinstance(tex, str) or tex.startswith("#"):
            continue
        tpath = texture_path(tex)
        if tpath is not None and not os.path.isfile(tpath):
            report.gap(f"{context}: texture {tex} (#{key}) missing ({os.path.relpath(tpath, ROOT)})")
        elif tpath is not None:
            report.ok()


def blockstate_models(state) -> list[str]:
    refs: list[str] = []

    def visit(node) -> None:
        if isinstance(node, dict):
            if isinstance(node.get("model"), str):
                refs.append(node["model"])
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    visit(state.get("variants", {}))
    visit(state.get("multipart", []))
    return refs


def variant_key(key: str) -> dict[str, str]:
    pairs = {}
    for part in key.split(","):
        if part.strip():
            name, _, value = part.partition("=")
            pairs[name.strip()] = value.strip()
    return pairs


def check_blockstate_coverage(block: str, state, report: Report, ctx: str) -> None:
    """Every combination of the block's properties must resolve to a model."""
    declared = declared_states(block)
    variants = state.get("variants")
    if not isinstance(variants, dict):
        # multipart: only the property names and values can be checked against the declaration.
        if declared is not None:
            def visit(node) -> None:
                if isinstance(node, dict):
                    when = node.get("when")
                    if isinstance(when, dict):
                        for name, value in when.items():
                            if name in ("OR", "AND"):
                                continue
                            if name not in declared:
                                report.gap(f"{ctx}: blockstate uses undeclared property {name}")
                            else:
                                for v in str(value).split("|"):
                                    if v not in declared[name]:
                                        report.gap(f"{ctx}: blockstate uses {name}={v}, not a declared value")
                    for value in node.values():
                        visit(value)
                elif isinstance(node, list):
                    for value in node:
                        visit(value)
            visit(state.get("multipart", []))
        report.ok()
        return
    keys = [variant_key(k) for k in variants]
    seen: dict[str, set[str]] = {}
    for pairs in keys:
        for name, value in pairs.items():
            seen.setdefault(name, set()).add(value)
    if declared is None:
        props = {name: tuple(sorted(values)) for name, values in seen.items()}
    else:
        props = declared
        for name, values in seen.items():
            if name not in declared:
                report.gap(f"{ctx}: blockstate uses undeclared property {name}")
            else:
                for v in sorted(values - set(declared[name])):
                    report.gap(f"{ctx}: blockstate uses {name}={v}, not a declared value")
    names = sorted(props)
    missing = []
    for combo in itertools.product(*(props[n] for n in names)):
        actual = dict(zip(names, combo))
        hits = sum(1 for pairs in keys if all(actual.get(n) == v for n, v in pairs.items()))
        if hits == 0:
            missing.append(",".join(f"{n}={actual[n]}" for n in names) or "(default)")
        elif hits > 1:
            report.gap(f"{ctx}: blockstate has {hits} variants matching "
                       + (",".join(f"{n}={actual[n]}" for n in names) or "(default)"))
    if missing:
        shown = "; ".join(missing[:4]) + (f"; and {len(missing) - 4} more" if len(missing) > 4 else "")
        report.gap(f"{ctx}: blockstate has no variant for {shown}")
    else:
        report.ok()


def model_elements(ref: str, depth: int = 0):
    """(elements, textures) of a model, following in-mod parents. elements None = none declared."""
    path = model_path(ref)
    if path is None or depth > 8 or not os.path.isfile(path):
        return None, {}
    try:
        model = load_json(path)
    except (OSError, ValueError):
        return None, {}
    textures = dict(model.get("textures") or {})
    elements = model.get("elements")
    parent = model.get("parent")
    if isinstance(parent, str):
        parent_elements, parent_textures = model_elements(parent, depth + 1)
        if elements is None:
            elements = parent_elements
        for key, value in parent_textures.items():
            textures.setdefault(key, value)
    return elements, textures


def check_machine_model(block: str, refs: list[str], report: Report, ctx: str) -> None:
    """A 0.4.0 machine block is a multi-element model with the lamp socket, never a plain cube."""
    for ref in sorted(set(refs)):
        elements, textures = model_elements(ref)
        if model_path(ref) is None or not os.path.isfile(model_path(ref)):
            continue  # reported as a missing model already
        if not elements:
            report.gap(f"{ctx}: model {ref} has no elements (a machine block is never a plain cube)")
            continue
        if not 3 <= len(elements) <= 24:
            report.gap(f"{ctx}: model {ref} has {len(elements)} elements (3 to 24 allowed)")
        if not any(isinstance(e, dict) and e.get("name") == "status_socket" for e in elements):
            report.gap(f"{ctx}: model {ref} has no status_socket element")
        if "particle" not in textures:
            report.gap(f"{ctx}: model {ref} sets no particle texture")
        report.ok()


def item_definition_models(definition) -> list[str]:
    refs: list[str] = []

    def visit(node) -> None:
        if isinstance(node, dict):
            if node.get("type") == "minecraft:model" and isinstance(node.get("model"), str):
                refs.append(node["model"])
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    visit(definition)
    return refs


# ---------------------------------------------------------------------------------------------
# Recipes / loot / tags / lang
# ---------------------------------------------------------------------------------------------

def recipe_results() -> set[str]:
    results: set[str] = set()
    recipe_dir = os.path.join(DATA, "recipe")
    for path in walk(recipe_dir, ".json"):
        try:
            recipe = load_json(path)
        except (OSError, ValueError):
            continue  # reported by the JSON-parse pass

        def visit(node) -> None:
            if isinstance(node, dict):
                for entry in node.get("results") or []:
                    # NeroTech process recipes: "results": [{"item": "...", "count": n}]
                    if isinstance(entry, dict) and isinstance(entry.get("item"), str):
                        results.add(entry["item"])
                result = node.get("result")
                if isinstance(result, str):
                    results.add(result)
                elif isinstance(result, dict):
                    rid = result.get("id") or result.get("item")
                    if isinstance(rid, str):
                        results.add(rid)
                elif isinstance(result, list):
                    for entry in result:
                        if isinstance(entry, dict):
                            rid = entry.get("id") or entry.get("item")
                            if isinstance(rid, str):
                                results.add(rid)
                for value in node.values():
                    visit(value)
            elif isinstance(node, list):
                for value in node:
                    visit(value)

        visit(recipe)
    return results


def pickaxe_members() -> set[str]:
    if not os.path.isfile(PICKAXE_TAG):
        return set()
    try:
        tag = load_json(PICKAXE_TAG)
    except (OSError, ValueError):
        return set()
    members = set()
    for value in tag.get("values", []):
        if isinstance(value, str):
            members.add(value)
        elif isinstance(value, dict) and isinstance(value.get("id"), str):
            members.add(value["id"])
    return members


def png_info(path: str):
    """(width, height, has placeholder mark) from the PNG header and its text chunks."""
    with open(path, "rb") as fh:
        data = fh.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("not a PNG")
    width, height = struct.unpack(">II", data[16:24])
    marked = False
    pos = 8
    while pos + 8 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        if kind in (b"tEXt", b"iTXt", b"zTXt") and PLACEHOLDER_MARK in data[pos + 8:pos + 8 + length]:
            marked = True
        if kind == b"IDAT":
            break
        pos += 12 + length
    return width, height, marked


def check_textures(report: Report) -> None:
    tex_root = os.path.join(ASSETS, "textures")
    for path in walk(tex_root, ".png"):
        rel = os.path.relpath(path, tex_root).replace(os.sep, "/")
        name = rel[:-4].split("/")[-1]
        ctx = f"texture {name} ({rel})"
        try:
            width, height, marked = png_info(path)
        except (OSError, ValueError, struct.error) as err:
            report.gap(f"{ctx}: unreadable PNG: {err}")
            continue
        strip = height > width and height % width == 0
        if height != width and not strip:
            report.gap(f"{ctx}: {width}x{height} is neither a square nor a strip of square frames")
        has_meta = os.path.isfile(path + ".mcmeta")
        top = rel.split("/")[0]
        if top in ("block", "item", "particle"):
            if strip and not has_meta:
                report.gap(f"{ctx}: animated strip without a .png.mcmeta")
            elif has_meta and not strip:
                report.gap(f"{ctx}: has a .png.mcmeta but is a single frame")
        if marked:
            report.gap(f"{ctx}: placeholder sprite, final art missing")
        report.ok()
    for path in walk(tex_root, ".mcmeta"):
        if not os.path.isfile(path[:-len(".mcmeta")]):
            report.gap(f"texture meta {os.path.relpath(path, ROOT)}: no PNG beside it")


def check_sounds(report: Report, lang: dict) -> None:
    sounds_json = os.path.join(ASSETS, "sounds.json")
    sound_dir = os.path.join(ASSETS, "sounds")
    events = {}
    if os.path.isfile(sounds_json):
        try:
            events = load_json(sounds_json)
        except (OSError, ValueError):
            events = {}  # reported by the JSON-parse pass
    listed: set[str] = set()
    for event, body in sorted(events.items()):
        ctx = f"sound {event}"
        subtitle = body.get("subtitle") if isinstance(body, dict) else None
        if not subtitle:
            report.gap(f"{ctx}: no subtitle key")
        elif subtitle not in lang:
            report.gap(f"{ctx}: lang key {subtitle} missing")
        else:
            report.ok()
        entries = body.get("sounds", []) if isinstance(body, dict) else []
        if not entries:
            report.gap(f"{ctx}: lists no sound files")
        for entry in entries:
            ref = entry if isinstance(entry, str) else entry.get("name", "")
            ns, _, rel = ref.partition(":")
            if not rel:
                ns, rel = "minecraft", ns
            if ns != MOD_ID:
                continue
            listed.add(rel)
            if not os.path.isfile(os.path.join(sound_dir, rel + ".ogg")):
                report.gap(f"{ctx}: file sounds/{rel}.ogg missing")
            else:
                report.ok()
    for path in walk(sound_dir, ".ogg"):
        rel = os.path.relpath(path, sound_dir)[:-4].replace(os.sep, "/")
        if rel not in listed:
            report.gap(f"sound {rel.replace('/', '.')}: sounds/{rel}.ogg is in no sounds.json event")
    # Every recipe in tools/sounds must have reached sounds.json.
    recipe_dir = os.path.join(ROOT, "tools", "sounds")
    if os.path.isdir(recipe_dir):
        for path in walk(recipe_dir, ".py"):
            for match in re.finditer(r'^\s*\(\s*"([a-z0-9_]+\.[a-z0-9_]+)"\s*,', read(path), flags=re.M):
                if match.group(1) not in events:
                    report.gap(f"sound {match.group(1)}: recipe in tools/sounds/{os.path.basename(path)} "
                               f"but no sounds.json event")
                else:
                    report.ok()


def check_fluids(report: Report, lang: dict) -> None:
    spec = os.path.join(JAVA_ROOT, "fluid", "FluidSpec.java")
    if not os.path.isfile(spec):
        return
    text = re.sub(r"/\*.*?\*/", "", read(spec), flags=re.S)
    for fluid in re.findall(r'^\s*[A-Z][A-Z0-9_]*\(\s*"([a-z0-9_]+)"', text, flags=re.M):
        ctx = f"fluid {fluid}"
        for part in ("still", "flow"):
            rel = f"textures/block/fluid/{fluid}_{part}.png"
            if not os.path.isfile(os.path.join(ASSETS, *rel.split("/"))):
                report.gap(f"{ctx}: sprite {rel} missing")
            else:
                report.ok()
        for key in (f"fluid.{MOD_ID}.{fluid}", f"fluid_type.{MOD_ID}.{fluid}"):
            if key not in lang:
                report.gap(f"{ctx}: lang key {key} missing")
            else:
                report.ok()


def check_particles(report: Report) -> None:
    for path in walk(os.path.join(ASSETS, "particles"), ".json"):
        name = os.path.basename(path)[:-5]
        try:
            definition = load_json(path)
        except (OSError, ValueError):
            continue  # reported by the JSON-parse pass
        textures = definition.get("textures") or []
        if not textures:
            report.gap(f"particle {name}: lists no textures")
        for ref in textures:
            ns, _, rel = ref.partition(":")
            if not rel:
                ns, rel = "minecraft", ns
            if ns != MOD_ID:
                continue
            if not os.path.isfile(os.path.join(ASSETS, "textures", "particle", rel + ".png")):
                report.gap(f"particle {name}: sprite textures/particle/{rel}.png missing")
            else:
                report.ok()


def family_filter(gaps: list[str], family: str) -> list[str]:
    """The gaps that name one of the family's ids (also as the stem of a texture or lang key) or
    one of its sound groups."""
    ids = "|".join(re.escape(i) for i in sorted(FAMILIES.get(family, ()))) or "(?!)"
    groups = "|".join(re.escape(g) for g in sorted(SOUND_GROUPS.get(family, ()))) or "(?!)"
    pattern = re.compile(r"(?<![a-z0-9_])(?:(?:" + ids + r")(?![a-z0-9])|sound (?:" + groups + r")[./])")
    return [g for g in gaps if pattern.search(g)]


def check_all_json(report: Report) -> None:
    for path in walk(RES, ".json"):
        try:
            load_json(path)
            report.ok()
        except ValueError as err:
            report.gap(f"invalid JSON: {os.path.relpath(path, ROOT)}: {err}")


def java_lang_keys() -> dict[str, set[str]]:
    """Map of lang key (or key prefix ending in '.') -> set of source files using it."""
    keys: dict[str, set[str]] = {}
    for src in walk(JAVA_ROOT, ".java"):
        text = read(src)
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        text = re.sub(r"//[^\n]*", "", text)
        rel = os.path.relpath(src, ROOT)
        for match in TRANSLATABLE_RE.finditer(text):
            keys.setdefault(match.group(1), set()).add(rel)
        for match in LITERAL_RE.finditer(text):
            key = match.group(1)
            if "." in key:
                keys.setdefault(key, set()).add(rel)
    return keys


def is_prefix(key: str) -> bool:
    """A literal that Java completes at runtime ends in '.' or '_'."""
    return key.endswith(".") or key.endswith("_")


# ---------------------------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------------------------

def main() -> int:
    report = Report()

    blocks, items = collect_registrations()
    if not blocks or not items:
        print("error: found no registrations - is the script in tools/ of the NeroPower repo?")
        return 2

    try:
        lang = load_json(LANG)
    except (OSError, ValueError) as err:
        print(f"error: cannot read {LANG}: {err}")
        return 2

    results = recipe_results()
    pickaxe = pickaxe_members()
    seen_models: set[str] = set()

    # Every registered block.
    for block in sorted(blocks):
        ctx = f"block {block}"
        state_path = os.path.join(ASSETS, "blockstates", block + ".json")
        if not os.path.isfile(state_path):
            report.gap(f"{ctx}: blockstate missing")
        else:
            try:
                state = load_json(state_path)
                report.ok()
                refs = blockstate_models(state)
                if not refs:
                    report.gap(f"{ctx}: blockstate references no models")
                for ref in refs:
                    check_model(ref, report, seen_models, ctx)
                check_blockstate_coverage(block, state, report, ctx)
                if block in MACHINES or block in ALARM_MACHINES:
                    check_machine_model(block, refs, report, ctx)
            except ValueError as err:
                report.gap(f"{ctx}: blockstate invalid JSON: {err}")
        if block not in items:
            report.gap(f"{ctx}: no block item registered")
        loot = os.path.join(DATA, "loot_table", "blocks", block + ".json")
        if not os.path.isfile(loot):
            report.gap(f"{ctx}: loot table missing")
        else:
            report.ok()
        if f"block.{MOD_ID}.{block}" not in lang:
            report.gap(f"{ctx}: lang key block.{MOD_ID}.{block} missing")
        else:
            report.ok()
        if f"{MOD_ID}:{block}" not in pickaxe:
            report.gap(f"{ctx}: not in #minecraft:mineable/pickaxe")
        else:
            report.ok()

    # Every registered item (block items included).
    for item in sorted(items):
        ctx = f"item {item}"
        definition = os.path.join(ASSETS, "items", item + ".json")
        if not os.path.isfile(definition):
            report.gap(f"{ctx}: item definition missing (assets/{MOD_ID}/items/{item}.json)")
        else:
            try:
                refs = item_definition_models(load_json(definition))
                report.ok()
                if not refs:
                    report.gap(f"{ctx}: item definition references no model")
                for ref in refs:
                    check_model(ref, report, seen_models, ctx)
            except ValueError as err:
                report.gap(f"{ctx}: item definition invalid JSON: {err}")
        item_model = os.path.join(ASSETS, "models", "item", item + ".json")
        if not os.path.isfile(item_model):
            report.gap(f"{ctx}: item model missing (models/item/{item}.json)")
        else:
            check_model(f"{MOD_ID}:item/{item}", report, seen_models, ctx)
        # Every item — block items included — is named by item.<mod>.<id>; a block item's block.
        # key names only the placed block, and the two must agree.
        lang_key = f"item.{MOD_ID}.{item}"
        if lang_key not in lang:
            report.gap(f"{ctx}: lang key {lang_key} missing")
        else:
            report.ok()
            block_key = f"block.{MOD_ID}.{item}"
            if item in blocks and lang.get(block_key) != lang[lang_key]:
                report.gap(f"{ctx}: {lang_key} ({lang[lang_key]!r}) differs from {block_key} "
                           f"({lang.get(block_key)!r})")
        if f"{MOD_ID}:{item}" not in results and item not in ALLOW_NO_RECIPE:
            report.gap(f"{ctx}: no recipe produces {MOD_ID}:{item}")
        else:
            report.ok()

    # Every model on disk, whether or not something references it, must resolve its textures.
    for path in walk(os.path.join(ASSETS, "models"), ".json"):
        rel = os.path.relpath(path, os.path.join(ASSETS, "models"))[:-5].replace(os.sep, "/")
        check_model(f"{MOD_ID}:{rel}", report, seen_models, f"model {rel}")

    # Every JSON file must parse.
    check_all_json(report)

    # Every "translate" key in an advancement must exist, and every advancement icon must be ours
    # or vanilla (an unknown namespace would be a typo).
    for path in walk(os.path.join(DATA, "advancement"), ".json"):
        rel = os.path.relpath(path, ROOT)
        try:
            adv = load_json(path)
        except ValueError:
            continue
        display = adv.get("display") or {}
        for field in ("title", "description"):
            key = (display.get(field) or {}).get("translate")
            if key and key not in lang:
                report.gap(f"advancement {rel}: lang key {key} missing")
            elif key:
                report.ok()
        icon = (display.get("icon") or {}).get("id", "")
        if icon.startswith(f"{MOD_ID}:") and icon[len(MOD_ID) + 1:] not in items:
            report.gap(f"advancement {rel}: icon {icon} is not a registered item")
        parent = adv.get("parent")
        if isinstance(parent, str) and parent.startswith(f"{MOD_ID}:"):
            if not os.path.isfile(os.path.join(DATA, "advancement", parent[len(MOD_ID) + 1:] + ".json")):
                report.gap(f"advancement {rel}: parent {parent} does not exist")
            else:
                report.ok()
        elif isinstance(parent, str) and parent.startswith("nerotech:"):
            # Continues NeroTech's chain: verify against a sibling NeroTech checkout when there is one.
            sibling = os.path.join(ROOT, os.pardir, "nerotech", "common", "src", "main", "resources", "data",
                                   "nerotech", "advancement", parent[len("nerotech:"):] + ".json")
            if os.path.isdir(os.path.join(ROOT, os.pardir, "nerotech")):
                if not os.path.isfile(sibling):
                    report.gap(f"advancement {rel}: parent {parent} does not exist in the NeroTech checkout")
                else:
                    report.ok()

    # Lang keys referenced from Java.
    used = java_lang_keys()
    for key in sorted(used):
        if is_prefix(key):
            if not any(k.startswith(key) for k in lang):
                report.gap(f"lang: no key with prefix {key} (used by {', '.join(sorted(used[key]))})")
            else:
                report.ok()
        elif not key.startswith(("gui.", "block.", "item.", "container.", "message.", "subtitles.",
                                 "command.", "advancement", "fluid.", "fluid_type.", "death.", "status.",
                                 "itemGroup.", "neropower.")) or (
                key.split(".")[1] not in (MOD_ID,) and key.startswith(("block.", "item.", "fluid.", "fluid_type."))):
            report.ok()  # vanilla's or another mod's key (block.minecraft.water, options.on, fluid.nerotech.*)
        elif key not in lang:
            report.gap(f"lang: key {key} missing (used by {', '.join(sorted(used[key]))})")
        else:
            report.ok()

    # Textures, sounds, fluids and particles.
    check_textures(report)
    check_sounds(report, lang)
    check_fluids(report, lang)
    check_particles(report)

    gaps = report.gaps
    scope = ""
    if "--family" in sys.argv[1:-1]:
        family = sys.argv[sys.argv.index("--family") + 1]
        if family not in FAMILIES:
            print(f"error: unknown family {family} (have: {', '.join(sorted(FAMILIES))})")
            return 2
        gaps = family_filter(gaps, family)
        scope = f" [{family}: {len(gaps)} of {len(report.gaps)} gaps]"
    print(f"NeroPower content check: {len(blocks)} blocks, {len(items)} items, "
          f"{report.checked} checks passed, {len(gaps)} gaps{scope}")
    for gap in gaps:
        print("  - " + gap)
    return 1 if gaps else 0


if __name__ == "__main__":
    sys.exit(main())

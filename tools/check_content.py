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
* every JSON file under the resource tree parses;
* every lang key the Java code uses (``Component.translatable("...")`` and string literals
  starting with ``container.neropower.``, ``gui.neropower.``, ``command.neropower.`` or
  ``neropower.``) exists in ``en_us.json``. Keys built from a prefix plus a runtime value are
  matched by prefix.

Prints a summary and exits non-zero when anything is missing. Run from anywhere:

    python3 tools/check_content.py
"""

from __future__ import annotations

import json
import os
import re
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
ALLOW_NO_RECIPE = {"spent_fuel_rod", "spent_isotope_pellet"}

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
        elif key not in lang:
            report.gap(f"lang: key {key} missing (used by {', '.join(sorted(used[key]))})")
        else:
            report.ok()

    print(f"NeroPower content check: {len(blocks)} blocks, {len(items)} items, "
          f"{report.checked} checks passed, {len(report.gaps)} gaps")
    for gap in report.gaps:
        print("  - " + gap)
    return 1 if report.gaps else 0


if __name__ == "__main__":
    sys.exit(main())

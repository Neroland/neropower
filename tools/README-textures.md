# NeroPower textures and sounds

Every PNG under `common/src/main/resources/assets/neropower/textures/{block,item,particle}` and
every `.ogg` under `assets/neropower/sounds` is procedural. Nothing there is hand-edited: change
the painter or the recipe, regenerate, commit both.

## Layout

| File | What it is |
|---|---|
| `tools/gen_textures.py` | Driver: runs the painters |
| `tools/gen_animations.py` | Driver: turns breathing and travelling textures into frame strips |
| `tools/painters/kit.py` | Palette, output handling, shared painters, palette audit |
| `tools/painters/existing.py` | The 0.3.2 art, kept pixel-identical |
| `tools/painters/fluids.py` | Steam, exhaust steam, heavy water |
| `tools/painters/<family>.py` | One module per family: `fission`, `steam`, `backend`, `storage`, `grid`, `radiation` |
| `tools/contact_sheet.py` | Review sheet and palette audit for one family |
| `tools/gen_sounds.py` | Sound synthesis kit and driver |
| `tools/sounds/<family>.py` | Sound recipes, one module per family |
| `tools/check_content.py` | Completeness check for everything registered |

## Regenerating

```
pip install pillow                        # once; the texture tools exit 0 with a notice without it
python tools/gen_textures.py              # paint what is missing
python tools/gen_textures.py --force      # repaint; files whose pixels do not change are left alone
python tools/gen_animations.py            # build the frame strips and their .png.mcmeta
python tools/gen_textures.py --verify     # prove no shipped texture would change (exit 1 if one would)
python tools/gen_sounds.py                # needs ffmpeg on PATH for the .ogg step
python tools/check_content.py             # models, blockstates, textures, lang, sounds, fluids
```

* `--family NAME` (repeatable) limits `gen_textures.py`, `gen_animations.py` and `gen_sounds.py`
  to one family; `--out DIR` writes to `DIR` instead of the mod (`DIR/textures/...`).
* `gen_textures.py` is additive by default (an existing PNG is kept). With `--force` it repaints,
  but a file whose decoded pixels would be the same is not rewritten, so a regeneration never
  churns art that did not change. It ends with a coverage check naming any PNG no painter owns.
* `gen_animations.py` rebuilds each strip from its frame 0, so it is idempotent and must run
  **after** `gen_textures.py`. It writes `<name>.png.mcmeta` as `{"animation": {"frametime": n}}`.
* Every painter seeds its RNG from the texture name (`rng_for(name)`), so output is stable.
* Sizes: 32x32 for blocks and items; breathing strips 32x256 (8 frames, frametime 3); fluid
  stills 32x512 (16 frames, frametime 2); renderer sheets 32x32 or 64x64.

`--verify` compares decoded pixels, not file bytes: a PNG may carry metadata chunks the painter
never wrote. The 0.3.2 set is the reference: `painters/existing.py` must always verify clean.

## Palette

NeroPower is NeroTech's alloy body with an amber emissive ramp. The names below are the constants
in `painters/kit.py`.

Shared body (never retinted):

| Name | Hex | Role |
|---|---|---|
| `A_DARK` | `#181E24` | alloy shadow |
| `ALLOY[]` | `#28323A` `#303C44` `#222A32` `#3A4852` | alloy noise ramp |
| `A_LIGHT` | `#849CAC` | alloy highlight, 2 px bevel |
| `T_CYAN` | `#24D0DE` | the one thin static family tick per block |

Power ramp, the mod's light:

| Name | Hex | Role |
|---|---|---|
| `P_EMBER` | `#401806` | unlit sockets, idle |
| `P_AMBER` | `#CC7012` | base emissive, working |
| `P_HOT` | `#FFA028` | accents, LED cores |
| `P_GLOW` | `#FFE2A0` | peak, at most 4 px per face |

Accent and functional colours (each has one job):

| Name | Hex | Used for, and only for |
|---|---|---|
| `V_DEEP` `V_ARC` `V_LIGHT` `V_GLOW` | `#1C1240` `#5B3FD6` `#9B7BFF` `#E0D6FF` | Arc violet: superconducting-tier fields. At most 8 % of a face, never on a teal material |
| `STEAM_W` / `STEAM_X` | `#E8ECEF` / `#B9C2C8` | steam and exhaust steam, pipe ring marks, plumes |
| `HW_BLUE` (`HW_SHADE`, `HW_LIGHT`) | `#2E6BAA` (`#1C4474`, `#6FA3D8`) | heavy water |
| `RAD_LIME` (`RAD_DEEP`) | `#B6F23A` (`#2C3F0A`) | contamination only, never a machine that is merely working |
| `TRIP_W` on `TRIP_R` | `#F2F4F6` on `#FF3824` | the mechanical trip flag |
| `R_ALARM` | `#FF3824` | failing |
| `H_CRIT` | `#FF5438` | overheat; drawn by renderers, never baked on a face |
| `HAZ_Y` / `HAZ_K` | `#FFD61E` / `#18181E` | hazard stripes |
| `CH_GREEN` `CH_AMBER` `CH_RED` `CH_OFF` | `#48D660` `#FFA028` `#EC4028` `#0E1216` | charge windows |

Materials (`[base, shade, light]`): `CINDER` `#704640` `#4E2E2C` `#A87060`; `SUPER`
`#60DCD6` `#289696` `#BEFFFA`; `GRAPHITE` `#17181B` `#22242A` `#34373F`; `SHIELD` `#8E949A`
`#A7ADB2` `#6C7278`; item metals `SILVER`, `URANIUM`, `REPROC`, `SPENT`. `TIER_TRIM` is alloy,
cindersteel, arc violet for tiers 1 to 3. `PLASMA` (`#82F8FF`) is defined only so the audit can
ban it.

## What breathes

`gen_animations.py` pulses only pixels inside a hue window, and only on textures a family lists
in its `PULSE` table:

| Window | Hue | Catches |
|---|---|---|
| `HUE_AMBER` | 0.03 to 0.125 | the power ramp |
| `HUE_VIOLET` | 0.68 to 0.75 | arc violet |
| `HUE_ALARM` | 0.0 to 0.125 | amber and red, for alarm faces |
| `HUE_POWER_AND_ARC` | both | a face carrying both lights |

Lime, red, white, hazard yellow, cyan, heavy-water blue and the charge colours sit outside the
amber and violet windows and never breathe. Cindersteel falls inside the amber window but is a
material, so the colours in `STATIC_COLOURS` are masked out by exact match: paint cindersteel
with the three `CINDER` colours unblended on any texture that pulses.

## Shared painters

All take `px = img.load()` and inclusive rectangles.

* Bodies: `machine_base(name)`, `front_base(name)` (with the lamp socket), `noise_fill`, `bevel`,
  `rivets`, `recess`.
* Marks: `led`, `tell` (the cyan tick, one per block), `status_socket`, `tier_marks`,
  `family_mark(px, family)` for top faces, `haz_band`, `haz_band_v`, `hazard_edge`, `trip_flag`.
* Parts: `louvre`, `flange`, `pipe_ring`, `plate_stack`, `can`, `rib`, `frost`, `trace`,
  `chevrons`.
* Primitives: `rect`, `frame`, `line`, `radial`, `mix`, `ramp_at`.
* Output: `save(img, folder, name)`, `save_strip(frames, folder, name, frametime)`,
  `write_mcmeta`. `folder` is `block`, `item` or `particle`; `name` may hold a sub-folder
  (`fluid/steam_still`).
* `kit_sprites()` paints `status_lamp`, `status_lamp_half` and `heat_shimmer`, the white sprites
  the renderers' shared lamp helper tints.

Rules for new art: a front carries one icon motif at 10x10 px or more; no centred orb, concentric
rings, bar graph, spiral or three-blade prop; tiers differ by shape count before colour; state is
told by rhythm and shape first, colour second.

## Fluids

`fluid/<id>_still.png` is a 16-frame strip, `fluid/<id>_flow.png` a single tile that tiles both
ways. The fluid models multiply the sprite by the fluid's tint, so the sprites are painted
relative to it (white shows as the tint itself). `painters/fluids.py` explains the switch to
flip if the models ever stop tinting.

## Contact sheets

```
python tools/contact_sheet.py --family steam --out path/to/steam.png
```

One row per block or item on three backgrounds: faces and state sprites at 4x and 1x, black-fill
silhouettes of the model, and a grayscale copy of the states. It prints a palette audit (violet
share, cyan tells, plasma, lime, stray hues); `--strict` makes findings fail the run. A family
controls its rows with a `SHEET` list (see the tool's docstring).

## Sounds

Recipes build every sound from oscillators, noise, filters and envelopes: no samples and no
third-party audio. `python tools/gen_sounds.py --list` prints the event table; `--manifest FILE`
writes the `sounds.json` fragment and subtitle keys for the recipes. Event `a.b` becomes
`sounds/a/b.ogg` with subtitle key `subtitles.neropower.a.b`; `variants: n` writes
`b_1.ogg` to `b_n.ogg` under one event. Loops are at most 2 s.

## Adding a texture

Add a painter to the family's module, call it from that module's `paint()`, and if it should
breathe list it in the module's `PULSE` table (or `SCROLL` for travelling art). A new family is a
new file in `tools/painters/`; the drivers find it by name.

# NeroPower textures

All block and item art under `common/src/main/resources/assets/neropower/textures/{block,item}`
is procedural: two standalone Pillow scripts paint it, deterministically, from the texture name.
Nothing under those two folders is hand-edited — change the painter, regenerate, commit both.

## Regenerating

```
pip install pillow                      # once; both scripts exit 0 with a notice without it
python tools/gen_textures.py --force    # paint every 32x32 (block + item); frame 0 of animated ones
python tools/gen_animations.py          # turn the animated fronts into 32x256 8-frame strips + .mcmeta
```

* `gen_textures.py` is ADDITIVE-ONLY by default (skips PNGs that already exist); `--force`
  replaces the whole set. It ends with a coverage check that names any PNG no painter owns.
* `gen_animations.py` always rebuilds from frame 0 of the existing strip, so it is idempotent and
  must run **after** `gen_textures.py`. It writes `<name>.png.mcmeta` as
  `{"animation": {"frametime": 3}}` (NeroTech's format, frametime 3 across the set).
* Both resolve paths relative to `tools/`, need no NeroTech checkout and no Gradle.
* Every painter seeds its RNG from the texture name (`rng_for(name)`), so re-runs are byte-stable.
* Textures are 32x32 like NeroTech's; animated ones are 32x(32*8).

## Palette

NeroPower is NeroTech's alloy body with the emissive ramp swapped from teal to amber "power".
Every emissive pixel uses ONLY the `P_*` ramp so the PULSE animator's hue window (0.03..0.125)
catches it; everything else stays static.

| Name       | RGB             | Role                                                          |
|------------|-----------------|---------------------------------------------------------------|
| `A_DARK`   | 24, 30, 36      | alloy shadow (== NeroTech)                                    |
| `ALLOY[]`  | 40,50,58 / 48,60,68 / 34,42,50 / 58,72,82 | alloy noise ramp (== NeroTech)      |
| `A_LIGHT`  | 132, 156, 172   | alloy highlight (== NeroTech)                                 |
| `P_EMBER`  | 64, 24, 6       | power deep ember — LED sockets, conduit dashes (replaces T_DEEP) |
| `P_AMBER`  | 204, 112, 18    | power amber — base emissive (replaces T_TEAL)                 |
| `P_HOT`    | 255, 160, 40    | power hot orange — accents, LED cores (replaces T_CYAN/T_PLASMA) |
| `P_GLOW`   | 255, 226, 160   | power white-gold peak (replaces T_GLOW)                       |
| `T_CYAN`   | 36, 208, 222    | NeroTech's cyan — the ONE thin family tell per block (static) |
| `R_ALARM`  | 255, 56, 36     | fission alarm red — `fission_core_front_alarm` only (pulses)  |
| `HAZ_Y`/`HAZ_K` | 255,214,30 / 24,24,30 | hazard stripes — fission core shell edges, casing corners, RTG trefoil |
| `CH_GREEN` | 72, 214, 96     | battery charge window: full (levels 3–4)                      |
| `CH_AMBER` | 255, 160, 40    | battery charge window: mid (level 2)                          |
| `CH_RED`   | 236, 64, 40     | battery charge window: low (level 1, empty pip on level 0)    |
| `CH_OFF`   | 14, 18, 22      | battery charge window: unlit segment                          |
| `SILVER`   | 150,154,162 / 118,122,130 / 180,184,192 | rod caps, capsule shells              |
| `URANIUM`  | 106,128,92 / 78,96,70 / 150,172,128 | uranium pellet                            |
| `REPROC`   | 148,160,140 / 116,128,110 / 188,198,176 | reprocessed pellet (paler)            |
| `SPENT`    | 70,66,62 / 52,48,46 / 96,90,84 | spent rods / capsules                         |

Shared painters (ported from NeroTech): `noise_fill`, 2px `bevel`, `rivets`, `machine_base`,
`recess`, `led` (ember socket + hot core), `tell` (the cyan tick), `haz_band`/`haz_band_v`,
`radial` (polar painter for rings, discs, wheels).

## Manifest

Blocks (`textures/block`), `*` = animated (32x256, `.png.mcmeta`):

* fission_core, fission_core_top, fission_core_front\*, fission_core_front_alarm\*,
  fission_core_port, fission_core_glow\* (BER, transparent), fission_casing,
  control_rod_assembly, control_rod_assembly_top
* battery_cell_{basic,advanced,elite}_{side,top,front_0..4}, battery_cell_bar (BER strip:
  left = empty/red, right = full/green), battery_bank_controller_{side,top,front\*}
* beam_transmitter, beam_receiver, beam_relay, orbital_receiver (each `<name>`, `_top`, `_front`),
  beam_transmitter_linked_front\*, beam_relay_linked_front\*, beam_dish (BER disc),
  beam_ray\* (BER beam, scrolls down 4px/frame, y-tileable)
* radioisotope_generator{,_top,_front\*}, rtg_fin (BER fin, hot at the bottom rows)
* stirling_generator{,_top,_front\*}, stirling_flywheel (BER spoked wheel, cyan balance mark)

Items (`textures/item`): control_rod, fuel_rod, spent_fuel_rod, uranium_pellet,
reprocessed_pellet, isotope_pellet, spent_isotope_pellet.

Adding a texture: add a painter (or extend one) in `gen_textures.py`, call it from `main()`, and
if it should breathe add it to `PULSE` in `gen_animations.py` (or `SCROLL` for travelling art).

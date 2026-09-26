# Configuration

NeroPower's settings live in **`config/neropower.properties`**, managed by Neroland Core's shared
config system: the file is created with defaults and comments on first run, every value is range
checked, and `/neroland config reload` applies changes without a restart. Every key below is
**server-authoritative** — on a multiplayer server the server's values win and are synced to
clients. NeroPower never reads `nerotech.properties`; NeroTech's thermal balance stays NeroTech's.
The one exception is `telemetryEnabled` (see [Privacy](#privacy)), which is **client-local**:
each player and server decides for themselves.

Permille values are thousandths: `600` = 60 %.

## Master toggles

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `overloadEnabled` | `true` | — | `true`: an unmanaged reactor walks the full [failure ladder](Failure-Stages.md) and destroys itself at the top. `false` (survival-friendly): the ladder is pinned at *Unstable* — throttled output and alarms, never a failure action. |
| `terrainDamageMode` | `auto` | `on` / `off` / `auto` | Whether a reactor failure breaks blocks. `auto` = off on a dedicated server, on in singleplayer and LAN. `off` keeps the blast's damage and knockback (and the machine is still lost) but breaks nothing. |
| `failureRadiusCap` | `8` | 1–16 | Hard cap on any failure blast radius, whatever the machine asks for. |
| `transmissionEnabled` | `true` | — | `true`: [beamed power](Beamed-Power.md) links and transfers. `false`: the blocks place but every beam stays dark. |
| `planetEfficiencyEnabled` | `true` | — | `true`: the [Stirling Generator](RTG-and-Stirling.md#stirling-generator)'s cold-face bonus scales with how cold the local ambient is (NeroTech's planet ambient): `bonus × (1000 + clamp(−ambient × 5, 0, 1000)) ⁄ 1000` — ambient −80 on a cold planet gives +40 % on top of the bonus, ambient 0 or warmer none. `false`: the flat bonus everywhere. The RTG ignores it (decay is the same on every world). |

## Failure ladder

See [Failure Stages](Failure-Stages.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `failureWarningPermille` | `600` | 1–1000 | Heat (permille of heat capacity) at which a reactor enters *Warning* (alarm state, full output). |
| `failureUnstablePermille` | `800` | 1–1000 | Heat at which a reactor enters *Unstable* (output scaled by `unstablePenaltyPermille`, warning alert to the owner). Must exceed `failureWarningPermille`. |
| `failureFailurePermille` | `1000` | 1–1000 | Heat at which a reactor *fails* (its failure action runs). Must exceed `failureUnstablePermille`; ignored when `overloadEnabled` is `false`. |
| `failureHysteresisPermille` | `50` | 0–1000 | A stage is only left downwards once heat drops this far below the threshold that entered it. |
| `failureMinDwellTicks` | `100` | 0–72000 | Minimum ticks a reactor stays in each stage before it may climb to the next (100 = 5 s). |
| `unstablePenaltyPermille` | `600` | 0–1000 | Output multiplier while *Unstable* (600 = 60 %). *Failure* always yields 0. |

## Fission reactor

See [Fission Reactor](Fission-Reactor.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `fissionNePerRod` | `120` | 1–100000 | Nominal NE/tick one fresh Fuel Rod contributes at 100 % of the burn-up curve; the core sums every loaded rod, then scales by control rods, preset, upgrades and the failure ladder. |
| `fissionHeatPerRodPerTick` | `6` | 0–10000 | Heat one rod adds per tick at 100 % of its heat factor (heat factor = output factor + 200 permille). Control rods reduce it in step with output. |
| `fissionBurnupPerTick` | `8` | 1–1000000 | Burn-up a loaded rod gains per tick, in permille ×1000 (8 = a rod lasts about 125,000 ticks, ~104 minutes, at Balanced). |
| `fissionBurnupCurve` | `0=1200,200=1000,600=800,1000=300` | text | Output factor (permille of nominal) by rod burn-up (permille), as `burnup=factor` knots interpolated linearly. Fewer than two valid knots falls back to the default. |
| `fissionControlRodPermille` | `150` | 0–1000 | Output and heat reduction per Control Rod Assembly inside the shell; the factor never drops below 15 %. |
| `fissionPoisonPerTick` | `2` | 0–1000 | Neutron poison gained per tick while the core runs **hot**: full output, every usable slot loaded, and the effective control-rod factor above `fissionPoisonRodThresholdPermille` (0 disables poisoning). |
| `fissionPoisonRodThresholdPermille` | `900` | 0–1000 | Poison only builds while the effective control-rod factor (permille; 1000 = no assemblies) is strictly **above** this line. With the defaults one Control Rod Assembly (850) or a SCRAM (150) keeps the core steady and poison decays. |
| `fissionPoisonDecayPerTick` | `3` | 0–1000 | Poison shed per tick whenever the core is not accumulating it. |
| `fissionPoisonStallPermille` | `900` | 1–1000 | Poison level at which the core stalls (the xenon pit) until it has decayed 300 below this line. |
| `fissionScorchEnabled` | `false` | — | `true`: a fission failure also leaves a scorch zone that hurts living creatures for `fissionScorchDays` real days. |
| `fissionScorchDays` | `3` | 1–365 | Real (calendar, UTC) days a scorch zone persists. |
| `fissionScorchRadius` | `6` | 1–32 | Half-edge (blocks) of the cubic scorch zone centred on the failed reactor. |

## Battery banks

See [Battery Banks](Battery-Banks.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `batteryCellCapacityBasic` | `500000` | 1000–100000000 | Basic Battery Cell capacity (NE). |
| `batteryCellCapacityAdvanced` | `2000000` | 1000–100000000 | Advanced Battery Cell capacity (NE). |
| `batteryCellCapacityElite` | `8000000` | 1000–100000000 | Elite Battery Cell capacity (NE). |
| `batteryCellIoBasic` | `1000` | 1–1000000 | Basic Battery Cell maximum insert / extract per tick (NE/t). |
| `batteryCellIoAdvanced` | `4000` | 1–1000000 | Advanced Battery Cell maximum insert / extract per tick (NE/t). |
| `batteryCellIoElite` | `16000` | 1–1000000 | Elite Battery Cell maximum insert / extract per tick (NE/t). |
| `bankScanRadius` | `2` | 1–4 | How far (blocks, each axis) a Battery Bank Controller looks for connected cells: 1 = 3×3×3, 2 = 5×5×5, … 4 = 9×9×9. |
| `bankMaxIoCap` | `64000` | 1–10000000 | Hard cap on a bank's pooled insert / extract per tick; otherwise min(member cell I/O) × member count. |
| `bankPriorityThresholdPermille` | `300` | 0–1000 | Priority Source mode: a bank only pushes into a neighbour whose own buffer is below this fill. Buffer mode ignores it. |

## Beamed power

See [Beamed Power](Beamed-Power.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `beamRange` | `128` | 8–512 | Maximum straight-line distance (blocks) a transmitter or relay may beam to its target. |
| `beamLossPermillePerBlock` | `3` | 0–100 | Loss per block of distance (permille): delivered = sent × (1 − loss/1000)^distance. 3 = about 68 % delivered at 128 blocks; 0 = lossless. |
| `beamTransferPerPass` | `2000` | 1–1000000 | NE a transmitter or relay sends per pass, before loss. |
| `beamCheckIntervalTicks` | `5` | 1–40 | Ticks between passes; each pass re-checks line of sight, moves energy and damages anything in the beam. |
| `beamDamage` | `0.5` | 0.0–10.0 | Damage (half-hearts) dealt every pass to living creatures standing in an active beam; 0 disables the hazard. |
| `beamMaxHops` | `8` | 1–32 | Longest relay chain a single pass follows before it stops (loop guard). |
| `beamCrossDimension` | `false` | — | `true`: a transmitter may target an Orbital Receiver in a space dimension from another dimension, paying `beamOrbitalHopLossPermille` instead of distance loss. |
| `beamOrbitalHopLossPermille` | `300` | 0–1000 | Fixed loss of a cross-dimension orbital hop (300 = 70 % delivered). |

## RTG and Stirling

See [RTG and Stirling](RTG-and-Stirling.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `rtgNePerTick` | `30` | 1–10000 | NE per tick a Radioisotope Generator produces from a fresh Isotope Pellet; halves every `rtgHalfLifeDays`. |
| `rtgHalfLifeDays` | `20` | 1–365 | Half-life of a pellet in in-game days (1 day = 24,000 ticks). |
| `rtgCutoffPermille` | `50` | 1–999 | A pellet is spent once its output falls below this permille of `rtgNePerTick` (50 = 5 %, about 4.3 half-lives). |
| `rtgSpentPelletPollution` | `200` | 0–100000 | Pollution burst (NeroTech's regional pollution, never attributed to a player) recorded per Spent Isotope Pellet destroyed as a dropped item — fire, lava, cactus, explosion. 0 disables it. |
| `stirlingNePerHeatUnit` | `15` | 1–1000 | NE a Stirling Generator produces per unit of heat it draws. |
| `stirlingMaxDrawPerOp` | `8` | 1–1000 | Hard cap on the heat drawn from a neighbour in one tick. |
| `stirlingDrawPermille` | `100` | 1–1000 | Share (permille) of the hot neighbour's gradient above ambient drawn per tick. |
| `stirlingColdFaceBonusPermille` | `500` | 0–1000 | Output bonus while touching a cold sink (water, ice, snow or a NeroTech Radiator); without one the usable gradient is halved. Scaled by the local ambient when `planetEfficiencyEnabled` is on. |

## Privacy

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `telemetryEnabled` | `true` | — | Anonymous crash reports for errors in NeroPower code (Sentry, EU servers; no names, UUIDs, IPs or world data). Set `false` to opt out; takes effect on restart. Client-local, never synced. See [Privacy](Privacy.md). |

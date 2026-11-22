# Configuration

NeroPower's settings live in **`config/neropower.properties`**, managed by Neroland Core's shared
config system: the file is created with defaults and comments on first run, every value is range
checked, and `/neroland config reload` applies changes without a restart. Every key below is
**server-authoritative** — on a multiplayer server the server's values win and are synced to
clients. NeroPower never reads `nerotech.properties`; NeroTech's thermal balance stays NeroTech's.
The exceptions are `telemetryEnabled` and `geigerHudCorner` (see [Client and privacy](#client-and-privacy)),
which are **client-local**: each player decides for themselves.

Permille values are thousandths: `600` = 60 %.

This page lists all 144 keys.

## Master toggles

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `overloadEnabled` | `true` | — | `true`: an unmanaged reactor walks the full [failure ladder](Failure-Stages.md) and destroys itself at the top. `false` (survival-friendly): the ladder is pinned at *Unstable* — throttled output and alarms, never a failure action. |
| `terrainDamageMode` | `auto` | on / off / auto | Whether a reactor failure breaks blocks. `auto` = off on a dedicated server, on in singleplayer and LAN. `off` keeps the blast's damage and knockback (and the machine is still lost) but breaks nothing. |
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

See [Fission Reactor](Fission-Reactor.md) and [Fuel Lines](Fuel-Lines.md).

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
| `fissionModeratorPermillePerContact` | `10` | 0–100 | Output AND heat bonus (permille) per moderator fuel-channel contact: each interior moderator counts its face-adjacent interior air blocks, weighted 1 (graphite) or 2 (moderator tank). |
| `fissionModeratorCapPermille` | `400` | 0–1000 | Largest output AND heat bonus (permille) moderators can give one reactor. |
| `fissionEuropiumControlRodPermille` | `250` | 0–1000 | Output AND heat reduction (permille) per Europium Control Rod Assembly; shares the 15% floor. |
| `breederTicksPerPellet` | `36000` | 1200–1000000 | Running ticks one Breeder Blanket at 100% output factor needs to breed one Kindled Pellet. |
| `breederMaxBlankets` | `8` | 1–64 | Breeder Blankets counted for the breeding rate (more still cost reactivity). |
| `breederReactivityCostPermille` | `50` | 0–500 | Output AND heat reduction (permille) per Breeder Blanket; shares the 15% floor. |
| `fissionSteamPerHeat` | `4` | 1–64 | MB of steam per heat unit a core in steam mode raises (consuming the same mB of water). |
| `fissionCryoHeatPerMb` | `6` | 1–100 | Heat removed per mB of cryo coolant a Reactor Steam Port feeds the core (either mode). |
| `fissionCryoSafeBandPermille` | `150` | 0–300 | How far (permille of heat capacity) WARNING and UNSTABLE move up while cryo coolant is fed. |
| `reactorSteamPortTankMb` | `16000` | 1000–256000 | Capacity (mB) of each Reactor Steam Port tank, coolant and steam alike. |
| `fissionBraidedOutputPermille` | `1400` | 100–5000 | A Braided Fuel Rod's output AND heat, permille of a Fuel Rod at the same burn-up. |
| `fissionBraidedBurnupPermille` | `800` | 100–5000 | A Braided Fuel Rod's burn-up rate, permille of a Fuel Rod's (800 = lasts 1.25 times as long). |
| `fissionBraidedHeavyWaterContacts` | `8` | 0–300 | Moderator-tank contact score (contacts x 2) a reactor needs before Braided Fuel Rods fission. |
| `fissionEconomizerBurnupPermille` | `750` | 100–1000 | Burn-up rate (permille) of every rod while a Fuel Economizer is fitted. |
| `fissionEconomizerOutputPermille` | `900` | 100–1000 | Output (permille; steam in steam mode) while a Fuel Economizer is fitted. |

## Steam turbine and condenser

See [Steam Plant](Steam-Plant.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `turbineNePerSteamMb` | `8` | 1–100 | NE one mB of steam yields at rated speed, 1000 permille vane efficiency and a condensed exhaust. |
| `turbineVaneFlowSteel` | `40` | 1–10000 | MB/t of steam one steel vane slice passes. |
| `turbineVaneFlowCindersteel` | `70` | 1–10000 | MB/t of steam one cindersteel vane slice passes. |
| `turbineVaneFlowSuperconducting` | `110` | 1–10000 | MB/t of steam one superconducting vane slice passes. |
| `turbineVaneEffSteel` | `550` | 1–1000 | Steel vane efficiency (permille). |
| `turbineVaneEffCindersteel` | `650` | 1–1000 | Cindersteel vane efficiency (permille). |
| `turbineVaneEffSuperconducting` | `750` | 1–1000 | Superconducting vane efficiency (permille). |
| `turbineStatorCeilingTier1` | `15` | 1–100000 | NE/t one tier 1 wound Turbine Stator can convert (8 per stator slice). |
| `turbineStatorCeilingTier2` | `40` | 1–100000 | NE/t one tier 2 wound Turbine Stator can convert. |
| `turbineStatorCeilingTier3` | `100` | 1–100000 | NE/t one tier 3 wound Turbine Stator can convert. |
| `turbineSpinUpRpmPerTick` | `3` | 1–100 | Rpm the rotor gains per tick at most (3 = 0 to 1800 in 30 s). |
| `turbineCoastDownRpmPerTick` | `1` | 1–100 | Rpm the rotor loses per tick at most (1 = 1800 to 0 in 90 s). |
| `turbineTripRpm` | `2100` | 1900–3000 | Rpm at which overspeed stress reaches 1000 permille on the failure ladder (rated speed is 1800). |
| `turbineFailureAction` | `trip` | trip / shed_blades / blast | What an overspeed FAILURE does: trip (inlets close, rotor coasts), shed_blades (trip and the vane sets are destroyed) or blast (trip and the shroud bursts); with overloadEnabled=false always trip. |
| `turbineVentEfficiencyPermille` | `600` | 0–1000 | Share of the yield kept for exhaust vented to the air through a vent port instead of a condenser. |
| `turbineSteamPortTankMb` | `16000` | 1000–256000 | Tank size (mB) of a Turbine Steam Port. |
| `portTransferInterval` | `10` | 1–200 | Ticks between the pipe-less fluid exchanges of Reactor Steam Ports, Turbine Steam Ports and Steam Condensers with the blocks they touch (never per tick). |
| `condenserMbPerTick` | `200` | 1–100000 | MB/t of exhaust steam an air-cooled Steam Condenser condenses at atmosphere factor 1000. |
| `condenserAtmosphereFactors` | `EARTHLIKE=1000,NITROGEN_OXYGEN=1000,NITROGEN_METHANE=1100,THIN_CO2=900,SULFUROUS=600,VACUUM=0` | text | Air-cooling factor (permille, 0-2000) by the air NeroTech reports at the condenser; unknown = 1000. |
| `condenserRadiatorMbPerTick` | `30` | 0–10000 | MB/t each face-adjacent NeroTech Radiator adds to a Steam Condenser (works in vacuum). |
| `condenserSteamPerCryoMb` | `20` | 1–1000 | MB of exhaust steam one mB of cryo coolant condenses (works in vacuum). |
| `condenserTankMb` | `16000` | 1000–256000 | Size (mB) of each of a Steam Condenser's three tanks (exhaust in, water out, cryo coolant). |

## Waste

See [Waste](Waste.md).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `poolRodHeatPerTick` | `4` | 0–1000 | Decay heat per tick each Hot Spent Fuel Rod adds to a Spent Fuel Rack. |
| `poolCoolingPerContact` | `4` | 0–1000 | Heat per tick a Spent Fuel Rack loses for each water source block touching one of its five non-bottom faces (0-5 contacts). |
| `poolCoolTicks` | `20000` | 20–10000000 | Ticks a Hot Spent Fuel Rod must sit in a calm Spent Fuel Rack (below the warning stage) before it becomes a Spent Fuel Rod. |
| `vitrifierNePerTick` | `40` | 1–100000 | NE per tick a Vitrifier draws while it works. |
| `vitrifierTicksPerOp` | `200` | 1–100000 | Ticks one vitrification takes (one waste item and two sand become one Vitrified Waste). |
| `caskHeatIntervalTicks` | `20` | 1–12000 | How often a Dry Cask adds its trace heat. |
| `caskSlotsPerHeat` | `9` | 1–27 | A Dry Cask adds 1 heat per caskHeatIntervalTicks for every this many slots holding spent rods or fission residue (vitrified waste is inert). |

## Generation

See [Steam Plant](Steam-Plant.md#other-generators) for the Hydrogen Fuel Cell and Betavoltaic Cell.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `fuelCellTicksPerUnit` | `20` | 1–12000 | Ticks a Hydrogen Fuel Cell spends on one unit of hydrogen with oxygen fed (twice as long when it breathes the air instead). |
| `fuelCellNePerHydrogenUnit` | `60` | 1–70 | NE one unit of hydrogen gives in a Hydrogen Fuel Cell, spread evenly over the unit's ticks (on air the same NE over twice the time). Capped at 70 so electrolysing water and burning the hydrogen always loses energy, even with the cheapest upgraded Electrolyzer. |
| `fuelCellWaterPerUnitMb` | `50` | 0–1000 | MB of water a Hydrogen Fuel Cell puts out per unit of hydrogen. |
| `fuelCellTankMb` | `4000` | 200–1000000 | Capacity (mB) of each of a Hydrogen Fuel Cell's three tanks (hydrogen in, oxygen in, water out). |
| `betavoltaicNePerTick` | `4` | 1–10000 | Flat NE per tick a Betavoltaic Cell makes while its Beta Capsule is live (it does not fade; it stops). |
| `betavoltaicCapsuleTicks` | `720000` | 20–100000000 | Ticks of output one Beta Capsule gives before it becomes a Spent Beta Capsule (720000 = 30 in-game days); a full buffer pauses it. |

## Storage

See [Battery Banks](Battery-Banks.md) and [Storage](Storage.md).

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
| `bankCapacityMilestone` | `32000000` | 1000–1000000000000 | Pooled capacity (NE) at which a bank opens the `neropower:bank_capacity` milestone record for its owner and for the player operating it at that moment — the *Power Bank* quest signal. It gates nothing. |
| `surgeBankCapacity` | `200000` | 1000–100000000 | Surge Bank capacity (NE): small, but it moves energy fast. |
| `surgeBankIo` | `50000` | 1–10000000 | Surge Bank maximum insert / extract per tick (NE/t). |
| `bankFastTierIoCap` | `256000` | 1–100000000 | Hard cap on the summed I/O of the Surge Banks in one bank (NE/t); added on top of the cells' pooled I/O, which bankMaxIoCap still caps. |
| `momentumWheelCapacity` | `2000000` | 1000–1000000000 | Momentum Wheel capacity (NE) at full speed. |
| `momentumWheelIo` | `2000` | 1–1000000 | Momentum Wheel maximum insert / extract per tick (NE/t). |
| `momentumWheelLossPermillePerSecond` | `2` | 0–100 | Share of the stored energy (permille) a Momentum Wheel loses to bearing friction every second; never part of a bank pool, so the loss is always visible. |
| `smesCapacityPerCoil` | `50000000` | 1000000–1000000000 | Storage Ring capacity per coil in the ring (NE). |
| `smesIoPerCoil` | `2000` | 1–1000000 | Storage Ring insert / extract per coil per tick (NE/t), capped by smesMaxIo. |
| `smesMaxIo` | `200000` | 1–100000000 | Hard cap on a Storage Ring's insert / extract per tick (NE/t). |
| `smesCryoTankMb` | `8000` | 1000–256000 | Storage Ring Controller cryo coolant tank size (mB of #nerotech:cryo_coolant). |
| `smesCryoPerCoilPer100Ticks` | `1` | 0–100 | Cryo coolant a formed Storage Ring uses per coil every 100 ticks (mB); 0 = never starves. |
| `smesWarmingPerTick` | `2` | 0–1000 | How fast a starved Storage Ring warms (permille of quench warmth per tick). |
| `smesCoolingPerTick` | `5` | 0–1000 | How fast a cooled Storage Ring sheds warmth (permille per tick). |
| `smesQuenchMode` | `drain` | drain / vent | What a Storage Ring quench does: drain (default) = lose smesQuenchLossPermille of the charge and go offline for smesQuenchLockoutTicks, no blast, no terrain change; blast = an explosion through the shared failure rules (failureRadiusCap, terrainDamageMode, spawn protection). |
| `smesQuenchLossPermille` | `500` | 0–1000 | Share of the stored energy (permille) a drain-mode quench loses. |
| `smesQuenchLockoutTicks` | `1200` | 0–72000 | Ticks a quenched Storage Ring stays offline; it re-arms only once this has passed and its warmth is back below the warning threshold minus the hysteresis. |
| `powerCellCapacityBasic` | `250000` | 1000–1000000000 | Basic Power Cell capacity (NE). |
| `powerCellCapacityAdvanced` | `1000000` | 1000–1000000000 | Advanced Power Cell capacity (NE). |
| `powerCellCapacityElite` | `4000000` | 1000–1000000000 | Elite Power Cell capacity (NE). |
| `powerCellIoBasic` | `500` | 1–1000000 | Basic Power Cell charge / draw rate (NE/t). |
| `powerCellIoAdvanced` | `2000` | 1–1000000 | Advanced Power Cell charge / draw rate (NE/t). |
| `powerCellIoElite` | `8000` | 1–1000000 | Elite Power Cell charge / draw rate (NE/t). |

## Beamed power

See [Beamed Power](Beamed-Power.md) and [Beam Grids](Beam-Grids.md).

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
| `beamSplitterMaxLinks` | `4` | 1–8 | Outgoing links a Beam Splitter may hold; each gets a share (permille) of what arrives after the lens, and the shares never add up to more than 100%. |
| `beamSplitterLensLoss` | `quartz=50,glacite=30,ember=20,void=10` | text | Beam Splitter loss (permille of what arrives) by the focus lens in its slot, as lens=permille pairs (quartz, glacite, ember, void); with no lens the splitter accepts no beam. |
| `phasedArrayRange` | `384` | 8–1024 | Maximum straight-line distance (blocks) from a Phased Array Emitter to each of its receivers. |
| `phasedArrayLossPermillePerBlock` | `1` | 0–100 | Phased Array Emitter loss per block of distance (permille), compounded like beamLossPermillePerBlock. |
| `phasedArrayTransferPerPass` | `8000` | 1–1000000 | NE a Phased Array Emitter sends per pass (before loss) to the one receiver it serves that pass. |
| `phasedArrayMaxTargets` | `4` | 1–8 | Receivers a Phased Array Emitter may serve, one per pass in list order (round-robin). |

## Grid tools

See [Beam Grids](Beam-Grids.md#grid-tools).

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `breakerMinDwellTicks` | `10` | 0–200 | Ticks a Circuit Breaker holds each warning stage before it climbs to the next; it trips once its load reaches its limit (100%) for that long. |
| `loadBankMaxRate` | `20000` | 1–1000000 | Highest rate (NE/t) a Load Bank can be set to burn. |
| `loadBankNePerHeat` | `100` | 1–1000000 | NE a Load Bank burns per unit of heat it makes; raised automatically so a Stirling Generator on it always returns less than half the power burned. |

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

## Radiation

Radiation is an optional world system; see [Radiation](Radiation.md). The store holds a level per chunk and nothing about players.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `radiationMode` | `auto` | on / off / auto | Whether radiation is simulated. `auto` is on in singleplayer and LAN and off on a dedicated server. Off writes no contamination and keeps what exists frozen. |
| `radiationDecayPermillePerMinute` | `5` | 0–1000 | Contamination each chunk loses per minute of game time, as permille of its level (at least 1 while above 0; 0 disables decay). |
| `radiationMaxChunksPerDimension` | `4096` | 64–65536 | Most contaminated chunks kept per dimension; past it the lowest levels are dropped first. |
| `radiationDecayBudgetPerTick` | `256` | 16–4096 | Contaminated chunks the decay pass visits per tick per dimension. |
| `radiationEffectIntervalTicks` | `40` | 20–200 | Ticks between radiation effect passes. |
| `radiationEffectChunksPerPass` | `64` | 1–1024 | Contaminated loaded chunks one effect pass visits per dimension (round-robin). |
| `radiationFailureBurst` | `600` | 0–1000 | Contamination added to a failed reactor's chunk (a third of it to each of the 8 neighbouring chunks). |
| `radiationPoolFailureBurst` | `300` | 0–1000 | Contamination added to a Spent Fuel Rack's chunk when it reaches FAILURE. |
| `radiationCaskPerFace` | `1` | 0–100 | Contamination per unshielded face of a loaded source (dry cask with spent rods or residue, failed rack) per minute. |
| `radiationDroppedHotRod` | `4` | 0–100 | Contamination per dropped hot spent fuel rod per 100 ticks. |
| `radiationDroppedSpent` | `1` | 0–100 | Contamination per dropped spent fuel rod or fission residue per 100 ticks. |
| `decontaminantStrength` | `250` | 1–1000 | Contamination one Decontaminant removes from the clicked chunk. |

## Client and privacy

These two keys are **client-local**: each player decides for themselves.

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `telemetryEnabled` | `true` | — | Anonymous crash reports for errors in NeroPower code (Sentry, EU servers; no names, UUIDs, IPs or world data). **On by default**; set `false` to opt out (takes effect on restart). Client-local, never synced. See [Privacy](Privacy.md). |
| `geigerHudCorner` | `bottom_left` | bottom_left / top_left / top_right / bottom_right | Screen corner for the Geiger Counter readout while one is held: bottom_left, top_left, top_right or bottom_right (client display only). |

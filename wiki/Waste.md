# Waste

Every rod that burns becomes waste, and waste has to go somewhere. NeroPower's back end cools it,
recovers what is still useful, and stores or vitrifies the rest. Nothing is a dead end, and nothing can
be thrown away for free.

```text
Fuel Rod ─burn─▶ Hot Spent Fuel Rod ─Spent Fuel Rack─▶ Spent Fuel Rod ─Centrifuge─▶ 2 Reprocessed Pellets
                                                                                  + 1 Fission Residue
Fission Residue ─Centrifuge (×4)─▶ Beta Capsule (+2 residue back) ─Betavoltaic Cell─▶ Spent Beta Capsule
Fission Residue / Spent Beta Capsule / Spent Isotope Pellet ─Vitrifier─▶ Vitrified Waste (inert)
Spent Fuel Rods, Fission Residue, Vitrified Waste ─▶ Dry Cask (long-term store)
```

## How it works

### Hot Spent Fuel Rod

A burnt rod leaves the reactor as a **Hot Spent Fuel Rod**. It is too hot to reprocess and it gives
off radiation if you drop it on the ground. It cools in one place only: a Spent Fuel Rack.

### Spent Fuel Rack

Place the rack **under water** and put up to four hot rods in it.

- Each rod adds decay heat (`poolRodHeatPerTick`).
- Each water source block on the rack's top or sides carries heat away (`poolCoolingPerContact`).
- Kept calm (below *Warning*), a rod becomes a **Spent Fuel Rod** after `poolCoolTicks` (about 17
  minutes).
- An overheated rack stops cooling. A rack that reaches *Failure* releases a burst of
  [radiation](Radiation.md) into its chunk.

The rack's heat is NeroTech heat, so a [Stirling Generator](RTG-and-Stirling.md) beside it can turn
decay heat into a little power.

### Reprocessing

- A NeroTech **Centrifuge** turns a Spent Fuel Rod into **two Reprocessed Pellets and one Fission
  Residue**.
- A NeroTech **Chemical Processor** gives one pellet and no residue (its recipe can have one result
  only, so that recipe is unchanged from 0.3.x).

Two Reprocessed Pellets make a Uranium Pellet, and every Braided Fuel Rod needs one
(see [Fuel Lines](Fuel-Lines.md)).

### Fission Residue and the Beta Capsule

Residue is waste, but not a dead end. A Centrifuge turns four Fission Residue into a **Beta Capsule**
and hands two residue back. The capsule fuels the
[Betavoltaic Cell](Steam-Plant.md#betavoltaic-cell). Whatever you do not use must be stored or
vitrified.

### Vitrifier

Melts one piece of final waste (Fission Residue, a Spent Beta Capsule or a Spent Isotope Pellet,
anything in `#neropower:vitrifiable`) with two sand into **Vitrified Waste**. Each one costs 8,000 NE
(40 NE/t for 200 ticks) and makes nothing useful: this is how waste leaves your base for good. It is
the one NeroPower block that uses power to process items. Vitrified Waste is inert.

### Dry Cask

A 27-slot store for Spent Fuel Rods, Fission Residue and Vitrified Waste (`#neropower:cask_storable`).
Rods and residue give off a little heat and trace radiation through every face **not** covered by
shielding. Cover all six faces with Shielding Blocks or Shielding Glass and the cask gives off nothing.
A leaking cask opens its vents and shows a lime seep, so you can see it from outside. Heat sinks in its
recipe cool it passively.

## Recipes

| Result | How |
| --- | --- |
| **Spent Fuel Rack** | Crafting: 4 × any `#c:plates/cindersteel`, 3 × Iron Bars, Machine Frame, any `#nerotech:components/heat_sinks` |
| **2 × Reprocessed Pellet + Fission Residue** | NeroTech Centrifuge: Spent Fuel Rod (120 NE/t, 400 ticks) |
| **Reprocessed Pellet** | NeroTech Chemical Processor: Spent Fuel Rod |
| **2 × Reprocessed Pellet** | NeroTech Chemical Processor: Fuel Rod |
| **Beta Capsule + 2 × Fission Residue** | NeroTech Centrifuge: 4 × Fission Residue (100 NE/t, 600 ticks) |
| **Vitrifier** | Crafting: 4 × any `#c:ingots/cindersteel`, Glass, 2 × Nero Coil, Machine Frame, Circuit Board |
| **Dry Cask** | Crafting: 4 × any `#c:plates/cindersteel`, 3 × Smooth Stone, Chest, any `#nerotech:components/heat_sinks` |

The Chemical Processor's fresh-rod recipe gives two Reprocessed Pellets for an unburnt Fuel Rod. That
is still a loss: the rod cost three Uranium Pellets.

## Config keys

See [Configuration](Configuration.md#waste).

| Key | Default | What it does |
| --- | --- | --- |
| `poolRodHeatPerTick` | 4 | Decay heat per hot rod per tick |
| `poolCoolingPerContact` | 4 | Heat removed per touching water source per tick |
| `poolCoolTicks` | 20000 | Calm ticks a hot rod needs to cool |
| `vitrifierNePerTick` | 40 | Vitrifier draw while working |
| `vitrifierTicksPerOp` | 200 | Ticks per Vitrified Waste |
| `caskHeatIntervalTicks` | 20 | How often a cask adds trace heat |
| `caskSlotsPerHeat` | 9 | Slots of rods or residue per heat unit |

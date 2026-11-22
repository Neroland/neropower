# Steam Plant

A reactor in **steam mode** makes steam instead of power. A **steam turbine** turns that steam into
power, and a **Steam Condenser** turns the exhaust back into water for the reactor. Built together,
they get more out of every fuel rod than direct mode does. This page also covers the two small
generators added in 0.4.0, the Hydrogen Fuel Cell and the Betavoltaic Cell.

The in-game **Power Guide** (the Guide button above any NeroPower machine screen) walks through the
same steps.

## How it works

### Steam mode

1. Set a **Reactor Steam Port** into the wall of a formed [Fission Reactor](Fission-Reactor.md) and
   feed it water (by a pipe, or from a block touching it; see
   [Building without pipes](#building-without-pipes)).
2. Switch the **Fission Core** to steam mode in its screen.

The core now raises steam: 4 mB of steam per unit of heat, using the same amount of water
(`fissionSteamPerHeat`). It makes no power of its own. If the water runs out or the steam has nowhere
to go, the heat stays in the core and the [failure ladder](Failure-Stages.md) climbs as usual.

Steam is raised in step with the burn rate, so presets and upgrades change how fast a rod makes steam,
not how much steam it makes over its life.

A Reactor Steam Port also takes **cryo coolant** (`#nerotech:cryo_coolant`). In either mode it pulls
heat out of the core (6 heat per mB, `fissionCryoHeatPerMb`) and moves the *Warning* and *Unstable*
lines 15 points higher while it flows (`fissionCryoSafeBandPermille`).

### Building the turbine

A steam turbine is a tube **5 × 5 across and 4 to 16 blocks long**:

- **Turbine Shroud** forms the walls. It has windows, so you can watch the rotor.
- The **Turbine Helm** sits in the centre of one end. Its screen shows rotor speed, output, steam and,
  when the turbine stops, why.
- **Turbine Spindles** run down the middle of the tube.
- Each slice along the spindle is one of two kinds: a **vaned slice** (an open ring with a Vane Set on
  the spindle) or a **stator slice** (a ring of eight **Turbine Stators**). A turbine needs at least
  one of each.
- **1 to 8 Turbine Steam Ports** go in the side walls. Click a port with an empty hand to cycle it
  between *inlet* (steam in), *exhaust* (used steam out to a condenser) and *vent* (blow it into the
  air).

The structure is checked when a part is placed or removed, never every tick.

### Vane Sets and Stators

Right-click a spindle with a Vane Set to mount it.

| Vane Set | Steam per slice | Efficiency |
| --- | --- | --- |
| Steel | 40 mB/t | 55 % |
| Cindersteel | 70 mB/t | 65 % |
| Superconducting | 110 mB/t | 75 % |

More vaned slices pass more steam. Stators turn spin into power: wind each one by right-clicking it
with a NeroTech coil. The coil tier sets how much one stator can convert.

| Winding | Per stator | Per ring of eight |
| --- | --- | --- |
| Nero Coil (tier 1) | 15 NE/t | 120 NE/t |
| Tier 2 coil | 40 NE/t | 320 NE/t |
| Tier 3 coil | 100 NE/t | 800 NE/t |

A stator slice shows one, two or three bands for its winding tier.

### Spin-up, overspeed and the Governor Module

The rotor has inertia. It takes **30 seconds** to reach its rated **1,800 rpm** and **90 seconds** to
coast down, and power scales with speed. If the stators or the Helm's buffer cannot take the power,
the rotor speeds up. Overspeed stress builds on the failure ladder and the turbine **trips at
2,100 rpm**: the inlets close and the rotor coasts. Reset it at the Helm once it is below 600 rpm.

What a full overspeed failure does is a server choice (`turbineFailureAction`): `trip` (the default,
never destructive), `shed_blades` (the vane sets are lost) or `blast` (the shroud bursts, within the
server's blast cap and terrain rules). With `overloadEnabled=false` it always just trips.

A **Governor Module** in the Helm holds the rotor at speed when the steam supply changes, so it never
overspeeds.

### The Steam Condenser

Turns exhaust steam back into water, one for one. Send the water back to a Reactor Steam Port (it pushes into a
block touching it, or use a pipe) and the loop never needs topping up.

- On Earth-like air it condenses **200 mB a tick** (`condenserMbPerTick`). The air on other worlds
  changes this: the factors come from NeroTech's atmosphere reading (`condenserAtmosphereFactors`;
  thin air cools less, vacuum not at all).
- Each NeroTech **Radiator** touching it adds 30 mB a tick, in a vacuum too.
- **Cryo coolant** condenses 20 mB of steam per mB, in a vacuum too.

### What steam is worth

Per fuel rod, compared with direct mode:

| Vanes | Exhaust condensed | Exhaust vented |
| --- | --- | --- |
| Steel | 1.10 × | 0.66 × |
| Cindersteel | 1.30 × | 0.78 × |
| Superconducting | 1.50 × | 0.90 × |

Vented exhaust keeps 60 % of the power (`turbineVentEfficiencyPermille`), so steam without a condenser
always loses. Steam mode can never give more than twice direct mode per rod, whatever the config.

### Building without pipes

A steam plant runs with no pipe mod installed. Every Reactor Steam Port of a reactor shares one coolant
tank and one steam tank (each `ports × reactorSteamPortTankMb`), so water that enters any port can be
boiled and leave as steam through any other. A turbine's inlet ports share one steam tank and its exhaust
ports one exhaust tank.

Every 10 ticks (`portTransferInterval`) ports and condensers exchange fluid with the blocks they touch:

- A **Reactor Steam Port** pushes steam into an abutting Turbine Steam Port set to *inlet*, and draws water
  or cryo coolant from what it touches.
- A **Turbine Steam Port** set to *exhaust* pushes exhaust steam into an abutting Steam Condenser.
- A **Steam Condenser** pushes water into an abutting Reactor Steam Port or a tank.

So: put one Reactor Steam Port against a turbine inlet, put the turbine's exhaust port against a Steam
Condenser, and put the condenser (or a fluid tank it fills) against a second Reactor Steam Port. Steam
flows into the turbine, exhaust into the condenser and water back into the reactor. Pipes from other mods
still work on the same faces.

Faces follow the block's side configuration (default: input and output, with auto-eject and auto-input
on). On an automatic face, steam and exhaust steam are pushed **only into blocks that do not also take
water**, so a water tank next to a reactor port stays a water tank. Set a face to **Push** to send steam
into a general tank anyway. Pooling never makes or loses fluid, and reactors and turbines from earlier
saves load with every mB in place.

## Other generators

### Hydrogen Fuel Cell

Pipe in hydrogen and, if you can, oxygen. Each unit of hydrogen gives **60 NE**, spread over one
second with oxygen fed. Without oxygen, on a world with breathable air, it breathes the air at half
rate: the same 60 NE over twice the time. It puts out water (50 mB per unit). No pipe is needed: every
`portTransferInterval` ticks (10) it pushes its water into a touching block that takes water (a tank,
an Electrolyzer) and pulls hydrogen and oxygen from touching blocks that hold them, through the faces its
side config allows. Without a water outlet the tank fills after 80 units and the cell stops. Splitting water in NeroTech's Electrolyzer and burning the hydrogen here always loses
energy.

### Betavoltaic Cell

A half-height cell that turns one **Beta Capsule** into a flat **4 NE/t** for about 30 in-game days,
with no heat and nothing to feed. The output never fades; it stops when the capsule is spent. A
capsule is sealed in once inserted, and a full energy buffer pauses it instead of wasting it. Take out
the **Spent Beta Capsule** and vitrify it (see [Waste](Waste.md)).

## Recipes

| Result | How |
| --- | --- |
| **Reactor Steam Port** | Crafting: 4 × Iron Ingot, 4 × Bucket, Fission Casing |
| **Turbine Helm** | Crafting: 4 × Turbine Shroud, 2 × Circuit Board, 2 × Nero Coil, Machine Frame |
| **Turbine Shroud** ×4 | Crafting: 4 × Iron Ingot, 2 × any `#c:plates/cindersteel`, 3 × Iron Bars |
| **Turbine Spindle** ×2 | Crafting: 4 × Iron Ingot, 3 × any `#c:rods/cindersteel` |
| **Turbine Stator** ×4 | Crafting: 7 × Iron Ingot, Machine Frame |
| **Turbine Steam Port** ×2 | Crafting: 2 × Turbine Shroud, 2 × Bucket, Circuit Board |
| **Steel Vane Set** | Crafting: 4 × any `#c:plates/titanium`, Iron Ingot |
| **Cindersteel Vane Set** | Crafting: 4 × any `#c:ingots/cindersteel`, Steel Vane Set |
| **Superconducting Vane Set** | Crafting: 4 × any `#c:wires/superconductor`, Cindersteel Vane Set |
| **Governor Module** | Crafting: 2 × Gold Ingot, 2 × any `#nerotech:components/servos`, Circuit Board |
| **Steam Condenser** | Crafting: 3 × Copper Ingot, 4 × Iron Ingot, Machine Frame, Bucket |
| **Hydrogen Fuel Cell** | Crafting: 4 × Copper Ingot, Glass Pane, 2 × Iron Ingot, Machine Frame, Circuit Board |
| **Betavoltaic Cell** | Crafting: 2 × Quartz, Glass Pane, 2 × Iron Ingot, Circuit Board, 3 × Smooth Stone Slab |

## Config keys

All in `config/neropower.properties`; see [Configuration](Configuration.md#steam-turbine-and-condenser)
for the full list. The main ones:

| Key | Default | What it does |
| --- | --- | --- |
| `fissionSteamPerHeat` | 4 | mB of steam per unit of heat in steam mode |
| `turbineNePerSteamMb` | 8 | NE per mB of steam at rated speed, full vane efficiency, condensed exhaust |
| `turbineTripRpm` | 2100 | Speed at which overspeed reaches the top of the ladder |
| `turbineFailureAction` | `trip` | `trip`, `shed_blades` or `blast` |
| `turbineVentEfficiencyPermille` | 600 | Share of the yield kept when venting |
| `condenserMbPerTick` | 200 | Air-cooled condensing rate on Earth-like air |
| `portTransferInterval` | 10 | Ticks between port and condenser exchanges with the blocks they touch (1–200) |
| `fuelCellNePerHydrogenUnit` | 60 | NE per unit of hydrogen (at most 70) |
| `betavoltaicNePerTick` | 4 | Betavoltaic Cell output |

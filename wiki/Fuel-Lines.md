# Fuel Lines

NeroPower has two fuel lines. The **standard line** runs on Uranium Pellets and Fuel Rods. The
**Pallurite line**, new in 0.4.0, breeds a second fuel inside a running reactor and ends in the
**Braided Fuel Rod**. Both feed the [Fission Reactor](Fission-Reactor.md), and both end in the
[waste back end](Waste.md).

All names and numbers here are game rules, not real-world physics.

## How it works

### The standard line

Uranium Pellets make **Fuel Rods**, and Fuel Rods go in the Fission Core. A rod gives the most power
when fresh and less as it burns up (see [Fission Reactor](Fission-Reactor.md#fuel-rods-and-burn-up)).

**Pull rods only when they are spent.** A rod that has burnt at all leaves the core as a **Hot Spent
Fuel Rod**, however it leaves (taken by hand, shift-clicked, swapped, dropped from the screen or the core broken), and must go through
the [Spent Fuel Rack](Waste.md#spent-fuel-rack). A fresh rod comes out unchanged.

### Moderators

Moderators sit **inside** the reactor shell and raise output and heat:

- A **Graphite Moderator** adds 1 % for every open space it touches.
- A **Moderator Tank** (graphite filled with heavy water) counts double.
- The bonus for one reactor is capped at **40 %**.

Layout matters: leave gaps beside the moderators.

### Control rods and the computer

A **Control Rod Assembly** lowers output and heat by 15 %, a **Europium Control Rod Assembly** by
25 %. The combined factor never drops below 15 %. A **Reactor Control Computer** in the wall gives
redstone and comparator signals and can SCRAM the reactor by itself. The largest reactor,
**7 blocks a side**, needs one. See [Fission Reactor](Fission-Reactor.md#reactor-control-computer).

### Pallurite

The second line starts with **Pallurite Dust**:

- With Nerospace installed, a NeroTech **Centrifuge** spins certain Nerospace crystals, or a meteor
  core, into two dust (anything in `#neropower:fertile_sources`).
- Without Nerospace, 8 tuff and 4 glowstone dust give one dust, at about four times the energy and
  time per pellet.

Two dust and an iron nugget make a **Pallurite Pellet**.

### The Breeder Blanket

Build **Breeder Blankets** into the reactor interior and put Pallurite Pellets in the core's blanket
slot. While the reactor runs, they become **Kindled Pellets**: about 30 minutes each with one blanket
at full output, faster with more blankets, up to 8 counted. Each blanket costs 5 % of output and heat.

### The Braided Fuel Rod

Two Kindled Pellets, a Reprocessed Pellet and two cindersteel rods make a **Braided Fuel Rod**. It
gives **1.4 ×** a Fuel Rod's power and lasts a quarter longer. It only works when the reactor's
Moderator Tanks touch at least four open spaces between them, so build 5 or 7 blocks a side.

### The Fuel Economizer

A Fission Core upgrade. Rods burn up a quarter slower and give a tenth less power, so each rod gives
about 20 % more over its life.

### Heavy water

**Heavy Water** (`c:heavy_water`) is made in a NeroTech Centrifuge from water and a little deuterium
gas. It fills Moderator Tanks. It travels in pipes and tanks and cannot be placed in the world.

## Recipes

| Result | How |
| --- | --- |
| **Uranium Pellet** | Crafting (shapeless): 4 × Glowstone Dust, Iron Ingot |
| **Uranium Pellet** ×2 | Crafting (shapeless): any `#c:raw_materials/uranium` |
| **Uranium Pellet** | Crafting (shapeless): 2 × Reprocessed Pellet |
| **Fuel Rod** | Crafting: 2 × Iron Ingot, 3 × Uranium Pellet |
| **Graphite Moderator** ×2 | Crafting: 8 × Coal, Iron Ingot |
| **1000 mB Heavy Water** | NeroTech Centrifuge: 1000 mB Water, 4 mB Deuterium (60 NE/t, 300 ticks) |
| **Moderator Tank** | NeroTech Reaction Vat: 4 × any `#c:plates/cindersteel`, Graphite Moderator, 1000 mB any `#c:heavy_water` (60 NE/t, 300 ticks) |
| **Control Rod** | Crafting: 2 × Iron Ingot, Redstone |
| **Control Rod Assembly** | Crafting: 2 × Control Rod, Iron Ingot |
| **Europium Control Rod** | Crafting (shapeless): 2 × any `#c:dusts/europium`, Control Rod |
| **Europium Control Rod Assembly** | Crafting: 2 × Europium Control Rod, Iron Ingot |
| **Reactor Control Computer** | Crafting: 4 × any `#c:plates/cindersteel`, 2 × any `#nerotech:processors/logic`, 2 × Comparator, Fission Casing |
| **2 × Pallurite Dust** | NeroTech Centrifuge: any `#neropower:fertile_sources` (80 NE/t, 200 ticks) |
| **Pallurite Dust** | NeroTech Centrifuge: 8 × Tuff, 4 × Glowstone Dust (80 NE/t, 400 ticks) |
| **Pallurite Pellet** | Crafting (shapeless): 2 × Pallurite Dust, Iron Nugget |
| **Breeder Blanket** | Crafting: 4 × Pallurite Pellet, 4 × Iron Ingot, Graphite Moderator |
| **Braided Fuel Rod** | Crafting: 2 × Kindled Pellet, 2 × any `#c:rods/cindersteel`, Reprocessed Pellet |
| **Fuel Economizer** | Crafting: 2 × any `#c:plates/cindersteel`, 2 × Gold Ingot, any `#nerotech:processors/logic` |

## Config keys

See [Configuration](Configuration.md#fission-reactor).

| Key | Default | What it does |
| --- | --- | --- |
| `fissionModeratorPermillePerContact` | 10 | Bonus per moderator contact (permille) |
| `fissionModeratorCapPermille` | 400 | Largest moderator bonus per reactor |
| `fissionEuropiumControlRodPermille` | 250 | Reduction per Europium Control Rod Assembly |
| `breederTicksPerPellet` | 36000 | Running ticks one blanket needs per Kindled Pellet |
| `breederMaxBlankets` | 8 | Blankets counted for the breeding rate |
| `breederReactivityCostPermille` | 50 | Output and heat cost per blanket |
| `fissionBraidedOutputPermille` | 1400 | Braided Fuel Rod output against a Fuel Rod |
| `fissionBraidedBurnupPermille` | 800 | Braided Fuel Rod burn-up rate against a Fuel Rod |
| `fissionBraidedHeavyWaterContacts` | 8 | Moderator Tank contact score a Braided Fuel Rod needs |
| `fissionEconomizerBurnupPermille` | 750 | Burn-up rate with a Fuel Economizer |
| `fissionEconomizerOutputPermille` | 900 | Output with a Fuel Economizer |

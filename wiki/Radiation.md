# Radiation

Radiation is an **optional world system**. Waste and failures can contaminate the chunk they are in;
contamination hurts creatures standing there and fades by itself. It never damages blocks, never
spreads on its own and **stores nothing about players**.

## How it works

### The setting

Radiation is a server setting, `radiationMode`:

| Value | Effect |
| --- | --- |
| `auto` (default) | On in singleplayer and LAN worlds, **off on a dedicated server** until the operator turns it on |
| `on` | Always on |
| `off` | Nothing is contaminated, existing contamination is frozen, and Decontaminant is not used up |

### Where it comes from

Contamination is measured in **pall (pl)**, from 0 to 1,000 per chunk.

| Source | What it adds |
| --- | --- |
| A Fission Reactor that fails | 600 pl to its chunk, a third of that to each of the eight around it |
| A Spent Fuel Rack that fails | 300 pl to its chunk |
| A Dry Cask holding spent rods or residue | 1 pl for every face not covered by shielding, regularly |
| A Hot Spent Fuel Rod dropped on the ground | 4 pl every 5 seconds |
| A Spent Fuel Rod or Fission Residue dropped on the ground | 1 pl every 5 seconds |

Contamination decays over a few hours (`radiationDecayPermillePerMinute`), never spreads to the next
chunk and never harms blocks. Each dimension keeps at most `radiationMaxChunksPerDimension`
contaminated chunks; past that the lowest levels go first.

### What it does

Creatures in a contaminated chunk are affected in the moment:

| Level | Effect |
| --- | --- |
| 200 pl | Weakness |
| 500 pl | Weakness and Hunger |
| 800 pl | Weakness, Hunger and radiation damage |

Each piece of shielding armour you wear takes a quarter off; a full set makes you immune. A piece counts
when it is in the item tag `neropower:radiation_shielding`. A piece that is also in
`neropower:radiation_shielding_lined` counts only while it carries a Nerospace rad lining. The Nerospace
oxygen suit pieces are optional entries in both tags, so a Nerospace suit protects once it is lined.
Modpack makers can add any armour to the first tag.

No dose is added up per player. See [Privacy](Privacy.md).

**Protected ground.** Radiation never changes a block. NeroPower also asks its protection check before
it writes contamination to a chunk (dropped rods, leaking casks, failure bursts) and before it harms any
creature: a chunk whose centre is protected is never contaminated, and nothing standing on protected
ground is harmed. Out of the box that check knows only vanilla spawn protection on a dedicated server;
no claims mod plugs into it yet. On a server with land claims, keep `radiationMode` off (the default on
a dedicated server) or accept that claimed land is not exempt. Dropped rods and residue add at most 40
to one chunk per pass, however many lie there.

### Shielding Block and Shielding Glass

Put them against a waste store: every face you cover stops leaking. A Dry Cask boxed in on all six sides
gives off nothing, and the glass lets you see in. Shielding does not hold back the burst from a failure.
Both are in `#neropower:radiation_barrier`.

### Measuring and cleaning

- The **Geiger Counter** shows the contamination of your chunk and the eight around it, and how many
  leaking sources are within 16 blocks. It clicks faster the hotter the ground.
- The **Dosimeter Badge** adds up the radiation it has been in, on the badge itself.
- **Decontaminant**, used on any block, cleans 250 pl from that chunk (`decontaminantStrength`).

See [Tools](Tools.md) for the Geiger Counter and Dosimeter Badge.

## Recipes

| Result | How |
| --- | --- |
| **Shielding Block** ×8 | Crafting: 4 × Polished Deepslate, 4 × any `#c:plates/cindersteel`, Iron Ingot |
| **Shielding Glass** ×4 | Crafting: 4 × Glass, Shielding Block |
| **Geiger Counter** | Crafting: 5 × any `#c:plates/cindersteel`, Tinted Glass, 2 × Redstone, any `#nerotech:processors/logic` |
| **Dosimeter Badge** | Crafting (shapeless): any `#c:plates/cindersteel`, Paper, Glowstone Dust, String |
| **Dosimeter Badge** | Crafting (shapeless): Dosimeter Badge |
| **4 × Decontaminant** | NeroTech Reaction Vat: 2 × Clay Ball, Bone Meal, 100 mB any `#c:sulfuric_acid` (40 NE/t, 200 ticks) |

## Config keys

See [Configuration](Configuration.md#radiation) for every radiation key (mode, decay, caps, effect
passes, the size of each source and the Decontaminant's strength).

# Storage

NeroPower's stores, from the fastest to the largest. [Battery Banks](Battery-Banks.md) covers the
Battery Cells and the Bank Controller that pools them; this page covers what 0.4.0 adds on top.

| Store | Holds | In / out | Character |
| --- | --- | --- | --- |
| Surge Bank | 200,000 NE | 50,000 NE/t | Small and very fast; the fast tier of a Battery Bank |
| Battery Cells (3 tiers) | 0.5 M / 2 M / 8 M NE | 1,000 / 4,000 / 16,000 NE/t | Pooled by a Battery Bank Controller |
| Momentum Wheel | 2 M NE | 2,000 NE/t | Kinetic: loses 0.2 % a second, never fails |
| Storage Ring | 50 M NE per coil | 2,000 NE/t per coil, at most 200,000 | The largest store; needs cryo coolant |
| Power Cells (3 tiers) | 250,000 / 1 M / 4 M NE | 500 / 2,000 / 8,000 NE/t | Portable, in your inventory |

All numbers are server defaults.

## How it works

### Surge Bank

Holds little but moves energy fast. Next to a Battery Bank it joins the pool as the **fast tier**: it
fills first and empties first, soaking up turbine spin-up and beam bursts. The fast tier's combined I/O
is capped separately (`bankFastTierIoCap`). Alone, a Surge Bank is a small, fast store. Like Battery
Cells, it has a slot that charges a Power Cell.

### Momentum Wheel

Stores energy as spin, and you can see the rotor turn. Friction takes 0.2 % of its charge every second
(`momentumWheelLossPermillePerSecond`), so use it to smooth short dips, not to hold power for days. It
has no fuel and no failure, and it never joins a bank.

### Storage Ring

A superconducting ring and the largest store there is.

1. Build a hollow square of **Storage Ring Coils**, **5 to 15 blocks a side**, one layer high.
2. Put the **Storage Ring Controller** in the middle of one side.
3. Keep the controller's tank fed with **cryo coolant** (`#nerotech:cryo_coolant`).

Every coil adds 50 million NE. A ring uses a little coolant per coil. **Starved**, the coils warm and the
ring's I/O halves. At the top of the warming scale it **quenches** through the shared
[failure ladder](Failure-Stages.md): by default (`smesQuenchMode=drain`) it loses half its charge and
goes offline for at least a minute, until it is cold again. Nothing is damaged. A server may choose
`blast` instead, within its blast cap and terrain rules.

### Power Cells

Portable energy items in three tiers, made in a NeroTech **Reaction Vat** with sulfuric acid as the
electrolyte. Charge them:

- in the charge slot of a Battery Cell or a Surge Bank, or
- near a NeroTech **Induction Charger**.

On NeoForge and Forge a Power Cell also offers its energy to other mods' items and machines as standard
item energy. Fabric has no shared item-energy standard for this yet, so there it works with NeroPower's
own slots and the Induction Charger only.

## Recipes

| Result | How |
| --- | --- |
| **Surge Bank** | Crafting: 4 × Iron Ingot, 3 × any `#nerotech:components/capacitors`, Machine Frame, Redstone |
| **Momentum Wheel** | Crafting: 4 × any `#nerotech:components/servos`, 2 × Iron Block, 2 × Nero Coil, Machine Frame |
| **Storage Ring Coil** ×2 | Crafting: 4 × any `#c:plates/cindersteel`, 4 × any `#c:wires/superconductor`, any `#nerotech:coils/tier3` |
| **Storage Ring Controller** | Crafting: 4 × any `#c:plates/cindersteel`, 2 × any `#nerotech:processors/quantum`, 2 × any `#nerotech:coils/tier3`, Machine Frame |
| **Basic Power Cell** | NeroTech Reaction Vat: 2 × any `#c:plates/titanium`, any `#nerotech:components/capacitors`, 250 mB any `#c:sulfuric_acid` (40 NE/t, 200 ticks) |
| **Advanced Power Cell** | NeroTech Reaction Vat: Basic Power Cell, 2 × any `#c:plates/cindersteel`, 250 mB any `#c:sulfuric_acid` (60 NE/t, 300 ticks) |
| **Elite Power Cell** | NeroTech Reaction Vat: Advanced Power Cell, 2 × any `#c:wires/superconductor`, 250 mB any `#c:sulfuric_acid` (80 NE/t, 400 ticks) |

## Config keys

See [Configuration](Configuration.md#storage) for every storage key (capacities, rates, the ring's
coolant use, warming and quench).

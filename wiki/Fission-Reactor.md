# Fission Reactor

The Fission Reactor is NeroPower's mid-game generator: a hollow cube of **Fission Casing** with a
**Fission Core** set into one wall, burning **Fuel Rods** that age as they burn. It is the first
machine that walks the [failure ladder](Failure-Stages.md), so it rewards an operator who watches
the heat and punishes one who does not. The core block on its own is **inert** — nothing happens
until you build it a shell.

## How it works

### Building a shell

Two sizes are valid — **3×3×3** and **5×5×5** (outer dimensions):

1. Build a hollow cube of **Fission Casing**.
2. Set the **Fission Core** into the **centre of one vertical wall**, facing outward. It replaces
   that casing position and counts as part of the shell.
3. Leave the interior **empty or filled only with Control Rod Assemblies** — any other block
   (including fluids) unforms the reactor.

The core re-checks its shell every two seconds and whenever a neighbouring block changes (the
larger size is tried first). It never loads chunks to do so: if part of the shell sits in an
unloaded chunk the reactor simply reads as *unformed* until the area is back. The GUI shows the
shell size once it forms; breaking any casing block unforms it and the core goes dark.

### Fuel rods and burn-up

The core has four rod slots, but only `shell size − 1` of them are usable: **two** in a 3×3×3 shell,
**all four** in a 5×5×5. Locked slots refuse everything. Only fresh **Fuel Rods** go in; a slot can
be pulled by hoppers or pipes only once its rod is spent.

Every loaded rod carries its **burn-up** (0–100 %) as item data, and every running tick advances
all loaded rods together. Output follows a curve over burn-up rather than a flat number:

| Burn-up | Output (of nominal) | Heat factor |
| --- | --- | --- |
| 0 % (fresh) | 120 % | 140 % |
| 20 % | 100 % | 120 % |
| 60 % | 80 % | 100 % |
| 100 % (spent) | 30 % | 50 % |

Between the knots the curve is linear. A fresh rod over-performs and runs hottest; a nearly spent
rod limps along. At 100 % the rod turns into a **Spent Fuel Rod** in its slot, ready for
reprocessing. The work bar in the GUI is the average burn-up of the loaded rods.

At the default rate a rod lasts about **104 minutes** on the Balanced preset. Overdrive and Speed
modules burn rods faster (and make more power while they do).

### Control rods

Every **Control Rod Assembly** you place *inside* the shell scales output **and** heat by
`1 − (assemblies × 15 %)`, down to a floor of **15 %**. They are the operator's throttle: a 3×3×3
shell holds one, a 5×5×5 up to 27. Fewer assemblies means more power and more heat.

### Neutron poisoning (the xenon pit)

Running flat out — full output, every usable slot loaded, no failure penalty — builds **poison**.
Once it reaches 90 % the core **stalls**: no output, no burn-up, status *Xenon pit*. It only
restarts once the poison has decayed 30 points below the line, so a core hovering at the threshold
cannot flicker on and off. Poison decays whenever the core is idle, throttled, part-loaded or
stalled. Leave one slot empty, or fit a control rod, and a reactor will run steadily without ever
stalling. The poison level is shown in the GUI.

### Heat, cooling and failure

The reactor pushes heat into NeroTech's thermal model exactly like a NeroTech machine: coolant
blocks, Radiators and Coolant Pumps next to the shell all help, and a
[Stirling Generator](RTG-and-Stirling.md) parked against it will draw heat *and* pay you for it.

Heat drives the [failure ladder](Failure-Stages.md). At **FAILURE** the shell explodes with a
radius of `shell size + 2` (5 or 7 blocks, before the server cap) and — if the server enables it —
leaves a **scorch zone** that hurts living creatures for a few real days.

### SCRAM

The NeroLink companion app offers a **SCRAM** action to the reactor's owner. For 60 seconds the
core behaves as if every control rod were fully inserted (the 15 % floor), whatever is actually
inside the shell, so output and heat drop and the ladder can climb back down.

### Automation

- Energy leaves every face (Generator preset).
- Fuel Rods are accepted on every face; faces set to *output* in the Side Config tab let pipes
  pull **spent** rods only.
- The reactor is never load-shed by NeroTech's throttling.

## Recipes

| Item | Recipe |
| --- | --- |
| **Uranium Pellet** | 4 Glowstone Dust + 1 Iron Ingot (shapeless), or 2 Reprocessed Pellets |
| **Fuel Rod** | 3 Uranium Pellets in a row between two Iron Ingots |
| **Control Rod** | Iron Ingot, Redstone, Iron Ingot in a column |
| **Control Rod Assembly** | Control Rod, Iron Ingot, Control Rod in a row |
| **Fission Casing** (×4) | 8 Iron Ingots around a NeroTech Machine Frame |
| **Fission Core** | Iron Blocks in the corners, Circuit Boards top and bottom, Nero Coils on the sides, a Machine Frame in the middle |
| **Reprocessed Pellet** | a Spent Fuel Rod in NeroTech's **Chemical Processor** |

Spent Fuel Rods are never crafted — they come out of the reactor.

## Config keys

All in `config/neropower.properties`; see [Configuration](Configuration.md) for the full list.

| Key | Default | What it does |
| --- | --- | --- |
| `fissionNePerRod` | 120 | Nominal NE/tick per fresh rod at 100 % of the curve |
| `fissionHeatPerRodPerTick` | 6 | Heat per rod per tick at 100 % heat factor |
| `fissionBurnupPerTick` | 8 | Burn-up per tick (permille ×1000) |
| `fissionBurnupCurve` | `0=1200,200=1000,600=800,1000=300` | The output curve knots |
| `fissionControlRodPermille` | 150 | Reduction per Control Rod Assembly |
| `fissionPoisonPerTick` | 2 | Poison gained per tick at full output |
| `fissionPoisonDecayPerTick` | 3 | Poison shed per tick otherwise |
| `fissionPoisonStallPermille` | 900 | Poison level that stalls the core |
| `fissionScorchEnabled` | false | Leave a scorch zone on failure |
| `fissionScorchDays` | 3 | Real days a scorch zone lasts |
| `fissionScorchRadius` | 6 | Half-edge of the scorch cube |

# Fission Reactor

The Fission Reactor is NeroPower's mid-game generator: a hollow cube of **Fission Casing** with a
**Fission Core** set into one wall, burning **Fuel Rods** that age as they burn. It is the first
machine that walks the [failure ladder](Failure-Stages.md), so it rewards an operator who watches
the heat and punishes one who does not. The core block on its own is **inert** — nothing happens
until you build it a shell.

## How it works

### Building a shell

Three sizes are valid — **3×3×3**, **5×5×5** and **7×7×7** (outer dimensions):

1. Build a hollow cube of **Fission Casing**.
2. Set the **Fission Core** into the **centre of one vertical wall**, facing outward. It replaces
   that casing position and counts as part of the shell.
3. The wall may also hold **Reactor Steam Ports** (see [Steam Plant](Steam-Plant.md)) and a
   **Reactor Control Computer**. A 7×7×7 shell forms only with a Reactor Control Computer in its wall.
4. Leave the interior **empty or filled only with reactor interior blocks**: Control Rod Assemblies,
   Europium Control Rod Assemblies, Graphite Moderators, Moderator Tanks and Breeder Blankets (see
   [Fuel Lines](Fuel-Lines.md)). Any other block (including fluids) unforms the reactor.

The core checks its shell when something changes: a casing or interior block placed or removed
anywhere in the shell, a neighbouring block, loading, opening its screen, and starting up after
being idle. There is no periodic recheck. It never loads chunks to do so: if part of the shell sits
in an unloaded chunk the reactor simply reads as *unformed* until the area is back. The GUI shows the
shell size once it forms; breaking any casing block unforms it and the core goes dark. A piston that
pushes a shell block unforms the reactor too.

### Fuel rods and burn-up

The core has six rod slots, but only `shell size − 1` of them are usable: **two** in a 3×3×3 shell,
**four** in a 5×5×5 and **all six** in a 7×7×7. Locked slots refuse everything. Fresh **Fuel Rods**
and **Braided Fuel Rods** go in; a slot can be pulled by hoppers or pipes only once its rod is spent.
Two more slots take Pallurite Pellets in and Kindled Pellets out (see
[Fuel Lines](Fuel-Lines.md#the-breeder-blanket)).

Every loaded rod carries its **burn-up** (0–100 %) as item data, and every running tick advances
all loaded rods together. Output follows a curve over burn-up rather than a flat number:

| Burn-up | Output (of nominal) | Heat factor |
| --- | --- | --- |
| 0 % (fresh) | 120 % | 140 % |
| 20 % | 100 % | 120 % |
| 60 % | 80 % | 100 % |
| 100 % (spent) | 30 % | 50 % |

Between the knots the curve is linear. A fresh rod over-performs and runs hottest; a nearly spent
rod limps along. At 100 % the rod turns into a **Hot Spent Fuel Rod** in its slot, which must cool
in a [Spent Fuel Rack](Waste.md#spent-fuel-rack) before it can be reprocessed. A rod that has burnt
at all also comes out as a Hot Spent Fuel Rod if you take it out early, so pull rods only when they
are spent. The work bar in the GUI is the average burn-up of the loaded rods.

At the default rate a rod lasts about **104 minutes** on the Balanced preset. Overdrive and Speed
modules burn rods faster (and make more power while they do).

### Control rods

Every **Control Rod Assembly** you place *inside* the shell lowers output **and** heat by 15 %, and
every **Europium Control Rod Assembly** by 25 %, down to a combined floor of **15 %**. They are the
operator's throttle: a 3×3×3 shell has room for one interior block, a 5×5×5 for 27, a 7×7×7 for 125.
Fewer assemblies means more power and more heat. Moderators and Breeder Blankets share the same
interior; see [Fuel Lines](Fuel-Lines.md).

### Neutron poisoning (the xenon pit)

Poison builds only while the core runs **hot**: it is producing, there is no failure-ladder
penalty, every usable slot is loaded, **and** the effective control-rod factor is above
`fissionPoisonRodThresholdPermille` (90 % by default). A core with no Control Rod Assembly runs at
100 %, so it builds poison at `fissionPoisonPerTick` (2) per tick. Once poison reaches
`fissionPoisonStallPermille` (90 %) the core **stalls**: no output, no burn-up, status *Xenon pit*.
It only restarts once the poison has decayed 30 points (300 ‰) below the line, so a core hovering
at the threshold cannot flicker on and off.

In every other case poison decays at `fissionPoisonDecayPerTick` (3) per tick: idle, stalled,
part-loaded, derated by the failure ladder, or control-rodded at or below the threshold. With the
defaults a single Control Rod Assembly (factor 85 %) is enough, and so is a SCRAM (15 %), so a core
with at least one control rod, or with one slot left empty, runs steadily without ever stalling.
The poison level is shown in the GUI.

### Heat, cooling and failure

The reactor pushes heat into NeroTech's thermal model exactly like a NeroTech machine: coolant
blocks, Radiators and Coolant Pumps next to the shell all help, and a
[Stirling Generator](RTG-and-Stirling.md) parked against it will draw heat *and* pay you for it.

Heat drives the [failure ladder](Failure-Stages.md). At **FAILURE** the shell explodes with a
radius of `shell size + 2` (5, 7 or 9 blocks, before the server cap of 8) and — if the server enables
it — leaves a **scorch zone** that hurts living creatures for a few real days. With
[radiation](Radiation.md) on, a failure also contaminates its chunk and the eight around it.

### Steam mode

With a Reactor Steam Port in the wall the core can run in **steam mode**: it raises steam for a
turbine instead of making power. See [Steam Plant](Steam-Plant.md).

### Reactor Control Computer

A wall block with redstone and comparator outputs. In its screen you pick:

- the **redstone signal** (heat, fuel, output, failure stage, or off; default: failure stage), which
  gives 15 while that signal is at or past its threshold (fuel: at or below);
- the **comparator signal** (heat, fuel, output or stage; default: heat), read as a level 0 to 15;
- the threshold for each signal (defaults: heat 80 %, fuel 10 %, output 50 %, stage *Warning*);
- **auto-SCRAM**: when on, the core is SCRAMmed and held while heat is at or above a level you set
  (50 % to 100 %, default 90 %).

It takes no power and holds no items.

### SCRAM

For 60 seconds after a SCRAM the core behaves as if every control rod were fully inserted (the
15 % floor), whatever is actually inside the shell, so output and heat drop and the ladder can climb
back down. Three things SCRAM a reactor:

- the **SCRAM Key**, used on the core or any block of its shell (in reach only; see [Tools](Tools.md));
- a **Reactor Control Computer** with auto-SCRAM on;
- the NeroLink app's **SCRAM** action, which also offers *acknowledge alarm*.

The app's actions work only on a reactor you own (NeroTech's recorded owner, which exists only when
the server turned on per-player attribution), while you are online, in its dimension and within 8
chunks of it. A reactor with no recorded owner accepts no remote action: use it in person. The server
checks this itself on every request. See [Link Module](Link-Module.md).

### Automation

- Energy leaves every face (Generator preset).
- Fuel Rods are accepted on every face; faces set to *output* in the Side Config tab let pipes
  pull **Hot Spent Fuel Rods** and Kindled Pellets only.
- The reactor is never load-shed by NeroTech's throttling.

## Recipes

| Item | Recipe |
| --- | --- |
| **Uranium Pellet** | 4 Glowstone Dust + 1 Iron Ingot (shapeless), or 2 Reprocessed Pellets |
| **Uranium Pellet** ×2 | 1 Raw Uranium — anything tagged `#c:raw_materials/uranium` (shapeless) |
| **Fuel Rod** | 3 Uranium Pellets in a row between two Iron Ingots |
| **Control Rod** | Iron Ingot, Redstone, Iron Ingot in a column |
| **Control Rod Assembly** | Control Rod, Iron Ingot, Control Rod in a row |
| **Fission Casing** (×4) | 8 Iron Ingots around a NeroTech Machine Frame |
| **Fission Core** | Iron Blocks in the corners, Circuit Boards top and bottom, Nero Coils on the sides, a Machine Frame in the middle |
| **Reprocessed Pellet** | a Spent Fuel Rod in NeroTech's **Chemical Processor** |
| **Reprocessed Pellet** ×2 + **Fission Residue** | a Spent Fuel Rod in NeroTech's **Centrifuge** |
| **Reprocessed Pellet** ×2 | a fresh Fuel Rod in the **Chemical Processor** |

Spent Fuel Rods are never crafted — they come out of the reactor hot and cool in a Spent Fuel Rack.
The 0.4.0 reactor blocks and fuel items are listed on [Fuel Lines](Fuel-Lines.md),
[Steam Plant](Steam-Plant.md) and [Waste](Waste.md).

**Uranium source.** No mod in the Neroland ecosystem adds uranium ore yet. NeroPower declares the
`#c:raw_materials/uranium` tag (empty), so the raw-uranium recipe starts working as soon as another
mod you install puts its raw uranium in that common tag. Until then the glowstone recipe is the
fallback and the raw-uranium recipe simply cannot be crafted.

**Reprocessing yields.** In the Chemical Processor a spent rod gives 1 Reprocessed Pellet; the
Centrifuge gives 2 plus a Fission Residue (see [Waste](Waste.md)). A rod that has burnt at all leaves
the core hot and spent, so only an unburnt rod can take the Chemical Processor's two-pellet route.
Two Reprocessed Pellets make one Uranium Pellet, so those 2 pellets are worth 1 Uranium Pellet,
against the 3 that went into the rod.

## Config keys

All in `config/neropower.properties`; see [Configuration](Configuration.md) for the full list.

| Key | Default | What it does |
| --- | --- | --- |
| `fissionNePerRod` | 120 | Nominal NE/tick per fresh rod at 100 % of the curve |
| `fissionHeatPerRodPerTick` | 6 | Heat per rod per tick at 100 % heat factor |
| `fissionBurnupPerTick` | 8 | Burn-up per tick (permille ×1000) |
| `fissionBurnupCurve` | `0=1200,200=1000,600=800,1000=300` | The output curve knots |
| `fissionControlRodPermille` | 150 | Reduction per Control Rod Assembly |
| `fissionPoisonPerTick` | 2 | Poison gained per tick while the core runs hot |
| `fissionPoisonRodThresholdPermille` | 900 | Poison builds only above this control-rod factor |
| `fissionPoisonDecayPerTick` | 3 | Poison shed per tick otherwise |
| `fissionPoisonStallPermille` | 900 | Poison level that stalls the core |
| `fissionScorchEnabled` | false | Leave a scorch zone on failure |
| `fissionScorchDays` | 3 | Real days a scorch zone lasts |
| `fissionScorchRadius` | 6 | Half-edge of the scorch cube |

The 0.4.0 fission keys (moderators, breeding, steam, cryo, Braided rods, Fuel Economizer) are on
[Fuel Lines](Fuel-Lines.md#config-keys) and [Configuration](Configuration.md#fission-reactor).

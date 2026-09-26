# Battery Banks

NeroPower adds three tiers of **Battery Cell** and a **Battery Bank Controller** that pools the
cells around it into one big store. A cell on its own is a simple buffer, much like NeroTech's
Battery Bank; next to a controller it becomes part of a bank with a single capacity, a single
in/out limit and a switchable mode.

## How it works

### Battery cells

| Tier | Capacity | Max in / out per tick |
| --- | --- | --- |
| **Basic Battery Cell** | 500,000 NE | 1,000 NE/t |
| **Advanced Battery Cell** | 2,000,000 NE | 4,000 NE/t |
| **Elite Battery Cell** | 8,000,000 NE | 16,000 NE/t |

A cell accepts and gives energy on every face (the Storage preset — reconfigure faces with the
Configurator as usual). Its model shows the stored charge in five steps from empty to full, so a
wall of cells reads at a glance.

**No sloshing.** A cell never pushes energy into another storage block — another cell, a
controller or a NeroTech Battery Bank — so a wall of half-full cells does not shuffle charge
between itself all day. Pipes and machines that *pull* from a cell are still served normally.

### The bank controller

Place a **Battery Bank Controller** touching one or more cells. Once a second it walks
face-to-face from the cells touching it through connected cells (within `bankScanRadius` blocks
on each axis — 2 by default, a 5×5×5 region) and pools every cell it finds:

- **Capacity** = the sum of the member cells.
- **In/out per tick** = the smallest member's limit × the number of members, capped by
  `bankMaxIoCap` (64,000 NE/t by default). Mixing tiers therefore drags the whole bank down to the
  weakest cell's rate — keep a bank to one tier.
- Energy is spread across the members round-robin, so every cell charges and drains together.

The controller itself stores nothing; its GUI and NeroTech's Analytics Terminal show the pooled
figures — cells pooled, stored / capacity, bank I/O and the current mode. Members are rediscovered
from the world on every load; a bank straddling an unloaded chunk shrinks until it comes back
(it never loads chunks itself). Breaking a member forces an immediate rescan.

### Modes

Sneak-right-click the controller with an **empty hand** to cycle the mode:

- **Buffer** (default) — pushes into every willing neighbour each tick, like any generator.
- **Priority Source** — only pushes into a neighbour whose own buffer is below
  `bankPriorityThresholdPermille` (30 % by default). The bank tops up machines that are running
  low and otherwise holds its charge, so a generator feeding the same machines does the everyday
  work and the bank covers the gaps.

Both modes govern only the controller's own push. Anything that pulls from the controller (a
pipe, an input face) is served in either mode, and neither mode ever feeds another storage block.

## Recipes

| Item | Recipe |
| --- | --- |
| **Basic Battery Cell** | Redstone in the corners, Iron Ingots on the edges, a NeroTech Nero Coil in the middle |
| **Advanced Battery Cell** | a Basic Battery Cell wrapped in Gold Ingots with a Circuit Board |
| **Elite Battery Cell** | an Advanced Battery Cell wrapped in Diamonds with two Circuit Boards |
| **Battery Bank Controller** | a Machine Frame between two Nero Coils, a Circuit Board above and a Basic Battery Cell below |

## Config keys

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `batteryCellCapacityBasic` | 500000 | 1000–100000000 | Basic cell capacity (NE) |
| `batteryCellCapacityAdvanced` | 2000000 | 1000–100000000 | Advanced cell capacity (NE) |
| `batteryCellCapacityElite` | 8000000 | 1000–100000000 | Elite cell capacity (NE) |
| `batteryCellIoBasic` | 1000 | 1–1000000 | Basic cell in/out per tick (NE/t) |
| `batteryCellIoAdvanced` | 4000 | 1–1000000 | Advanced cell in/out per tick (NE/t) |
| `batteryCellIoElite` | 16000 | 1–1000000 | Elite cell in/out per tick (NE/t) |
| `bankScanRadius` | 2 | 1–4 | How far a controller looks for cells (1 = 3×3×3 … 4 = 9×9×9) |
| `bankMaxIoCap` | 64000 | 1–10000000 | Hard cap on a bank's pooled in/out per tick |
| `bankPriorityThresholdPermille` | 300 | 0–1000 | Priority Source: feed a neighbour only below this fill |

# Beam Grids

[Beamed Power](Beamed-Power.md) covers the Beam Transmitter, Receiver, Relay and Orbital Receiver.
This page covers the 0.4.0 blocks that turn single links into a grid, and the grid tools that let you
test and protect one.

## How it works

### Beam Splitter

Takes one incoming beam and shares it over **up to four** outgoing links (`beamSplitterMaxLinks`).

- It needs a NeroTech **focus lens** in its slot. The lens sets its loss: quartz 5 %, glacite 3 %,
  ember 2 %, void 1 % (`beamSplitterLensLoss`). With no lens it passes nothing.
- Link it to receivers with NeroTech's **Configurator**, as with a transmitter, then set each link's
  share in its screen. What you leave unshared stays in its buffer.
- Its links record no player (see [Privacy](Privacy.md)). Permission is checked once, when you link.

### Phased Array Emitter

A long-range transmitter built from superconductor wire and quantum processors.

- Reaches **384 blocks** (`phasedArrayRange`) and loses a third as much per block as a Beam
  Transmitter (`phasedArrayLossPermillePerBlock`).
- Serves **up to four** receivers in turn, one each pass (`phasedArrayMaxTargets`). Reorder its targets
  in its screen.
- Sends 8,000 NE per pass before loss (`phasedArrayTransferPerPass`).

Beams from both blocks follow the same rules as any beam: straight line of sight, loss by distance,
blocked by any solid block, and they hurt anything standing in them.

### Beam Alignment Scope

Hold it and look at any block that sends a beam to see the beam's line of sight and the first block in
its way. Reach for it when a screen says the beam is blocked. See [Tools](Tools.md).

## Grid tools

### Circuit Breaker

An inline block: back to the supply, front to the load. Set a limit in its screen (it starts at
10,000 NE/t).

- Load above 60 % of the limit warns.
- Load at the limit for a moment **trips** it open, through the same staged ladder as every NeroPower
  failure, so it always warns first (`breakerMinDwellTicks`).
- Nothing is damaged. Reset it from its screen, by right-clicking its front, or with a redstone pulse.
- A comparator reads whether it is tripped.

### Load Bank

Burns power on purpose, up to 20,000 NE/t (`loadBankMaxRate`), and turns it into heat. Use it to test a
supply or soak up a surplus. It makes nothing. A Stirling Generator or Condenser loop on it gets back
less than went in, so it can never be part of a free-energy loop.

### Power Monitor

Mount it on a store or a machine to see its last minute of power in, power out and charge as a rolling
graph. A comparator reads how full the store is. It only syncs its graph while a player is near.

## Recipes

| Result | How |
| --- | --- |
| **Beam Splitter** | Crafting: Glass, 4 × Iron Ingot, Beam Relay, any `#nerotech:components/capacitors` |
| **Phased Array Emitter** | Crafting: 4 × any `#c:wires/superconductor`, any `#nerotech:focus_lenses`, 2 × any `#nerotech:processors/quantum`, Beam Transmitter, Machine Frame |
| **Beam Alignment Scope** | Crafting: any `#nerotech:focus_lenses`, 2 × Iron Ingot, Spyglass, Redstone |
| **Circuit Breaker** | Crafting: 4 × Iron Ingot, any `#nerotech:processors/logic`, 2 × Redstone, Machine Frame, Copper Ingot |
| **Load Bank** | Crafting: 3 × Iron Bars, 2 × any `#nerotech:components/heat_sinks`, Machine Frame, 3 × Copper Ingot |
| **Power Monitor** | Crafting: 4 × Iron Ingot, Glass Pane, any `#nerotech:processors/logic` |

## Config keys

See [Configuration](Configuration.md#beamed-power) and [Configuration](Configuration.md#grid-tools).

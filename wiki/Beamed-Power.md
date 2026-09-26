# Beamed Power

Beamed power moves energy across the map without a cable: a **Beam Transmitter** aims a straight,
line-of-sight beam at a **Beam Receiver**, optionally bouncing through **Beam Relays** on the way.
Distance costs a little, anything solid in the way stops the beam outright, and standing in one
hurts. An **Orbital Receiver** is the special case that can be beamed to from another dimension.

## How it works

### The blocks

| Block | Role | Faces by default |
| --- | --- | --- |
| **Beam Transmitter** | Takes energy in and beams it to one target | Input on every face |
| **Beam Receiver** | The far end; fills by beam and feeds its neighbours like a generator | Output on every face |
| **Beam Relay** | Receives like a receiver and forwards like a transmitter, to bend a beam around terrain or extend it past the range | Disabled — open a face in the Side Config tab to tap it |
| **Orbital Receiver** | A receiver that may also be targeted from another dimension | Output on every face |

Every endpoint has a GUI with the shared NeroTech machine tabs plus a beam readout: linked or not,
distance, whole-beam loss, energy moved on the last pass and a status line (*Transmitting*,
*Beam blocked*, *Target out of range*, *Target full*, and so on).

### Linking with the Configurator

Use NeroTech's **Configurator** in configure mode:

1. Click the **transmitter** (or a relay) — it becomes your pending source for 30 seconds.
2. Click the **receiver**, **orbital receiver** or **relay** it should aim at. The link is made.

Clicking a linked transmitter or relay with nothing pending **unlinks** it. A relay can be both a
target (of the transmitter before it) and a source (aimed at the next endpoint), so chains are just
repeated pairs of clicks.

Linking is checked on the player holding the wrench: you must be allowed to interact with **both**
endpoints (spawn protection counts), both must be loaded, and endpoints further apart than
`beamRange` are refused. Only the player who made a link — or an operator — can unlink or re-aim
it, so nobody can quietly re-aim your transmitter. The linking player's UUID is the one thing
NeroPower stores about players. It stays on the server (world save only, never sent to clients);
see [Privacy](Privacy.md).

### Passes, loss and line of sight

A transmitter fires a **pass** every `beamCheckIntervalTicks` (5 ticks — four times a second).
Each pass:

- re-checks that the target still exists and is a beam endpoint (a broken or replaced target
  drops the link; an unloaded one just skips the pass — chunks are never force-loaded);
- checks **range** (128 blocks by default) and **line of sight** — every block cell between the two
  endpoints must be air or another beam endpoint. One solid block, water included, blocks the beam
  until it is removed;
- offers up to `beamTransferPerPass` NE (2,000 by default) from its buffer. What arrives is
  `sent × (1 − loss ⁄ 1000)^distance`: at the default 3 ‰ per block, about **68 %** survives 128
  blocks. The transmitter is only charged for what the target actually kept;
- deals `beamDamage` (half a heart) to every living creature standing in the beam.

Relays forward what lands on them **in the same pass**, up to `beamMaxHops` deep (8), and a pass
never visits the same endpoint twice — a chain that loops back on itself reports *Relay chain loops
or is too deep* and stops. Whatever a relay cannot forward sits in its small buffer (8,000 NE) and
goes out on its next own pass.

Turn `transmissionEnabled` off and every beam goes dark while the blocks stay in place.

### Cross-dimension beams

With `beamCrossDimension=true`, a transmitter may target an **Orbital Receiver** that sits in a
dimension tagged as space (`neroland:space/dimensions`, which Nerospace orbits carry). An orbital
hop ignores distance and line of sight and instead pays the flat `beamOrbitalHopLossPermille`
(30 % — 70 % delivered). Only orbital receivers accept cross-dimension beams, only in space
dimensions, and the target chunk must already be loaded. In its own dimension an Orbital Receiver
behaves exactly like a Beam Receiver.

## Recipes

| Block | Recipe |
| --- | --- |
| **Beam Transmitter** | Glass, Ender Pearl, Glass over Nero Coil, Machine Frame, Circuit Board |
| **Beam Receiver** | Glass, Nero Coil, Glass over a Machine Frame and an Iron Ingot in a column |
| **Beam Relay** | Nero Coil, Glass, Nero Coil over an Iron Ingot |
| **Orbital Receiver** | Gold Ingot, Eye of Ender, Gold Ingot over a Beam Receiver and a Circuit Board in a column |

Machine Frame, Nero Coil and Circuit Board are NeroTech intermediates.

## Config keys

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `transmissionEnabled` | true | — | false darkens every beam |
| `beamRange` | 128 | 8–512 | Maximum straight-line distance of one link |
| `beamLossPermillePerBlock` | 3 | 0–100 | Loss per block of distance (permille) |
| `beamTransferPerPass` | 2000 | 1–1000000 | NE offered per pass, before loss |
| `beamCheckIntervalTicks` | 5 | 1–40 | Ticks between passes |
| `beamDamage` | 0.5 | 0.0–10.0 | Damage per pass to creatures in a beam (0 disables) |
| `beamMaxHops` | 8 | 1–32 | Longest relay chain one pass follows |
| `beamCrossDimension` | false | — | Allow beams to an Orbital Receiver in a space dimension |
| `beamOrbitalHopLossPermille` | 300 | 0–1000 | Flat loss of a cross-dimension hop |

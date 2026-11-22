# Tools

NeroPower adds four hand tools and two upgrade modules. None of them stores anything about you: every
reading is kept on the item itself. See [Privacy](Privacy.md).

## How it works

### Geiger Counter

Hold it in either hand. An on-screen reading shows the contamination (in pall, pl) of your chunk and
the eight around it, and how many **leaking** radiation sources are within 16 blocks. A source boxed in
by shielding on every face does not count. It clicks faster the hotter the ground.

The reading is drawn in a screen corner you choose with the client setting `geigerHudCorner`
(`bottom_left`, `top_left`, `top_right` or `bottom_right`). Only your own client draws it, and the
reading is cleared from a counter that is no longer in your hand. See [Radiation](Radiation.md).

### Dosimeter Badge

Carry it, and it adds up the radiation it has been in. The reading is kept **on the badge and nowhere
else**: no name, no place, no time. Its texture fills up as the total rises. Craft the badge on its own
to reset it.

### Beam Alignment Scope

Hold it and look at any block that sends a beam (a Beam Transmitter, Relay, Beam Splitter or Phased
Array Emitter). It draws the beam's line of sight and marks the first block in its way. Use it when a
screen says *Beam blocked*. See [Beam Grids](Beam-Grids.md).

### SCRAM Key

Use it on a Fission Core, or on any block of a formed reactor's shell, to SCRAM that reactor: for 60
seconds the core behaves as if every control rod were fully inserted. It works only on the block you
are touching, never at range, and respects the server's protection rules. One turn a second.

### Governor Module

A Turbine Helm upgrade. It holds the rotor at speed when the steam supply changes, so the turbine never
overspeeds. See [Steam Plant](Steam-Plant.md#spin-up-overspeed-and-the-governor-module).

### Fuel Economizer

A Fission Core upgrade. Rods burn up a quarter slower and give a tenth less power, about 20 % more over
a rod's life. See [Fuel Lines](Fuel-Lines.md#the-fuel-economizer).

### Also useful

- NeroTech's **Configurator** links beam endpoints (transmitters, relays, splitters, phased arrays).
- **Decontaminant**, used on any block, cleans 250 pl from that chunk. See [Radiation](Radiation.md).

## Recipes

| Result | How |
| --- | --- |
| **Geiger Counter** | Crafting: 5 × any `#c:plates/cindersteel`, Tinted Glass, 2 × Redstone, any `#nerotech:processors/logic` |
| **Dosimeter Badge** | Crafting (shapeless): any `#c:plates/cindersteel`, Paper, Glowstone Dust, String |
| **Dosimeter Badge** | Crafting (shapeless): Dosimeter Badge |
| **Beam Alignment Scope** | Crafting: any `#nerotech:focus_lenses`, 2 × Iron Ingot, Spyglass, Redstone |
| **SCRAM Key** | Crafting: any `#c:plates/cindersteel`, Redstone, Iron Ingot |
| **Governor Module** | Crafting: 2 × Gold Ingot, 2 × any `#nerotech:components/servos`, Circuit Board |
| **Fuel Economizer** | Crafting: 2 × any `#c:plates/cindersteel`, 2 × Gold Ingot, any `#nerotech:processors/logic` |

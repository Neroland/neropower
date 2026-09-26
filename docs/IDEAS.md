# Ideas — candidates after 0.1.0

Everything here is **out of scope for 0.1.0** and is a candidate for 0.2.0 or later. None of it is
promised. Each idea must still respect the rules the shipped content follows: NeroPower adds only
what NeroTech lacks, it stays usable standalone (no progression gate is required to play), chunks
are never force-loaded, failures are telegraphed and protection-aware, and no new player data is
stored without a `PRIVACY.md` update first.

## Stellarator

A large toroidal fusion multiblock that sits above NeroTech's Fusion Reactor. A plasma-confinement
figure rises while the coils are fed and the ring is kept below a coil-heat ceiling, and output
scales with it. When confinement is lost the machine does not explode: it quenches, drains its
stored NE into a heat spike and has to be brought back up from cold. The work is in keeping
confinement steady, which means coolant logistics and matching coil power to the fuel's burn rate.
It would reuse the shared failure ladder with a quench action in place of an explosion.

## Tidal generator

A shoreline block, or a small row of them, that makes power from the moon's phase and the time of
day. Output peaks at spring tides and falls to almost nothing at neap. It only works in a water
column that connects to an ocean biome within a set radius, so a bucket pond gives nothing. It is
steady and predictable, and it rewards building at the coast rather than packing generators close
together. On a Nerospace planet with no ocean it would do nothing, and it would read that through
NeroTech's `PlanetApi`.

## Atmospheric turbine

A tall wind turbine that reads the density of the planet's atmosphere as well as the wind. On a
thick-atmosphere world it produces well even at low wind, on a thin one it hardly turns, and in a
vacuum it produces nothing. This goes beyond NeroTech's Wind Turbine, which only uses the wind
multiplier. Height above the local terrain adds a bonus, and blocks within its sweep reduce
output, so where you put it matters.

## Volcanic vent tap

A cap that sits over a lava source in a volcanic biome (or on a Nerospace planet tagged as
geologically active) and turns the vent's heat into NE through a heat exchanger. Unlike a burner it
uses no fuel, but the vent's heat drifts over in-game days. Tapping too hard can "cool" a vent for
a while, and a badly cooled tap can join the failure ladder with a steam blow-out that sprays lava
and pushes entities back, so it is geothermal power that needs looking after.

## Bioreactor

A digester multiblock that turns organic feedstock into NE over time, with a buffer that has to
stay within a temperature band. The planned feedstock is a NeroAgriculture item. That dependency is
why it is out of scope for now: it would need a soft, optional hook so that without
NeroAgriculture it either takes vanilla compostables at a lower yield or does not register at all.
A spoiled batch (from overheating or starving it) produces pollution through NeroTech's
`PollutionManager`.

## Lightning capacitor

A lightning rod mounted on a capacitor bank. Each strike during a thunderstorm dumps a large burst
of NE into the bank, which then leaks it out slowly. Players would build it for the burst, not for
steady output, since it can top off a battery bank overnight in a storm. A full capacitor struck
again discharges into the area around it (damage within a small radius, telegraphed with sparks
first), so overflow has to be handled. It would follow vanilla's rules for where lightning can
strike and never call up storms of its own.

## Antimatter burner

The endgame fuel. Antimatter pellets, made at very high energy cost in a particle-collider chain
built on NeroTech's Collider, are burned against matter for a huge output per pellet. Containment
is the whole design: a containment field that draws power has to stay up all the time, even while
the burner is idle. If containment is lost with pellets inside, the failure ladder ends in the
biggest blast NeroPower has, still capped by `failureRadiusCap`, still respecting protection, and
never silent. With the default settings it would probably be server-opt-in.

## Orbital solar

A large solar multiblock for vacuum and orbit dimensions (Core `SpaceTags`). There is no weather
and no night side while in sunlight, so the output is steady, and it pairs with the existing
Orbital Receiver to beam power down to the surface. It is the natural off-world endgame power
source. The work is keeping a big multiblock valid without rescanning it every tick, and handling
orbital day and night cycles where the space dimension defines them.

## Reactor automation hooks

Expose the fission core's heat, poison, fuel burn-up, failure stage and SCRAM state as read-outs
that automation can use: a comparator output profile, a small data-component "reactor probe" that
NeroTech logic blocks can read, and Create-style redstone links where Create is present. The goal
is player-built auto-SCRAM and auto-cooling loops that go beyond the remote NeroLink SCRAM that
0.1.0 already has. It must not become a way to control someone else's reactor from a distance:
reading and acting should follow the same protection seam as walking up to the block.

## Strict energy mode

A server config switch for a closed energy economy. Every NeroPower generator would draw from a
real, finite source (burn-up, heat moved, stored charge) with no hidden constants, and the
Stirling's cold-face bonus, the RTG's flat curve and the beam relay's pass-through buffer would be
re-checked so that no loop produces free NE. The default stays the current forgiving balance, and
strict mode is for pack makers who want energy to be something players trade.

## Progression gates — not planned

Decision (2026-09-26): NeroPower uses **natural progression only**. Tiers are earned by crafting
through NeroTech's intermediates (Machine Frame, Nero Coil, Circuit Board) and NeroPower's own fuel
cycle. There are no Core progression gates and no material-milestone recipe gates.

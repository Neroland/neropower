# NeroPower — design record

> **How this document is used (2026-09-25).** NeroPower was retired on 2026-07-31 in favour of merging its
> features into NeroTech, then **revived as an optional add-on to NeroTech** (Option C). The sections below are
> the original design record, kept verbatim as the source of the pillars and system ideas; the current scope
> and stage order live in [`../PLAN-0.1.0.md`](../PLAN-0.1.0.md). Where the record says "standalone", read
> "add-on that depends on NeroTech": nothing is extracted from NeroTech and no NeroTech block changes.

## Design


## Design pillars

- **Generation is a decision, not a faucet.** Every generator trades something — fuel, space, cooling, location, risk. A solar field is cheap but weather/planet dependent; a fusion reactor is enormous output but demanding to feed and dangerous to neglect.
- **Depth over breadth.** NeroPower adds relatively few block *types* but each one has real internal mechanics (efficiency curves, heat, cooling loops, overload thresholds) rather than a flat RF/tick number.
- **Plays nice with the ecosystem's energy.** All power flows through Core's power-type framework so it interoperates with Forge/NeoForge `IEnergyStorage`, Mekanism, AE2, and Ad Astra-tier consumers instead of inventing a parallel currency.
- **Location matters.** Planet and environment (sun, atmosphere, wind, geology) change output. This is the hook that ties NeroPower to Nerospace and Ad Astra and makes "where do I build my power plant" a strategic question.
- **Risk is opt-in but real.** Low-tier generation is safe. High-tier generation (nuclear, fusion) can fail catastrophically if mismanaged — but only the player who built it chooses to take that on.

## Core systems

**Energy units & transport.** NeroPower produces and stores energy in Core's unified power type (a thin layer over the Forge/NeoForge energy capability). Generators expose an `IEnergyStorage`-compatible capability on their output faces; cables (NeroLogistics) or wireless nodes carry it; battery banks buffer it. NeroPower does not ship its own consumer machines — it relies on Nerotech and other mods for load.

**Efficiency model.** Each generator computes an effective output per tick from a base rate multiplied by environmental modifiers. For solar this is sky access, time of day, weather, and a *planet/atmosphere modifier* pulled from Nerospace/Ad Astra dimension data via Core. For wind it is exposed height and biome wind class. Geothermal reads nearby heat sources (lava, configured geothermal blocks). Reactors are environment-independent but gated by fuel and heat.

**Heat & cooling.** Nuclear and fusion reactors generate heat as a block-entity stat that rises with output and falls with cooling throughput. Cooling is supplied by passive structures (multiblock heat sinks, water/coolant blocks) and active loops (pumped coolant, consuming fluid/power). If heat exceeds a threshold the reactor enters an unstable state; sustained overheating triggers an overload event.

**Overload / failure.** Overload is a staged process: warning state → unstable (efficiency penalty, particles, sound, alarms) → failure (explosion, optional radiation/scorch zone for nuclear). Thresholds, blast radius, and whether failure is enabled at all are configurable. Failure is always preceded by clear in-world telegraphing so it is never a silent surprise.

**Storage & balancing.** Battery banks are multiblock or stackable blocks that store energy and smooth the gap between intermittent generation (solar/wind) and steady demand. They expose insertion/extraction limits and can be configured as buffers or as priority sources.

## Progression integration

NeroPower is built to mirror the ecosystem's Earth → industrialise → space → colonies arc:

- **Early (Earth, industrialising):** Solar panels, wind turbines, and geothermal generators. Cheap, safe, environment-driven. Players learn that power output depends on *where* and *when*. Burnable/biofuel generators (see NeroAgriculture synergy) fill the early steady-supply gap.
- **Mid (advanced industry):** Nuclear reactors. Introduces fuel cycles, heat, cooling loops, and the first real risk of catastrophic failure. This is the "you now have to think about your power plant as a system" tier.
- **Late / space (colonies):** Fusion reactors and wireless power. Fusion is the high-output endgame source suited to running colonies and heavy automation. Planet-based efficiency becomes central: solar is far stronger off-atmosphere, wind is dead in vacuum, geothermal depends on the body's geology — so off-world colonies are pushed toward reactors and orbital/exposed solar.

The progression intentionally maps generation choices onto the player's physical journey: the further from Earth, the more the environment rewrites which generators make sense.

## Multiplayer / server considerations

- **Overload griefing.** Reactor explosions are the obvious abuse vector. Mitigations: explosions respect claim/protection systems (FTB Chunks / Core claim hooks); a server config can disable terrain damage while keeping the block-destruction-of-the-reactor "soft fail"; failure can be limited to the reactor multiblock plus a configurable radius. Default should be conservative on shared servers.
- **Claims & protection.** Wireless power transmission must not let a player push or pull energy across claim boundaries they don't have access to. Wireless links should require both endpoints be accessible to the linking player, validated against Core's claim/permission API.
- **Performance.** Solar/wind tick constantly; large fields are a known lag source. Use batched/aggregated ticking, sky-light caching, and chunk-aware throttling. Battery banks and reactors should avoid per-tick block updates where possible.
- **Server progression rules.** Core's server-side progression gating can lock fusion (and optionally nuclear) behind ecosystem milestones so a fresh player can't skip straight to endgame power.

## Balance & config notes

- All generator base rates, efficiency curves, fuel values, heat rates, cooling values, and overload thresholds are config-exposed via Core's config framework.
- Planet efficiency modifiers are data-driven so packmakers can tune per-dimension values (and so Nerospace/Ad Astra planets can ship their own).
- Master toggles: enable/disable overload explosions; enable/disable terrain damage on failure; enable/disable wireless power; enable/disable planet-based efficiency (for packs that want flat behaviour).
- Energy interop is on by default so NeroPower power is fungible with Nerotech/Create/AE2/Mekanism. A "strict" mode can isolate NeroPower energy if a pack wants a closed economy.

## Standalone mod vs. part of Nerotech — the open tension

> **RESOLVED 2026-07-31 — Option A (merge) won.** NeroPower is retired; its generation, storage,
> cooling/overload and planet-efficiency features were absorbed into **Nerotech `0.1.0-beta.1`**, and the
> repo's publish workflows were neutralised. The discussion below is preserved as the reasoning record —
> read "must be revisited before implementation" as historical.

This was the single most important unresolved design decision for NeroPower, and it had to be revisited before any real implementation work began.

**The question:** should NeroPower be a separate optional mod, or should this energy system simply be a subsystem inside Nerotech?

**Option A — part of Nerotech.**
- *Pros:* No cross-mod boundary to maintain. Generators and consumers ship together, so the energy economy is guaranteed consistent and balanced as one unit. One mod to install, one less dependency edge. Simpler for the player. Avoids the "useless without Nerotech" awkwardness baked into a standalone build order.
- *Cons:* Nerotech becomes large and unfocused. Players who want deep power but not Nerotech's full machine suite can't opt in. Energy mechanics compete for design attention with machine mechanics. Harder to iterate on power balance independently.

**Option B — standalone NeroPower (this mod).**
- *Pros:* Focused scope, "a focused, deep energy mod" can actually *be* deep. Optional — packmakers and players opt in. Energy can be iterated, balanced, and versioned independently. Other mods (NeroLogistics, Nerospace) can depend on it directly. Clear single responsibility.
- *Cons:* Useless on its own (no consumers) — bad build-order optics. A cross-mod energy boundary must be kept perfectly compatible. Two mods to maintain and release in lockstep. Risk of the split feeling arbitrary if the system stays shallow.

**Deciding criterion — system depth.** The deciding factor is whether the power layer is genuinely deep. Concretely, NeroPower earns standalone status only if *several* of these hold: meaningful per-generator mechanics (efficiency curves, fuel cycles), a real heat/cooling subsystem, planet-based efficiency that interacts with Nerospace, an overload/failure model worth balancing, and wireless networking with its own configuration surface. If the feature set collapses to "a few generators that output RF," it is not deep enough to justify a separate mod and should merge into Nerotech.

**Recommended path:** prototype the generators and the heat/cooling/overload subsystem *inside* Nerotech first (package-isolated). If that subsystem grows the depth above, extract it into standalone NeroPower with Nerotech depending on it. Build it to be extractable from day one — keep the package boundary clean — but do not commit to the split until depth is proven.

**Outcome (2026-07-31):** the prototype-inside-Nerotech path was taken and the extraction never happened — the features shipped as part of **Nerotech `0.1.0-beta.1`** and NeroPower was retired.


## Features (as designed)


> **Retired 2026-07-31.** These features were absorbed into **Nerotech `0.1.0-beta.1`**; nothing below ships
> as a standalone NeroPower mod. Kept as the design record — see NeroTech.

Ten features, grouped roughly by progression tier. Each generator exposes Core's power-type capability (a layer over the Forge/NeoForge `IEnergyStorage` energy capability) on its output faces so it interoperates with the wider ecosystem.

> **Side configuration (via Core).** "Exposes the power capability on its output faces" is provided by Core's **universal side-configuration** layer (proposed; see Core's `sideconfig` package), not by per-block code here. Each NeroPower generator, reactor and battery bank declares the `ENERGY` channel and a default preset (`GENERATOR` for sources — default OUTPUT faces; `STORAGE` for battery banks — input *and* output), and the player can then reconfigure any face to power-in / power-out / disabled through the shared Side Config tab. Battery banks' "configurable insert/extract rate limits" are the per-channel auto-eject/auto-input rates of that same layer. NeroPower writes no face-routing of its own.

### Solar Panels
Flat, sky-facing blocks (or a thin slab-style block-entity) that generate energy proportional to available sunlight. Output is computed per tick from a base rate scaled by sky-light access, time of day, weather, and a planet/atmosphere modifier. Internally a `BlockEntity` caches its sky exposure and ticks in a batched group to limit per-tick cost; energy is pushed to adjacent storage/cables via the energy capability. Connects to **Nerospace/Ad Astra** (atmosphere and sun-strength data make solar dramatically stronger off-atmosphere and in orbit) and to **NeroLogistics/AE2/Create** for distribution. Tiered variants (basic → advanced) raise base rate and cap.

### Wind Turbines
Multi-block or tall single-block generators whose output depends on exposed height and biome wind class. The turbine `BlockEntity` checks unobstructed space and altitude to derive a wind factor, animates the rotor model, and feeds energy out via capability. Higher and more open placements produce more; sheltered or low builds produce little. Connects to **Nerospace** (wind is zero in vacuum / thin-atmosphere bodies, pushing off-world players toward reactors) and to **NeroLogistics** for grid hookup. Pairs naturally with solar as a complementary intermittent source.

### Geothermal Generators
Generators that tap ambient heat — lava, configured geothermal/heat blocks, or volcanic biomes — and convert it to steady power. The `BlockEntity` scans adjacent/nearby heat sources (data-driven tag of valid heat blocks) and may consume or be adjacent to lava/coolant fluids. Provides reliable, non-intermittent early-mid power where geology allows. Connects to **Nerospace/Ad Astra** (planet geology determines viability — hot worlds reward geothermal, cold ones don't) and to **Create/Mekanism** heat or fluid systems via Core compat tags where present.

### Nuclear Reactors
Multiblock mid-game generators that burn nuclear fuel for high sustained output while producing heat that must be managed. The reactor core `BlockEntity` tracks fuel burn, a heat stat, and cooling throughput; output and heat rise together, and inadequate cooling drives the reactor toward an unstable state and eventual overload. Fuel and depleted-fuel items are produced (with hooks for **Mekanism**/**Nerotech** fuel processing). Connects to the **Reactor Cooling** and **Overload Explosions** systems below, to **NeroLogistics** for power export, and to Core's progression gating so it can be locked behind a milestone.

### Fusion Reactors
Late-game / space-tier multiblock reactors delivering the ecosystem's highest output, intended to power colonies and heavy automation. Requires a startup energy charge and a steady plasma/fuel feed (e.g. configured fusion fuel, optionally produced by **Mekanism** or **Nerotech**), and runs hot — leaning heavily on active cooling. Implemented as a large multiblock with a controller `BlockEntity` managing fuel, plasma stability, heat, and a high-consequence overload. Connects to **Nerospace** (the natural power source for off-world colonies where solar/wind/geothermal falter), **Reactor Cooling**, and Core progression gating (fusion is the endgame unlock).

### Battery Banks
Storage blocks (stackable units or a multiblock bank) that buffer energy and smooth the gap between intermittent generation and steady demand. Each unit is a `BlockEntity` exposing the energy capability with configurable capacity and insert/extract rate limits, and can act as a buffer or a prioritised source. Renders a charge-level model/overlay. Connects to everything that produces or consumes power — **solar/wind** especially (storing daytime/windy surplus), **NeroLogistics** grids, and external storage from **Mekanism**/**AE2**/**Create** via energy interop.

### Wireless Power
Point-to-point and networked energy transmission without physical cables, for spanning gaps, terrain, or claim-respecting links between bases. A transmitter and receiver `BlockEntity` pair (or a network controller with registered nodes) move energy by capability over a configured range with optional transfer loss. Linking validates both endpoints against Core's claim/permission API so players can't siphon across protected boundaries. Connects to **NeroLogistics** as the wireless complement to its cables, and to **Nerospace** (linking surface generation to orbital or distant colony loads, subject to range/relay rules).

### Planet-Based Efficiency
A cross-cutting modifier system, not a block: environmental generation (solar, wind, geothermal) reads per-dimension data to scale output by planet. Solar is stronger off-atmosphere and in orbit; wind is dead in vacuum; geothermal depends on the body's geology. Implemented as a data-driven modifier table keyed by dimension/planet, fed by **Nerospace** and **Ad Astra** planet data through Core, and consumed by each environmental generator's efficiency calculation. This is the feature that makes "where you build" strategic and ties power generation directly to the space-progression arc. Fully toggleable for packs wanting flat behaviour.

### Reactor Cooling
The subsystem that keeps nuclear and fusion reactors safe and productive. Cooling is supplied passively (multiblock heat sinks, water/coolant-block adjacency) and actively (pumped coolant loops that consume fluid and/or power). The reactor `BlockEntity` subtracts cooling throughput from its heat each tick; more output demands more cooling. Coolant fluids and pumps can integrate with **Create**/**Mekanism** fluid handling and **Nerotech** pumps via Core compat tags. Inadequate cooling is the direct on-ramp to the Overload system, making cooling design the core mid-late game power-engineering puzzle.

### Overload Explosions
The failure model that gives high-tier generation real stakes. When a reactor's heat (or a grid's load imbalance, if enabled) exceeds thresholds, it enters a staged failure: warning → unstable (efficiency penalty, particles, sound, alarm) → failure (explosion, with optional radiation/scorch zone for nuclear). Failure is always telegraphed in-world so it's never silent. Server config controls thresholds, blast radius, terrain damage on/off, and whether overload is enabled at all; failures respect claim/protection systems. Connects to **Reactor Cooling** (the usual trigger), to Core's claim and config APIs, and to server progression/safety policy.


## Ideas and open questions


## Open questions

- **Split vs. merge (the big one).** ~~Standalone NeroPower or a subsystem inside Nerotech?~~ **Resolved 2026-07-31: merged.** NeroPower is retired and its features were absorbed into **Nerotech `0.1.0-beta.1`**; the remaining questions in this list are now Nerotech's to answer. Original wording: decision criterion is system depth (see DESIGN.md); recommended prototyping inside Nerotech with a clean, extractable package boundary and extracting only if the heat/cooling/overload/planet-efficiency depth proved out.
- **Where is the consumer line drawn?** NeroPower ships zero consumers by design — but does it ship *any* convenience load (e.g. a charger, a dummy load for testing)? Risk of scope creep into Nerotech's territory if it does.
- **Does "grid load imbalance" overload exist, or only reactor heat overload?** A whole-grid overload model is richer but much harder to balance and to make non-griefy. May be cut to reactor-only.
- **Wireless range & relays.** Flat range, range with loss, or relay-node networks? How does it interact with cross-dimension (surface → orbit) links — allowed, relay-gated, or forbidden?
- **Whose planet data wins?** When both Nerospace and Ad Astra define a planet, which efficiency table takes precedence, and how is that resolved in Core?
- **Fuel sourcing.** Do nuclear/fusion fuels come from NeroPower, Nerotech, Mekanism, or a data-driven recipe layer? Avoid forcing a hard Mekanism dependency.
- **Default safety posture on servers.** Should overload terrain damage be off by default? Likely yes for shared-server friendliness, on by default in single-player.

## Stretch goals

- **Tiered/upgrade modules** via Core's upgrade-module framework — overclock, efficiency, capacity, cooling-boost modules for generators and reactors.
- **Orbital / space solar arrays** — large multiblock solar suited to vacuum and orbit, the natural endgame off-world power source.
- **Smart grid / load balancing controller** — a block that prioritises sources, sheds load, and reports grid health; ties into NeroLogistics.
- **Radiation mechanic** for nuclear failure and spent fuel (optional, config-gated) — scorch/radiation zones, cleanup gameplay.
- **Reactor automation hooks** — expose heat/fuel/output to Create/Nerotech logic so players can build auto-SCRAM and auto-cooling.
- **Pollution / waste heat** as an environmental cost of dirty generation, rewarding solar/fusion over reactors in eco-focused packs.
- **In-game power monitoring UI** — graphs of generation vs. demand for a base/grid.

## Risks / scope warnings

- **Useless-without-Nerotech optics.** A standalone energy mod with no consumers is awkward; the merge option exists precisely because of this. Don't over-invest in standalone polish before the split decision is made.
- **Performance of intermittent generators.** Large solar/wind fields are a classic lag source. Batched ticking, sky-light caching, and chunk throttling are mandatory, not nice-to-haves.
- **Overload griefing.** Explosions on shared servers are a real abuse vector. Claim-respecting, configurable, telegraphed failure is required from the first reactor.
- **Energy-interop fragmentation.** If interop with Create/AE2/Mekanism breaks, NeroPower forks the ecosystem's energy economy. Keep everything on Core's unified type.
- **Multiblock complexity.** Reactors and fusion as multiblocks add structure-validation, rendering, and save/load complexity — budget for it or start with single-block reactors.
- **Balance surface area.** Deep mechanics mean many tunables; lean hard on Core config and data-driven tables so packmakers (and we) can rebalance without code changes.

## Cross-mod hooks to explore

- **Nerospace** — planet efficiency data; off-world colony power expectations; orbital solar; vacuum-disables-wind rule.
- **Nerotech** — primary consumer; fuel processing; cooling pumps; the merge target; reactor automation logic.
- **NeroLogistics** — physical cables vs. NeroPower wireless; shared grid/network model; smart-grid controller.
- **NeroAgriculture** — biofuel/burnable feedstock for early steady generation.
- **Core** — power-type framework, config, claims, progression gating, upgrade-module framework, compat tags, creative tabs.
- **Ad Astra** — planet data source; dimension-keyed efficiency tables.
- **Create / Mekanism** — fluid/heat for cooling loops; fuel processing; energy bridges.
- **AE2** — networked energy draw/feed as fungible power.

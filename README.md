# NeroPower

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**. NeroPower is an
> **optional add-on to [NeroTech](https://github.com/Neroland/nerotech)**: it adds power *depth* on top
> of NeroTech's machine framework and generators.

**Version:** `0.4.0` ("Power Station"). See the [wiki](wiki/Home.md) for players,
[`docs/DESIGN.md`](docs/DESIGN.md) for the design record and [`CHANGELOG.md`](CHANGELOG.md) for what
each release shipped.

## What it adds

- **Fission reactor** in three sizes with a real fuel cycle — burn-up curves, control rods,
  moderators, neutron poisoning, a control computer with automatic SCRAM.
- **Two fuel lines** — the standard Fuel Rod and the bred Pallurite line ending in the Braided Fuel Rod.
- **Steam plant** — steam mode, a scalable steam turbine with inertia and an overspeed trip, and a
  Steam Condenser; ports move fluid by adjacency, so no pipe mod is needed.
- **Waste back end** — Spent Fuel Rack, reprocessing, Vitrifier and Dry Cask; nothing is a dead end.
- **Storage** — tiered, pooled battery banks, the Surge Bank, Momentum Wheel, superconducting Storage
  Ring and portable Power Cells.
- **Beamed power and grids** — transmitters, receivers, relays, the Beam Splitter and Phased Array
  Emitter, plus Circuit Breaker, Load Bank and Power Monitor.
- **Optional radiation** — chunk contamination with shielding, a Geiger Counter, a Dosimeter Badge and
  Decontaminant; no per-player dose is stored.
- **Staged failure model** — stable → warning → unstable → failure, always telegraphed, claim-aware,
  terrain damage off by default on servers.
- **RTG**, **Stirling**, **Hydrogen Fuel Cell** and **Betavoltaic Cell** generators.
- **Power Guide, quests and a gallery** — an in-game guide, the *Reactor Log* NeroQuests chapter
  (18 quests, inert data without NeroQuests) and `/neropower gallery` with a guided tour.
- **NeroLink module** (link schema 2) — scoped, coordinate-free plant data for the companion app.

## Requirements

- **Neroland Core** 1.24.0 or newer (below 2.0) and **NeroTech** 0.10.1 or newer (below 1.0).
- **Minecraft:** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also 1.21.7), 1.21.10 (also 1.21.9), 1.21.11, 26.1.2, 26.2 and 26.3
  · **Loaders:** NeoForge, MinecraftForge/Forge, Fabric · **Java:** 21 for 1.21.x, 25 for 26.x
- Mod id: `neropower` · package `za.co.neroland.neropower`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

Minecraft 1.20 through 1.20.6 runs on Fabric, 1.20 through 1.20.4 and 1.20.6 on Forge,
and 1.20.4 through 1.20.6 on NeoForge. NeoForge 1.20.5 uses loader 20.5.21-beta.
Minecraft 1.20 through 1.20.4 uses Java 17. Later 1.20 builds use Java 21.

```sh
./gradlew :fabric:26.2:build          # one cell
./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build \
          :forge:26.1.2:build :forge:26.2:build :forge:26.3:build \
          :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build   # the 26.x cells
# 1.21.x cells: :<loader>:1.21.1:build ... :<loader>:1.21.11:build (see mc_versions in gradle.properties)
```

See [`AGENTS.md`](AGENTS.md) / [`CLAUDE.md`](CLAUDE.md) for agent and contributor context, and
[`PRIVACY.md`](PRIVACY.md) for what the mod stores. Anonymous crash reporting (Sentry, EU servers)
is **on by default** and **opt-out**: set `telemetryEnabled=false` in `config/neropower.properties`.

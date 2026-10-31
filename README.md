# NeroPower

> Part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core**. NeroPower is an
> **optional add-on to [NeroTech](https://github.com/Neroland/nerotech)**: it adds power *depth* on top
> of NeroTech's machine framework and generators.

**Status:** in development — version `0.2.0-alpha.1`. First release will be `0.2.0-beta.1`.
See [`PLAN-0.1.0.md`](PLAN-0.1.0.md) for the staged plan and [`docs/DESIGN.md`](docs/DESIGN.md) for the
design record.

## What it adds

- **Fission reactor** with a real fuel cycle — burn-up curves, control rods, neutron poisoning,
  reprocessing.
- **Staged failure model** — stable → warning → unstable → failure, always telegraphed, claim-aware,
  terrain damage off by default on servers.
- **Tiered, pooled battery banks** with buffer / priority-source modes.
- **Beamed power** — line-of-sight transmitters, receivers and relays with distance loss and
  owner-checked linking.
- **RTG** and **Stirling gradient** generators — output that is a function of state, not a flat number.
- **Quest chapter** — a NeroQuests quest pack, *Reactor Log* (14 quests), ships inside the jar and
  appears in the quest book when NeroQuests is installed. NeroQuests is **optional**: without it the
  pack is inert data and NeroPower has no dependency on it.

## Requirements

- **Neroland Core** 1.17.0+ and **NeroTech** 0.7.1-beta.1+ (both required; version floors are in `gradle.properties`).
- **Minecraft:** 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also 1.21.7), 1.21.10 (also 1.21.9), 1.21.11, 26.1.2, 26.2 and 26.3
  · **Loaders:** NeoForge, MinecraftForge/Forge, Fabric (27 cells) · **Java:** 21 for 1.21.x, 25 for 26.x
- **Java:** 25 · Mod id: `neropower` · package `za.co.neroland.neropower`

## Layout

The build is the repo root, with a flattened cross-loader structure driven by Stonecutter:

- `common/` — shared, loader-agnostic source spliced into every loader node
- `fabric/` — Fabric Loom
- `forge/` — ForgeGradle
- `neoforge/` — ModDevGradle
- `stonecutter.gradle` — the real root build script; `build.gradle` is intentionally inert

## Building

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

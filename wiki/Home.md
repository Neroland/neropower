# NeroPower Wiki

Player- and contributor-facing documentation for **NeroPower**, an optional power-depth add-on to
**NeroTech**, built on **Neroland Core**.

> **Version:** `0.7.0` ("Power Station"). Keep this wiki updated alongside code changes (see the
> repository's contributor notes).

Minecraft 1.20 through 26.3 runs on Fabric, every line but 1.20.5 on Forge, and 1.20.2 onwards
on NeoForge (1.21.3, 26.1 and 26.1.1 included). NeoForge 1.20.3, 1.20.5, 26.1 and 26.1.1 use beta loaders.
Minecraft 1.20 through 1.20.4 uses Java 17. Later 1.20 builds use Java 21.

All builds require **NeroTech 0.10.1** or newer (below 1.0) and
**Neroland Core 1.25.0** or newer (below 2.0). Its machines share NeroTech's GUI, side configuration, presets, upgrades and thermal model;
the pages below cover only what NeroPower adds on top. In game, the **Power Guide** (the Guide button
above any NeroPower machine screen) walks through the same ground step by step.

## Contents

### Generating power

- [Fission Reactor](Fission-Reactor.md) — the multiblock in three sizes, fuel rods, burn-up, control rods, poisoning, the Control Computer, SCRAM
- [Fuel Lines](Fuel-Lines.md) — the standard line, moderators, Pallurite, the Breeder Blanket, the Braided Fuel Rod
- [Steam Plant](Steam-Plant.md) — steam mode, the steam turbine, the Steam Condenser, the Hydrogen Fuel Cell and Betavoltaic Cell
- [RTG and Stirling](RTG-and-Stirling.md) — the two stateful generators
- [Failure Stages](Failure-Stages.md) — how machines warn, degrade and fail; server safety config

### Handling waste and radiation

- [Waste](Waste.md) — hot spent rods, the Spent Fuel Rack, reprocessing, the Vitrifier, the Dry Cask
- [Radiation](Radiation.md) — the optional world system: sources, effects, shielding, the setting

### Storing and moving power

- [Battery Banks](Battery-Banks.md) — cell tiers, pooling, buffer vs priority mode
- [Storage](Storage.md) — the Surge Bank, Momentum Wheel, Storage Ring and Power Cells
- [Beamed Power](Beamed-Power.md) — transmitters, receivers, relays, range and loss, linking rules
- [Beam Grids](Beam-Grids.md) — the Beam Splitter, Phased Array Emitter, Circuit Breaker, Load Bank and Power Monitor

### Reference

- [Tools](Tools.md) — Geiger Counter, Dosimeter Badge, Beam Alignment Scope, SCRAM Key, the two upgrade modules
- [Commands](Commands.md) — `/neropower gallery` (with `tour` and `clear`) and `/neropower budget`
- [Configuration](Configuration.md) — every `neropower.properties` key
- [Link Module](Link-Module.md) — what the NeroLink companion app can see and do (link schema 2)
- [Privacy](Privacy.md) — what the mod stores about players and how to erase it
- [Quest Chapter](Quest-Chapter.md) — the *Reactor Log* quests shown when NeroQuests is installed

### Other integrations

- **Maps** — with a Neroland map installed, your own loaded reactors and beam endpoints near you, and live scorch zones, appear as map layers. Other players' machines and machines with no owner never do.
- **Fluids** — Steam, Exhaust Steam and Heavy Water travel in pipes and tanks (tags `c:steam`, `neropower:exhaust_steam`, `c:heavy_water`); they cannot be placed in the world.
- **Trophies** — the milestone records listed on [Quest Chapter](Quest-Chapter.md) are what NeroTrophies keys its NeroPower trophies on.

## See also

- [Privacy](Privacy.md) — what NeroPower stores and how to erase it (links on to the full disclosure)
- [NeroTech](https://github.com/Neroland/nerotech) — the machine framework this mod extends

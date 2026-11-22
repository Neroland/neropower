# NeroPower Wiki

Player- and contributor-facing documentation for **NeroPower**, an optional power-depth add-on to
**NeroTech**, built on **Neroland Core**.

> **Version:** `0.4.0` ("Power Station"). Keep this wiki updated alongside code changes (see the
> repository's contributor notes).

NeroPower requires **NeroTech 0.8.0** or newer and **Neroland Core 1.20.0** or newer. It runs on
Minecraft 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3, on NeoForge, Forge
and Fabric. Its machines share NeroTech's GUI, side configuration, presets, upgrades and thermal model;
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

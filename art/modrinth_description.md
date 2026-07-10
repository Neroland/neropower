# NeroPower

**Generate it, cool it, risk it — deep, decision-driven power generation for the Neroland universe, where every reactor is a system you have to run.**

NeroPower is the **focused, deep energy** member of the Neroland ecosystem — a dedicated generation mod that supplies the full arc of power (solar, wind, geothermal, nuclear and fusion) plus battery banks, wireless distribution, and the systems that make high-end generators interesting to run. It deliberately ships **no machines that consume power** — that is NeroTech's job — so it slots in as a deep companion that extends NeroTech's industry with a real power layer.

Built on **Neroland Core**, so its shared Nero Energy surface (`c:` energy tags, unified power type over the Forge/NeoForge energy capability), progression gates, side-config, claims and config framework are all shared with the rest of the lineup. NeroPower is designed to be **extractable**: the design docs describe it as possibly living inside NeroTech until the power layer is deep enough to stand on its own, so treat it as a focused add-on that is adjacent to NeroTech by nature.

*(Planned — in design; not yet released.)*

---

## What you'll generate

1. **Solar Panels.** Sky-facing generators whose output scales with sky-light access, time of day, weather, and a planet/atmosphere modifier — dramatically stronger off-atmosphere and in orbit. Tiered basic → advanced variants raise base rate and cap, and batched ticking keeps big fields cheap.
2. **Wind Turbines.** Output scales with exposed height and biome wind class — high, open placements produce well; sheltered or low builds barely turn. A natural complement to solar's intermittency, and dead in a vacuum.
3. **Geothermal Generators.** Tap ambient heat — lava, configured heat blocks, volcanic biomes — for steady, non-intermittent early-mid power where the geology allows.
4. **Nuclear Reactors.** Multiblock mid-game generators that burn fuel for high sustained output while producing heat you must manage. Inadequate cooling drives them toward instability and overload; gated behind a Core progression milestone.
5. **Fusion Reactors.** The late-game, space-tier endgame source, built to power colonies and heavy automation. Needs a startup charge and a steady plasma/fuel feed, runs hot, and leans heavily on active cooling.
6. **Battery Banks.** Storage blocks that buffer energy and smooth the gap between intermittent generation and steady demand — configurable capacity and insert/extract limits, usable as a buffer or a prioritised source.
7. **Wireless Power.** Point-to-point and networked transmission with no cables, for spanning terrain or claim-respecting links between bases. Linking validates both endpoints against Core's claim/permission API so nobody siphons across protected boundaries.

## Systems that make it deep

- 🌍 **Planet-based efficiency** — a data-driven, per-dimension modifier: solar climbs off-atmosphere and in orbit, wind dies in vacuum, geothermal follows the body's geology. This is what makes *where* you build a strategic decision, tying power straight into the space-progression arc. Fully toggleable for packs wanting flat behaviour.
- ❄️ **Reactor cooling** — passive heat sinks and coolant-block adjacency plus active pumped loops that consume fluid and/or power. More output demands more cooling; cooling design is the core mid-late power-engineering puzzle.
- 💥 **Overload explosions** — a staged, telegraphed failure model: warning → unstable (efficiency penalty, particles, sound, alarms) → failure (explosion, optional radiation/scorch zone for nuclear). Never a silent surprise, and server config controls thresholds, blast radius, terrain damage, and whether failure happens at all — always respecting claim/protection systems.
- ⚡ **Portable power** — generation talks only to Core's Nero Energy surface, so power is fungible across the whole ecosystem and with external energy mods rather than forking a parallel currency.

## Privacy (POPIA / GDPR)

NeroPower stores **no personal data** — generators, reactors, batteries and wireless nodes are keyed by block and dimension, never by player. Any optional crash telemetry is **anonymous and opt-out**, carrying only version strings (mod / MC / loader / OS / Java) — never names, UUIDs, IPs or world data. Nothing to erase, and nothing routed through the shared data-erasure hook because nothing personal is ever stored.

## Why it fits the ecosystem

- 🧩 **Built on Neroland Core** — one energy type, one side-config vocabulary, one progression arc, shared `c:` tags and config framework. NeroPower ships in its own creative tab.
- 🔌 **Shares one Nero Energy network** — every generator, battery and wireless node exposes Core's energy capability on its faces, so power flows straight into NeroTech machines and NeroLogistics grids as one shared network.
- 🤝 **Interoperates, never hard-depends** — all external interop (Create, AE2, Mekanism, Ad Astra) goes through Core's common compat tags, and **Energized Power** exchanges FE live today. With optional siblings absent, environmental generation still plays fully standalone.
- 🚀 **Synergy across the lineup** — NeroTech supplies the consumers, NeroLogistics supplies the cabling its wireless layer complements, and Nerospace/Ad Astra feed the planet data behind planet-based efficiency.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2** and **26.2**.

## Requirements & compatibility

- **Requires [Neroland Core](https://modrinth.com/mod/nerolandcore)** — install it alongside NeroPower (it loads first).
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroPower by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/neropower) and the [GitHub repository](https://github.com/Neroland/neropower). Full terms: [LICENSE](https://github.com/Neroland/neropower/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/neropower/wiki)** — every generator, reactor and system documented.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/neropower/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/neropower/blob/main/CHANGELOG.md)**
- 🔥 **[Also on CurseForge](https://www.curseforge.com/minecraft/mc-mods/neropower)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*

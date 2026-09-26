# NeroPower

**Run it, cool it, risk it — power depth for NeroTech, where every reactor is a system you have to operate.**

NeroPower is an **optional add-on to [NeroTech](https://www.curseforge.com/minecraft/mc-mods/nerotech)**. NeroTech supplies the machine framework and its everyday generators; NeroPower adds the deep end of the power arc on top: a fission reactor with a real fuel cycle, a staged failure model that never fails silently, tiered pooled battery banks, authenticated line-of-sight beamed power, and generators whose output is a function of their state rather than a flat number. Nothing in NeroTech moves or changes id — a NeroTech world without NeroPower keeps working, and adding NeroPower later just adds blocks.

Built on **Neroland Core**, so it speaks the ecosystem's one Nero Energy type, shares NeroTech's GUI, side configuration, presets and thermal model, and takes its config from Core's shared framework.

*(In development — first release 0.1.0-beta.1.)*

---

## What it adds

1. **Fission Reactor.** A 3×3×3 or 5×5×5 casing shell around a Fission Core burning Fuel Rods that age as they burn — output and heat follow a burn-up curve, Control Rod Assemblies inside the shell throttle it, running flat out with no control rod builds neutron poison until the core drops into the xenon pit, and spent rods reprocess in NeroTech's Chemical Processor. Remote SCRAM through the NeroLink app.
2. **Staged failure model.** Stable → Warning → Unstable → Failure, one rung at a time with a minimum dwell, hysteresis, alarms, sound, particles and owner alerts on every step. Unstable throttles output; Failure destroys the reactor. Server config sets every threshold, caps the blast radius, keeps terrain damage **off by default on dedicated servers**, respects spawn protection and unbreakable blocks, and can pin the ladder below Failure entirely.
3. **Battery Banks.** Basic, Advanced and Elite Battery Cells, and a Battery Bank Controller that pools connected cells into one store with summed capacity and pooled I/O. Buffer mode feeds everything; Priority Source mode only tops up machines running low. Storage never sloshes charge into other storage.
4. **Beamed Power.** Beam Transmitters, Receivers and Relays link with NeroTech's Configurator and move energy in a straight line of sight — distance loss per block, blocked by anything solid, hazardous to stand in, relayed around terrain. Linking requires the player to be allowed to interact with both ends, and only the linking player or an operator can unlink. An Orbital Receiver can optionally be beamed to from another dimension.
5. **Radioisotope Generator.** Fuel-free power from one Isotope Pellet that halves with every half-life until the pellet is spent. No heat, no running pollution, the same on every world — built for airless bases (just don't throw the spent pellet in lava).
6. **Stirling Generator.** Turns the temperature gradient of the hottest adjacent machine into energy, drawing heat out of it as it goes — a coolant that pays a dividend, stronger with a cold face of water, ice or a Radiator.

## Privacy (POPIA / GDPR)

NeroPower stores exactly one thing about players: the **UUID of the player who linked a Beam Transmitter or Relay**, kept in that block's own world data so that only they (or an operator) can unlink it. It is never a name, never sent to clients, never logged. Erase it at any time with Neroland Core's `/neroland data eraseme`. Every other block — reactors, batteries, receivers, generators — records no player at all, and NeroPower carries no analytics. It does send **anonymous crash reports** for errors in its own code (Sentry, EU servers; no names, UUIDs, IPs or world data), which you can turn off with `telemetryEnabled=false` in `config/neropower.properties`.

## Why it fits the ecosystem

- 🧩 **An add-on, not a fork** — every NeroPower machine is a NeroTech machine: same GUI tabs, side config, presets, upgrade modules and thermal model, shown in NeroTech's Analytics Terminal and the NeroLink companion app.
- 🔌 **One Nero Energy network** — energy flows straight into NeroTech machines, NeroTech Battery Banks and anything else on Core's energy surface, including FE bridging through Core.
- 🤝 **Interoperates, never hard-depends** — no third-party mod is required; interop goes through Core's common tags. Nerospace is optional (space dimensions unlock cross-dimension beams).
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **26.1.2**, **26.2** and **26.3**.

## Requirements & compatibility

- **Requires [Neroland Core](https://www.curseforge.com/minecraft/mc-mods/nerolandcore) and [NeroTech](https://www.curseforge.com/minecraft/mc-mods/nerotech)** — install both alongside NeroPower (they load first).
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroPower by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/neropower) and the [GitHub repository](https://github.com/Neroland/neropower). Full terms: [LICENSE](https://github.com/Neroland/neropower/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/neropower/wiki)** — the reactor, failure stages, banks, beams, generators and every config key.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/neropower/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/neropower/blob/main/CHANGELOG.md)**
- 🟢 **[Also on Modrinth](https://modrinth.com/mod/neropower)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*

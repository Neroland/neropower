[![NeroLink App — Beta](https://img.shields.io/badge/NeroLink_App-Now_in_Beta-60d4e8?style=for-the-badge)](https://nerolandmc.net/nerolink/#beta) [![Explore the Neroland ecosystem](https://img.shields.io/badge/Explore-The_Neroland_Ecosystem-1a5a6c?style=for-the-badge)](https://nerolandmc.net/ecosystem/)

> 📱 **NeroLink App Beta — your Neroland world on your phone.** Check energy, alerts and machines live, claim quest rewards and search your storage without logging in. **[Join the beta at nerolandmc.net →](https://nerolandmc.net/nerolink/#beta)**
>
> 🌌 **Explore the Neroland ecosystem.** See how NeroPower fits together with the rest of the Nero mods — every mod, wiki and changelog in one place. **[View the ecosystem at nerolandmc.net →](https://nerolandmc.net/ecosystem/)** · [NeroPower on the website](https://nerolandmc.net/mods/neropower/)

---

# NeroPower

**Run it, cool it, risk it — power depth for NeroTech, where every reactor is a system you have to operate.**

NeroPower is an **optional add-on to [NeroTech](https://www.curseforge.com/minecraft/mc-mods/nerotech)**. NeroTech supplies the machine framework and its everyday generators; NeroPower adds the deep end of the power arc on top: a fission reactor with a real fuel cycle, a staged failure model that never fails silently, tiered pooled battery banks, authenticated line-of-sight beamed power, and generators whose output is a function of their state rather than a flat number. Nothing in NeroTech moves or changes id — a NeroTech world without NeroPower keeps working, and adding NeroPower later just adds blocks.

Built on **Neroland Core**, so it speaks the ecosystem's one Nero Energy type, shares NeroTech's GUI, side configuration, presets and thermal model, and takes its config from Core's shared framework.

*(Version 0.4.0, "Power Station".)*

---

## What it adds

1. **Fission Reactor.** A 3×3×3, 5×5×5 or 7×7×7 casing shell around a Fission Core burning Fuel Rods that age as they burn. Output and heat follow a burn-up curve; Control Rod Assemblies (and Europium ones) throttle it; Graphite Moderators and Moderator Tanks raise it, so the layout of the core matters; running flat out builds neutron poison until the core drops into the xenon pit. A Reactor Control Computer gives redstone and comparator signals and an automatic SCRAM, and a SCRAM Key stops a reactor by hand.
2. **A second fuel line.** Breeder Blankets turn Pallurite Pellets into Kindled Pellets while the reactor runs, and those make the Braided Fuel Rod: more power, longer life, if the core is built for it. Pallurite comes from Nerospace crystals, or from tuff at four times the cost without Nerospace. A Fuel Economizer stretches every rod.
3. **The steam plant.** Switch a reactor to steam mode with a Reactor Steam Port and build a steam turbine around it: a 5×5 tube 4 to 16 blocks long, with three tiers of Vane Sets, stators wound with NeroTech coils, a rotor with real inertia (spin-up, overspeed trip, coast-down) and a Governor Module. A Steam Condenser closes the loop, air-cooled where the air allows and cryo- or radiator-cooled in a vacuum. With a condenser, steam gets up to 1.5 times more out of every rod than direct mode. Ports move fluid to the block they touch, so a plant runs with no pipe mod at all.
4. **Waste with uses and costs.** Burnt rods leave the core hot and cool in a Spent Fuel Rack under water. A NeroTech Centrifuge recovers fuel and Fission Residue; residue becomes Beta Capsules for the Betavoltaic Cell; the rest is melted into inert glass in the Vitrifier or kept in a Dry Cask. Nothing is a dead end and nothing can be voided for free.
5. **Storage with character.** Basic, Advanced and Elite Battery Cells pooled by a Battery Bank Controller, a Surge Bank that joins the pool as its fast tier, a Momentum Wheel you can watch spin, and the Storage Ring: a superconducting square of coils, the largest store in the ecosystem, which quenches if it runs out of cryo coolant. Three Power Cells carry energy in your inventory and charge in battery slots or near NeroTech's Induction Charger.
6. **Beamed power and grids.** Beam Transmitters, Receivers and Relays link with NeroTech's Configurator and move energy in a straight line of sight. The Beam Splitter shares one beam over four through a focus lens, and the Phased Array Emitter reaches 384 blocks and serves several receivers in turn. A Circuit Breaker trips on overload, a Load Bank burns power to test a supply, and a Power Monitor graphs any store. The Beam Alignment Scope shows what blocks a beam.
7. **Radiation, if you want it.** Failed reactors and racks, unshielded casks and dropped rods contaminate their chunk; contamination fades, never spreads and never damages blocks. Shielding Blocks and Shielding Glass seal waste in, the Geiger Counter and Dosimeter Badge measure it, Decontaminant cleans it, and shielded armour (including lined Nerospace suits) protects you. It is a server setting, on in singleplayer and off on dedicated servers by default.
8. **A staged failure model.** Stable → Warning → Unstable → Failure, one rung at a time with a minimum dwell, hysteresis, alarms, sound and particles. Reactors, turbines, Storage Rings, fuel racks and breakers all use it. Server config sets every threshold, caps the blast radius, keeps terrain damage **off by default on dedicated servers**, respects spawn protection, and can pin the ladder below Failure entirely.
9. **Radioisotope and Stirling generators.** Fuel-free power from an Isotope Pellet that halves with every half-life, and a Stirling Generator that turns a hot neighbour's heat into power.
10. **A Hydrogen Fuel Cell** for quiet, steady power from hydrogen and oxygen (or the air on a breathable world).
11. **An in-game Power Guide, quests and a gallery.** The Guide button above any machine screen opens step-by-step chapters. The *Reactor Log* quest chapter (with NeroQuests) covers the whole plant, and `/neropower gallery` builds a walkable power station in creative, with a guided tour.

## Privacy (POPIA / GDPR)

NeroPower stores exactly one thing about players: the **UUID of the player who linked a Beam Transmitter or Relay**, kept in that block's own world data so that only they (or an operator) can unlink it. It is never a name, never sent to clients, never logged. Erase it at any time with Neroland Core's `/neroland data eraseme`. Version 0.4.0 adds **no player-keyed store**: radiation keeps no dose per player, and the Geiger and dosimeter readings live on the items only. The NeroLink app sees only machines near you, with no coordinates and no owners, and can control remotely only machines you own. NeroPower sends **anonymous crash reports** for errors in its own code (Sentry, EU servers; no names, UUIDs, IPs, positions or world data). On by default — opt out any time by setting `telemetryEnabled=false` in `config/neropower.properties`.

## Why it fits the ecosystem

- 🧩 **An add-on, not a fork** — every NeroPower machine is a NeroTech machine: same GUI tabs, side config, presets, upgrade modules and thermal model, shown in NeroTech's Analytics Terminal and the NeroLink companion app.
- 🔌 **One Nero Energy network** — energy flows straight into NeroTech machines, NeroTech Battery Banks and anything else on Core's energy surface, including FE bridging through Core.
- 🤝 **Interoperates, never hard-depends** — no third-party mod is required; interop goes through Core's common tags. Nerospace is optional: it makes the Pallurite line cheaper, lines suits against radiation and unlocks cross-dimension beams.
- 🧱 **Cross-loader** — NeoForge, Forge and Fabric on Minecraft **1.21.1** to **1.21.11**, **26.1.2**, **26.2** and **26.3**.

## Requirements & compatibility

- **Requires NeroTech 0.8.0 or newer and Neroland Core 1.20.0 or newer:** [Neroland Core](https://www.curseforge.com/minecraft/mc-mods/nerolandcore) and [NeroTech](https://www.curseforge.com/minecraft/mc-mods/nerotech)** — install both alongside NeroPower (they load first).
- **Modpacks are allowed and encouraged** — any platform, no need to ask. Use the official files and credit *NeroPower by Neroland* with links to the [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/neropower) and the [GitHub repository](https://github.com/Neroland/neropower). Full terms: [LICENSE](https://github.com/Neroland/neropower/blob/main/LICENSE).

## Links

- 📖 **[Wiki](https://github.com/Neroland/neropower/wiki)** — the reactor, fuel lines, steam plant, waste, storage, beam grids, radiation, tools, commands and every config key.
- 💬 **[Discord](https://discord.gg/ArPXvYUzJG)** — chat, help, and sneak peeks.
- 🐞 **[Issues](https://github.com/Neroland/neropower/issues)** — bug reports and feature requests.
- 🗒️ **[Changelog](https://github.com/Neroland/neropower/blob/main/CHANGELOG.md)**

---

*Created by Neroland. The project logo was made with the help of AI image tools; in-game art is generated by the project's own tooling and refined by hand.*

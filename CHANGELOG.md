# Changelog

All notable changes to **NeroPower** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-11-22

### Added

- Power Cells work with any Neroland charger or tool through Neroland Core's item-energy lookup.
- Land claimed through Neroland Core's claim checks (for example by NeroColonies or NeroSecurity) is now protected from NeroPower: radiation and reactor blasts leave it alone, and players can only link, configure or decontaminate there where the claim allows.
- `/neroland data exportme` now includes NeroPower's part of your data.
- Every Nerospace suit in `#nerospace:suits/radiation_capable` now shields from radiation, and every item in `#nerospace:radioactive` counts as a fertile source, so new Nerospace items join without a NeroPower update.

### Changed

- Requires Neroland Core 1.20.1 and NeroTech 0.8.1.
- NeroPower now reads NeroTech's settings, Configurator, pollution and privacy choices through NeroTech's public API instead of its internals.

### Fixed

- Crash reports now use NeroPower's own Sentry client. Before, whichever Neroland mod started last took over the shared one and NeroPower's reports could be dropped.

## [0.4.0] - 2026-11-22

The "Power Station" release: a steam plant, a second fuel line, a waste back end, new storage tiers, beam grids, optional radiation, link schema 2 and a gallery power station. Existing reactors, banks and beam links from 0.3.x load unchanged, and no block, item, fluid, recipe or tag id was removed or renamed.

### Added

- Hovering a gauge now shows what it is, how much, and the rate.
- Three new fluids for pipes and tanks: Steam, Exhaust Steam and Heavy Water (tagged `c:steam`, `neropower:exhaust_steam` and `c:heavy_water`). They cannot be placed in the world and have no bucket. Their textures are placeholders for now.
- `/neropower budget start [ticks]` and `/neropower budget` (operators): how many milliseconds per tick NeroPower's machines take, per machine type and in total, over a sampling window (10 seconds by default). Nothing is measured while no window is open.
- **Steam plant:** the Turbine Helm, Shroud, Spindle, Stator and Steam Port build a 5 × 5 steam turbine 4 to 16 blocks long, with Steel, Cindersteel and Superconducting Vane Sets and a Governor Module. The Steam Condenser turns exhaust steam back into water.
- **Fission:** Graphite Moderator, Moderator Tank, Breeder Blanket, Europium Control Rod Assembly, Reactor Steam Port (steam mode) and Reactor Control Computer (redstone, comparator and automatic SCRAM). Reactors can now be 7³. New fuel line: Pallurite Dust, Pallurite Pellet, Kindled Pellet and the Braided Fuel Rod, plus the Fuel Economizer module. Burnt rods now leave the core as Hot Spent Fuel Rods.
- **Waste:** Spent Fuel Rack (cools hot rods under water), Vitrifier (turns final waste into inert Vitrified Waste) and Dry Cask. New items: Fission Residue, Vitrified Waste, Beta Capsule, Spent Beta Capsule.
- **Generation:** Hydrogen Fuel Cell and Betavoltaic Cell.
- **Storage:** Surge Bank, Momentum Wheel, the Storage Ring (Controller and Coil) and three Power Cells. Power Cells charge in battery cells, Surge Banks and near an Induction Charger, and offer item energy to other mods on NeoForge and Forge.
- **Beam and grid:** Beam Splitter, Phased Array Emitter, Circuit Breaker, Load Bank and Power Monitor.
- **Radiation:** Shielding Block, Shielding Glass, Geiger Counter (with an on-screen reading; the `geigerHudCorner` setting picks its corner), Dosimeter Badge and Decontaminant. Failing reactors, failing fuel racks, casks and rods dropped on the ground contaminate their chunk.
- **Tools:** the Beam Alignment Scope shows a beam's line of sight and the first block in its way while you hold it and look at an emitter. The SCRAM Key SCRAMs a reactor when you use it on the core or its shell. It only works within reach.
- Sounds with subtitles and particles for the new machines (steam, quench vapour, fuel rack bubbles, radiation motes). Recipe-book unlocks for every new recipe.
- Chunk contamination storage for the coming radiation system, behind the new `radiationMode` setting (`auto` by default: on in singleplayer and LAN, off on dedicated servers). It records only a level per chunk, decays over time and is capped per dimension (`radiationDecayPermillePerMinute`, `radiationMaxChunksPerDimension`, `radiationDecayBudgetPerTick`). No player data.
- **NeroLink (link schema 2):** a new `turbines` section, a public `catalogue` section (every block by family and role, the fuel lines as step lists, and the storage tiers with this server's capacity and I/O, the same for every player), and a `turbine_trip` action that latches a turbine's trip like an overspeed would. Plant entries carry more readings (reactor mode, size, rods and output; turbine speed, output and steam; stored energy and I/O for every store kind). Failure events go to online players near the machine as live notices; only a recorded owner under NeroTech's per-player attribution can get a push, and only when a machine becomes unstable or starts to fail. At most five events per machine a minute.
- **Power Guide:** an in-game guide to the steam plant, both fuel lines, waste, storage, beam grids, grid tools, generation (Hydrogen Fuel Cell and Betavoltaic Cell) and radiation, each step naming the next. Open it with the Guide button above any NeroPower machine screen; it opens at that block's chapter. Status-line fix hovers point to it. No new item, block or key binding.
- Steam plants run with no pipe mod: Reactor Steam Ports and Turbine Steam Ports are faces onto their structure's shared tanks, and every 10 ticks they move fluid into the block they touch. A reactor port pushes steam into an abutting Turbine Steam Port inlet and draws water or cryo coolant from what it touches; an exhaust port pushes exhaust steam into an abutting Steam Condenser; the condenser pushes water into an abutting Reactor Steam Port or tank. Faces follow the side configuration (default I/O with auto-eject and auto-input on); set a face to Push to force it.
- Config `portTransferInterval` (default 10 ticks, 1–200): how often ports and condensers exchange fluid with the blocks they touch.
- **Gallery power station:** `/neropower gallery` now builds a walkable station with every NeroPower block running: a 7×7×7 reactor in steam mode feeding a turbine and condenser, a breeder reactor, the waste line ending in a cask yard behind shielding, a storage hall (Surge Bank, Momentum Wheel, Storage Ring), a breaker and monitor wall, a Load Bank test bench and a beam yard with a splitter and a phased array. `/neropower gallery tour [stop]` steps through 16 stops with captions; `/neropower gallery clear` removes everything, including any contamination in the chunks it covers. Creative only. Gallery machines are exhibits: they record no owner or operator, credit no milestone, quest or trophy, send no alert or NeroLink event, give off no radiation and never run a destructive failure action.
- **Reactor Log quests:** Turbine Spin-up, First Breed, Into Glass (first Vitrified Waste) and Full Ring (a fully charged Storage Ring), with new milestone records `neropower:turbine_spinup`, `neropower:first_breed`, `neropower:first_vitrified` and `neropower:smes_charged`.

### Changed

- Requires NeroTech 0.8.0 or newer with Neroland Core 1.20.0 or newer. The Fabric manifest now also caps NeroTech below 1.0, as the NeoForge and Forge manifests already did.
- Each gauge tooltip now appears exactly once: NeroTech's machine screen shows the energy, heat and progress tooltips, and NeroPower adds only its own readouts. Storage screens read stored energy and capacity past one billion NE correctly.
- The reactor shell is now checked only when something changes (a casing or rod assembly placed or removed anywhere in the shell, the core's neighbours, loading, opening the screen) instead of every two seconds. Reactors, banks and beam links from 0.3.x load unchanged.
- A machine saved mid-failure now loads one step back (unstable instead of failing), so a world never runs a failure on its first tick after loading.
- NeroPower no longer reads NeroTech's internal block registry: the Stirling Generator's cold face and the gallery find NeroTech's blocks by id, and the storage push guard uses the `neropower:energy_storages` block tag (NeroTech's Battery Bank is an optional entry).
- The Chemical Processor's spent-rod reprocessing still yields only the reprocessed pellet, because its recipe format allows a single result. The Centrifuge route gives 2 pellets plus 1 Fission Residue.
- Fluid names show in other mods' tank and pipe screens on Fabric too.
- **NeroLink (link schema 2):** no section entry carries a coordinate, a dimension or a beam's length any more, and no event names a dimension. Every entry carries an opaque handle `h` instead, valid for 30 seconds for the player who asked. Actions take that handle and nothing else; an older app that still sends a position gets `update_required`. Actions are limited to 10 a minute per player on top of the bridge's own limit, and the target must still be in reach when the action runs. Handles and rate windows are kept in memory only and are dropped when a player is erased.
- A reactor's steam ports share one coolant tank and one steam tank (capacity = ports × `reactorSteamPortTankMb`): water piped into any port can be boiled and leave as steam through any other. A turbine's inlet ports share one steam tank and its exhaust ports one exhaust tank.
- On an automatic face, steam and exhaust steam are pushed only into blocks that do not also take water, so a relay tank between a condenser and a reactor port is never filled with steam.
- Balance: a reactor in steam mode now raises steam in step with its burn rate, so Eco, Overdrive and Speed modules no longer change how much steam a rod makes over its life (Eco used to give a third more).
- Balance: the turbine's NE per mB of steam is capped so steam mode can never yield more than twice direct mode per rod, whatever the config. The default (8) is unchanged.
- Balance: Speed modules and the Overdrive preset make the Stirling Generator draw heat faster; they no longer multiply the NE it makes from that heat. (A Load Bank feeding a Stirling could make energy from nothing.)
- Balance: standalone Pallurite Dust from tuff now takes 80 NE/t (was 160), four times the offworld route's energy and time per pellet.
- Balance: the Hydrogen Fuel Cell now gives 60 NE per unit of hydrogen (was 200), and `fuelCellNePerHydrogenUnit` is capped at 70. Splitting water in NeroTech's Electrolyzer and burning the hydrogen always loses energy, even with the Electrolyzer fully upgraded.
- A Fuel Rod or Braided Fuel Rod that has burnt at all now leaves the core as a Hot Spent Fuel Rod, however it leaves (taken by hand, shift-clicked, swapped or dropped from the screen, or the core broken). Pull rods only when spent: a partly used rod comes out spent and goes through the rack, cooling and the waste route. Fresh rods come out unchanged, and rods already in inventories are left as they are.
- Machines now say what they are doing in the world: the Turbine Steam Port shows when it is flowing and when it is starved or blocked, a leaking Dry Cask (radioactive waste with a face not shielded) opens its vents, and a Betavoltaic Cell with a spent capsule shows it (a full energy buffer no longer looks spent).
- Turbine, reactor, storage ring, breaker, load bank, beam, fuel cell, condenser, vitrifier and cask sounds now play: the turbine spools up, hums by speed, overspeeds, trips and winds down; the Reactor Control Computer sounds its alarm while lit; the Storage Ring hums and warns while warming. Loops follow the machine and stop when it stops or is removed; nothing extra is sent from the server.
- The twelve parts with no screen (moderators, Breeder Blanket, Europium Control Rod Assembly, Reactor and Turbine Steam Ports, Turbine Shroud, Spindle and Stator, Storage Ring Coil, Shielding Block and Glass) say what they do, or what to use on them, in their item tooltip.
- Screens: a Fission Core at warning or unstable says "Running hot" or "Failing" instead of "Idle"; the Fission Core and Reactor Control Computer status lines explain themselves and their fix on hover, as do the storage, grid and beam screens; a Turbine Helm with the right shape but no working vane slice or no wound stators says which, instead of "Turbine not formed"; a hot Spent Fuel Rack says cooling is paused; a half-loaded Vitrifier says it needs waste and sand; a Storage Ring warming for lack of coolant says so.
- **NeroLink:** plant sections no longer say how far away a machine is. Each entry carries only a band: `near` (about two chunks) or `in_range`. Each player gets at most 6 fresh answers per section a minute; a faster read gets the last answer again. Acknowledge, SCRAM and turbine trip work remotely only on machines you own; any other machine answers `NOT_OWNER` and is used in person.
- Version label: `0.4.0`, a stable version with no pre-release suffix.
- Documentation: new wiki pages Steam Plant, Fuel Lines, Waste, Storage, Beam Grids, Radiation, Tools, Commands and Link Module; Configuration now lists every key; Fission Reactor, Failure Stages, Battery Banks, Beamed Power, RTG and Stirling, Quest Chapter, Privacy and Home brought up to date.
- Milestone records (`criticality`, `bank_capacity`, `first_breed`, `turbine_spinup`, `smes_charged`, `first_vitrified`) now also go to the player operating the machine at that moment, if they are online, so trophies and quests work without a recorded owner. Criticality is recorded when a formed reactor actually burns fuel. Nothing about that player is saved.

### Privacy

- Privacy: the NeroLink inbox alert for an unstable or failed reactor no longer contains the machine's coordinates. It names the machine type and stage, and its id no longer encodes the position.
- Privacy: crash reports now mask UUIDs and block positions in error text, including text from other mods and from Minecraft itself.
- Privacy: `PRIVACY.md` covers radiation (no dose stored, contamination per chunk, Geiger and dosimeter readings on the item only), operator credit at the moment, link schema 2 (no coordinates, opaque handles, rate limit, catalogue) and how to access the beam link owner.
- Privacy: NeroMaps no longer shows other players' or unowned reactors and beam endpoints. The "Power nearby" layer is removed and only your own machines are drawn.
- Privacy: beam link owner erasure now finishes in chunks unloaded for any length of time. Tombstones are salted hashes and never the UUID, and links made after an erasure keep their owner (new `LinkDay` save key).
- Privacy: NeroLink plant sections decide reach on the chunk grid, show only a coarse `band` (`near` / `in_range`), and allow 6 fresh reads per section a minute.
- Privacy: remote SCRAM, turbine trip and alarm acknowledge now need a machine you own. Use unowned machines in person.
- Privacy: an unlinked Beam Transmitter / Relay is scoped to its NeroTech owner.
- Privacy: nearby clients no longer receive a beam's absolute target beyond 64 blocks or in another dimension.
- Privacy: the Geiger Counter ignores fully shielded sources, and only your own held counter animates.
- Privacy: crash-report scrubbing now also masks player and world names and decimal positions.
- Privacy: `/neropower gallery` beams record no player.

### Fixed

- Beam damage now hits only what stands on the line between the two endpoints (half a block either side), at most 16 creatures a pass, for the Beam Transmitter, Relay, Beam Splitter and Phased Array Emitter alike. It used to hit everything inside the box the two ends span, which for a long diagonal beam was a large area.
- The Hydrogen Fuel Cell now pushes its water into touching blocks that take water and pulls hydrogen and oxygen from touching blocks that hold them (every `portTransferInterval` ticks, by its side config). It used to stop for good after 80 units unless a water-using machine touched it.
- Every reactor failure now adds the radiation burst (with radiation on), not only a cryo-cooled one.
- Radiation no longer contaminates a protected chunk or harms anything standing on protected ground (NeroPower's protection check; vanilla spawn protection until a claims mod implements it). Dropped rods and residue add at most 40 to one chunk per pass.
- `/neropower gallery clear` no longer clears around the player when the server does not remember the gallery (after a restart): it finds the gallery from its own machines or refuses. It removes only the blocks and labels the gallery placed, leaves blocks and machines built there since, and no longer clears contamination. Only one gallery per dimension; a remembered gallery from another world is never used, and the build message gives the box size.
- The Fission Core, Reactor Control Computer and Turbine Helm screens now check protection before acting on a button, as the grid and beam screens do.
- A steam-mode reactor no longer stalls once its port's water is used up when no pipe mod is installed (the port could not take water and give steam on its one face).
- A `beamDamage` of `NaN` in the config reads as the default.
- The Turbine Steam Port's model now loads in game (its block state was missing the `status` property its art names).

### Not in this release

- **Rad Cloak:** not added. Nerospace suits already protect once lined, and any armour can join the `neropower:radiation_shielding` tag.
- **Loss-free beam relay upgrade:** declined. Superconducting parts go into the Phased Array Emitter instead, which loses a third as much per block.
- **Reprocessing residue from the Chemical Processor:** its spent-rod recipe is unchanged (one Reprocessed Pellet, no residue), because the recipe format allows one result. Use the Centrifuge route for residue.
- **Helium-3:** no fission use; it stays NeroTech's fusion fuel.

### Seams (built locally until a sibling offers a shared version)

- **Item energy:** Power Cells use NeroPower's own portable-energy seam, with standard item energy on NeoForge and Forge only. Fabric gets item energy when Neroland Core ships a shared item-energy lookup.
- **Multiblocks:** the reactor, turbine and Storage Ring use NeroPower's own multiblock base; NeroTech has no public one yet.
- **Guide:** the Power Guide has its own viewer until NeroTech's guide accepts chapters from other mods.
- **Gauges:** large values (past one billion NE) use NeroPower's own wide gauge data on top of NeroTech's gauge tail.
- **Data export:** Neroland Core has no shared export command yet, so a copy of a player's beam link owner records is made by the server operator on request.
- **Radiation-capable suits and Pallurite sources:** Nerospace items are listed by id as optional tag entries until Nerospace ships tags for them.
- **Fluid tags:** NeroPower declares `c:steam` and `c:heavy_water` itself.
- **NeroTech classes:** NeroPower still compiles against a few NeroTech machine classes outside its public API (the machine base, its configuration, the Configurator and pollution); these follow NeroTech's 0.8.x line.

### Dependencies

- Updated NeroTech from 0.7.1-beta.1 to 0.8.0 (NeroTech 0.8.0 must be published before this release can be).
- Updated Neroland Core from 1.19.0 to 1.20.0.

## [0.3.2] - 2026-11-21

### Changed

- Transitioning to stable versions: the alpha/beta label is dropped from the version number, so this release is **0.3.2** (previously 0.3.2-beta.1). Nothing else changes with the version label.

## [0.3.2-beta.1] - 2026-11-16

### Added

- Map layers for Neroland Core's map API (drawn by NeroMaps and the companion app): your own reactors and beam endpoints loaded within 128 blocks of you, unowned ones within 64 blocks, and live scorch zones within 128 blocks. Another player's machines never show, and no layer names a player.

### Changed

- The Fabric metadata now caps the Neroland Core dependency below 2.0.0, matching Forge and NeoForge.

### Dependencies

- Updated Neroland Core from 1.17.6 to 1.19.0 (map layer API).

## [0.3.1-beta.1] - 2026-10-31

### Changed

- Release uploads now target CurseForge and GitHub Releases only.
- Removed automatic project description and gallery uploads.
- Renamed the website overview source to `art/description.md` and refreshed public documentation links.

### Dependencies

- Updated Neroland Core from 1.17.0 to 1.17.6.
- Updated Fabric Loader from 0.19.5 to 0.20.1.
- Updated Fabric API (Minecraft 1.21.1) from 0.116.17+1.21.1 to 0.116.18+1.21.1.
- Updated Fabric API (Minecraft 26.3) from 0.162.0+26.3 to 0.166.2+26.3.
- Updated NeoForge (Minecraft 1.21.1) from 21.1.265 to 21.1.266.
- Updated NeoForge (Minecraft 26.1.2) from 26.1.2.116 to 26.1.2.126.
- Updated NeoForge (Minecraft 26.2) from 26.2.0.90 to 26.2.0.93.
- Updated NeoForge (Minecraft 26.3) from 26.3.0.76 to 26.3.0.103.
- Updated Forge (Minecraft 1.21.1) from 1.21.1-52.1.20 to 1.21.1-52.1.21.
- Updated Forge (Minecraft 1.21.11) from 1.21.11-61.2.4 to 1.21.11-61.2.5.
- Updated Forge (Minecraft 26.1.2) from 26.1.2-64.1.4 to 26.1.2-64.1.6.
- Updated Forge (Minecraft 26.2) from 26.2-65.1.4 to 26.2-65.1.6.
- Updated Forge (Minecraft 26.3) from 26.3-66.0.11 to 26.3-66.1.8.
- Updated JEI (Minecraft 26.1.2) from 29.47.0.109 to 29.53.0.115.
- Updated JEI (Minecraft 26.2) from 30.42.0.236 to 30.48.0.244.
- Updated JEI (Minecraft 26.3) from 31.12.0.65 to 31.17.0.113.
- Updated the EMI port for Minecraft 26.3 to build+62 on Fabric and NeoForge.
- Updated Stonecutter from 0.9.8 to 0.10.
- Updated the Gradle distribution from 9.8.1 to 9.9.1.
- Updated the Sentry Java SDK from 8.60.0 to 8.64.0.

## [0.3.0-beta.1] - 2026-10-30

Minecraft 1.21.x support. Requires **Neroland Core 1.17.0** and **NeroTech 0.7.1-beta.1**.

### Added

- **Builds for Minecraft 1.21.x** on NeoForge, Fabric and Forge: 1.21.1, 1.21.4, 1.21.5, 1.21.8 (also
  runs on 1.21.7), 1.21.10 (also runs on 1.21.9) and 1.21.11, next to the existing 26.1.2, 26.2 and 26.3
  builds. The 1.21.x builds need Java 21. Fission, beam power, the RTG and Stirling generators, battery
  banks and their animations work the same on every version.

### Changed

- Now requires Neroland Core 1.17.0 or later.
- Now requires NeroTech 0.7.1-beta.1 or later (the first NeroTech release with 1.21.x builds).

### Fixed

- Crafting recipes now unlock in the recipe book the first time a player picks up one of their ingredients (19 recipe-unlock advancements, generated by `tools/gen_recipe_unlocks.py`; run it again after adding a recipe, `--check` verifies).
- **Block and loot drops work as intended on Minecraft 26.3** (NeoForge, Fabric and Forge). Minecraft
  26.3 changed the loot-table file format and silently ignores the old one, so conditions and drop
  modifiers (explosion survival, door/slab block-state checks, drop counts, random chances) were skipped.
  The 26.3 builds now ship every loot table in the new format. 26.1.2 and 26.2 were not affected.

## [0.2.1-beta.1] - 2026-10-17

Item-highlight tags and a dependency refresh. Still **requires Neroland Core 1.15.0** and NeroTech
0.6.0-beta.1. No id change, no config change.

### Added

- **Item-highlight tags.** NeroPower's machines (`neroland:highlight/machines` — battery bank
  controller and cells, beam transmitter / receiver / relay, orbital receiver, fission casing / core,
  control-rod assembly, RTG and Stirling generator) and materials (`neroland:highlight/materials` —
  fuel, control and spent rods, uranium / isotope / reprocessed / spent pellets) now get Neroland Core's
  coloured slot borders.

### Fixed

- The NeroLink discovery entry reported NeroPower as `0.1.0-alpha.1` (also in 0.2.0-alpha.1); it now
  reports the real mod version.

### Changed

- Build and loader dependencies refreshed: NeoForge 26.1.2.116 / 26.2.0.90 / 26.3.0.76, Forge
  64.1.4 / 65.1.4 / 66.0.11, JEI 29.47 / 30.42 / 31.12, Fabric API 0.162.0+26.3, Sentry 8.60.0,
  Gradle 9.8.1, ModDevGradle 2.0.148, Fabric Loom 1.18.4. Neroland Core and NeroTech are now resolved
  only from their own GitHub Packages repositories.

## [0.2.0-alpha.1] - 2026-10-11

NeroQuests quest pack, milestone gates and the fuel-rod hook. **Requires Neroland Core 1.15.0** and
NeroTech 0.6.0-beta.1. No id change; one new config key. (Supersedes the never-published 2026-09-24 entry of the same number below.)

### Added

- **Quest pack.** Ships a NeroQuests quest pack — chapter `neropower:reactor_log` "Reactor Log",
  14 quests (13 player, 1 server world-log) — that appears in the quest book when NeroQuests is
  installed; inert otherwise, no dependency. The pack is data + lang inside the jar
  (`data/neropower/neroquests/`); `common/src/test/.../QuestPackTest` checks it against the pack
  rules on every build. *First Burn* rewards a fresh Fuel Rod through the `mod_hook` below.
- **Milestone record gates** (`data/neropower/neroland_gates/`, player scope, Neroland Core's store):
  `neropower:criticality` (a formed reactor burns fuel, for its owner), `neropower:beam_linked` (the
  player links a Beam Transmitter) and `neropower:bank_capacity` (a bank's pooled capacity reaches the
  new `bankCapacityMilestone` config, default 32 000 000 NE, for the controller's owner). Opened
  through `progression.MilestoneGates` for online or offline owners; an unowned machine or an owner
  who opted out of NeroTech's attribution records nothing. They are records only — nothing checks
  them for access.
- **`neropower:fission_output` channel.** The first time a reactor burns fuel in a dimension, one
  rising crossing (scope = the dimension id, value = threshold = 1) is published on Core's threshold
  bus, attributed to `neropower`; the dimensions that have burned persist in the new
  `neropower:fission_log` saved data (dimension ids only).
- **`neropower:hook/fuel_rod_refill` hook gate** with a `progression.QuestHooks` listener on Core's
  progression events: hands the completing player one fresh `neropower:fuel_rod`, on their next login
  when the hook opened while they were offline, then closes the gate to re-arm it. A new item every
  time; a burning rod's components are never touched. Lang key `message.neropower.fuel_rod_refill`.
- New config key `bankCapacityMilestone` (1 000 – 1 000 000 000 000 NE).
- **Item tags** for other mods to key on: `neropower:pellets` (uranium, reprocessed and isotope
  pellets), `neropower:battery_cells` (basic, advanced and elite cells) and `neropower:spent_fuel`
  (spent fuel rod, spent isotope pellet). Every entry is `"required": false`.
- The partly burnt `neropower:fuel_rod`, whose burn state lives in item components, is added to the
  optional `neroeconomy:never_commodity` item tag (`"replace": false`) so an economy mod never prices it
  by id. The tag is ignored when no such mod is installed.

### Changed

- **Requires Neroland Core 1.15.0** (`ProgressionGates.open(server, uuid, gate)` for offline
  owners and sourced threshold crossings) and **NeroTech 0.6.0-beta.1**, whose
  `nerotech:machine_failure` scope is `machineId@dimension` with no coordinates — NeroPower's
  failure crossings follow it with no code change, since NeroTech builds that scope.
- **NeroLink `failure` events: `scope` is now `machineId@dimension`** (e.g.
  `neropower:fission_core@minecraft:overworld`) — the packed block position is gone. App clients or
  bridges that parsed a position out of the scope must stop doing so; `machineId`, `stage` and
  `rising` are unchanged. Recipients (the owner, or online players within 128 blocks of an unowned
  machine) are now resolved from the machine itself when it changes stage, rather than from the
  machine-failure channel, so delivery keeps working with NeroTech 0.6.0+, whose
  `nerotech:machine_failure` scope no longer carries a position.

### Privacy

- The four new gates are rows in Core's progression store (gate id against the player's UUID,
  written only through `ProgressionGates`, never logged or broadcast) and are covered, exported and
  erased by Core's own privacy disclosure; `PRIVACY.md` and `wiki/Privacy.md` describe them. The
  `fission_output` scope is a dimension id, never a player or a position.
- Failure events no longer carry a machine's coordinates: on a shared server a base's location
  identifies its builder. The position is used only in process, for the proximity check.

## [0.1.0-beta.1] — 2026-09-26

NeroPower returns as an **optional add-on that depends on NeroTech** (`0.4.0-beta.1` or later) and
Neroland Core (`1.13.0` or later). Nothing moves out of NeroTech and no NeroTech id changes; a
NeroTech world loads with or without NeroPower. Nine loader × version jars: NeoForge, Forge and
Fabric on Minecraft 26.1.2, 26.2 and 26.3.

### Added

- Visual pass: 32×32 NeroTech-style art (alloy body, amber "power" emissive ramp, cyan family tell, hazard stripes on
  fission) generated by `tools/gen_textures.py` + `gen_animations.py`; custom non-cube block models; block-entity
  renderers (fission core glow/alarm strobe, battery charge strips, beam dishes + rendered beam, RTG fins, Stirling
  flywheel); animated front faces.
- `/neropower gallery` / `gallery clear` (op, creative): a showcase plaza with a live 5³ fission reactor, pooled bank,
  beam link, RTG and Stirling.
- Block items now carry `item.neropower.*` names (creative tab showed raw keys).

- **Fission Reactor** — `fission_core` controller in a hollow 3×3×3 or 5×5×5 `fission_casing`
  shell (validated on neighbour change and every 40 ticks, never loading chunks), with
  `control_rod_assembly` blocks inside the shell as the throttle. Four rod slots (two usable in a
  3³ shell, four in a 5³); `fuel_rod` items carry their burn-up in a `neropower:burnup` data
  component and follow a configurable piecewise-linear output/heat curve
  (`fissionBurnupCurve`, default `0=1200,200=1000,600=800,1000=300`), turning into `spent_fuel_rod`
  at 100 %. Neutron poisoning stalls a core run hot (the xenon pit, with a recovery margin).
  Fuel chain: `uranium_pellet` → `fuel_rod` → `spent_fuel_rod` → `reprocessed_pellet` (NeroTech
  Chemical Processor) → `uranium_pellet`. Failure explodes the shell (radius shell + 2, capped) and
  optionally leaves a **scorch zone** persisted through Core's `SavedDataRecovery` for
  `fissionScorchDays` real days (no player data). **SCRAM** (60 s at the control-rod floor)
  through NeroLink.
- **Staged failure ladder** shared by every NeroPower reactor — `STABLE → WARNING → UNSTABLE →
  FAILURE` driven by heat as permille of capacity, one rung per tick, minimum dwell, hysteresis,
  output penalty while `UNSTABLE`, and a pluggable failure action (`RemoveOnly`, `Explode`, scorch).
  Every transition is telegraphed: NeroTech's `nerotech:machine_failure` event, the block's `alarm`
  state, escalating sound and particles, and a Core link alert to the recorded owner (WARN on
  `UNSTABLE`, CRITICAL on `FAILURE`). `overloadEnabled=false` pins the ladder at `UNSTABLE`.
- **Protection-aware blasts** — `terrainDamageMode` (`on` / `off` / `auto`, auto = off on a
  dedicated server), `failureRadiusCap`, and a `ProtectionCheck` seam (default: spawn protection
  plus `mayInteract`) that shrinks a blast to the multiblock's footprint, or to damage-only, when any
  block in range is protected or unbreakable.
- **Battery banks** — `battery_cell_basic` / `_advanced` / `_elite` (capacity and I/O per tier in
  config, a 0–4 `charge` block-state overlay) and `battery_bank_controller`, which flood-fills
  connected cells within `bankScanRadius` into one pooled energy surface (capacity = sum,
  I/O = min(cell I/O) × cells, capped by `bankMaxIoCap`) with round-robin distribution. Modes
  `BUFFER` and `PRIORITY_SOURCE` (feeds only neighbours below `bankPriorityThresholdPermille`),
  cycled by sneak-use with an empty hand. A slosh guard stops storage blocks feeding other storage.
- **Beamed power** — `beam_transmitter`, `beam_receiver`, `beam_relay` and `orbital_receiver`.
  Straight-line links up to `beamRange` with `beamLossPermillePerBlock` distance loss, a voxel
  line-of-sight walk every `beamCheckIntervalTicks`, `beamDamage` to entities in the beam, relay
  chains up to `beamMaxHops` with loop detection, and optional cross-dimension hops to an Orbital
  Receiver in a Core space dimension (`beamCrossDimension`, flat `beamOrbitalHopLossPermille`).
  Linking uses NeroTech's Configurator (two clicks, 30 s pending session); the acting player must
  be able to interact with both endpoints and only the linking player or an operator may unlink.
  Chunks are never force-loaded.
- **Radioisotope Generator** — `radioisotope_generator` burning one `isotope_pellet` whose output
  halves every `rtgHalfLifeDays` (exact integer decay table) until `rtgCutoffPermille`, then
  ejects a `spent_isotope_pellet`. No heat, no failure ladder, no planet dependence; a spent
  pellet destroyed as a dropped item vents a pollution burst (see below).
- **Stirling Generator** — `stirling_generator` draws heat from the hottest adjacent
  NeroTech-family machine through the public `PowerMachine.extractHeat` (never below ambient) and
  converts it at `stirlingNePerHeatUnit`, with a cold-face bonus (water, ice, snow or a Radiator)
  and a halved gradient without one.
- **NeroLink module** — snapshot sections for beams, reactors, banks and generators; owner-scoped
  for machines with a recorded owner, proximity-scoped (same dimension, 128 blocks, online only)
  otherwise; actions *acknowledge alarm* and *SCRAM*, re-checked server side (owner-only on an
  owned reactor, proximity + protection on an unowned one — see below).
- **Player-data erasure** — a `PlayerDataEraser` registered with Core clears beam link-owner UUIDs
  on loaded blocks at once and on unloaded ones at their next load (30-day pending list), verified
  by Core's `ErasureConformance` fixture. `PRIVACY.md` and `wiki/Privacy.md` document the single
  stored field and the opt-out crash reporting below.
- **Anonymous crash reporting (opt-out)** — errors in NeroPower code go to NeroPower's own Sentry
  project (EU ingest): no names, UUIDs, IPs, coordinates or world data, home paths scrubbed, at most
  10 reports per session. Client-local `telemetryEnabled=false` turns it off before the SDK starts.
- **Content** — survival recipes for every block and item (consuming NeroTech's Machine Frame,
  Nero Coil and Circuit Board), lang, blockstates, models, placeholder textures, loot tables,
  pickaxe tags, a `neropower` advancement tree (root plus one per feature), and a creative tab.
- **Docs** — wiki pages for the Fission Reactor, Failure Stages, Battery Banks, Beamed Power,
  RTG and Stirling, Configuration and Privacy; `docs/DESIGN.md` (design record) and
  `docs/COMPAT.md` (recipe-viewer notes).
- **Tooling** — `tools/check_content.py` audits registrations against resources, recipes, tags,
  advancements and lang keys; `ContentCompletenessTest` runs the lang/resource subset in every CI
  cell; unit tests for the failure ladder, fission maths, poison model, bank pooling, beam maths and
  path walk, RTG decay, Stirling draw, link scoping and erasure conformance.

- **Reprocessing yield** — a new NeroTech Chemical Processor recipe takes a (partially burnt)
  `fuel_rod` to **2** `reprocessed_pellet`, so pulling a rod early recovers more than a spent rod
  (1) while staying below the 3 uranium pellets a fresh rod costs (2 reprocessed = 1 uranium).
  Locked by `ReprocessingYieldTest`.
- **Uranium from ore** — `uranium_pellet_from_ore`: 1 `#c:raw_materials/uranium` → 2 Uranium
  Pellets. No mod in the Neroland ecosystem ships uranium yet, so NeroPower declares the tag empty
  (`data/c/tags/item/raw_materials/uranium.json`, `replace: false`) and the recipe becomes usable as
  soon as any mod adds raw uranium to the tag; the glowstone recipe stays as the fallback.
- **Spent Isotope Pellet pollution** — `SpentIsotopePelletItem`: destroying a spent pellet as a
  dropped item (fire, lava, cactus, explosion) records `rtgSpentPelletPollution` (default 200) per
  pellet into NeroTech's regional pollution, never attributed to a player. Water does not destroy
  item entities, so the burst triggers on destruction only.
- **Planet efficiency** — `planetEfficiencyEnabled` now does something: the Stirling Generator's
  cold-face bonus scales with the local ambient from NeroTech's `PlanetApi.ambientAt`
  (`bonus × (1000 + clamp(−ambient × 5, 0, 1000)) ⁄ 1000`; ambient −80 → +40 %).
- **Config key** `fissionPoisonRodThresholdPermille` (default 900) and `rtgSpentPelletPollution`
  (default 200).
- **Energized Power** declared as an optional dependency / suggestion on all three loaders, as
  NeroTech declares it.
- **Pure helpers + tests** named by the plan: `failure/TerrainPolicy` (`TerrainPolicyTest`),
  `BankModeTest`, `PoolMath.shouldSkip` (`SloshGuardTest`), `BeamMath.clear` / `blocks`
  (`BeamLineOfSightTest`), `beam/BeamLinkRules` (`BeamLinkRulesTest`), poison accumulation cases in
  `FissionMathTest`, link rules in `NeroPowerLinkModuleTest`, pellet burst and planet bonus cases
  in `RtgMathTest` / `StirlingMathTest`; `ContentCompletenessTest` also checks block models and a
  recipe per block and item.
- `docs/IDEAS.md` — the post-0.1.0 generator ideas, one paragraph each.

### Fixed

- **Privacy: beam link owner no longer reaches clients.** NeroTech's base update tag is the full
  custom save, so the linking player's UUID (`LinkOwnerMost` / `LinkOwnerLeast`) was synced to
  every nearby client. `BeamTransmitterBlockEntity.getUpdateTag` (inherited by the relay) now strips
  both; the UUID lives in the world save only. `PRIVACY.md` / `wiki/Privacy.md` corrected.
- **Poison only builds when the core runs hot.** Poison accumulated at any control-rod setting, so
  a rodded core still fell into the xenon pit. It now builds only while the effective control-rod
  factor is above `fissionPoisonRodThresholdPermille` (with the defaults one Control Rod Assembly,
  factor 850, keeps a core steady and poison decays).
- **NeroLink failure events are targeted.** They were broadcast (machine + coordinates) to every
  link client; they now go to the machine's owner only, or — for an unowned machine — to online
  players within 128 blocks of it, and are dropped when nobody qualifies.

### Changed

- **NeroLink actions on unowned reactors.** `scram` and `acknowledge_alarm` refused every unowned
  reactor (reactors are only owned when NeroTech's opt-in attribution is on). An owned reactor is
  still its owner's alone; an unowned one now accepts them from an online player in its dimension,
  within 128 blocks, who passes `Protection.mayInteract` (`LinkScope.mayAct`).
- The Battery Bank Controller advancement now continues NeroTech's chain (parent
  `nerotech:battery_bank`) instead of hanging off `neropower:root`.
- Docs: `wiki/Home.md` states the current version (`0.1.0-alpha.1`) and links the wiki's own
  Privacy page; `tools/gradle-mcp` renamed from the copied `nerospace` ids to `neropower`.
- Config is NeroPower's own `config/neropower.properties` on Core's `ConfigSchema` (`neropower`),
  server-authoritative and hot-reloadable; it never reads `nerotech.properties`. See
  `wiki/Configuration.md` for every key.
- Every energy block-entity type registers with NeroTech's `MachineTypeRegistry` so NeroTech's
  loader glue attaches energy / item capabilities on all three loaders.
- Dependabot now targets `main` (the old `dev` branch does not exist).
- Docs rewritten for the add-on status; the store descriptions describe the
  add-on and its privacy posture.

### Removed

- Dead `syncModels` / `genAssets` Gradle tasks whose scripts never existed.
- The whole retired standalone design (solar, wind, geothermal, nuclear, fusion generators): those
  live in NeroTech; NeroPower ships only what NeroTech lacks.

## [0.2.0-alpha.1] - 2026-09-24 (never published — upload failed, no tag)

EMI compatibility. No gameplay, id, tag or config change.

### Added

- **EMI support.** NeroPower adds no recipes of its own, so nothing needed a plugin. The build now
  compiles against the community EMI Unofficial Port (Unstable), the only EMI build for Minecraft 26.x,
  and dev clients load it with `-PwithEmi` (default runs stay JEI-only). EMI stays optional.

## [0.1.0-alpha.1] - 2026-09-20 (never published — upload failed, no tag)

Minecraft **26.3** support. No gameplay, id, tag or config change.

### Added

- **Minecraft 26.3** as a new Stonecutter node on every loader — NeoForge `26.3.0.7-beta`,
  Forge `26.3-66.0.2` and Fabric (fabric-api `0.161.0+26.3`, NeoForm `26.3-1`) — built alongside
  26.1.2 and 26.2, so every release now ships **nine** loader × version jars.

### Changed

- VS Code run/debug configurations (`.vscode/launch.json`, `.vscode/tasks.json`) gain the three
  26.3 cells; the "Build all" task now builds all nine.
- CI (`multiloader.yml`, `publish.yml`) builds, attaches and publishes the 26.3 jars.
- JEI pins moved to the newest published builds on each Minecraft version: `29.40.0.101` (26.1.2), `30.35.0.223` (26.2) and `31.3.0.18` (26.3). Compile-time API only — JEI remains a soft dependency and the shipped jar gains no hard requirement.

### 26.3 port notes

- Build: the shared `common/` Java source is now preprocessed by Stonecutter for every non-active node (`stonecutterProcessCommon`), so common code can carry `//? if >=26.3 {` blocks, and `common/src/main/resources-<mc>` overlay folders are merged over the shared resources for matching nodes (`mergeCommonResources`). The active node still compiles the raw `common/` folder.
- Build plugins aligned with Neroland Core: ModDevGradle `2.0.147` (the older 2.0.141 cannot set up NeoForge 26.3), ForgeGradle `7.0.40`, Stonecutter `0.9.8`.

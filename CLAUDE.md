# Project context for AI coding agents — neropower

> `CLAUDE.md` and `AGENTS.md` are kept byte-identical; update both together.

## The mod

- **NeroPower** — part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core** and an
  **optional add-on to NeroTech** (`../nerotech`). NeroTech owns the machine framework (thermal model,
  presets, GUI stack, capability wiring) and its seven generators; NeroPower ships only the power depth
  NeroTech lacks — fission with a fuel cycle, a staged failure model, tiered pooled storage, beamed
  transmission, RTG and Stirling generators. It never renames or moves a NeroTech block.
- Design record: `docs/DESIGN.md`. What each release shipped: `CHANGELOG.md`. A change is done only
  when all 27 cells and their `ecjCheck` build green.
- Mod id: **`neropower`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.neropower`. Author: **Neroland**.
- Version: **0.3.2** (`mod_version`; the next release is **0.4.0**, stable label, no pre-release suffix).
- Dependencies: **Neroland Core 1.20.0** (`nerolandcore_version`) and **NeroTech 0.8.0**
  (`nerotech_version`). Loader ranges are derived from the compiled pins, never hardcoded: NeoForge/Forge
  `[${nerolandcoreVersion},2.0)` and `[${nerotechVersion},1.0)`; Fabric `>=${nerolandcore_version} <2.0.0`
  and `>=${nerotech_version} <1.0.0`. Both are real `implementation` dependencies resolved from mavenLocal
  (`./gradlew publishToMavenLocal` in `../neroland-core` / `../nerotech`) or GitHub Packages — never
  reflection. NeroTech 0.8.0 resolves from mavenLocal only until it is published to GitHub Packages; a
  NeroPower release needs it there first.
- Nerospace is never a build dependency: planet traits arrive through NeroTech's `api.PlanetApi` /
  `api.Atmosphere`, Nerospace resources only through optional tags.
- Targets **MC 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 AND 26.3** on **NeoForge,
  MinecraftForge/Forge, and Fabric** → **27 cells** (the source is written for 26.x; older lines use `//?` blocks and the
  `legacyRenames` list in `stonecutter.gradle`). **Java 25** for 26.x, **Java 21** for 1.21.x. Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).

## Working rules

- **Keep responses concise and direct** — minimal verbosity, minimal formatting.
- **POPIA & GDPR**: keep all logging/telemetry/scripts compliant — only public version strings, never
  personal data; minimise data, set retention limits, support export/erasure and opt-out.
- **NEVER commit or push automatically.** Leave changes **staged**; the developer reviews and commits
  with native git (the source of truth).
- **Use relative paths only** — never hard-code machine-specific absolute paths in committed files.
- **Never run commands against production databases.** Treat any DB command as illustrative.

## Repo layout — flattened cross-loader build

- **The build IS the repo root.** `common/` (shared source spliced into every node), `neoforge/`
  (ModDevGradle), `forge/` (ForgeGradle), `fabric/` (Fabric Loom). Root build files: `settings.gradle`,
  `stonecutter.gradle` (the REAL root build script — Stonecutter repoints `buildFileName` here; the root
  `build.gradle` is inert), `gradle.properties`, `gradlew`, `gradle/`.
- **Version/loader axis = Stonecutter.** Each loader×MC is a real node `:<loader>:<mc>`
  for every `<loader>` in `fabric neoforge forge` and every `<mc>` in
  `1.21.1 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2 26.3` — 27 nodes. `common` is NOT a node — its
  source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins live in
  `gradle.properties` as `*_version_<mc>` keys; `mc_versions=1.21.1,1.21.4,1.21.5,1.21.8,1.21.10,1.21.11,26.1.2,26.2,26.3`.
- **Version-specific code in `common/`.** Non-active nodes run `common/` through Stonecutter (`stonecutterProcessCommon`), so shared code uses the same `//? if >=26.3 {` blocks as the loader `src/` trees. Keep the files in the vcsVersion state, and never put a `*/` inside a disabled block. Datapack files whose format differs by version go in `common/src/main/resources-<mc>/`, which is merged over `common/src/main/resources` for every node at or above `<mc>` (`mergeCommonResources`).

## Build & verify

- Never run plain `./gradlew build`; build per cell, e.g. `./gradlew :neoforge:26.3:build`. Compile the
  26.x cells first, then 1.21.11 down to 1.21.1:
  - 26.x: `:neoforge:26.3:build :forge:26.3:build :fabric:26.3:build :neoforge:26.2:build :forge:26.2:build
    :fabric:26.2:build :neoforge:26.1.2:build :forge:26.1.2:build :fabric:26.1.2:build`
  - 1.21.x: the same three loaders for `1.21.11`, `1.21.10`, `1.21.8`, `1.21.5`, `1.21.4`, `1.21.1`.
- Static analysis: every node has `ecjCheck`, e.g. `./gradlew :neoforge:26.3:ecjCheck` (the VS Code
  Problems panel, via `tools/ecj.prefs`). The task only FAILS on errors; run it on all 27 nodes at a gate.
- `build` runs the unit tests (`common/src/test`) in each cell.
- A Cowork agent sandbox cannot decompile Minecraft — run builds natively (or via the local gradle MCP)
  on the developer's machine.
- **Verify the cells build before marking a task done.** Never sign off on an uncompiled change.

## Conventions (cross-loader)

- **Resources are HAND-AUTHORED in `common/src/main/resources`** — the multiloader does not run datagen.
  Validate JSON after edits.
- **Platform seams via ServiceLoader (no Architectury).** Put loader-agnostic code in `common/`; ship one
  impl per loader plus a `META-INF/services` entry. Keep `common/` free of `net.neoforged.*` /
  `net.fabricmc.*` / `net.minecraftforge.*` imports.
- Loader entry points: `NeroPowerFabric` (+ `NeroPowerFabricClient`), `NeroPowerForge`,
  `NeroPowerNeoForge` — each calls `NeroPowerCommon.init()` during construction.
- **Registration (0.4.0).** Each feature package registers its own blocks, items, block entities and
  menus in a holder `registry/content/<Family>Content.java` (`init()` is called from
  `NeroPowerCommon.init()`, after `ModRegistries.init()` and before `ModBlockEntities.registerMachineTypes()`;
  feature configs class-load before `NeroPowerConfig.load()`). `ModBlockEntities` aggregates every holder's
  `energyTypes()` / `itemTypes()` / `fluidTypes()` / `gasTypes()` into NeroTech's `MachineTypeRegistry`,
  which is what NeroTech's loader code reads on all three loaders (`registry/MachineWiringTest` guards both
  this and the `SideConfig` default presets). Shared holders: `registry/ModSounds` (every sound event except
  the three radiation ones in `RadiationContent`), `registry/ModParticles`, `registry/content/ToolContent`
  (Beam Alignment Scope, SCRAM Key). The creative order is `ModItems.creativeOrder()`.
- **Client wiring (0.4.0)** is loader-neutral in `client/NeroPowerClient` (screens through a `ScreenSink`,
  the client tick, the Geiger HUD layer, `PowerItemProperties` for the Geiger/dosimeter item frames),
  `client/ClientBlockEntityRenderers` (BERs) and `client/particle/PowerParticles`; each loader's client
  setup only binds them. Power cells get item energy from `neoforge/NeoForgeItemEnergy` and
  `forge/ForgeItemEnergy` (none on Fabric). Fabric registers fluid names via `FluidVariantAttributes` and
  render layers below 26.1 (26.x picks the layer from the sprites).
- **`status` block state.** Machines with state art but no renderer (`reactor_steam_port`,
  `reactor_control_computer`, `dry_cask`, `betavoltaic_cell`) override `hasStatus()`; their block entity
  calls `NeroPowerMachineBlock.applyStatus(...)`, which writes only when the value changes.
- NeoForge/Forge debug tasks use `-PneropowerDebug`; Fabric Loom honours Gradle `--debug-jvm`.

## IDE (VS Code) run & debug

- Workspace: **`neropower.code-workspace`** (single-root `"."`). Import the Stonecutter nodes as **static
  Eclipse projects**: `./gradlew eclipse` (live Buildship/Loom import is disabled —
  `java.import.gradle.enabled=false`). Re-run `./gradlew eclipse` after dependency changes, then reload
  VS Code. Per-node Eclipse project names are `neropower-<loader>-<mc>`.
- **Run/Debug** a cell from `tasks.json` / `launch.json`.

## Wiki — keep `wiki/` updated

- This mod has its own **dedicated wiki** in `wiki/` at the repo root: the player- and
  contributor-facing docs for NeroPower (features, blocks/items, machines, progression, recipes, FAQ).
- **Whenever you add, change, or remove a feature, update `wiki/` in the same change** — treat the
  wiki as part of "done"; code without a matching wiki update is incomplete.
- One page per topic; keep `wiki/Home.md` as the index that links every page, with relative links
  between pages. Validate Markdown via the gradle MCP `markdown_check` (honours `.markdownlint.json`).
- The wiki is **per-mod** and **PUBLIC** (it is pushed to the GitHub wiki). Document only NeroPower
  here. Never reference private planning material, `PLAN-*.md`, audits or any private repository from
  `wiki/`. Before pushing, check with
  `grep -rniE 'neroland-mc-[e]|BUILD-[P]ROMPT|AUDIT|PROGRESS[.]md|RUNTIME-[S]CRIPT|decision[s]/|REWORK-[P]ROMPT|PLAN-[0]|SMOKE-[T]EST|MANUAL-|[m]odrinth' wiki/`
  (must be empty).

## Release pipeline

- CI is the ecosystem standard and is not modified for the rebuild: `publish.yml` runs on a push to
  `main` that changes `gradle.properties` and publishes any `mod_version` that has no `v<version>` tag
  yet. Only bump `mod_version` to a new value when that version is meant to ship.
- Recipes use **natural progression only** (NeroTech intermediates such as Machine Frame, Nero Coil and
  Circuit Board). No Core progression gates or material-milestone gates lock anything. The player-scope
  gates under `data/neropower/neroland_gates/` (`progression.MilestoneGates`, `hook/*`) are **records**
  for the quest pack only — never check one for access.
- **Quest pack** (`data/neropower/neroquests/`, chapter `neropower:reactor_log`) is inert data read by
  NeroQuests when installed. **No dependency on NeroQuests in any form** (no import, manifest entry or
  compileOnly); `progression.QuestHooks` listens on Core's progression events only.
- Telemetry (Stage 9): opt-out, anonymous Sentry crash reports via `telemetry/NeroPowerTelemetry` (EU ingest,
  NeroPower's own DSN). Keep it PII-free, and update `PRIVACY.md` **before** widening what it sends.

## Safety rules specific to this mod

- Failures (reactor explosions) go through `failure.FailureController` + `protection.ProtectionCheck`;
  terrain damage is OFF by default on dedicated servers; radius is capped by config.
- Transmission links need the linking player to be able to interact with BOTH endpoints. Never use
  `getNearestPlayer` for authorisation.
- Every `SavedData.get()` goes through Core's `data.SavedDataRecovery` (shipped in the pinned Core). Today's
  stores: `data.NeroPowerErasureState`, `fission.ScorchZones`, `progression.FissionWorldLog`,
  `radiation.ContaminationState` (per dimension; chunk levels only, no player data).
- Multiblocks go through `multiblock/` (pure `*Shape` classes over a `Probe`, the in-memory
  `MultiblockIndex`, `MultiblockPartBlock` for BE-less members): validate on change, never on a cadence.
- Gauge tooltips: NeroTech's `MachineScreen` is the only emitter. NeroPower screens extend
  `client.PowerMachineScreen` and add readouts through `readoutTooltip`; never emit a tooltip directly.
  `menu.GaugeTail` carries only what NeroTech's tail cannot (extra value, wide energy), indexed through
  the object, never by absolute slot.
- Fluids go through `fluid.NeroPowerFluids` and the `platform.IFluidPlatform` seam (one loader fluid type
  per fluid, built lazily). Never name another mod's block through its registry classes: use
  `registry.ForeignBlocks` (by id) or a tag.
- Any player identifier stored (link owner UUID) is minimal, retention-limited and cleared by the
  `PlayerDataEraser` registered with Core's `data.PlayerDataErasure`. See `PRIVACY.md`.
- Link module (`link.NeroPowerLinkModule`) scopes by owner UUID where recorded, else by proximity to the
  online requester. Never a server-wide roster.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.

## Translation workflow

English is the source language for new and changed text. Community translations are intended to be contributed through Weblate; other-language translations are not required when implementing a feature. Preserve existing translations. This supersedes older instructions that require translating every change.

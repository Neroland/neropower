# Project context for AI coding agents — neropower

> This and `CLAUDE.md` are kept identical; update both together.

## The mod

- **NeroPower** — part of the Neroland sci-fi Minecraft mod ecosystem, built on **Neroland Core** and an
  **optional add-on to NeroTech** (`../nerotech`). NeroTech owns the machine framework (thermal model,
  presets, GUI stack, capability wiring) and its seven generators; NeroPower ships only the power depth
  NeroTech lacks — fission with a fuel cycle, a staged failure model, tiered pooled storage, beamed
  transmission, RTG and Stirling generators. It never renames or moves a NeroTech block.
- The staged build plan with acceptance criteria is **`PLAN-0.1.0.md`** (repo root). Design record:
  `docs/DESIGN.md`. Work stage by stage; a stage is done only when all nine cells build green.
- Mod id: **`neropower`** (matches the registry namespace + every loader manifest). Package root:
  `za.co.neroland.neropower`. Author: **Neroland**.
- Version: **0.1.0-alpha.1** (pre-release; first release will be `0.1.0-beta.1`).
- Dependencies: **Neroland Core** (`nerolandcore_version`, floor `[${nerolandcoreVersion},2.0)`) and
  **NeroTech** (`nerotech_version`, floor `[${nerotechVersion},1.0)`). Both are real `implementation`
  dependencies resolved from GitHub Packages / mavenLocal — never reflection.
- Targets **MC 26.1.2, 26.2 AND 26.3** on **NeoForge, MinecraftForge/Forge, and Fabric** → the **"9 cells"**.
  **Java 25.** Mappings = official Mojang names (26.x ships de-obfuscated; no Parchment).

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
  (`:fabric:26.1.2 :fabric:26.2 :fabric:26.3 :neoforge:26.1.2 :neoforge:26.2 :neoforge:26.3 :forge:26.1.2 :forge:26.2 :forge:26.3`). `common` is
  NOT a node — its source is spliced via `rootProject.ext.commonJava` / `commonResources`. Dependency pins
  live in `gradle.properties` as `*_version_<mc>` keys; `mc_versions=26.1.2,26.2,26.3`.
- **Version-specific code in `common/`.** Non-active nodes run `common/` through Stonecutter (`stonecutterProcessCommon`), so shared code uses the same `//? if >=26.3 {` blocks as the loader `src/` trees. Keep the files in the vcsVersion state, and never put a `*/` inside a disabled block. Datapack files whose format differs by version go in `common/src/main/resources-<mc>/`, which is merged over `common/src/main/resources` for every node at or above `<mc>` (`mergeCommonResources`).

## Build & verify

- Build the cells with the Gradle wrapper, e.g. `./gradlew :fabric:26.2:build` or all nine:
  `:neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build :forge:26.1.2:build :forge:26.2:build :forge:26.3:build
  :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build`.
- Static analysis: `./gradlew :fabric:26.2:ecjCheck` (the VS Code Problems panel, via `tools/ecj.prefs`).
  The task only FAILS on errors.
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
  `grep -rniE 'neroland-mc-[e]cosystem|REWORK-PROMPT|PLAN-0|AUDIT|SMOKE-TEST|MANUAL-' wiki/` (must be empty).

## Release pipeline

- CI is the ecosystem standard and is not modified for the rebuild: `publish.yml` runs on a push to
  `main` that changes `gradle.properties` and publishes any `mod_version` that has no `v<version>` tag
  yet. Only bump `mod_version` to a new value when that version is meant to ship.
- Recipes use **natural progression only** (NeroTech intermediates such as Machine Frame, Nero Coil and
  Circuit Board). No Core progression gates or material-milestone gates.
- Telemetry (Stage 9): opt-out, anonymous Sentry crash reports via `telemetry/NeroPowerTelemetry` (EU ingest,
  NeroPower's own DSN). Keep it PII-free, and update `PRIVACY.md` **before** widening what it sends.

## Safety rules specific to this mod

- Failures (reactor explosions) go through `failure.FailureController` + `protection.ProtectionCheck`;
  terrain damage is OFF by default on dedicated servers; radius is capped by config.
- Transmission links need the linking player to be able to interact with BOTH endpoints. Never use
  `getNearestPlayer` for authorisation.
- Every `SavedData.get()` goes through Core's `data.SavedDataRecovery`.
- Any player identifier stored (link owner UUID) is minimal, retention-limited and cleared by the
  `PlayerDataEraser` registered with Core's `data.PlayerDataErasure`. See `PRIVACY.md`.
- Link module (`link.NeroPowerLinkModule`) scopes by owner UUID where recorded, else by proximity to the
  online requester. Never a server-wide roster.

## DO NOT

- Commit or push automatically — leave changes staged for the developer.
- Hard-code absolute machine paths in committed files.
- Add loader-specific code to `common/` — use the platform seams.

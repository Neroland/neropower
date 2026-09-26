# NeroPower 0.1.0-beta.1 — runtime verification checklist (Stage 11)

Status: **build gate passed 2026-09-26** (NeroTech 0.4.0-beta.1 and NeroPower 0.1.0-alpha.1 both green on all nine cells, 14 test
classes per cell, `check_content.py` 0 gaps). Everything below the build gate is **pending in-game verification**.
Tick each line in a dev world on the loader/version named. "S" = dedicated server + client, "C" = singleplayer.

## Build gate

- [x] `./gradlew :neoforge:26.1.2:build :neoforge:26.2:build :neoforge:26.3:build :forge:26.1.2:build :forge:26.2:build :forge:26.3:build :fabric:26.1.2:build :fabric:26.2:build :fabric:26.3:build --stacktrace` green (includes `ecjCheck` + unit tests)
- [x] `python3 tools/check_content.py` → 0 gaps (455 checks)
- [x] NeroTech 0.4.0-beta.1 cells green and `./gradlew publishToMavenLocal` run in `../nerotech` (NeroPower resolves it from mavenLocal until GitHub Packages carries it)

## Load

- [ ] NeoForge 26.2 C: mod loads with Core 1.13.0 + NeroTech 0.4.0-beta.1; `config/neropower.properties` written with all 46 keys
- [ ] Fabric 26.2 C: same
- [ ] Forge 26.2 C: same
- [ ] NeoForge 26.2 S: dedicated server starts; `terrainDamageMode=auto` resolves to OFF (log line or failure test below)
- [ ] Creative tab "NeroPower" lists 20 items in order; every block places and shows its model/texture

## Fission

- [ ] 3³ shell (26 casing + core mid-wall) forms; GUI shows shell size 3, 2 usable rod slots; locked slots refuse rods
- [ ] 5³ shell forms; 4 usable slots; interior `control_rod_assembly` blocks raise the control-rod factor shown in GUI
- [ ] Fresh `fuel_rod` produces ~`fissionNePerRod`×1.2 per rod at burn-up 0 and less as burn-up rises; rod turns into `spent_fuel_rod` at 1000‰
- [ ] Sustained full output with no cooling: heat climbs, GUI stage walks STABLE → WARNING → UNSTABLE → FAILURE with ≥ `failureMinDwellTicks` in each, alarm block-state/sound/particles visible; FAILURE removes the core; with `overloadEnabled=false` it pins at UNSTABLE
- [ ] Same on a dedicated server: explosion leaves terrain intact (`ExplosionInteraction.NONE`), radius ≤ `failureRadiusCap`
- [ ] Coolant Pump / radiator adjacency keeps a 3³ core in STABLE at full output for one full rod life
- [ ] Poison: after long full-output operation the core stalls (THROTTLED) and recovers after poison falls 300‰ below the stall threshold
- [ ] `fissionScorchEnabled=true`: FAILURE leaves a scorch zone that damages entities and expires after `fissionScorchDays`
- [ ] Spent rod → Chemical Processor → reprocessed pellet; 2 reprocessed → 1 uranium pellet (crafting)
- [ ] NeroLink `scram` action (owner) drops output to floor for 1200 ticks; `acknowledge_alarm` clears the alarm state

## Storage

- [ ] Three cell tiers report configured capacity/I-O in GUI; CHARGE block-state overlay changes at 0/25/50/75/100 %
- [ ] Bank controller adjacent to a 2×2×2 of cells shows member count 8 and summed capacity; Analytics Terminal sees the pool
- [ ] Two adjacent banks/cells never ping-pong energy (watch Analytics Δ over 1 min)
- [ ] Sneak-use with empty hand cycles BUFFER ↔ PRIORITY_SOURCE; in PRIORITY the controller only feeds neighbours below `bankPriorityThresholdPermille`

## Beamed power

- [ ] Configurator click on transmitter then receiver (100 blocks, clear line) links; GUI shows distance 100, loss `(1−0.003)^100`; receiver gains `beamTransferPerPass × loss` per pass
- [ ] Placing a block in the beam sets status BLOCKED within `beamCheckIntervalTicks`; removing it restores
- [ ] Relay chain transmitter → relay → receiver works; loops are cut by `beamMaxHops`
- [ ] Non-owner cannot unlink; a player without `mayInteract` at either end cannot link (spawn protection test); operator can unlink
- [ ] `beamCrossDimension=false`: orbital receiver refuses a cross-dimension link; `=true` in a Core space-tagged dimension: works with the fixed hop loss
- [ ] Entities standing in the beam take `beamDamage`

## RTG / Stirling

- [ ] RTG: isotope pellet inserted → output starts at `rtgNePerTick`, halves after `rtgHalfLifeDays` (use `/time add`), spent pellet ejected to output slot at cutoff
- [ ] Stirling beside a running fission core: core heat drops, NE produced ∝ gradient; cold face (water/radiator) raises output by the bonus

## Privacy / erasure

- [ ] `/neroland data erase <uuid>` (or `eraseme`) clears the beam link owner on loaded transmitters immediately and on unloaded ones at next load; `BeamLinkSession` entry cleared
- [ ] NeroLink snapshot for a non-owner shows only proximity-scoped, non-identifying entries; never a server-wide roster
- [ ] `ErasureConformance` test passes in the `common` test run
- [ ] Telemetry: log shows `[NeroPower] Telemetry enabled`; a forced NeroPower exception appears in the NeroPower Sentry project (EU) with release `neropower@<version>` and **no** user, IP, server name, coordinates or unscrubbed home path
- [ ] Telemetry opt-out: `telemetryEnabled=false` in `config/neropower.properties` → log shows `Telemetry disabled`, no request to `ingest.de.sentry.io`

## World safety

- [ ] A NeroTech 0.3.0-beta.1 world opened with NeroTech 0.4.0-beta.1 + NeroPower: no ID remaps, no data loss, no log errors
- [ ] Remove NeroPower again: world loads, NeroPower blocks vanish, no crash
- [ ] Corrupt `neropower_scorch_zones.dat` / `neropower_erasure_state.dat` → `SavedDataRecovery` backs up and recreates, no crash loop

## Release (only after every line above)

- [ ] `MANUAL-GITHUB/` files applied by the maintainer (wiki.yml with guard; publish.yml re-arm per `MANUAL-GITHUB/README.md`)
- [ ] `mod_version=0.1.0-beta.1`, CHANGELOG dated, `art/*` reviewed
- [ ] `publish.yml` keeps the `getsentry/action-release` step and repo secret `SENTRY_PROJECT` points at NeroPower's Sentry project (release name `neropower@<mod_version>`)
- [ ] NeroTech 0.4.0-beta.1 released first and its Maven package readable by this repo

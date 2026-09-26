# Failure Stages

Every NeroPower reactor owns a **staged failure ladder** driven by its heat. Nothing ever fails
silently: a reactor climbs one rung at a time, each rung is announced with a sound, particles, an
alarm state on the block and (for owned machines) a NeroLink alert, and there is always time to act
before the top. In 0.1.0 the [Fission Reactor](Fission-Reactor.md) is the machine on the ladder.

## How it works

### The four stages

| Stage | Entered at (heat, % of capacity) | Effect |
| --- | --- | --- |
| **Stable** | — | Normal operation |
| **Warning** | 60 % | Alarm state on the block, a bell and smoke; full output |
| **Unstable** | 80 % | Output scaled to **60 %**, heavy smoke, a *warning* alert to the owner |
| **Failure** | 100 % | The failure action runs — the reactor is destroyed; a *critical* alert to the owner |

The thresholds are server config; the defaults are above.

### Rules of the ladder

- **One rung per tick, never skipped.** However fast heat spikes, a reactor passes through every
  stage in order, so each escalation is visible.
- **Minimum dwell.** A reactor stays at least **5 seconds** (100 ticks) in a stage before it may
  climb to the next one.
- **Hysteresis.** A stage is only left downwards once heat has dropped **5 points** below the
  threshold that entered it, so a reactor hovering on a line does not flap between stages.
- **Cooling is immediate.** No dwell applies on the way down — cool it and it recovers rung by
  rung, with a soft chime when it reaches *Stable* again.

### What happens at Failure

The machine's failure action runs once and the reactor block is gone. For the Fission Reactor
that is an **explosion** centred on the shell's interior with a radius of `shell size + 2`, plus an
optional **scorch zone**. The blast is always clamped to the server's `failureRadiusCap`.

Whether the blast **breaks blocks** follows `terrainDamageMode`:

- `auto` (default): terrain damage is **off on a dedicated server** and **on** in singleplayer and
  LAN worlds.
- `off`: the blast still damages and knocks back entities and the reactor is still lost, but no
  block is broken.
- `on`: always breaks blocks.

Even with terrain damage on, the blast respects protection: if any block it could reach is inside
spawn protection or is unbreakable (bedrock and the like), the radius shrinks to the reactor's own
footprint, and if even that is protected the explosion breaks nothing. A reactor built against
spawn cannot be used to crater it.

### Survival-friendly mode

Set `overloadEnabled=false` and the ladder is **pinned at Unstable**: a neglected reactor throttles
to 60 % and alarms forever, but never runs its failure action. A reactor that was already at
*Failure* when the setting changes drops back to *Unstable* at once.

### Alerts and the ecosystem bus

Every transition is published on NeroTech's machine-failure event channel, so NeroEvents, NeroLink
and other mods can react (interlocks, sirens, dashboards). If the reactor has a recorded owner (only
when the server turned on NeroTech's per-player attribution), that player gets a NeroLink alert on
entering *Unstable* and *Failure*. NeroLink `failure` events go to the owner only; an unowned
machine's events go only to online players within 128 blocks of it — never to everyone. The
NeroLink app also lets the owner (or, on an unowned reactor, a nearby player allowed to use it)
acknowledge an alarm or [SCRAM](Fission-Reactor.md#scram) the reactor.

## Recipes

The failure ladder is not an item — it is built into every reactor. There is nothing to craft.

## Config keys

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `overloadEnabled` | true | — | false pins the ladder at Unstable (no failure action ever) |
| `terrainDamageMode` | auto | on / off / auto | Whether a failure blast breaks blocks (auto = off on a dedicated server) |
| `failureRadiusCap` | 8 | 1–16 | Hard cap on any failure blast radius |
| `failureWarningPermille` | 600 | 1–1000 | Heat (permille of capacity) that enters Warning |
| `failureUnstablePermille` | 800 | 1–1000 | Heat that enters Unstable (must exceed the warning line) |
| `failureFailurePermille` | 1000 | 1–1000 | Heat that enters Failure (must exceed the unstable line) |
| `failureHysteresisPermille` | 50 | 0–1000 | How far below a threshold heat must fall to leave the stage |
| `failureMinDwellTicks` | 100 | 0–72000 | Minimum ticks in a stage before climbing |
| `unstablePenaltyPermille` | 600 | 0–1000 | Output multiplier while Unstable |

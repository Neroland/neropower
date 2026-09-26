# NeroPower privacy disclosure

NeroPower is an optional power-depth add-on to NeroTech, built on Neroland Core. This page is the
full disclosure of what the mod stores about players, where, for how long, how it is erased, and
what it exposes through the NeroLink companion app — and it documents how the design complies with
the GDPR (EU) and POPIA (South Africa). It mirrors NeroTech's `PRIVACY.md` in structure.

> **Short version:** NeroPower stores exactly one piece of personal data — the **UUID of the player
> who linked a Beam Transmitter or Beam Relay** — inside that block's own save data, for as long as
> the link exists. A single Neroland Core command erases it. Separately, NeroPower sends
> **anonymous, opt-out crash reports** (Sentry, EU servers) that contain no personal data — see
> *Crash reporting (telemetry)* below; set `telemetryEnabled=false` to turn it off.

## What is stored

### Beam link owner (UUID)

| | |
|---|---|
| **What** | The UUID of the player who made the current link on a **Beam Transmitter** or **Beam Relay**. Never a name, never a timestamp, never an IP. |
| **Why** | Unlink authority: only the linking player (or an operator) may break a beam link, so someone cannot cut power to your base by re-aiming your transmitter. It also scopes what the NeroLink companion app may show — an owned transmitter appears only in its owner's snapshot. |
| **Where** | In the block entity's NBT (`LinkOwner`) inside the world save, exactly like NeroTech's wireless-node partner data. It is never synced to clients, written to a log, or sent off the server. |
| **Retention** | Until the link is changed or removed, the block is broken, or an erasure request is made. After an erasure request the UUID is held in a **pending-erasure list for at most 30 days** (see *How to erase*), so a transmitter whose chunk was not loaded at the time still drops it on its next load. |
| **Erasable** | Yes — registered with Neroland Core's shared data-erasure hook. |

### Transient beam-linking session (UUID, in memory only)

While you are linking two beam endpoints with the Configurator, the server remembers "which block
you picked first" keyed by your UUID for **at most 30 seconds**, in memory only. It is never
written to disk, never synced and never logged, and is dropped on timeout, on completing the link,
or on an erasure request.

### Reactor owner (NeroTech's, not NeroPower's)

A Fission Reactor is a NeroTech-family machine. The "owner" a reactor may record is NeroTech's
own per-machine owner field, which NeroTech stores **only when a server admin has enabled
per-player pollution attribution** (`pollutionPerPlayerAttribution=true` in
`config/nerotech.properties`, off by default). NeroPower reads it for two things only — to address
a failure alert to the owner, and to gate the NeroLink actions below — and never stores a copy.
Its retention and erasure are documented in NeroTech's `PRIVACY.md` and handled by NeroTech's own
eraser; NeroPower does not duplicate that.

## What is NOT stored

- **No usernames, IP addresses, or machine/host names** — anywhere, ever.
- **No player positions, inventories, chat, or gameplay statistics.**
- **Scorch zones** (the poisoned ground a failed reactor leaves behind) record a place, a radius
  and an expiry day — no owner, no victims.
- **Battery banks, RTGs, Stirling generators, Beam Receivers and Orbital Receivers** record no
  player at all; they are keyed by block and dimension only.
- **Failure events** published on the ecosystem event bus name a machine and a place
  (`neropower:fission_core@minecraft:overworld:<packed position>`), never a player.
- Owner UUIDs are **compared, never logged**: erasure and alerts log anonymous counts only.

## Crash reporting (telemetry)

NeroPower bundles the Sentry SDK and sends **anonymous error reports** to NeroPower's own Sentry
project, hosted in the **EU** (`ingest.de.sentry.io`), so crashes in NeroPower code can be found and
fixed. It is **on by default and opt-out**, the same as Neroland Core, NeroTech and the rest of the
Neroland family.

| | |
|---|---|
| **What is sent** | The stack trace of an error **in NeroPower code**; the NeroPower, Minecraft, mod-loader, Java and OS versions; whether it is a client or a dedicated server; the ids and versions of your installed mods (public manifest strings, at most 300); and three NeroPower settings useful for triage (`overloadEnabled`, `terrainDamageMode`, `transmissionEnabled`). |
| **What is never sent** | No usernames, UUIDs, IP addresses (Sentry's default PII collection is off), host or server names, coordinates, world data, chat or config values other than the three above. Home-directory paths are rewritten to `/~` so your OS account name is not transmitted, and absolute file paths are dropped from stack frames. |
| **Which errors** | Only events whose stack trace passes through `za.co.neroland.neropower`. Errors from other mods, and known noise that NeroPower did not cause, are dropped on your machine before anything is sent. At most **10 reports per game session**, duplicates suppressed. |
| **Where / how long** | Sentry (Functional Software, Inc.), EU data region; held under Sentry's retention for the project (90 days by default) and used only to fix bugs. Reports cannot be linked to a player, so there is nothing to access or erase per person. |
| **Opt out** | Set `telemetryEnabled=false` in `config/neropower.properties` and restart. The setting is **client-local** (a server cannot turn it on for you) and is read *before* the SDK starts, so an opted-out install never contacts Sentry. Neroland Core and NeroTech have their own `telemetryEnabled` keys in their own files. |

## How to erase

NeroPower registers a data eraser with Neroland Core's shared erasure hook, so the ecosystem-wide
commands cover it:

```
/neroland data eraseme            # erase your own data (any player)
/neroland data erase <uuid>       # erase a player's data (operators, permission level 2+)
/neroland data purge-inactive     # Core's inactivity retention sweep (operators)
```

One request purges the player across Neroland Core, NeroTech, NeroPower and every other Neroland
mod on the server. For NeroPower it:

1. drops the player's in-memory beam-linking session, if any;
2. records the UUID in NeroPower's pending-erasure list (the `neropower:erasure_state` saved-data
   store in the overworld's `data/` folder, plus Core's last-known-good backup copy of it,
   refreshed immediately) — every
   **loaded** Beam Transmitter / Relay linked by that player clears its `LinkOwner` on its next
   tick, and every **unloaded** one on its next load;
3. lets the pending row expire **30 days** after the request (purged on load and on each new
   request). A transmitter that stays unloaded longer than that keeps a UUID nobody can be matched
   to any more — every other Neroland store has forgotten the player by then — and drops it the
   next time its link is changed or the block is broken.

The beam link itself (which block the transmitter aims at) is world data and survives the erasure;
only the player reference is removed. The reactor owner field is erased by NeroTech's eraser in the
same request.

## NeroLink companion-app exposure

NeroPower registers a module (`neropower`) with Neroland Core's NeroLink SPI. What the companion
app can see and do is deliberately narrow:

- **Never a server-wide roster.** Every section — beam links, fission reactors, battery banks,
  environmental generators — lists only machines in **loaded chunks within 128 blocks of the
  requesting player, in their dimension, while they are online**. An offline player sees an empty
  section with a note. The requester is only ever the player Neroland Core authenticated; the mod
  never guesses "the nearest player".
- **Owner-scoped where an owner is recorded.** A Beam Transmitter / Relay with a link owner, or a
  reactor with a NeroTech owner, appears only in that owner's snapshot. Machines with no recorded
  owner appear by proximity alone.
- **Non-identifying data only.** Snapshots carry machine ids, positions, status, failure stage,
  pooled energy, link distance / loss and output figures. They never carry a UUID or a name — not
  even your own.
- **Actions are owner-only and online-only.** `acknowledge_alarm` (silence a reactor's alarm) and
  `scram` (drop every control rod for 60 seconds) are refused unless the requesting player is the
  reactor's recorded owner; a reactor with no recorded owner refuses both, because ownership cannot
  be established. The server re-checks this itself — the app holds no authority.
- **Live events** forward failure-stage changes as broadcasts naming a machine and a place only.

## Legal basis and your rights (GDPR / POPIA)

The beam link owner UUID is processed on the basis of **legitimate interest** (GDPR Art. 6(1)(f);
POPIA s11(1)(f)): protecting the integrity of a player's power infrastructure on shared servers,
using the minimum data necessary (a UUID, no name), stored only in the world save of the server you
play on, and retained only for as long as the link exists. The UUID never leaves that server.

Crash reports are anonymous technical data processed on the basis of **legitimate interest**
(keeping the mod stable), minimised as described above, hosted in the EU, and can be switched off
at any time with `telemetryEnabled=false`.

Your rights of access, rectification, erasure and objection are exercised **on the server that
holds the world save** — with the commands above (erasure is self-service for your own data), or
through that server's operator. For questions about the mod's design, contact
**[info@neroland.co.za](mailto:info@neroland.co.za)**. The only thing the author receives is the
anonymous crash reports above, which identify no one, so there is nothing personal held by the
author to access or delete.

## Changes to this policy

If a future version of NeroPower stores additional player data, exposes more through NeroLink, or
widens what its crash reports contain, this page will be updated **before** that version is released and the
change will be called out in the changelog. Erasure through Neroland Core's shared hook will
continue to cover everything NeroPower stores.

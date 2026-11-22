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
>
> **Version 0.4.0 adds no player-keyed store.** The steam plant, fuel line, waste handling, storage
> tiers, beam grids, radiation and the reworked NeroLink module all hold world data or short-lived
> memory only. The beam link owner is still the only player identifier NeroPower writes.

## What is stored

### Beam link owner (UUID)

| | |
|---|---|
| **What** | The UUID of the player who made the current link on a **Beam Transmitter** or **Beam Relay**. Never a name, never a timestamp, never an IP. |
| **Why** | Unlink authority: only the linking player (or an operator) may break a beam link, so someone cannot cut power to your base by re-aiming your transmitter. It also scopes what the NeroLink companion app may show — an owned transmitter appears only in its owner's snapshot. |
| **Where** | In the block entity's NBT — two longs, `LinkOwnerMost` and `LinkOwnerLeast` — inside the world save, exactly like NeroTech's wireless-node partner data. It stays **server-side**: it is in the world save only and is **stripped from the client sync tag** (the block-entity update NeroTech's base sends to every nearby client), so no client ever receives it. It is never written to a log or sent off the server. |
| **Retention** | Until the link is changed or removed, the block is broken, or an erasure request is made. After an erasure request NeroPower keeps a **tombstone**: a salted one-way hash of the UUID with the request day, never the UUID itself (see *How to erase*). A transmitter whose chunk was not loaded at the time drops the owner on its next load, however much later that is. |
| **Erasable** | Yes — registered with Neroland Core's shared data-erasure hook. |

### Transient beam-linking session (UUID, in memory only)

While you are linking two beam endpoints with the Configurator, the server remembers "which block
you picked first" keyed by your UUID for **at most 30 seconds**, in memory only. It is never
written to disk, never synced and never logged, and is dropped on timeout, on completing the link,
or on an erasure request.

### Transient NeroLink state (UUID, in memory only)

Three short-lived tables back the NeroLink module, keyed by the requesting player's UUID and held **in
memory only**. Neither is written to disk, synced to other players or logged:

- **Opaque handles.** Each section answer gives every machine a random handle (`<nonce>-<index>`) in
  place of a position. The server remembers which machine each handle stands for, per player and per
  section, for **at most 30 seconds**. A new answer for that section replaces the table.
- **Action window.** The time stamps of your last NeroLink actions, so that no player can send more
  than **10 actions a minute**. The window is dropped 60 seconds after your last action.
- **Read budget.** When you last read each section, and a copy of that last answer, so that a
  faster read gets the same answer again instead of a new scan. The times go 60 seconds after your
  last read, and the copies within five seconds of logging out.

Your handle tables are dropped within five seconds of logging out. Both tables are dropped at once
on an erasure request.

### Reactor owner (NeroTech's, not NeroPower's)

A Fission Reactor is a NeroTech-family machine. The "owner" a reactor may record is NeroTech's
own per-machine owner field, which NeroTech stores **only when a server admin has enabled
per-player pollution attribution** (`pollutionPerPlayerAttribution=true` in
`config/nerotech.properties`, off by default). NeroPower reads it for three things only — to address
a failure event to the owner only, to gate the NeroLink actions below, and to open the owner's quest
milestone records (next section) — and never stores a copy. Its retention and erasure are documented
in NeroTech's `PRIVACY.md` and handled by NeroTech's own eraser; NeroPower does not duplicate that.

### Quest milestone records (Neroland Core's store, not NeroPower's)

NeroPower opens six **player-scope progression gates** in Neroland Core's store — `neropower:criticality`,
`neropower:first_breed`, `neropower:turbine_spinup`, `neropower:beam_linked`, `neropower:bank_capacity` and
`neropower:hook/fuel_rod_refill` — the first time a player's reactor burns fuel or breeds its first fuel,
their turbine first spins up to speed, they link a beam, their battery bank reaches `bankCapacityMilestone`,
or a quest mod rewards them a fuel rod. Each row is a gate id against the player's UUID, nothing else; it is
written only through Core's `ProgressionGates` API (never logged or broadcast), an owner who opted out of
NeroTech's attribution gets none, and the rows are stored, exported and erased by Neroland Core under
Core's own privacy disclosure. The records gate nothing. The world-log crossing
`neropower:fission_output` names a dimension only — never a player or a position.

**Operator credit is taken at the moment and never kept by NeroPower.** A gate goes to the machine's
NeroTech owner where one is recorded, or to the player who last used the machine. That "last
operator" is NeroTech's transient field on the shared machine base: it is never saved, never synced
and is cleared when the block unloads. NeroPower never picks the nearest player for credit, for
authorisation or as a push recipient.

### Radiation: no dose is stored

Radiation is world data. NeroPower keeps **no per-player dose, exposure history or health record**.

- **Contamination** is one level (0 to 1 000) per chunk, in the `neropower:contamination` saved-data
  store of each dimension. Each entry is a packed chunk position and a level. It has no owner, no
  source player and no victim list. It decays over time, has a hard cap per dimension, and nothing is
  written while radiation is off.
- **Effects** (Weakness, Hunger, radiation damage) are applied in the moment to whoever stands in a
  contaminated chunk. Nothing is added up per player, and no chat line, log line or report names
  anyone. Nothing standing on protected ground is harmed and no protected chunk is contaminated, as far
  as NeroPower's protection check knows: today that is vanilla spawn protection only, since no claims
  mod implements the check yet. On a server with land claims, keep `radiationMode` off or accept that
  claimed land is not exempt.
- **Geiger Counter.** While a counter is in your hand, the server writes the nine chunk levels around
  you and a count of nearby sources onto that item (`neropower:geiger_reading`), so your client can
  draw the dial. Only sources that actually leak are counted: a cask behind shielding on every face
  does not show. Minecraft sends a held item to players who can see you holding it, so they could read
  the same numbers. This is world data only, with no player, place or time. Only your own client
  draws a reading from a counter in your hand, and the server removes the reading from any counter
  in your inventory that is no longer in your hand.
- **Dosimeter Badge.** The badge adds up the contamination it has been through on the item itself
  (`neropower:dosimeter_reading`, a single number). It holds no UUID, name, place or time. It stays
  with whoever holds the badge, and crafting the badge alone resets it.
- Emitting casks and pools are tracked in memory only, so the Geiger can count them. This list holds
  no player.

## What is NOT stored

- **No usernames, IP addresses, or machine/host names** — anywhere, ever.
- **No player positions, inventories, chat, or gameplay statistics.**
- **Scorch zones** (the poisoned ground a failed reactor leaves behind) record a place, a radius
  and an expiry day — no owner, no victims.
- **The gallery records no player.** `/neropower gallery` links its exhibit beams under a random token
  that matches no player, never the operator's UUID.
- **Machines record no player.** This covers battery banks, Surge Banks, Momentum Wheels, Storage
  Rings, RTGs, Stirling generators, fuel cells, betavoltaic cells, turbines and condensers, steam
  ports, reactor control computers, spent fuel pools, dry casks, the Vitrifier, circuit breakers,
  load banks, power monitors, Beam Receivers and Orbital Receivers. Their saves hold machine state
  only.
- **Beam Splitters and Phased Array Emitters** store their outgoing links (target block and dimension,
  and a share) with **no owner**. Permission is checked once, at link time. The link keys are also
  removed from the update sent to clients.
- **Item data holds no player.** Fuel rod burnup, the energy stored in a power cell, and the Geiger and
  dosimeter readings are numbers on the item only.
- **Failure events** carry a machine type and a stage only, never a place or a player, and are
  delivered to specific players only (see *NeroLink companion-app exposure*).
- Owner UUIDs are **compared, never logged**. No NeroPower log line at INFO or above carries a UUID,
  name, position or player-written text, and a unit test checks every logger call in the source for this.

## Crash reporting (telemetry)

NeroPower bundles the Sentry SDK and sends **anonymous error reports** to NeroPower's own Sentry
project, hosted in the **EU** (`ingest.de.sentry.io`), so crashes in NeroPower code can be found and
fixed. It is **on by default and opt-out**, the same as Neroland Core, NeroTech and the rest of the
Neroland family.

| | |
|---|---|
| **What is sent** | The stack trace of an error **in NeroPower code**; the NeroPower, Minecraft, mod-loader, Java and OS versions; whether it is a client or a dedicated server; the ids and versions of your installed mods (public manifest strings, at most 300); and three NeroPower settings useful for triage (`overloadEnabled`, `terrainDamageMode`, `transmissionEnabled`). |
| **What is never sent** | No usernames, UUIDs, IP addresses (Sentry's default PII collection is off), host or server names, coordinates, world data, chat or config values other than the three above. No user is attached to a report. Home-directory paths are rewritten to `/~` so your OS account name is not transmitted, and absolute file paths are dropped from stack frames. Before a report leaves your machine, any UUID in its text is replaced by `<uuid>` and any block position (`x=…, y=…, z=…` or a triple of whole numbers) is masked, even when the message came from another mod or from Minecraft itself. |
| **Which errors** | Only events whose stack trace passes through `za.co.neroland.neropower`. Errors from other mods, and known noise that NeroPower did not cause, are dropped on your machine before anything is sent. At most **10 reports per game session**, duplicates suppressed. |
| **Where / how long** | Sentry (Functional Software, Inc.), EU data region; held under Sentry's retention for the project (90 days by default) and used only to fix bugs. Reports cannot be linked to a player, so there is nothing to access or erase per person. |
| **Opt out** | Set `telemetryEnabled=false` in `config/neropower.properties` and restart. The setting is **client-local** (a server cannot turn it on for you) and is read *before* the SDK starts, so an opted-out install never contacts Sentry. Neroland Core and NeroTech have their own `telemetryEnabled` keys in their own files. |

## How to erase

NeroPower registers a data eraser with Neroland Core's shared erasure hook, so the ecosystem-wide
commands cover it:

```text
/neroland data eraseme            # erase your own data (any player)
/neroland data erase <uuid>       # erase a player's data (operators, permission level 2+)
/neroland data purge-inactive     # Core's inactivity retention sweep (operators)
```

One request purges the player across Neroland Core, NeroTech, NeroPower and every other Neroland
mod on the server. For NeroPower it:

1. drops the player's in-memory beam-linking session, if any, and their NeroLink handle tables and
   action window;
2. writes a **tombstone** to the `neropower:erasure_state` saved-data store in the overworld's `data/`
   folder, and Core's last-known-good backup copy of it is refreshed at once. A tombstone is a 128-bit
   one-way hash of the UUID, made with a random salt kept in that world only, plus the day of the
   request. It is never the UUID. Every **loaded** Beam Transmitter / Relay that player linked on or
   before that day clears its `LinkOwnerMost` / `LinkOwnerLeast` on its next tick. Every **unloaded**
   one does the same when its chunk next loads, however long that takes;
3. keeps the tombstone. It does **not expire**, because NeroPower cannot know when the last unloaded
   transmitter has been scrubbed. A tombstone is 16 bytes and a day. Links you make *after* the
   erasure day carry a newer link day (`LinkDay`), so the tombstone never removes them. A link made
   on the erasure day itself is removed too. At most 65 536 tombstones are kept, and the oldest goes first.
   Older saves that held plain UUIDs here are hashed on load.

The beam link itself (which block the transmitter aims at) is world data and survives the erasure;
only the player reference is removed. The reactor owner field is erased by NeroTech's eraser in the
same request. NeroPower's milestone gate rows and any NeroPower alert in your NeroLink inbox are
held by Neroland Core and erased by Core in the same request.

### Access to your data

The only personal data NeroPower holds is your UUID on a beam you linked. In the NeroLink app, a
transmitter or relay you linked is marked as yours (`owned`) in your own snapshot, and no one else
can see it. Neroland Core 1.20.0 has no shared export command, so a full copy is made by the
server's operator on request. They read the `LinkOwnerMost` / `LinkOwnerLeast` keys of the
transmitters and relays in the world save.

## NeroLink companion-app exposure

NeroPower registers a module (`neropower`, **link schema 2**) with Neroland Core's NeroLink SPI. What
the companion app can see and do is deliberately narrow:

- **Never a server-wide roster.** The fission, turbine, storage, beam and generator sections list only
  machines in **loaded chunks within 8 chunks of the requesting player's chunk (about 128 blocks),
  in their dimension, while they are online**. Reach is decided on the chunk grid and ignores height,
  so walking along its edge cannot pin a machine down more finely than its chunk. Each section is
  shuffled and capped at 64 entries, and each player gets at most **6 fresh answers per section a
  minute**. A faster read gets the last answer again. An offline player sees an empty
  section with a note. The requester is only ever the player Neroland Core authenticated, and the mod
  never guesses "the nearest player".
- **No places, no owners.** Since schema 2, entries carry **no coordinates, dimension, distance,
  direction or height**. The only hint of distance is a coarse `band`: `near` (within 2 chunks, about
  32 blocks) or `in_range`. They also carry no owner UUID or name, not even your own, and say nothing about other
  players or about contamination at any place. Each machine is named by an opaque handle that only
  you can use, only for 30 seconds, and only until your next answer for that section (see *Transient
  NeroLink state*). Before an answer leaves the server, it is checked once more and any location key
  or UUID-shaped value is dropped. A section that still carries one is withheld.
- **Owner-scoped where an owner is recorded.** A machine appears only in its owner's snapshot, as
  `owned`. The owner is the beam link owner for a linked Beam Transmitter / Relay. Otherwise it is the
  machine's NeroTech owner. Machines with no
  recorded owner appear by proximity alone.
- **A public catalogue.** The `catalogue` section lists block ids, the fuel lines and the storage
  tiers from the server config. It is the same for every player and contains no world data.
- **Actions are online-only, handle-only and rate-limited.** `acknowledge_alarm` (silence a reactor's
  alarm), `scram` (drop every control rod for 60 seconds) and `turbine_trip` (latch a turbine's trip;
  it only makes the plant safer) need a current handle. A call that sends a position instead is refused
  with `update_required`, and the reply is the same whether or not a machine is there, so positions
  cannot be probed. **Remote control needs ownership:** a machine accepts actions only from its
  recorded owner. A machine **without** an owner accepts no remote action at all. Use it in person
  through its screen or the SCRAM Key. The server checks all of this again when the action runs. Each player may send at most 10 actions a
  minute. No UUID is stored for any of it.
- **Live events are targeted, never broadcast, and carry no place.** A failure-stage change is sent to
  online players in that dimension within 8 chunks of the machine at that moment (the same chunk grid), and an owned
  machine's change goes to its owner only. These nearby notices are **never pushed** to a phone. A
  push goes only to a reactor's already-recorded NeroTech owner, when the machine rises into UNSTABLE
  or FAILURE, under the `neropower` notification category, which is **off by default** in the app. The
  beam link owner is never a push recipient. NeroPower keeps no new opt-in list. Event bodies hold
  the machine type, the stage and whether it is rising, and a push says only "<machine>: <stage>".
  At most five events per machine per minute.
- **The map.** On NeroMaps and the app's map, NeroPower draws only **your own** reactors and beam
  endpoints, plus scorch zones, which are hazards with no owner. Since 0.4.0 there is no layer for
  other players' machines or for machines with no owner. A marker is an exact position, so another
  player's or an unowned machine never appears on your map.
- **Your client never receives another block's absolute position from a beam.** The sync data a
  Beam Transmitter / Relay sends to nearby clients shows its target only when the target is within
  64 blocks in the same dimension. A farther target appears as a direction only, and a target in
  another dimension appears as no position at all.
- **Alerts in your NeroLink inbox.** When a reactor you own (by NeroTech attribution) becomes unstable
  or fails, Neroland Core stores an alert for you in your inbox. Its text names the machine type and
  the stage only. Its id is a salted hash that cannot be turned back into a position. Core erases these
  alerts with the rest of your data.

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

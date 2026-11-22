# Privacy

What NeroPower stores about players, and how to erase it. The full disclosure, including the
GDPR / POPIA basis, is in the repository's [`PRIVACY.md`](../PRIVACY.md); this page follows it.

## The one thing NeroPower stores

**The UUID of the player who linked a Beam Transmitter or Beam Relay**, saved in that block's own
data (`LinkOwnerMost` / `LinkOwnerLeast`) inside the world save. It exists so that only the linking
player (or an operator) can break a beam link, and so that the NeroLink app shows an owned transmitter
only to its owner. It stays **server-side**: it is stripped from the block update sent to nearby
clients, never logged and never sent off the server. It is never a name or a timestamp, and it goes
away when the link is changed, the block is broken, or you ask for erasure.

**Version 0.4.0 adds no player-keyed store.** The steam plant, fuel lines, waste, storage, beam grids,
radiation and the NeroLink module hold world data or short-lived memory only.

### Short-lived memory

Kept in memory only, keyed by your UUID, never written to disk, synced to others or logged:

- **Beam linking.** While you link two endpoints with the Configurator, which block you picked first,
  for at most 30 seconds.
- **NeroLink handles.** Which machine each opaque handle stands for, for at most 30 seconds.
- **NeroLink action window.** When you last sent actions, so no one sends more than 10 a minute.
- **NeroLink read budget.** When you last read each section and a copy of that answer.

Handles and stored answers go within five seconds of logging out, the time stamps 60 seconds after
your last action or read, and all of it at once on an erasure request.

### Owners NeroPower reads but does not store

- A machine's **owner** is NeroTech's machine-owner field, stored only when the server admin turned on
  NeroTech's per-player pollution attribution (off by default). NeroTech documents and erases it.
- **Milestone records** (`neropower:criticality`, `first_breed`, `turbine_spinup`, `beam_linked`,
  `bank_capacity`, `hook/fuel_rod_refill`) are player gates in Neroland Core's store, covered and erased
  by Core. A record goes to the machine's owner, or to the player using the machine at that moment.
  That "last operator" is never saved. NeroPower never picks the nearest player for credit,
  authorisation or a push.

## Radiation: no dose is stored

- **Contamination** is a level per chunk (`neropower:contamination`): no owner, no source player, no
  victims. It decays and has a cap per dimension.
- **Effects** are applied in the moment. Nothing is added up per player. Nothing on protected ground is
  harmed and no protected chunk is contaminated, as far as NeroPower's protection check knows (vanilla
  spawn protection today; no claims mod implements it yet).
- The **Geiger Counter** writes the nearby chunk levels and a count of leaking sources onto the item in
  your hand, so your client can draw the dial. Players who can see you holding it receive the held item
  too. It is world data only, and it is cleared from a counter that leaves your hand.
- The **Dosimeter Badge** keeps a single number on the item: no UUID, name, place or time.

## What NeroPower never stores or sends

- No usernames, IPs, positions, inventories, chat or statistics.
- Battery banks, Surge Banks, Momentum Wheels, Storage Rings, turbines, condensers, reactor ports and
  computers, fuel racks, casks, the Vitrifier, breakers, load banks, monitors, receivers, RTGs, Stirling
  generators, fuel cells and betavoltaic cells record no player. Neither do scorch zones.
- **Beam Splitters and Phased Array Emitters** store their links with no owner.
- **`/neropower gallery`** links its exhibit beams under a random token that matches no player.
- No log line at INFO or above carries a UUID, name, position or player-written text.

## Crash reports (opt-out)

NeroPower sends **anonymous error reports** to its developers (Sentry, EU servers) when something in
NeroPower's own code fails: the stack trace, mod / Minecraft / loader / Java / OS versions, your
installed mod list and three NeroPower settings. Never a username, UUID, IP, coordinates, world or
player names or world data. UUIDs and positions in error text are masked before a report leaves your
machine, and your account name is scrubbed from file paths. At most 10 reports per session.

It is **on by default**. To opt out, set `telemetryEnabled=false` in `config/neropower.properties` and
restart. The setting is client-local.

## Erasing your data

Neroland Core's shared erasure command covers every Neroland mod at once:

```text
/neroland data eraseme          # your own data
/neroland data erase <uuid>     # operators
```

For NeroPower this drops your short-lived memory and writes a **tombstone**: a salted one-way hash of
your UUID and the day, never the UUID itself. Every transmitter or relay you linked on or before that
day forgets you on its next tick if loaded, or when its chunk next loads, however long that takes.
Tombstones do not expire, and links you make after the erasure keep their owner. The beam link itself
(which block it aims at) is world data and stays; only the player reference is removed.

### Access to your data

In the NeroLink app, a transmitter or relay you linked is marked as yours (`owned`) in your own
snapshot. Neroland Core has no shared export command yet, so a full copy is made by the server
operator on request, from the transmitters' and relays' saved data.

## The NeroLink companion app

See [Link Module](Link-Module.md) for the details. In short:

- Only loaded machines within 8 chunks of you, in your dimension, while you are online. Never a
  server-wide list.
- No coordinates, dimension, distance, direction, owner or name in any entry: only a coarse `band`
  (`near` or `in_range`) and an opaque handle that only you can use, for 30 seconds.
- An owned machine is shown only to its owner. At most 6 fresh answers per section a minute.
- Remote acknowledge, SCRAM and turbine trip work only on machines you own (`NOT_OWNER` otherwise),
  at most 10 a minute.
- Failure notices go to nearby online players and are never pushed. A push goes only to a machine's
  recorded owner, under a category that is off by default.
- On NeroMaps and the app's map, NeroPower draws only **your own** reactors and beam endpoints, plus
  scorch zones (hazards with no owner).
- Nearby clients never receive a beam's absolute target beyond 64 blocks or in another dimension.

# Privacy

What NeroPower stores about players, and how to erase it. The full disclosure, including the
GDPR / POPIA basis, is in the repository's [`PRIVACY.md`](../PRIVACY.md).

## The one thing NeroPower stores

**The UUID of the player who linked a Beam Transmitter or Beam Relay**, saved in that block's own
data inside the world save. It exists so that only the linking player (or an operator) can break a
beam link — nobody can re-aim your transmitter and cut your base off. It is never a name, never a
timestamp, never sent to clients, never logged, and it goes away when the link is changed, the
block is broken, or you ask for erasure.

While you are linking two endpoints with the Configurator, the server also remembers which block
you picked first — in memory only, for at most 30 seconds.

A Fission Reactor's "owner" is NeroTech's own machine-owner field, stored only when a server admin
turned on NeroTech's per-player pollution attribution (off by default). NeroTech documents and
erases it; NeroPower only reads it to address failure alerts and gate remote actions.

## What NeroPower never stores or sends

- No usernames, IPs, positions, inventories, chat or statistics.
- Battery banks, RTGs, Stirling generators, receivers and scorch zones record no player at all.
- No analytics or tracking of any kind.

## Crash reports (opt-out)

NeroPower sends **anonymous error reports** to its developers (Sentry, EU servers) when something
in NeroPower's own code fails: the stack trace, mod / Minecraft / loader / Java / OS versions, your
installed mod list and three NeroPower settings. Never a username, UUID, IP, coordinates or world
data, and your account name is scrubbed from file paths. At most 10 reports per session.

To turn it off, set `telemetryEnabled=false` in `config/neropower.properties` and restart. The full
details are in [`PRIVACY.md`](../PRIVACY.md).

## Erasing your data

Neroland Core's shared erasure command covers every Neroland mod at once:

```text
/neroland data eraseme          # your own data
/neroland data erase <uuid>     # operators
```

For NeroPower this drops your linking session and lists your UUID for link-owner erasure: every
loaded transmitter you linked forgets you on its next tick, every unloaded one on its next load,
and the list entry itself expires after 30 days. The beam link (which block it aims at) is world
data and stays; only the player reference is removed.

## The NeroLink companion app

If the server runs the NeroLink bridge, the app's NeroPower sections show **only loaded machines
within 128 blocks of you, in your dimension, while you are online** — never a server-wide list.
A transmitter or reactor with a recorded owner is shown only to that owner. Snapshots carry machine
ids, positions, status and output figures, never a UUID or a name. The two remote actions —
acknowledging a reactor alarm and SCRAM — work only on a reactor you own, and the server re-checks
that itself.

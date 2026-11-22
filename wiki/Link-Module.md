# Link Module

NeroPower registers a module with Neroland Core's NeroLink API, so the **NeroLink** companion app can
show your power plant and send a few safe commands. The module id is `neropower` and it speaks **link
schema 2** (since 0.4.0). The NeroLink bridge mod serves it; NeroPower itself opens no network port.

Everything here is scoped to the player who asks. The full privacy rules are on the
[Privacy](Privacy.md) page.

## How it works

### Reach and the band

The five **plant sections** list only machines that the asking player could walk up to:

- the player is **online**,
- the machine is in a **loaded** chunk in the player's **dimension**,
- within **8 chunks** of the player's chunk (about 128 blocks), measured on the chunk grid and ignoring
  height.

There is **no position** in any entry: no coordinates, dimension, distance, direction or height. Each
entry carries only a coarse **`band`**:

| `band` | Meaning |
| --- | --- |
| `near` | Within 2 chunks of you (about 32 blocks) |
| `in_range` | Farther, but within reach |

A machine that recorded an **owner** (a beam's link owner, or a machine's NeroTech attribution owner)
appears only in that owner's answer, marked `owned`. Machines with no owner appear by reach alone. Each
section is shuffled and capped at **64 entries**, and is never a server-wide list.

### Read budget

Each player gets at most **6 fresh answers per section a minute**. A faster read returns the last
answer again (same `asOf`) instead of scanning the world. The app shows this as *Refresh limited:
showing the last reading* and disables actions on a repeated answer.

### Handles

Every entry carries an opaque handle **`h`**. Only the player who asked can use it, only for **30
seconds**, and only until their next answer for that section. Actions name a machine by its handle and
nothing else.

### Sections

| Section | What an entry shows |
| --- | --- |
| `fission` | Reactor status, failure stage, heat and energy fill, formed, shell size, mode (direct or steam), rods loaded and usable, rate (NE/t or mB/t), poison, control-rod factor, SCRAM, alarm |
| `turbines` | Formed, length, rpm, output, steam fill, tripped, Governor Module fitted, state word |
| `storage` | Battery banks (cells, stored and capacity, mode) and every other store kind (Surge Bank, Momentum Wheel, Storage Ring, battery cell): stored, capacity, fill, last in and out |
| `beams` | Endpoint kind, beam status, linked, loss, last pass (Phased Array Emitter) |
| `generators` | RTG (fuelled, output, days left), Stirling, Hydrogen Fuel Cell and Betavoltaic Cell state |
| `catalogue` | **Public**, the same for every player: every block by family and role, the fuel lines as step lists, and the storage tiers with this server's capacity and I/O |

Section field names from schema 1 keep their names and meanings, so an older reader still gets a valid
answer. A player who is offline gets an empty section with a note.

### Actions

| Action | What it does |
| --- | --- |
| `acknowledge_alarm` | Silences a machine's alarm |
| `scram` | Drops every control rod of a Fission Core for 60 seconds |
| `turbine_trip` | Latches a turbine's trip, as an overspeed would; the rotor coasts down. It only makes a plant safer |

Rules for every action:

- It needs a current handle `h`. A call that sends a position instead (an older app) gets
  **`update_required`**, and the answer is the same whether or not a machine is there.
- **Remote control needs ownership.** A machine accepts actions only from its recorded owner. Any other
  machine answers **`NOT_OWNER`**: use it in person, through its screen or the SCRAM Key.
- The server checks reach and ownership again when the action runs.
- At most **10 actions a minute** per player, on top of the bridge's own limit.

### Events

Failure-stage changes (topic `failure`) are sent to online players in that dimension within 8 chunks of
the machine at that moment, and an owned machine's change to its owner only. These nearby notices ride
topic `alerts` and are **never pushed** to a phone. A push goes only to a machine's recorded NeroTech
owner, when it rises into *Unstable* or *Failure*, under the `neropower` notification category, which is
off by default in the app. Event bodies name the machine type, the stage and whether it is rising,
never a place or a player. At most five events per machine a minute.

### Memory

Handles, read times, last answers and action windows are kept **in memory only**. Handles go when you
log out, and all of a player's link state goes on an erasure request.

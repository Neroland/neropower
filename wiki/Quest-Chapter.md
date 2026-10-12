# Quest Chapter

NeroPower ships a quest pack for **NeroQuests**: the chapter *Reactor Log* (`neropower:reactor_log`,
14 quests). It appears in the quest book when NeroQuests is installed and is inert data otherwise —
NeroPower has **no dependency** on NeroQuests, and nothing in NeroPower is locked behind a quest.

## The chapter

| Quest | Needs | How it completes |
| --- | --- | --- |
| Cells | — | Craft a Basic Battery Cell and a Battery Bank Controller |
| Tiered Storage | Cells | Craft an Advanced and an Elite Battery Cell |
| Power Bank | Tiered Storage | A battery bank you own reaches `bankCapacityMilestone` pooled NE |
| Gradient | Cells | Craft a Stirling Generator and a Radioisotope Generator |
| Pellets | Gradient | Hold 4 Uranium Pellets and craft an Isotope Pellet |
| Beamed | Tiered Storage | Craft a Beam Transmitter, Receiver and Relay |
| Beam Link | Beamed | Link a transmitter to a target with the Configurator |
| Orbital | Beamed | Craft an Orbital Receiver |
| Fission Build | Pellets | Craft a Fission Core and Control Rod Assembly; hold 16 casing, 4 control rods, 2 fuel rods |
| Criticality | Fission Build | A formed reactor you own burns fuel |
| First Burn | Fission Build | Hold a Spent Fuel Rod — rewards a fresh Fuel Rod |
| Decay Heat | Pellets | Hold a Spent Isotope Pellet |
| Closing the Loop | First Burn | Hold 2 Reprocessed Pellets |
| World First: Fission | — | Shared by the whole server: the first reactor in any dimension burns fuel |

Machine outputs (pellets, spent rods, reprocessed pellets) are *collect* objectives, not crafts.

## Milestone records

Three quests read **player-scope milestone gates** NeroPower opens through Neroland Core the first
time something happens. They are records for the quest book, not locks: nothing in NeroPower checks
them for access.

| Gate | Opened when |
| --- | --- |
| `neropower:criticality` | A formed Fission Reactor burns fuel, for the reactor's owner |
| `neropower:beam_linked` | A player links a Beam Transmitter |
| `neropower:bank_capacity` | A Battery Bank's pooled capacity reaches `bankCapacityMilestone` (default 32 M NE), for the controller's owner |

A reactor's or bank's owner is NeroTech's machine-owner field, which exists only when the server has
NeroTech's per-player attribution on; an unowned machine, or an owner who opted out, records nothing.
See [Privacy](Privacy.md).

## World log

The first time a reactor burns fuel in a dimension, NeroPower publishes one rising crossing on the
`neropower:fission_output` channel with the **dimension id** as scope (never a player or a position).
*World First: Fission* listens for it; any other mod may too.

## Fuel rod refill

*First Burn* carries a `mod_hook` reward on `neropower:hook/fuel_rod_refill`. When the quest completes,
NeroPower puts one **fresh Fuel Rod** in the player's inventory (dropped at their feet if it is full),
also on their next login if they were offline, then re-arms the hook. It never touches a rod already
in a reactor.

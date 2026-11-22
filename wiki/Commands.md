# Commands

NeroPower adds one command, `/neropower`, with two groups of subcommands. Every subcommand must be run
**by a player** with **permission level 2** (gamemaster or operator). The gallery subcommands also need
**creative mode**.

For erasing player data, use Neroland Core's `/neroland data` commands (see [Privacy](Privacy.md)).
NeroPower has no data command of its own.

## Gallery (creative only)

| Command | What it does |
| --- | --- |
| `/neropower gallery` | Builds a walkable power station around you, with every NeroPower block on show and running |
| `/neropower gallery tour` | Takes you to the first stop of the tour (same as `gallery tour 1`) |
| `/neropower gallery tour <stop>` | Takes you to stop `<stop>` (1 to 16), shows its caption and the command for the next stop |
| `/neropower gallery clear` | Removes the gallery and only what it placed: empties its containers, removes every block it put down that is still there (machines only while they are still exhibits) and its label stands. Blocks and machines built there since stay |

The station is built at your position, facing north, in a box from 31 blocks west to 31 blocks east,
45 blocks north to 5 blocks south, and 12 blocks up from your feet (a 63 x 13 x 51 box; the build message
says so). The station's blocks replace whatever stood at their positions. One gallery per dimension:
`gallery` refuses while one stands, so run `gallery clear` first. The server remembers where it was built
(a block position, until the server stops), and `gallery tour` and `gallery clear` use that spot. After
a restart, `gallery clear` finds the gallery from its own machines: stand inside it. If none is found it
removes nothing and says so. The command refuses to build if any chunk of the box is not loaded.

What the station shows:

- **Unit 1:** a 7×7×7 reactor in steam mode feeding a steam turbine (three vane sets, three stator
  rings) and a Steam Condenser that returns the water, then a Circuit Breaker and a Battery Bank.
- **Unit 2:** a 5×5×5 breeder reactor in direct mode with moderators and Breeder Blankets, cooled by a
  Stirling Generator and a NeroTech Coolant Pump.
- **The waste line:** a Spent Fuel Rack under water, a Vitrifier, and a cask yard sealed behind
  Shielding Glass.
- **The storage hall:** a Storage Ring, a Surge Bank and a Momentum Wheel, each with a Power Monitor.
- **A breaker wall:** three breakers under different loads (calm, strained, tripped) and two display
  breakers.
- **A Load Bank test bench** feeding a Stirling Generator.
- **A beam yard:** a Transmitter, a Relay, a Beam Splitter and a Phased Array Emitter serving several
  receivers, plus an Orbital Receiver.
- **The small generators:** an RTG, a Betavoltaic Cell, a Hydrogen Fuel Cell and the three Battery Cells.

Gallery machines are **exhibits**. They record no owner and no operator, so using them opens no
milestone, quest step or trophy; they send no alerts or NeroLink events; they give off no radiation;
and a failure never runs its destructive action. Gallery beams record no player. The tour only
teleports you and keeps nothing about you.

Some exhibits need NeroTech blocks (Radiators, a Coolant Pump); they are skipped if NeroTech does not
provide them.

## Tick budget (operators)

| Command | What it does |
| --- | --- |
| `/neropower budget start` | Starts a sampling window of 200 ticks (10 seconds) |
| `/neropower budget start <ticks>` | Starts a window of `<ticks>` ticks (1 to 6,000) |
| `/neropower budget` | Shows milliseconds per tick per NeroPower machine type and in total, for the current or last window |

Nothing is measured while no window is open. The budget commands work in survival too.

# RTG and Stirling

Two small generators whose output is a function of **state** rather than a flat number: the
**Radioisotope Generator** fades with the half-life of its pellet, and the **Stirling Generator**
makes power from the temperature difference between a hot machine and a cold face — cooling the
machine as it does.

## Radioisotope Generator (RTG)

### How it works

Drop an **Isotope Pellet** in. The RTG consumes it, notes the moment, and from then on produces
`rtgNePerTick × 2^(−elapsed ⁄ half-life)`: **30 NE/t** fresh, 15 NE/t after 20 in-game days,
7.5 after 40, and so on. The GUI shows the current output as a percentage of fresh, the day the
pellet is on and the days left until it is spent.

Once output falls below `rtgCutoffPermille` of the fresh rate (5 % — about 4.3 half-lives, or
roughly 86 in-game days at the defaults) the pellet is **spent** and a **Spent Isotope Pellet**
appears in the output slot. If the output slot is full the RTG waits, dark, until it is emptied.
Insert the next pellet and the cycle restarts.

- Sealed and fuel-free once loaded: **no heat, no pollution, no failure ladder**, and decay runs
  the same in every dimension — it is the generator for an airless moon base.
- Overdrive presets and Speed modules scale the output.
- Energy leaves every face; pellets are accepted on every face; spent pellets can be pulled from
  any face you set to *output* in the Side Config tab.
- Never load-shed by NeroTech's throttling.

### Recipes

| Item | Recipe |
| --- | --- |
| **Radioisotope Generator** | Iron Ingot, Nero Coil, Iron Ingot / Circuit Board, Machine Frame, Nero Coil / Iron Ingot, empty, Iron Ingot |
| **Isotope Pellet** | 1 Uranium Pellet + 4 Glowstone Dust (shapeless) |

Spent Isotope Pellets are never crafted — they come out of the generator.

### Config keys

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `rtgNePerTick` | 30 | 1–10000 | Output from a fresh pellet (NE/t) |
| `rtgHalfLifeDays` | 20 | 1–365 | In-game days per halving of output |
| `rtgCutoffPermille` | 50 | 1–999 | Output (permille of fresh) below which a pellet is spent |

## Stirling Generator

### How it works

Every tick the Stirling Generator looks at its six neighbours, finds the **hottest NeroTech-family
machine** (any NeroTech machine or NeroPower reactor) and reads how far above its ambient
temperature it is — the **gradient**. It then draws a share of that gradient as heat,
`gradient × stirlingDrawPermille ⁄ 1000` (10 %) per tick, capped at `stirlingMaxDrawPerOp` (8), and
never pulls a machine below its ambient level. Every unit of heat drawn becomes
`stirlingNePerHeatUnit` NE (15).

A **cold face** matters. If any neighbour is water, ice, packed ice, blue ice, a snow block,
powder snow or a NeroTech **Radiator**, output gets a **+50 %** bonus. Without a cold face the
usable gradient is **halved** — the engine has nowhere to reject its heat. The GUI shows the
gradient, the heat drawn last tick and whether a cold sink is attached.

Because it removes heat from its neighbour, a Stirling Generator is also a **passive coolant with
a dividend**: parked against a running [Fission Reactor](Fission-Reactor.md) it lowers the
reactor's heat and pays you for it. It has no slots, no fuel, no heat of its own and no failure
ladder; presets and Speed modules scale the output.

### Recipes

| Item | Recipe |
| --- | --- |
| **Stirling Generator** | Iron Ingot, Piston, Iron Ingot / Nero Coil, Machine Frame, Piston / empty, Copper Block, empty |

### Config keys

| Key | Default | Range | What it does |
| --- | --- | --- | --- |
| `stirlingNePerHeatUnit` | 15 | 1–1000 | NE produced per unit of heat drawn |
| `stirlingMaxDrawPerOp` | 8 | 1–1000 | Hard cap on heat drawn per tick |
| `stirlingDrawPermille` | 100 | 1–1000 | Share of the gradient drawn per tick |
| `stirlingColdFaceBonusPermille` | 500 | 0–1000 | Output bonus with a cold face |

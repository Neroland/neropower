# Compatibility notes

How NeroPower shows up in other mods, and what it deliberately does not ship.

## Recipe viewers (EMI / JEI)

**NeroPower ships no EMI or JEI plugin of its own, and needs none.**

- Every crafted NeroPower block and item uses vanilla `minecraft:crafting_shaped` /
  `minecraft:crafting_shapeless` recipes, which both viewers display without any plugin.
- The one machine recipe — `neropower:chemical_processing_spent_fuel_rod` (Spent Fuel Rod →
  Reprocessed Pellet) — is of NeroTech's recipe type **`nerotech:chemical_processing`**. NeroPower
  defines no recipe type or serializer; the JSON is the same shape as NeroTech's own
  `chemical_processing_raw_*.json` files.
- NeroTech's plugins (`compat/emi/NeroTechEmiPlugin`, `compat/jei/NeroTechJeiPlugin`) collect the
  recipes they display **by recipe type, not by namespace**: both walk the synced recipe list and
  keep every holder whose `value().getType()` is the registered `nerotech:chemical_processing`
  type (`JeiSyncedRecipes.byType` and the EMI plugin's private `byType` do the identical check).
  The recipe *id* namespace is never inspected, so `neropower:chemical_processing_spent_fuel_rod`
  appears in the Chemical Processor category alongside NeroTech's own entries in both EMI and JEI.
- The loader builds still put the JEI API (and, with `-PwithEmi`, EMI) on the dev classpath so a
  future NeroPower-specific category could be added the NeroTech way; nothing in `common/` refers
  to either API today, and the shipped jars gain no soft dependency they do not already have.

If NeroPower ever adds a recipe type of its own (a reactor fuel category, say), it should register
a category in the same two plugin styles NeroTech uses and load them behind the same
"only when the viewer is present" guard. Until then there is nothing to maintain here.

## NeroTech

NeroPower is an add-on: every machine extends NeroTech's machine base, registers its block-entity
types with NeroTech's `MachineTypeRegistry` (so NeroTech's loader glue attaches the energy / item
capabilities), uses NeroTech's Configurator for beam links, publishes failure transitions on
NeroTech's `nerotech:machine_failure` event channel and reprocesses spent fuel in NeroTech's
Chemical Processor. NeroTech never references NeroPower; a NeroTech world loads without it.

## Neroland Core

Energy flows only through Core's `EnergyBuffer` / `NeroEnergyStorage` / `EnergyLookup`; FE
bridging to Forge/NeoForge-energy mods (for example Energized Power) stays in Core, so NeroPower
carries no third-party energy code. Config, link alerts, the NeroLink snapshot module, the
player-data eraser and crash-safe `SavedData` loading are all Core services.

## Nerospace (optional)

Not required. When a Core space dimension exists (`neroland:space/dimensions`, which Nerospace's
orbits carry) and `beamCrossDimension=true`, a Beam Transmitter may target an Orbital Receiver in
that dimension from another world. Nothing else in NeroPower reads Nerospace data.

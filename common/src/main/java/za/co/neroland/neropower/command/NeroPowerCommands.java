package za.co.neroland.neropower.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;
import za.co.neroland.nerotech.registry.ModBlocks;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.beam.BeamContent;
import za.co.neroland.neropower.beam.BeamTarget;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;
import za.co.neroland.neropower.environmental.EnvironmentalContent;
import za.co.neroland.neropower.environmental.RadioisotopeGeneratorBlockEntity;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.storage.StorageContent;

/**
 * Creative-only debug commands (cheats / op level 2), NeroTech's {@code /nerotech gallery} recipe.
 * {@code /neropower gallery} builds a showcase around the player with every NeroPower block on
 * display and every machine RUNNING the way survival would wire it:
 * <ul>
 *   <li>a <b>formed 5×5×5 Fission Reactor</b> (Fission Casing shell, the Fission Core mid-wall facing
 *       the walkway, two Control Rod Assemblies inside, four Fuel Rods loaded);</li>
 *   <li>a <b>Battery Bank</b> — a mixed-tier 2×2×2 of Battery Cells under a Battery Bank Controller
 *       parked against the core's free face, so the reactor's output lands in the pool
 *       directly;</li>
 *   <li>a <b>Stirling Generator</b> and a NeroTech <b>Coolant Pump + Radiator</b> on the reactor
 *       cluster, both eating the heat the core conducts out through the controller (the core's five
 *       other faces are shell, so the controller is the only machine that can touch it);</li>
 *   <li>a <b>live beam link</b>: a Beam Transmitter fed by the bank, 24 blocks of clear air, a Beam
 *       Relay, a bend, and a Beam Receiver pushing into a NeroTech Battery Bank with an Analytics
 *       Terminal watching — linked through the block entities on arrival, no Configurator
 *       needed;</li>
 *   <li>a <b>Radioisotope Generator</b> with an Isotope Pellet inserted, charging a NeroTech Battery
 *       Bank behind it;</li>
 *   <li>an <b>Orbital Receiver</b> on a plinth as a static exhibit (its job needs a space
 *       dimension).</li>
 * </ul>
 * {@code /neropower gallery clear} wipes that footprint (blocks + label stands) so a rebuild doesn't
 * stack duplicates.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the command acts at the invoking player's position and records
 * nothing new — the beam link stores the invoking player's UUID as its link owner exactly as a
 * Configurator link would ({@link BeamTransmitterBlockEntity#link}), covered by the same erasure.
 */
public final class NeroPowerCommands {

    /** Shell edge of the reactor exhibit (the larger of the two sizes, so four rod slots are usable). */
    private static final int REACTOR_SIZE = 5;

    /** Clear air between the transmitter and the relay (blocks). */
    private static final int BEAM_GAP = 24;

    private NeroPowerCommands() {
    }

    /**
     * Cross-loader registration: each loader calls this from its command hook (Forge/NeoForge
     * {@code RegisterCommandsEvent}, Fabric {@code CommandRegistrationCallback}) with the dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Player-only and gamemaster-gated (the same predicate Core's commands and the beam
        // Configurator gate on); the executor further restricts to creative.
        dispatcher.register(
                Commands.literal("neropower")
                        .requires(src -> src.getPlayer() != null
                                && Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src))
                        .then(Commands.literal("gallery")
                                .executes(ctx -> runSafely(ctx.getSource(), "gallery",
                                        () -> buildGallery(ctx.getSource())))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> runSafely(ctx.getSource(), "gallery clear",
                                                () -> clearGallery(ctx.getSource()))))));
    }

    private static int runSafely(CommandSourceStack source, String commandName, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException ex) {
            NeroPowerCommon.LOGGER.error("[NeroPower] /neropower {} failed", commandName, ex);
            source.sendFailure(Component.translatable("command.neropower.gallery.failed", commandName));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandBody {
        int run();
    }

    /**
     * Build the gallery at the player's feet. Layout (relative to the player, floor at the player's
     * {@code y}): the reactor cluster straight NORTH with the bank, Stirling and coolant tower on its
     * south face; the beam lane running EAST from the bank and bending south to the receiver; the
     * RTG WEST of the walkway and the Orbital Receiver EAST of it.
     */
    private static int buildGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neropower.gallery.player_only"));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.neropower.gallery.creative_only"));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int ox = origin.getX();
        int oz = origin.getZ();
        int fy = origin.getY();

        // Exhibit floor: Fission Casing — NeroPower's own structural plate, as NeroTech's gallery
        // floors with Fusion Casing. One plaza covers every exhibit and the beam lane.
        BlockState floor = FissionContent.FISSION_CASING.get().defaultBlockState();
        tileFloor(level, floor, ox - 12, ox + 30, oz - 15, oz + 3, fy);

        // REACTOR CLUSTER (NORTH): the shell's south wall is at oz - 9, its core at that wall's
        // centre facing SOUTH — toward the walkway — so the shell extends north behind it.
        BlockPos corePos = buildReactorExhibit(level, ox - 2, oz - 13, fy);

        // The core's free face: the Battery Bank Controller, with its 2×2×2 of cells beneath and
        // beside it. Every NE the reactor makes is pushed straight into the pool.
        BlockPos controllerPos = corePos.south();
        buildBatteryBank(level, controllerPos);

        // Stirling Generator WEST of the controller, on a Radiator (a cold face — the output bonus).
        // The core is walled in on five sides, so the controller is what conducts its heat out; the
        // Stirling draws that gradient and pays it back as NE.
        BlockPos stirlingPos = controllerPos.west();
        placeMachine(level, stirlingPos, EnvironmentalContent.STIRLING_BLOCK.get(), Direction.SOUTH);
        level.setBlockAndUpdate(stirlingPos.below(), ModBlocks.RADIATOR.get().defaultBlockState());
        level.setBlockAndUpdate(stirlingPos.below(2), floor);
        spawnLabelStand(level, stirlingPos.above(2),
                Component.literal("Stirling Generator — reactor heat in, NE out; Radiator cold face"));

        // Coolant tower on the controller: pump on top, Radiator above it (the pump's straight-line
        // scan counts it). Fed by the bank beneath it; it pulls heat off every machine it touches.
        BlockPos pumpPos = controllerPos.above();
        level.setBlockAndUpdate(pumpPos, ModBlocks.COOLANT_PUMP.get().defaultBlockState());
        level.setBlockAndUpdate(pumpPos.above(), ModBlocks.RADIATOR.get().defaultBlockState());
        spawnLabelStand(level, pumpPos.above(3),
                Component.literal("Coolant Pump + Radiator — draining the reactor cluster"));

        // BEAM LANE (EAST): transmitter on the controller's east face (the bank fills it), a clear
        // run east to a relay, then a bend south to the receiver and its NeroTech Battery Bank.
        BlockPos transmitterPos = controllerPos.east();
        BlockPos relayPos = transmitterPos.east(BEAM_GAP + 1);
        BlockPos receiverPos = relayPos.south(6);
        buildBeamExhibit(level, floor, fy, player, transmitterPos, relayPos, receiverPos);

        // RTG (WEST of the walkway): a pellet in, a NeroTech Battery Bank behind it taking the trickle.
        BlockPos rtgPos = new BlockPos(ox - 8, fy + 1, oz - 2);
        placeMachine(level, rtgPos, EnvironmentalContent.RTG_BLOCK.get(), Direction.EAST);
        placeMachine(level, rtgPos.west(), ModBlocks.BATTERY_BANK.get(), Direction.EAST);
        if (level.getBlockEntity(rtgPos) instanceof RadioisotopeGeneratorBlockEntity rtg) {
            rtg.setItem(RadioisotopeGeneratorBlockEntity.INPUT_SLOT,
                    new ItemStack(EnvironmentalContent.ISOTOPE_PELLET.get()));
        }
        spawnLabelStand(level, rtgPos.above(2),
                Component.literal("Radioisotope Generator — one Isotope Pellet, fading with its half-life"));

        // ORBITAL RECEIVER (EAST of the walkway): static — it only does its job in a space dimension.
        BlockPos orbitalPos = new BlockPos(ox + 8, fy + 2, oz - 2);
        level.setBlockAndUpdate(orbitalPos.below(), floor);
        placeMachine(level, orbitalPos, BeamContent.ORBITAL_RECEIVER.get(), Direction.WEST);
        spawnLabelStand(level, orbitalPos.above(2),
                Component.literal("Orbital Receiver — the cross-dimension landing pad (space only)"));

        source.sendSuccess(() -> Component.translatable("command.neropower.gallery.built"), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Wipe the gallery built at the player's feet so a rebuild doesn't stack duplicates. Clears the
     * whole footprint to air from the floor layer ({@code origin.y}) up, leaving the natural ground at
     * {@code origin.y - 1} intact, and removes every non-player entity in the box (label stands). Run
     * it standing where you ran {@code gallery}.
     */
    private static int clearGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.neropower.gallery.player_only"));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("command.neropower.gallery.creative_only"));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int oy = origin.getY();

        // Footprint of buildGallery plus margin: the plaza spans x -12..+30, z -15..+3; the tallest
        // thing is the coolant tower's label at oy + 8 over the reactor's south wall.
        int minX = origin.getX() - 14;
        int maxX = origin.getX() + 32;
        int minZ = origin.getZ() - 17;
        int maxZ = origin.getZ() + 5;
        int topY = oy + 10;

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = oy; y <= topY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        // flag 2 = notify clients, skip the neighbour cascade (NeroTech's recipe).
                        level.setBlock(cursor, air, 2);
                        cleared++;
                    }
                }
            }
        }

        // Remove the spawned label stands — everything in the box but players.
        AABB box = new AABB(minX, oy - 1, minZ, maxX + 1, topY + 4, maxZ + 1);
        int removed = 0;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
            entity.discard();
            removed++;
        }

        int clearedBlocks = cleared;
        int removedEntities = removed;
        source.sendSuccess(() -> Component.translatable("command.neropower.gallery.cleared",
                clearedBlocks, removedEntities), false);
        return Command.SINGLE_SUCCESS;
    }

    // --- exhibits ---------------------------------------------------------------------------------

    /**
     * A formed {@value #REACTOR_SIZE}³ Fission Reactor: a hollow shell of Fission Casing from
     * {@code (bx, fy + 1, bz)}, two Control Rod Assemblies on the interior floor, the core at the
     * centre of the SOUTH wall facing SOUTH (so {@link za.co.neroland.neropower.fission.FissionStructure}
     * finds the shell behind it) placed LAST so its first validation sees the shell whole, and every
     * usable rod slot loaded with a fresh Fuel Rod. Never pre-charged: a full buffer refuses to run.
     *
     * @return the core's position, so the caller can hang the rest of the cluster off its free face
     */
    private static BlockPos buildReactorExhibit(ServerLevel level, int bx, int bz, int fy) {
        int size = REACTOR_SIZE;
        int half = (size - 1) / 2;
        int y0 = fy + 1;
        BlockState casing = FissionContent.FISSION_CASING.get().defaultBlockState();
        BlockState assembly = FissionContent.CONTROL_ROD_ASSEMBLY.get().defaultBlockState();
        BlockPos corePos = new BlockPos(bx + half, y0 + half, bz + size - 1); // south-wall centre
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    BlockPos pos = new BlockPos(bx + dx, y0 + dy, bz + dz);
                    boolean surface = dx == 0 || dx == size - 1
                            || dy == 0 || dy == size - 1
                            || dz == 0 || dz == size - 1;
                    if (pos.equals(corePos)) {
                        continue; // placed last, after the shell
                    }
                    if (surface) {
                        level.setBlockAndUpdate(pos, casing);
                    } else {
                        // Interior: air, with two assemblies on the interior floor's back corners —
                        // the throttle the core counts (output and heat scale down per assembly).
                        boolean rod = dy == 1 && dz == 1 && (dx == 1 || dx == size - 2);
                        level.setBlockAndUpdate(pos, rod ? assembly : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        placeMachine(level, corePos, FissionContent.FISSION_CORE.get(), Direction.SOUTH);
        if (level.getBlockEntity(corePos) instanceof FissionCoreBlockEntity core) {
            for (int slot = 0; slot < FissionCoreBlockEntity.ROD_SLOTS; slot++) {
                core.setItem(slot, new ItemStack(FissionContent.FUEL_ROD.get()));
            }
        }
        spawnLabelStand(level, new BlockPos(bx + half, y0 + size + 1, bz + half),
                Component.literal("Fission Reactor — formed 5×5×5 shell, 4 Fuel Rods, 2 Control Rod Assemblies"));
        return corePos;
    }

    /**
     * The storage exhibit: the Battery Bank Controller at {@code controllerPos} (the reactor core's
     * free face) standing on a 2×2×2 of Battery Cells that reaches down to the floor — tiers mixed
     * (basic, advanced, elite in turn) so the pool's capacity is visibly the sum of its parts. The
     * controller's scan finds the cell under it and floods through the rest face to face.
     */
    private static void buildBatteryBank(ServerLevel level, BlockPos controllerPos) {
        Block[] tiers = {
                StorageContent.BATTERY_CELL_BASIC.get(),
                StorageContent.BATTERY_CELL_ADVANCED.get(),
                StorageContent.BATTERY_CELL_ELITE.get(),
        };
        int i = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    BlockPos pos = controllerPos.offset(dx, -dy, dz);
                    level.setBlockAndUpdate(pos, tiers[i++ % tiers.length].defaultBlockState());
                }
            }
        }
        placeMachine(level, controllerPos, StorageContent.BATTERY_BANK_CONTROLLER_BLOCK.get(), Direction.SOUTH);
        spawnLabelStand(level, controllerPos.south(2).above(),
                Component.literal("Battery Bank — 8 mixed-tier cells pooled under one controller, fed by the core"));
    }

    /**
     * The beamed-power exhibit, linked live: transmitter → relay across {@value #BEAM_GAP} blocks of
     * air, relay → receiver round a bend, receiver pushing into a NeroTech Battery Bank with an
     * Analytics Terminal in range. The relay and receiver stand on Fission Casing plinths so the
     * beam runs level with the bank. Links are made through the block entities with the invoking
     * player as link owner, exactly what a Configurator link records.
     */
    private static void buildBeamExhibit(ServerLevel level, BlockState floor, int fy, ServerPlayer player,
            BlockPos transmitterPos, BlockPos relayPos, BlockPos receiverPos) {
        placeMachine(level, transmitterPos, BeamContent.BEAM_TRANSMITTER.get(), Direction.EAST);
        spawnLabelStand(level, transmitterPos.above(2),
                Component.literal("Beam Transmitter — bank-fed, aimed 24 blocks east at the relay"));

        plinth(level, floor, relayPos, fy);
        placeMachine(level, relayPos, BeamContent.BEAM_RELAY.get(), Direction.SOUTH);
        spawnLabelStand(level, relayPos.above(2),
                Component.literal("Beam Relay — bending the beam south, one hop deeper"));

        plinth(level, floor, receiverPos, fy);
        placeMachine(level, receiverPos, BeamContent.BEAM_RECEIVER.get(), Direction.NORTH);
        BlockPos bankPos = receiverPos.south();
        plinth(level, floor, bankPos, fy);
        placeMachine(level, bankPos, ModBlocks.BATTERY_BANK.get(), Direction.SOUTH);
        spawnLabelStand(level, receiverPos.above(2),
                Component.literal("Beam Receiver — landing the beam in a NeroTech Battery Bank"));

        BlockPos terminalPos = new BlockPos(receiverPos.getX() + 2, fy + 1, receiverPos.getZ());
        placeMachine(level, terminalPos, ModBlocks.ANALYTICS_TERMINAL.get(), Direction.WEST);
        spawnLabelStand(level, terminalPos.above(2),
                Component.literal("Analytics Terminal — watching the beam land"));

        // Live on arrival: the same call the Configurator makes, same owner semantics.
        if (level.getBlockEntity(transmitterPos) instanceof BeamTransmitterBlockEntity transmitter) {
            transmitter.link(BeamTarget.of(relayPos, level), player.getUUID());
        }
        if (level.getBlockEntity(relayPos) instanceof BeamTransmitterBlockEntity relay) {
            relay.link(BeamTarget.of(receiverPos, level), player.getUUID());
        }
    }

    // --- shared exhibit plumbing ------------------------------------------------------------------

    /** Lay the exhibit floor over an inclusive x/z rectangle at the floor layer. */
    private static void tileFloor(ServerLevel level, BlockState floor, int minX, int maxX, int minZ, int maxZ,
            int fy) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(new BlockPos(x, fy, z), floor);
            }
        }
    }

    /** A Fission Casing column from the floor layer up to just under {@code pos}, so a raised exhibit stands on something. */
    private static void plinth(ServerLevel level, BlockState floor, BlockPos pos, int fy) {
        for (int y = fy + 1; y < pos.getY(); y++) {
            level.setBlockAndUpdate(new BlockPos(pos.getX(), y, pos.getZ()), floor);
        }
    }

    /** Place a machine block with its {@code FACING} toward the given direction. */
    private static void placeMachine(ServerLevel level, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(NeroTechMachineBlock.FACING)) {
            state = state.setValue(NeroTechMachineBlock.FACING, facing);
        }
        level.setBlockAndUpdate(pos, state);
    }

    /** Small floating label for gallery display clusters (NeroTech's label-stand recipe, verbatim). */
    private static void spawnLabelStand(ServerLevel level, BlockPos pos, Component name) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // Marker = zero-size bounding box, and ArmorStand#isPickable() is false while it is set.
        // Without it an invisible stand overlapping a machine hijacks pick-block (middle click
        // returned an Armor Stand item instead of the machine). ArmorStand#setMarker is PRIVATE in
        // 26.x, so the flag goes in the only public way: replay {Marker:1b} through Entity#load.
        CompoundTag markerTag = new CompoundTag();
        markerTag.putBoolean("Marker", true);
        stand.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), markerTag));
        stand.refreshDimensions(); // adopt the zero-size marker hitbox immediately
        // load() re-reads every field from that tag, so re-apply the label state afterwards.
        stand.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); // load() zeroed Pos (absent)
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        //? if >=26.3 {
        /*stand.setPermanentlyInvulnerable(true);
        *///?} else {
        stand.setInvulnerable(true);
        //?}
        level.addFreshEntity(stand);
    }
}

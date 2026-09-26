package za.co.neroland.neropower.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.failure.FailureAction;
import za.co.neroland.neropower.failure.FailureContext;
import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureTelegraph;
import za.co.neroland.neropower.failure.FailureThresholds;
import za.co.neroland.neropower.failure.RemoveOnly;

/**
 * Shared base for every NeroPower machine block-entity: NeroTech's {@link NeroTechMachineBlockEntity}
 * (Core energy buffer + upgrades, machine slots, side config, the thermal model, presets, analytics,
 * ownership, the {@code PowerMachine} API) plus the staged failure ladder — a lazily built
 * {@link FailureController} that {@link #tickFailure} feeds from the inherited {@link #heat()} /
 * {@link #heatCapacity()} every tick, telegraphs through {@link FailureTelegraph}, and, at
 * {@link FailureStage#FAILURE}, resolves through {@link #failureAction()}.
 *
 * <p>Subclasses call {@link #tickFailure} from {@code tickMachine} (a machine that never fails simply
 * does not), scale their output by {@link #failureOutputPermille()}, and override
 * {@link #failureAction()} / {@link #failureContext()} for anything beyond "remove this one block".
 */
public abstract class NeroPowerMachineBlockEntity extends NeroTechMachineBlockEntity {

    @Nullable
    private FailureController failure;

    protected NeroPowerMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int machineSlots) {
        super(type, pos, state, machineSlots);
    }

    /**
     * Explicit-buffer variant for machines whose whole point is a buffer other than NeroTech's shared
     * Tier-1 one (the Stage 5 battery cells). Everything else is identical to the standard constructor.
     */
    protected NeroPowerMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int machineSlots, int energyCapacity, int maxTransfer) {
        super(type, pos, state, machineSlots, energyCapacity, maxTransfer);
    }

    // --- identity --------------------------------------------------------------

    /**
     * The stable, non-personal machine id used on NeroTech's failure channel and in failure contexts:
     * the block-entity type's registry id ({@code "neropower:fission_core"}).
     */
    public String machineId() {
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType());
        return id == null ? NeroPowerCommon.MOD_ID + ":unknown" : id.toString();
    }

    // --- failure ladder ----------------------------------------------------------

    /**
     * The failure controller, built from config on first use (so a machine loaded before the config
     * was read never sees stale thresholds) and wired to {@link FailureTelegraph}.
     */
    public FailureController failure() {
        FailureController controller = this.failure;
        if (controller == null) {
            controller = new FailureController(FailureThresholds.fromConfig());
            controller.setListener(this::onFailureStageChanged);
            this.failure = controller;
        }
        return controller;
    }

    /** The current failure stage ({@link FailureStage#STABLE} for a machine that has never ticked). */
    public FailureStage failureStage() {
        return this.failure == null ? FailureStage.STABLE : this.failure.stage();
    }

    /** The failure ladder's output multiplier (permille): full, the unstable penalty, or 0 in FAILURE. */
    public int failureOutputPermille() {
        return this.failure == null ? FailureThresholds.PERMILLE : this.failure.outputPermille();
    }

    /**
     * Advance the failure ladder one tick from the machine's heat; call from {@code tickMachine}. Every
     * transition is telegraphed (event, alarm state, sound, particles, owner alert) by the listener;
     * entering {@link FailureStage#FAILURE} runs {@link #failureAction()} — after which this block
     * entity is normally gone, so callers should return once this reports FAILURE.
     *
     * @return the stage after this tick
     */
    protected FailureStage tickFailure(Level level, BlockPos pos) {
        FailureController controller = failure();
        FailureStage stage = controller.tick(heat(), heatCapacity(), NeroPowerConfig.overloadEnabled());
        if (stage == FailureStage.FAILURE && controller.justEntered() && level instanceof ServerLevel serverLevel) {
            failureAction().apply(serverLevel, pos, failureContext());
        }
        return stage;
    }

    /** What happens at FAILURE. Default: the controller block is removed, nothing else ({@link RemoveOnly}). */
    protected FailureAction failureAction() {
        return RemoveOnly.INSTANCE;
    }

    /**
     * What the failure action is told about this machine. Default: a single-block machine at its own
     * position; multiblock controllers override with their structure's bounds.
     */
    protected FailureContext failureContext() {
        return FailureContext.single(this.worldPosition, owner(), machineId());
    }

    private void onFailureStageChanged(FailureStage from, FailureStage to) {
        FailureTelegraph.announce(this, from, to);
        setChanged();
    }

    // --- persistence (NeroTech's super handles the rest) --------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.failure != null) {
            output.putInt("FailureStage", this.failure.saveStage());
            output.putInt("FailureTicks", this.failure.saveTicksInStage());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int stage = input.getIntOr("FailureStage", 0);
        int ticks = input.getIntOr("FailureTicks", 0);
        if (stage != 0 || ticks != 0 || this.failure != null) {
            failure().load(stage, ticks);
        }
    }
}

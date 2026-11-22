package za.co.neroland.neropower.machine;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.command.BudgetProbe;

/**
 * Shared base for every NeroPower machine block: NeroTech's {@link NeroTechMachineBlock} (directional
 * state, server-side menu open, the Core ticker, ownership capture, the Configurator pass-through)
 * plus the optional {@link #ALARM} block-state property the failure ladder drives for blocks whose
 * model / renderer / sound wants to show it.
 *
 * <p>Subclasses supply their concrete block-entity ({@code newBlockEntity}), its type
 * ({@code machineType()}) and a {@code codec()} built with Core's {@code BlockCodecs.simple}. A block
 * that wants the alarm state overrides {@link #hasAlarm()} to return {@code true} (a constant — it is
 * consulted from the {@link Block} constructor, before any field of the subclass exists).
 */
public abstract class NeroPowerMachineBlock extends NeroTechMachineBlock {

    /**
     * {@code alarm=true} while the machine is at {@link za.co.neroland.neropower.failure.FailureStage#WARNING}
     * or worse. Only present on blocks that {@link #hasAlarm() declare it}; set by
     * {@link za.co.neroland.neropower.failure.FailureTelegraph}, never by hand.
     */
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

    /**
     * In-world state for machines whose art has states but no renderer (decision 012 addendum): set by the
     * block entity from its readout only when the bucket changes, never per tick.
     */
    public enum VisualStatus implements net.minecraft.util.StringRepresentable {
        IDLE("idle"), WORKING("working"), STARVED("starved"), TRIPPED("tripped");

        private final String id;

        VisualStatus(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        /** Readout code (0 rest, 1 work, 2 stall, 3 hot, 4 trip, 5 fail) to status. */
        public static VisualStatus fromReadout(int readout) {
            return switch (readout) {
                case 1, 3 -> WORKING;
                case 2 -> STARVED;
                case 4, 5 -> TRIPPED;
                default -> IDLE;
            };
        }
    }

    /** The {@link VisualStatus} property, on blocks that {@link #hasStatus() declare it}. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<VisualStatus> STATUS =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("status", VisualStatus.class);

    @SuppressWarnings("this-escape")
    protected NeroPowerMachineBlock(Properties properties) {
        super(properties);
        if (hasAlarm() || hasStatus()) {
            BlockState base = this.stateDefinition.any().setValue(FACING, Direction.NORTH);
            if (hasAlarm()) {
                base = base.setValue(ALARM, false);
            }
            if (hasStatus()) {
                base = base.setValue(STATUS, VisualStatus.IDLE);
            }
            this.registerDefaultState(base);
        }
    }

    /**
     * Whether this block carries the {@link #STATUS} property. Must be a constant per class (no field
     * reads): it is called from the {@link Block} constructor.
     */
    protected boolean hasStatus() {
        return false;
    }

    /**
     * Sets {@link #STATUS} on the block at {@code pos} if it has the property and the value differs;
     * clients only (no neighbour update). Server side; a no-op for any other block.
     */
    public static void applyStatus(Level level, net.minecraft.core.BlockPos pos, VisualStatus status) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState now = level.getBlockState(pos);
        if (now.hasProperty(STATUS) && now.getValue(STATUS) != status) {
            level.setBlock(pos, now.setValue(STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Whether this block carries the {@link #ALARM} property. Must be a constant per class (no
     * field reads) — it is called from the {@link Block} constructor.
     */
    protected boolean hasAlarm() {
        return false;
    }

    /**
     * NeroTech's server ticker, timed for {@code /neropower budget} while a sampling window is open
     * (one volatile read otherwise). Client tickers are returned unchanged.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        BlockEntityTicker<T> inner = super.getTicker(level, state, type);
        if (inner == null || level.isClientSide()) {
            return inner;
        }
        return (lvl, pos, st, be) -> {
            if (!BudgetProbe.sampling()) {
                inner.tick(lvl, pos, st, be);
                return;
            }
            long start = System.nanoTime();
            inner.tick(lvl, pos, st, be);
            long spent = System.nanoTime() - start;
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            BudgetProbe.record(id == null ? "unknown" : id.toString(), spent);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        if (hasAlarm()) {
            builder.add(ALARM);
        }
        if (hasStatus()) {
            builder.add(STATUS);
        }
    }
}

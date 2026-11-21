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

    @SuppressWarnings("this-escape")
    protected NeroPowerMachineBlock(Properties properties) {
        super(properties);
        if (hasAlarm()) {
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(ALARM, false));
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
    }
}

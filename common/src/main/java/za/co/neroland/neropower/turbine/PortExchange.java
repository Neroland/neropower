package za.co.neroland.neropower.turbine;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.platform.FluidLookup;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.ChannelConfig;
import za.co.neroland.nerolandcore.sideconfig.SideConfigComponent;
import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.nerotech.machine.MediumTransfer;

/**
 * The world side of {@link PortTransfer}: hands a shared tank's fluid to, or draws it from, the blocks
 * touching a port or condenser through Core's {@link FluidLookup} (a turbine port, a reactor port, a
 * condenser, a Core or NeroTech tank, any mod's tank on the same capability). Faces follow the block's
 * own FLUID side configuration. Every move goes through NeroTech's conserving {@link MediumTransfer#move}
 * (simulate both sides, then move exactly what lands, refunding any shortfall). Only loaded neighbours are
 * queried; nothing is loaded. Moves fluid only; no player data.
 */
public final class PortExchange {

    private PortExchange() {
    }

    @Nullable
    private static ChannelConfig channel(@Nullable SideConfigComponent config) {
        if (config == null || !config.config().has(Channel.FLUID)) {
            return null;
        }
        return config.config().get(Channel.FLUID);
    }

    /**
     * Push {@code own}'s fluid out of every face {@link PortTransfer#pushes} allows.
     *
     * @param guarded steam / exhaust steam: on automatic faces, only into neighbours that refuse water
     * @return mB moved
     */
    public static long push(Level level, BlockPos pos, @Nullable SideConfigComponent config, NeroFluidStorage own,
            boolean guarded, int interval) {
        ChannelConfig cfg = channel(config);
        if (cfg == null) {
            return 0L;
        }
        long budget = PortTransfer.budget(interval);
        long total = 0L;
        for (Direction side : Direction.values()) {
            Fluid fluid = own.getFluid();
            if (own.getAmount() <= 0L || fluid == null || fluid == Fluids.EMPTY) {
                break;
            }
            SideMode mode = config.config().modeAbsolute(Channel.FLUID, config.facing(), side);
            if (mode == null || mode == SideMode.DISABLED) {
                continue;
            }
            BlockPos at = pos.relative(side);
            if (!level.isLoaded(at)) {
                continue;
            }
            NeroFluidStorage sink = FluidLookup.INSTANCE.find(level, at, side.getOpposite());
            if (sink == null || sink == own) {
                continue;
            }
            boolean takesWater = guarded && sink.fill(Fluids.WATER, 1L, true) > 0L;
            if (!PortTransfer.pushes(mode, cfg.autoEject(), guarded, takesWater)) {
                continue;
            }
            total += MediumTransfer.move(store(own, fluid), store(sink, fluid), budget);
        }
        return total;
    }

    /**
     * Pull fluid {@code wants} accepts into {@code own} through every face {@link PortTransfer#pulls} allows.
     *
     * @return mB moved
     */
    public static long pull(Level level, BlockPos pos, @Nullable SideConfigComponent config, NeroFluidStorage own,
            Predicate<Fluid> wants, int interval) {
        ChannelConfig cfg = channel(config);
        if (cfg == null || !cfg.autoInput()) {
            return 0L;
        }
        long budget = PortTransfer.budget(interval);
        long total = 0L;
        for (Direction side : Direction.values()) {
            SideMode mode = config.config().modeAbsolute(Channel.FLUID, config.facing(), side);
            if (!PortTransfer.pulls(mode, cfg.autoInput())) {
                continue;
            }
            BlockPos at = pos.relative(side);
            if (!level.isLoaded(at)) {
                continue;
            }
            NeroFluidStorage source = FluidLookup.INSTANCE.find(level, at, side.getOpposite());
            if (source == null || source == own || source.getAmount() <= 0L) {
                continue;
            }
            Fluid fluid = source.getFluid();
            if (fluid == null || fluid == Fluids.EMPTY || !wants.test(fluid)) {
                continue;
            }
            total += MediumTransfer.move(store(source, fluid), store(own, fluid), budget);
        }
        return total;
    }

    private static MediumTransfer.Store store(NeroFluidStorage storage, Fluid fluid) {
        return new MediumTransfer.Store() {
            @Override
            public long drain(long max, boolean simulate) {
                return storage.drain(max, simulate);
            }

            @Override
            public long fill(long amount, boolean simulate) {
                return storage.fill(fluid, amount, simulate);
            }
        };
    }
}

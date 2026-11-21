package za.co.neroland.neropower.backend;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.sideconfig.SlotGroup;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.failure.FailureAction;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureThresholds;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.radiation.RadiationConfig;
import za.co.neroland.neropower.registry.content.BackEndContent;

/**
 * Spent Fuel Rack ({@code spent_fuel_pool}) — where a Hot Spent Fuel Rod sheds its decay heat before it
 * may be reprocessed. Four rod slots. Each hot rod adds {@code poolRodHeatPerTick} heat; each water
 * source block on the rack's five non-bottom faces removes {@code poolCoolingPerContact}. While the
 * failure ladder is calm (below WARNING) every hot rod's cooling clock runs, and at
 * {@code poolCoolTicks} the rod becomes a Spent Fuel Rod in place ({@link PoolMath}). Hot rods cool
 * nowhere else.
 *
 * <p>The rack is a NeroTech heat machine, so a Stirling Generator beside it draws the decay heat
 * through the ordinary {@code PowerMachine.extractHeat} seam and pays for the cooling. An overheating
 * rack climbs the shared failure ladder; at FAILURE it sends a radiation burst
 * ({@link BackEndHooks#bursts()}) and keeps its rods — no blast, no item loss — and while it sits at
 * FAILURE it is a {@link RadiationEmitter}.
 *
 * <p>No energy buffer. Items: hot rods go in through any face (only into an empty slot); only cooled
 * rods come out. Breaking the rack drops its rods once, through NeroTech's container drop; the loot
 * table drops only the rack.
 */
public class SpentFuelPoolBlockEntity extends NeroPowerMachineBlockEntity implements RadiationEmitter {

    /** How often the water contacts are recounted (five fluid lookups). */
    static final int CONTACT_REFRESH_TICKS = 20;

    private final int[] cooling = new int[PoolMath.SLOTS];

    /** Water-source contacts, recounted every {@link #CONTACT_REFRESH_TICKS}; -1 until first counted. */
    private int contacts = -1;

    /** Render surface (BLOCK-CONTRACT): coolStep ×4 then waterContacts, packed; compare-and-record state. */
    private int syncedRender = -1;
    private final int[] clientCoolStep = new int[PoolMath.SLOTS];
    private int clientContacts;

    private boolean emitterRegistered;

    /** Synced readout (0..5) and stall cause (0..15), see {@link BackEndReadout}. */
    private int readout;
    private int stallCause;

    public SpentFuelPoolBlockEntity(BlockPos pos, BlockState state) {
        super(BackEndContent.SPENT_FUEL_POOL_TYPE.get(), pos, state, PoolMath.SLOTS, 0, 0);
        SlotGroup rods = SlotGroup.of("rods", 0, 1, 2, 3);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ITEM, rods, rods)
                .defaultPreset(SidePreset.STORAGE)
                .build());
    }

    /** Whether {@code stack} is a Hot Spent Fuel Rod (resolved by id; the item belongs to the fission line). */
    public static boolean isHotRod(ItemStack stack) {
        return !stack.isEmpty()
                && BackEndContent.HOT_SPENT_FUEL_ROD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** A rack is never load-shed (it draws no power). */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        if (!this.emitterRegistered) {
            this.emitterRegistered = true;
            BackEndHooks.emitters().add(level, this);
        }
        if (this.contacts < 0 || (level.getGameTime() + exchangePhase()) % CONTACT_REFRESH_TICKS == 0) {
            this.contacts = countWaterContacts(level, pos);
        }

        int hot = 0;
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            if (isHotRod(this.items.get(i))) {
                hot++;
            }
        }

        // Decay heat in (not preset-scaled: a rod's decay does not care about the speed setting),
        // water out, never below ambient.
        int in = PoolMath.heatIn(hot, BackEndConfig.poolRodHeatPerTick());
        if (in > 0) {
            this.heat = Math.min(heatCapacity(), this.heat + in);
            setChanged();
        }
        int out = PoolMath.heatOut(this.contacts, BackEndConfig.poolCoolingPerContact());
        if (out > 0) {
            extractHeat(out, ambient());
        }

        FailureStage stage = tickFailure(level, pos);
        boolean calm = stage == FailureStage.STABLE;

        int coolTicks = BackEndConfig.poolCoolTicks();
        int best = 0;
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            if (!isHotRod(this.items.get(i))) {
                this.cooling[i] = 0;
                continue;
            }
            this.cooling[i] = PoolMath.advance(this.cooling[i], calm);
            if (PoolMath.cooled(this.cooling[i], coolTicks)) {
                this.items.set(i, new ItemStack(FissionContent.SPENT_FUEL_ROD.get()));
                this.cooling[i] = 0;
                hot--;
                pulseClient();
            } else {
                best = Math.max(best, this.cooling[i]);
            }
            setChanged();
        }

        // The work bar follows the rod closest to cooled.
        this.maxProgress = hot > 0 ? coolTicks : 0;
        this.progress = hot > 0 ? Math.min(best, coolTicks) : 0;

        this.stallCause = BackEndReadout.poolCause(hot, this.contacts);
        this.readout = BackEndReadout.readout(stage == FailureStage.FAILURE, !calm && stage != FailureStage.FAILURE,
                this.stallCause, hot > 0);

        if (hot <= 0) {
            reportStatus(MachineStatus.IDLE);
        } else if (!calm) {
            reportStatus(MachineStatus.THROTTLED);
        } else if (this.contacts <= 0) {
            reportStatus(MachineStatus.STARVED);
        }
        setActive(hot > 0 && calm);
    }

    /** Water source blocks on the five non-bottom faces (0..5). */
    static int countWaterContacts(Level level, BlockPos pos) {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) {
                continue;
            }
            FluidState fluid = level.getFluidState(pos.relative(side));
            if (fluid.isSource() && fluid.is(FluidTags.WATER)) {
                count++;
            }
        }
        return count;
    }

    /** The rack's last counted water contacts (0..5). */
    public int waterContacts() {
        return Math.max(0, this.contacts);
    }

    /** Ticks until the rod closest to cooled is done (0 with no hot rod). */
    public long ticksToNextCooled() {
        int coolTicks = BackEndConfig.poolCoolTicks();
        long best = 0L;
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            if (isHotRod(this.items.get(i))) {
                long left = Math.max(0L, (long) coolTicks - this.cooling[i]);
                best = best == 0L ? left : Math.min(best, left);
            }
        }
        return best;
    }

    // --- failure: a burst, the rods stay ---------------------------------------------------------

    @Override
    protected FailureAction failureAction() {
        return (level, pos, ctx) -> BackEndHooks.bursts().poolFailure(level, pos);
    }

    // --- RadiationEmitter ------------------------------------------------------------------------

    @Override
    public BlockPos emitterPos() {
        return this.worldPosition;
    }

    /** A rack is a source only while its ladder sits at FAILURE. */
    @Override
    public boolean emitting() {
        return failureStage() == FailureStage.FAILURE;
    }

    @Override
    public int unshieldedFaces() {
        return this.level == null ? 6 : Shielding.unshieldedFaces(this.level, this.worldPosition);
    }

    @Override
    public void setRemoved() {
        if (this.emitterRegistered && this.level != null) {
            this.emitterRegistered = false;
            BackEndHooks.emitters().remove(this.level, this);
        }
        super.setRemoved();
    }

    // --- slots: hot rods in (empty slot only), cooled rods out ------------------------------------

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < PoolMath.SLOTS && this.items.get(slot).isEmpty() && isHotRod(stack);
    }

    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot >= 0 && slot < PoolMath.SLOTS && this.items.get(slot).is(FissionContent.SPENT_FUEL_ROD.get());
    }

    /** A slot whose rod changes starts its cooling clock again: progress never carries to another rod. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < PoolMath.SLOTS && stack != this.items.get(slot)) {
            this.cooling[slot] = 0;
        }
        super.setItem(slot, stack);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = super.removeItem(slot, amount);
        if (slot >= 0 && slot < PoolMath.SLOTS && this.items.get(slot).isEmpty()) {
            this.cooling[slot] = 0;
        }
        return taken;
    }

    /** Cooling clock of {@code slot} (tests and the menu). */
    public int cooling(int slot) {
        return slot >= 0 && slot < PoolMath.SLOTS ? this.cooling[slot] : 0;
    }

    // --- menu sync (spec: screens/spent_fuel_pool.md) ----------------------------------------------

    /** Extra-data layout; read only through {@code SpentFuelPoolMenu}'s getters. */
    static final int D_COOLING = 0;          // 4: cooling permille per slot
    static final int D_LEFT = 4;             // 8: ticks left per slot, hi2/lo2 pairs
    static final int D_ROD_STATE = 12;       // 4: 0 empty, 1 hot, 2 cooled
    static final int D_CONTACTS = 16;
    static final int D_ROD_HEAT = 17;
    static final int D_COOLING_PER_CONTACT = 18;
    static final int D_STAGE = 19;
    static final int D_STAGE_HEAT = 20;      // heat permille of capacity
    static final int D_STAGE_NEXT = 21;      // entry permille of the next stage (1000 at FAILURE)
    static final int D_RADIATION_ON = 22;
    static final int D_READOUT = 23;
    static final int D_STALL = 24;
    static final int D_COUNT = 25;

    @Override
    protected int extraDataCount() {
        return D_COUNT;
    }

    @Override
    protected int extraData(int index) {
        int coolTicks = BackEndConfig.poolCoolTicks();
        if (index >= D_COOLING && index < D_COOLING + PoolMath.SLOTS) {
            int slot = index - D_COOLING;
            return isHotRod(this.items.get(slot)) ? PoolMath.permille(this.cooling[slot], coolTicks) : 0;
        }
        if (index >= D_LEFT && index < D_LEFT + 2 * PoolMath.SLOTS) {
            int slot = (index - D_LEFT) / 2;
            long left = isHotRod(this.items.get(slot)) ? Math.max(0L, (long) coolTicks - this.cooling[slot]) : 0L;
            return (index - D_LEFT) % 2 == 0 ? GaugeTail.hi2(left) : GaugeTail.lo2(left);
        }
        if (index >= D_ROD_STATE && index < D_ROD_STATE + PoolMath.SLOTS) {
            ItemStack stack = this.items.get(index - D_ROD_STATE);
            return stack.isEmpty() ? 0 : isHotRod(stack) ? 1 : 2;
        }
        return switch (index) {
            case D_CONTACTS -> waterContacts();
            case D_ROD_HEAT -> BackEndConfig.poolRodHeatPerTick();
            case D_COOLING_PER_CONTACT -> BackEndConfig.poolCoolingPerContact();
            case D_STAGE -> failureStage().code();
            case D_STAGE_HEAT -> heatPermille();
            case D_STAGE_NEXT -> nextStagePermille(failure().thresholds(), failureStage());
            case D_RADIATION_ON -> radiationOn() ? 1 : 0;
            case D_READOUT -> this.readout;
            case D_STALL -> this.stallCause;
            default -> 0;
        };
    }

    /** Entry permille of the stage after {@code stage} (1000 once at FAILURE). */
    static int nextStagePermille(FailureThresholds thresholds, FailureStage stage) {
        FailureStage next = stage.next();
        return next == null ? FailureThresholds.PERMILLE : thresholds.entryPermille(next);
    }

    private boolean radiationOn() {
        return this.level instanceof ServerLevel serverLevel
                && RadiationConfig.enabled(serverLevel.getServer().isDedicatedServer());
    }

    // --- render surface (update tag only) ---------------------------------------------------------

    private int coolStep(int slot) {
        ItemStack stack = this.items.get(slot);
        return PoolMath.coolStep(stack.isEmpty(), isHotRod(stack), this.cooling[slot], BackEndConfig.poolCoolTicks());
    }

    private int packedRender() {
        int packed = this.readout * 16 + this.stallCause;
        packed = packed * 8 + waterContacts();
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            packed = packed * 8 + coolStep(i);
        }
        return packed;
    }

    @Override
    protected boolean renderSyncDirty() {
        int packed = packedRender();
        if (packed != this.syncedRender) {
            this.syncedRender = packed;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            tag.putInt("CoolStep" + i, coolStep(i));
        }
        tag.putInt("WaterContacts", waterContacts());
        tag.putInt("NpReadout", this.readout);
        tag.putInt("NpStall", this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            this.clientCoolStep[i] = Math.max(0, Math.min(PoolMath.MAX_COOL_STEP, input.getIntOr("CoolStep" + i, 0)));
        }
        this.clientContacts = PoolMath.clampContacts(input.getIntOr("WaterContacts", 0));
        this.readout = input.getIntOr("NpReadout", 0);
        this.stallCause = input.getIntOr("NpStall", 0);
    }

    /** Synced readout (0..5, {@link BackEndReadout}). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause (0..15, {@link BackEndReadout}). */
    public int renderStallCause() {
        return this.stallCause;
    }

    /** BER input: {@code coolStep} of {@code slot} (0..4, see {@link PoolMath#coolStep}); client side, last synced. */
    public int renderCoolStep(int slot) {
        return slot >= 0 && slot < PoolMath.SLOTS ? this.clientCoolStep[slot] : 0;
    }

    /** BER input: water contacts (0..5); client side, last synced. */
    public int renderWaterContacts() {
        return this.clientContacts;
    }

    // --- persistence ------------------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            output.putInt("Cooling" + i, this.cooling[i]);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            this.cooling[i] = Math.max(0, input.getIntOr("Cooling" + i, 0));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.spent_fuel_pool");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SpentFuelPoolMenu(containerId, playerInventory, this, this.data);
    }
}

package za.co.neroland.neropower.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerotech.menu.MachineMenu;

/**
 * Opens the Power Guide from a NeroPower machine screen: the small Guide button above the panel's
 * top-right corner opens the chapter (and step) for the block whose screen it is. Client only; no
 * packet, no state. The block is the one the menu reports, else the one the player is looking at.
 */
public final class PowerGuideLinks {

    /** Button size; it sits just above the panel so it never covers the title or a gauge. */
    public static final int W = 40;
    public static final int H = 12;

    private PowerGuideLinks() {
    }

    /** The Guide button for a machine screen whose panel starts at {@code (leftPos, topPos)}. */
    public static Button button(Screen screen, MachineMenu menu, int leftPos, int topPos, int imageWidth) {
        return Button.builder(Component.translatable("gui.neropower.guide.button"), b -> open(screen, menu))
                .bounds(leftPos + imageWidth - W, topPos - H - 1, W, H)
                .tooltip(Tooltip.create(Component.translatable("gui.neropower.guide.button.tooltip")))
                .build();
    }

    /** Open the guide at this machine's chapter; Back returns to {@code parent}. */
    public static void open(Screen parent, @Nullable MachineMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        PowerGuideScreen guide = new PowerGuideScreen(parent, blockId(mc, menu));
        //? if >=26.2 {
        mc.gui.setScreen(guide);
        //?} else {
        /*mc.setScreen(guide);
        *///?}
    }

    @Nullable
    private static String blockId(Minecraft mc, @Nullable MachineMenu menu) {
        if (mc.level == null) {
            return null;
        }
        BlockPos pos = menu == null ? null : menu.machinePos();
        if (pos == null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getBlockPos();
        }
        if (pos == null) {
            return null;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock());
        return id == null ? null : id.toString();
    }
}

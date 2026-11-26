package za.co.neroland.neropower.radiation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neropower.radiation.DosimeterBadgeItem;
import za.co.neroland.neropower.radiation.GeigerReading;
import za.co.neroland.neropower.radiation.RadiationMath;
import za.co.neroland.neropower.registry.content.RadiationContent;

/**
 * Client-side read model of the Geiger Counter HUD (the art package's
 * spec; the renderer draws from here and owns nothing else). Everything is read from the local
 * player's own copy of the held counter's {@code neropower:geiger_reading} component and from the
 * badges in their own inventory; nothing is requested or sent. Also plays the clicks:
 * {@link #clientTick()} once per client tick (loader wiring by the integration agent).
 *
 * <p>Clicks are Poisson-timed at {@link RadiationMath#clickRate(int)}, at most one a tick; from 10 a
 * second the chatter sound replaces single clicks (one per second). No click while a screen is open,
 * the HUD is hidden, the player is a spectator, there is no data or radiation is off.
 */
public final class GeigerReadModel {

    /** Clicks per second from which the chatter replaces single clicks. */
    public static final double CHATTER_RATE = 10.0D;

    private static final RandomSource RANDOM = RandomSource.create();

    /** Ticks the current counter has been held (for the HUD's "no data yet" and "off" banner). */
    private static int heldTicks;
    private static int chatterCooldown;
    /** Smoothed needle level (eased over 10 ticks between updates). */
    private static float needle;
    private static float needlePrev;

    private GeigerReadModel() {
    }

    /** The held counter's reading (main hand first), or null when no counter is held. */
    public static GeigerReading held() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack stack = main.is(RadiationContent.GEIGER_COUNTER.get()) ? main : player.getOffhandItem();
        if (!stack.is(RadiationContent.GEIGER_COUNTER.get())) {
            return null;
        }
        GeigerReading reading = RadiationContent.geigerReading(stack);
        return reading == null ? new GeigerReading(java.util.List.of(), 0, true) : reading;
    }

    /**
     * Whether the HUD should show and click (counter in a hand, not spectator, no screen open: the mouse
     * is grabbed only in game). F1 hiding is the HUD layer's own check.
     */
    public static boolean visible() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.player.isSpectator() && mc.mouseHandler.isMouseGrabbed()
                && held() != null;
    }

    /** Whether a counter is held and its data has not arrived yet ("-- pl", hollow grid). */
    public static boolean awaitingData() {
        GeigerReading r = held();
        return r != null && r.enabled() && !r.hasData();
    }

    /** Whether the server says radiation is off. */
    public static boolean radiationOff() {
        GeigerReading r = held();
        return r != null && !r.enabled();
    }

    /** Ticks since the counter was taken in hand (the "disabled on this server" banner shows for 100). */
    public static int heldTicks() {
        return heldTicks;
    }

    /** The holder's chunk level in pall (0 without data). */
    public static int level() {
        GeigerReading r = held();
        return r == null || !r.hasData() ? 0 : r.centre();
    }

    /** The holder's band 0..4. */
    public static int band() {
        return RadiationMath.band(level());
    }

    /** Grid cell {@code index} 0..8, rows north to south; its level. */
    public static int cell(int index) {
        GeigerReading r = held();
        return r == null || !r.hasData() ? 0 : r.cell(index);
    }

    /** Grid cell band 0..4. */
    public static int cellBand(int index) {
        return RadiationMath.band(cell(index));
    }

    /** Registered sources within 16 blocks, 0..99 (99 = "99+"). */
    public static int sourcesNear() {
        GeigerReading r = held();
        return r == null ? 0 : r.sources();
    }

    /** Whether a Dosimeter Badge is in the local inventory (adds the badge line). */
    public static boolean badgeCarried() {
        return highestBadge() >= 0;
    }

    /** The highest reading among carried badges, or -1 with none. */
    public static int highestBadge() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return -1;
        }
        int best = -1;
        Container inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(RadiationContent.DOSIMETER_BADGE.get())) {
                best = Math.max(best, DosimeterBadgeItem.reading(stack));
            }
        }
        return best;
    }

    /** The eased needle level for this frame (reduced motion: pass {@code partialTick = 1}). */
    public static float needle(float partialTick) {
        return needlePrev + (needle - needlePrev) * partialTick;
    }

    /**
     * The Geiger item-model frame 0..2 for a stack, by its own reading, but only for a counter in the local
     * player's own hand: another player's counter, a dropped one or a stored one draws idle, whatever reading
     * it may still carry.
     */
    public static int frame(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || (stack != player.getMainHandItem() && stack != player.getOffhandItem())) {
            return 0;
        }
        GeigerReading r = RadiationContent.geigerReading(stack);
        return r == null || !r.hasData() ? 0 : RadiationMath.geigerFrame(r.centre());
    }

    /** One client tick: hold timer, needle easing and the clicks. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        GeigerReading reading = mc.player == null ? null : held();
        if (reading == null) {
            heldTicks = 0;
            needle = needlePrev = 0.0F;
            return;
        }
        heldTicks = Math.min(heldTicks + 1, 100_000);
        int target = reading.hasData() ? reading.centre() : 0;
        needlePrev = needle;
        needle += (target - needle) / 10.0F;
        if (!reading.hasData() || !visible()) {
            return;
        }
        double rate = RadiationMath.clickRate(target);
        if (rate >= CHATTER_RATE) {
            if (--chatterCooldown <= 0) {
                chatterCooldown = 20;
                mc.player.playSound(RadiationContent.GEIGER_CHATTER.get(), 0.6F, 1.0F);
            }
            return;
        }
        chatterCooldown = 0;
        // P(at least one click this tick) for a Poisson process at `rate` per second.
        double p = 1.0D - Math.exp(-rate / 20.0D);
        if (RANDOM.nextDouble() < p) {
            mc.player.playSound(RadiationContent.GEIGER_CLICK.get(), 0.5F, 0.9F + RANDOM.nextFloat() * 0.2F);
        }
    }
}

package za.co.neroland.neropower.menu;

/**
 * Screen controls over vanilla's container button click: the screen calls
 * {@code minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MenuButtons.encode(action, value))}
 * and the server's {@code clickMenuButton(Player, int id)} decodes it. {@code id = action * 1000 + value},
 * {@code value} 0..999; actions are per-menu constants. Toggles send 0/1, buttons 0, sliders a bucket.
 * Server side: validate the action, {@link #clamp} the value, never trust the range, act only on the
 * block the menu is bound to, return false for anything unknown.
 */
public final class MenuButtons {

    /** Values per action. */
    public static final int SPAN = 1000;
    /** Largest value one id can carry. */
    public static final int MAX_VALUE = SPAN - 1;

    private MenuButtons() {
    }

    /** The button id for {@code action} carrying {@code value} (clamped to 0..999; a negative action reads as 0). */
    public static int encode(int action, int value) {
        return Math.max(0, action) * SPAN + clampValue(value);
    }

    /** The action of a button id (-1 for a negative id). */
    public static int action(int id) {
        return id < 0 ? -1 : id / SPAN;
    }

    /** The value of a button id (0 for a negative id). */
    public static int value(int id) {
        return id < 0 ? 0 : id % SPAN;
    }

    /** {@code value} clamped to {@code min..max}. */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampValue(int value) {
        return clamp(value, 0, MAX_VALUE);
    }
}

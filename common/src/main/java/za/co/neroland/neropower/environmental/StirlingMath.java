package za.co.neroland.neropower.environmental;

/**
 * Pure integer maths for the Stirling Generator: how much heat one tick draws from the hottest
 * neighbour's gradient above ambient, and how much NE that heat becomes. Kept Minecraft-free so the
 * curve is unit-tested; the block entity only supplies the gradient and the cold-face flag.
 */
public final class StirlingMath {

    private StirlingMath() {
    }

    /**
     * The gradient the engine can actually use: the raw {@code heat - ambient} of the hot neighbour,
     * halved when the engine has no cold face to reject heat into. Never negative.
     */
    public static int usableGradient(int gradient, boolean hasColdFace) {
        int g = Math.max(0, gradient);
        return hasColdFace ? g : g / 2;
    }

    /**
     * Heat to draw this tick: {@code gradient × drawPermille / 1000}, capped at {@code maxDraw} — and
     * at least 1 while any gradient exists, so a small hot neighbour still turns the engine over.
     *
     * @param gradient the usable gradient ({@link #usableGradient}); 0 or less draws nothing
     * @param drawPermille share of the gradient drawn per tick (permille)
     * @param maxDraw hard cap per tick
     */
    public static int drawFor(int gradient, int drawPermille, int maxDraw) {
        if (gradient <= 0 || drawPermille <= 0 || maxDraw <= 0) {
            return 0;
        }
        long draw = (long) gradient * drawPermille / 1000L;
        return (int) Math.max(1L, Math.min(maxDraw, draw));
    }

    /**
     * NE produced from {@code drawn} heat units: {@code drawn × nePerUnit}, raised by
     * {@code coldFaceBonusPermille} (pass 0 when there is no cold face). Never negative.
     */
    public static int outputFor(int drawn, int nePerUnit, int coldFaceBonusPermille) {
        if (drawn <= 0 || nePerUnit <= 0) {
            return 0;
        }
        long base = (long) drawn * nePerUnit;
        long bonus = Math.max(0, coldFaceBonusPermille);
        return (int) Math.min(Integer.MAX_VALUE, base * (1000L + bonus) / 1000L);
    }
}

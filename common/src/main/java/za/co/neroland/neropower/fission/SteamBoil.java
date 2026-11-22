package za.co.neroland.neropower.fission;

/**
 * Steam mode's boil step over the reactor's <b>shared</b> tanks: the heat the
 * core makes this tick boils water drawn from any port into steam raised in any port with room, so one
 * port can face a turbine inlet while another takes the condenser's water. Pure JVM.
 *
 * <p>Conservation: {@code waterUsed = heatTaken x steamPerHeat}, and the steam raised is at most that (less
 * only by the failure / economizer output factor already applied) and never more than the
 * shared room.
 *
 * @param heatTaken the heat units carried off as steam
 * @param waterUsed mB of water to draw from the shared coolant tank
 * @param steam     mB of steam to raise into the shared steam tank
 */
public record SteamBoil(int heatTaken, long waterUsed, long steam) {

    public static final SteamBoil NONE = new SteamBoil(0, 0L, 0L);

    /**
     * Boil up to {@code heat} units.
     *
     * @param water         mB of water in the shared coolant tank
     * @param room          mB of free room in the shared steam tank
     * @param steamPermille output factor (1000 = every mB of water becomes a mB of steam)
     */
    public static SteamBoil boil(int heat, int steamPerHeat, int steamPermille, long water, long room) {
        if (heat <= 0 || steamPerHeat <= 0 || water <= 0L || room <= 0L) {
            return NONE;
        }
        long limit = Math.min(water, room) / steamPerHeat;
        int take = (int) Math.min(heat, limit);
        if (take <= 0) {
            return NONE;
        }
        long used = (long) take * steamPerHeat;
        long raised = Math.min(FissionMath.steamFromHeat(take, steamPerHeat, steamPermille), room);
        return new SteamBoil(take, used, Math.min(raised, used));
    }
}

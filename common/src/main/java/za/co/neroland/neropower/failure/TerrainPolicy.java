package za.co.neroland.neropower.failure;

import java.util.Locale;

/**
 * The {@code terrainDamageMode} rule, Minecraft-free so every mode is unit-tested:
 * {@code "on"} / {@code "off"} are explicit; {@code "auto"} (the default) — and anything
 * unrecognised, blank or null — is <b>off on a dedicated server</b>, where one unattended reactor
 * would otherwise crater a shared world, and on in singleplayer / LAN, where the blast is the
 * player's own consequence to keep. {@code NeroPowerConfig.terrainDamage(boolean)} delegates here.
 */
public final class TerrainPolicy {

    public static final String ON = "on";
    public static final String OFF = "off";
    public static final String AUTO = "auto";

    private TerrainPolicy() {
    }

    /**
     * The canonical mode for a raw setting: {@code on}, {@code off}, or {@code auto} for anything
     * else. Case- and space-insensitive.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return AUTO;
        }
        String mode = raw.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case ON, OFF -> mode;
            default -> AUTO;
        };
    }

    /**
     * Whether a failure may break blocks for this runtime.
     *
     * @param mode      the raw {@code terrainDamageMode} value (normalised here)
     * @param dedicated {@code MinecraftServer.isDedicatedServer()} for the running server
     */
    public static boolean resolve(String mode, boolean dedicated) {
        return switch (normalize(mode)) {
            case ON -> true;
            case OFF -> false;
            default -> !dedicated;
        };
    }
}

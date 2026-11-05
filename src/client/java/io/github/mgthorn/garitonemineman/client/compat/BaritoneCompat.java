package io.github.mgthorn.garitonemineman.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether a usable Baritone is installed. Both Baritone builds use the mod id {@code baritone}, but only
 * the {@code baritone-api-fabric} build keeps the {@code baritone.api} class names; the standalone build
 * obfuscates them, so this mod can't talk to it. Code touching {@code baritone.api} classes lives in
 * {@code BaritoneBridge} and must only run when {@link #isLoaded()} is true.
 */
public class BaritoneCompat {
    public static final String MOD_ID = "baritone";

    private static final boolean MOD_PRESENT = FabricLoader.getInstance().isModLoaded(MOD_ID);
    private static final boolean API_PRESENT = MOD_PRESENT && hasApiClasses();

    public static boolean isLoaded() {
        return API_PRESENT;
    }

    /** Baritone is installed, but a build without the API (the standalone jar). */
    public static boolean isWrongBuild() {
        return MOD_PRESENT && API_PRESENT == false;
    }

    /** Translation key of the warning to show on Baritone-dependent options, or null when Baritone is usable. */
    public static String getWarningKey() {
        if (API_PRESENT) {
            return null;
        }

        return MOD_PRESENT ? "mineman.config.warning.baritone_wrong_build" : "mineman.config.warning.baritone_missing";
    }

    private static boolean hasApiClasses() {
        try {
            Class.forName("baritone.api.BaritoneAPI", false, BaritoneCompat.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }
}

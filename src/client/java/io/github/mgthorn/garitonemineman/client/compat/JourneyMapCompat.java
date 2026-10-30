package io.github.mgthorn.garitonemineman.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether JourneyMap is installed. Everything that touches the JourneyMap API lives in
 * {@code compat.journeymap} and is only called after checking this, so the mod runs without JourneyMap.
 */
public class JourneyMapCompat {
    public static final String MOD_ID = "journeymap";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    public static boolean isLoaded() {
        return LOADED;
    }
}

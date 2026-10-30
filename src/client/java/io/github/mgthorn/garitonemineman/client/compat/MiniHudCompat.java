package io.github.mgthorn.garitonemineman.client.compat;

import java.util.OptionalLong;
import net.minecraft.world.level.Level;
import net.fabricmc.loader.api.FabricLoader;
import fi.dy.masa.minihud.data.HudDataManager;

/**
 * Reads the world seed MiniHUD knows (singleplayer, Servux, or the output of {@code /seed}).
 * {@link HudDataManager} is only touched when MiniHUD is installed.
 */
public class MiniHudCompat {
    public static final String MOD_ID = "minihud";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    public static boolean isLoaded() {
        return LOADED;
    }

    public static OptionalLong getWorldSeed(Level level) {
        if (!LOADED) {
            return OptionalLong.empty();
        }

        try {
            HudDataManager data = HudDataManager.getInstance();
            return data.isWorldSeedKnown(level) ? OptionalLong.of(data.getWorldSeed(level)) : OptionalLong.empty();
        }
        catch (Throwable e) {
            // A MiniHUD version with a different API: fall back to the other seed sources
            return OptionalLong.empty();
        }
    }
}

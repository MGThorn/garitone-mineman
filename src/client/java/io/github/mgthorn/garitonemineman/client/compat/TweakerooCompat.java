package io.github.mgthorn.garitonemineman.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether Tweakeroo is installed. Without it {@code MixinTweakerooInventoryUtils} is skipped, so
 * {@code ToolSwitchBackHandler} never sees a tool switch and toolSwitchBack does nothing.
 */
public class TweakerooCompat {
    public static final String MOD_ID = "tweakeroo";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    public static boolean isLoaded() {
        return LOADED;
    }
}

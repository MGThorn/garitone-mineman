package io.github.mgthorn.garitonemineman.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether Item Scroller is installed. Kept apart from {@code InventorySorter} and the Item Scroller
 * mixins, which reference Item Scroller classes and must only be loaded when Item Scroller is present.
 */
public class ItemScrollerCompat {
    public static final String MOD_ID = "itemscroller";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    public static boolean isLoaded() {
        return LOADED;
    }
}

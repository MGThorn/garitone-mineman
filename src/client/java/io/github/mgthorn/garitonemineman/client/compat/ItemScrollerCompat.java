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

    /**
     * How many container clicks to send per tick when moving items. Follows Item Scroller's click rate
     * limit ({@code rateLimitClickPackets} + {@code packetRateLimit}, meant for Spigot/Paper servers) when
     * it's installed and turned on; otherwise one click per tick, which servers accept.
     */
    public static int getClicksPerTick() {
        int limit = LOADED ? ItemScrollerSettings.getClickRateLimit() : -1;
        return limit > 0 ? limit : 1;
    }
}

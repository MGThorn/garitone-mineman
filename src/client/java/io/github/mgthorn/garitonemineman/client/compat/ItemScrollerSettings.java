package io.github.mgthorn.garitonemineman.client.compat;

import fi.dy.masa.itemscroller.config.Configs;

/**
 * Reads Item Scroller's own settings. References Item Scroller classes, so only call it through
 * {@link ItemScrollerCompat}, which checks that Item Scroller is installed first.
 */
final class ItemScrollerSettings {
    private ItemScrollerSettings() {}

    /** Item Scroller's {@code packetRateLimit} when its {@code rateLimitClickPackets} is on, otherwise -1. */
    static int getClickRateLimit() {
        if (Configs.Generic.RATE_LIMIT_CLICK_PACKETS.getBooleanValue() == false) {
            return -1;
        }

        return Configs.Generic.PACKET_RATE_LIMIT.getIntegerValue();
    }
}

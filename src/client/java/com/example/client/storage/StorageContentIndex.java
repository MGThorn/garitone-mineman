package com.example.client.storage;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Light, incremental-only cache of whether a storage block was recently found full. A chest's
 * contents aren't visible to the client at all until its container is opened — there is no way to
 * peek at an unopened chest — so this is populated only by SmartMinemanHandler's own deposit
 * attempts. Never-opened or stale entries are simply treated as unknown/worth trying.
 */
public final class StorageContentIndex {
    private static final long STALE_AFTER_TICKS = 200; // ~10s

    private record Entry(boolean full, long tick) {}

    private static final Map<BlockPos, Entry> KNOWN = new HashMap<>();
    private static long currentTick = 0;

    private StorageContentIndex() {}

    public static void tick() {
        currentTick++;
    }

    public static void recordFull(BlockPos pos, boolean full) {
        KNOWN.put(pos.immutable(), new Entry(full, currentTick));
    }

    /** True only if this position was verified full recently; unknown/stale positions return false (worth trying). */
    public static boolean isRecentlyKnownFull(BlockPos pos) {
        Entry entry = KNOWN.get(pos);
        return entry != null && entry.full() && (currentTick - entry.tick()) <= STALE_AFTER_TICKS;
    }

    public static void clear() {
        KNOWN.clear();
    }
}

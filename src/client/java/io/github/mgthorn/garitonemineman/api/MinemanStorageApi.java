package io.github.mgthorn.garitonemineman.api;

import java.util.Map;
import org.jetbrains.annotations.Nullable;
import io.github.mgthorn.garitonemineman.client.storage.StorageInventory;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/**
 * Read access to Mineman's <b>active storage point</b> for other mods (for example Shopping
 * Materialist, to know which items of a list are already stored). Client side, call from the client
 * thread. Kept stable: only add to it, don't change existing methods.
 *
 * <p>Use it as an optional dependency: check {@code FabricLoader.getInstance().isModLoaded("mineman")}
 * before touching this class.
 *
 * <p>Counts come from the last-known contents of the storage blocks: a chest counts once it has been
 * opened (or, in singleplayer, read directly) and is remembered across restarts. Items inside shulker
 * boxes count too. Poll {@link #getRevision()} to know when to read again.
 */
public final class MinemanStorageApi {
    /** Version of this API; goes up when methods are added. */
    public static final int API_VERSION = 1;

    private MinemanStorageApi() {}

    /** Whether the current world/server has an active storage point. */
    public static boolean hasActiveStoragePoint() {
        return StoragePointManager.getInstance().getActiveStoragePoint() != null;
    }

    /** Name of the active storage point, or null if none. */
    @Nullable
    public static String getActiveStoragePointName() {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? point.getName() : null;
    }

    /** Dimension of the active storage point (for example "minecraft:overworld"), or null if none. */
    @Nullable
    public static String getActiveStoragePointDimension() {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? point.getDimensionId() : null;
    }

    /** How many of the item (id like "minecraft:diamond") are in the active storage point; 0 if none or no active point. */
    public static long count(String itemId) {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? StorageInventory.count(point, itemId) : 0;
    }

    /** Everything in the active storage point: item id → count, largest first. Empty if no active point. Read-only. */
    public static Map<String, Long> getContents() {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? StorageInventory.getCounts(point) : Map.of();
    }

    /** Used slots / slots of the known storage blocks of the active storage point, 0 to 1. */
    public static float getFillLevel() {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? StorageInventory.getSummary(point).fillLevel() : 0;
    }

    /** Number of storage blocks in the active storage point. */
    public static int getStorageBlockCount() {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? StorageInventory.getSummary(point).chests() : 0;
    }

    /** Number of storage blocks whose contents are known (opened at least once). */
    public static int getKnownStorageBlockCount() {
        StoragePoint point = StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? StorageInventory.getSummary(point).knownChests() : 0;
    }

    /** Goes up whenever storage points or their contents change (and on world switch). */
    public static int getRevision() {
        return StoragePointManager.getInstance().getRevision();
    }
}

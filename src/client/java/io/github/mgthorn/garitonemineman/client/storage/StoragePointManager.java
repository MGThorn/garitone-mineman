package io.github.mgthorn.garitonemineman.client.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.WorldUtils;

/**
 * All storage points of the current world/server, grouped by dimension, and the world's one
 * <b>active storage point</b>. Only the active storage point is used for store trips and counted for
 * other mods ({@link io.github.mgthorn.garitonemineman.api.MinemanStorageApi}); it can be in any
 * dimension. Saved by {@link StoragePointPersistence}, one file per storage point.
 */
public class StoragePointManager {
    private static final StoragePointManager INSTANCE = new StoragePointManager();
    private final Map<String, StoragePointPersistence.WorldData> worlds = new HashMap<>();
    @Nullable private String loadedWorldId;
    // Goes up on every saved change, so other code (and other mods) can tell when to recount
    private int revision;

    private StoragePointManager() {}

    public static StoragePointManager getInstance() { return INSTANCE; }

    // ---- World / dimension ----

    private static boolean isSingleplayer() {
        return Minecraft.getInstance().isLocalServer();
    }

    @Nullable
    private static String getCurrentWorldOrServerId() {
        return Minecraft.getInstance().level != null ? StringUtils.getWorldOrServerName() : null;
    }

    @Nullable
    public static String getCurrentDimensionId() {
        var level = Minecraft.getInstance().level;
        return level != null ? WorldUtils.getDimensionId(level) : null;
    }

    @Nullable
    private StoragePointPersistence.WorldData getWorldData() {
        String worldId = getCurrentWorldOrServerId();

        if (worldId == null) {
            return null;
        }

        if (Objects.equals(this.loadedWorldId, worldId) == false || this.worlds.containsKey(worldId) == false) {
            this.loadedWorldId = worldId;
            this.worlds.computeIfAbsent(worldId, id -> StoragePointPersistence.loadWorld(isSingleplayer(), id));
            this.revision++;
        }

        return this.worlds.get(worldId);
    }

    public boolean isInCurrentDimension(StoragePoint point) {
        return point.getDimensionId().equals(getCurrentDimensionId());
    }

    // ---- Lists ----

    /** Storage points of the dimension the player is in (rendering, protection, chest lookups). */
    public List<StoragePoint> getStoragePoints() {
        StoragePointPersistence.WorldData data = this.getWorldData();
        String dimensionId = getCurrentDimensionId();

        if (data == null || dimensionId == null) {
            return Collections.emptyList();
        }

        return data.byDimension.computeIfAbsent(dimensionId, d -> new ArrayList<>());
    }

    /** Every storage point of the world, all dimensions (the Storage Points screen). */
    public List<StoragePoint> getAllStoragePoints() {
        StoragePointPersistence.WorldData data = this.getWorldData();
        List<StoragePoint> all = new ArrayList<>();

        if (data != null) {
            data.byDimension.values().forEach(all::addAll);
        }

        return all;
    }

    // ---- Active storage point ----

    /** The world's active storage point (any dimension), or null if none is set. */
    @Nullable
    public StoragePoint getActiveStoragePoint() {
        StoragePointPersistence.WorldData data = this.getWorldData();
        return data != null ? data.active : null;
    }

    public boolean isActive(StoragePoint point) {
        return this.getActiveStoragePoint() == point;
    }

    /** Makes the point the world's active storage point (null: none). */
    public void setActiveStoragePoint(@Nullable StoragePoint point) {
        StoragePointPersistence.WorldData data = this.getWorldData();

        if (data != null && data.active != point) {
            data.active = point;
            this.save();
        }
    }

    // ---- Lookups ----

    /**
     * Finds the storage block entry (if any) registered at the given world position, in the current
     * world/dimension. Matches either half of a double chest, since both halves share one entry.
     */
    @Nullable
    public StorageBlockEntry findStorageBlockEntry(BlockPos pos) {
        for (StoragePoint point : this.getStoragePoints()) {
            for (StorageBlockEntry entry : point.getStorageBlocks()) {
                if (entry.getX() == pos.getX() && entry.getY() == pos.getY() && entry.getZ() == pos.getZ()) {
                    return entry;
                }

                if (entry.hasSecondPosition()
                        && entry.getSecondX() == pos.getX()
                        && entry.getSecondY() == pos.getY()
                        && entry.getSecondZ() == pos.getZ()) {
                    return entry;
                }
            }
        }

        return null;
    }

    /** The storage point in this dimension closest to the position (by its area), or null if there is none. */
    @Nullable
    public StoragePoint getNearestStoragePoint(BlockPos pos) {
        StoragePoint nearest = null;
        double best = Double.MAX_VALUE;

        for (StoragePoint point : this.getStoragePoints()) {
            double distSq = point.distanceSqToArea(pos.getX(), pos.getY(), pos.getZ());

            if (distSq < best) {
                best = distSq;
                nearest = point;
            }
        }

        return nearest;
    }

    // ---- Changes ----

    @Nullable
    public StoragePoint addStoragePoint(String name, int x, int y, int z, BlockPos corner1, BlockPos corner2) {
        String dimensionId = getCurrentDimensionId();
        StoragePointPersistence.WorldData data = this.getWorldData();

        if (data == null || dimensionId == null) {
            return null;
        }

        StoragePoint point = new StoragePoint(name, true);
        point.setDimensionId(dimensionId);
        point.setX(x);
        point.setY(y);
        point.setZ(z);
        point.setCorner1(corner1.getX(), corner1.getY(), corner1.getZ());
        point.setCorner2(corner2.getX(), corner2.getY(), corner2.getZ());
        data.byDimension.computeIfAbsent(dimensionId, d -> new ArrayList<>()).add(point);
        this.save();
        return point;
    }

    public void renameStoragePoint(StoragePoint point, String newName) {
        String worldId = getCurrentWorldOrServerId();

        if (worldId != null) {
            point.setFileName(StoragePointPersistence.renamePointFile(isSingleplayer(), worldId, point, newName));
        }

        point.setName(newName);
        this.save();
    }

    public void removeStoragePoint(StoragePoint point) {
        StoragePointPersistence.WorldData data = this.getWorldData();
        String worldId = getCurrentWorldOrServerId();

        if (data == null || worldId == null) {
            return;
        }

        List<StoragePoint> list = data.byDimension.get(point.getDimensionId());

        if (list != null && list.remove(point)) {
            if (data.active == point) {
                data.active = null;
            }

            StoragePointPersistence.deletePointFile(isSingleplayer(), worldId, point);
            this.save();
        }
    }

    /** Saves the current world's storage points. Call after any change, contents included. */
    public void save() {
        String worldId = getCurrentWorldOrServerId();
        StoragePointPersistence.WorldData data = worldId != null ? this.worlds.get(worldId) : null;

        if (data != null) {
            StoragePointPersistence.saveWorld(isSingleplayer(), worldId, data);
        }

        this.revision++;
    }

    /** Goes up on every change (and world switch); compare to know when counts are outdated. */
    public int getRevision() {
        this.getWorldData(); // a world switch counts as a change
        return this.revision;
    }
}

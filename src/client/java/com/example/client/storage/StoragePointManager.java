package com.example.client.storage;

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

public class StoragePointManager {
    private static final StoragePointManager INSTANCE = new StoragePointManager();
    private final Map<String, Map<String, List<StoragePoint>>> storagePointsByWorld = new HashMap<>();
    @Nullable private StoragePoint selectedStoragePoint;
    @Nullable private String loadedWorldId;

    private StoragePointManager() {}

    public static StoragePointManager getInstance() { return INSTANCE; }

    @Nullable
    public StoragePoint getSelectedStoragePoint() {
        return this.selectedStoragePoint;
    }

    public void setSelectedStoragePoint(@Nullable StoragePoint point) {
        if (this.selectedStoragePoint == point) {
            return;
        }

        if (this.selectedStoragePoint != null) {
            this.selectedStoragePoint.setSelected(false);
        }

        this.selectedStoragePoint = point;

        if (point != null) {
            point.setSelected(true);
        }

        this.save();
    }

    /**
     * Returns the explicitly selected storage point if there is one, otherwise the closest enabled
     * storage point to the given position. Used by hotkeys, which have no GUI row to click on.
     */
    @Nullable
    public StoragePoint getSelectedOrNearestStoragePoint(BlockPos playerPos) {
        if (this.selectedStoragePoint != null) {
            return this.selectedStoragePoint;
        }

        StoragePoint nearest = null;
        long bestDistSq = Long.MAX_VALUE;

        for (StoragePoint point : this.getStoragePoints()) {
            if (point.isEnabled() == false) {
                continue;
            }

            long dx = point.getX() - playerPos.getX();
            long dy = point.getY() - playerPos.getY();
            long dz = point.getZ() - playerPos.getZ();
            long distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = point;
            }
        }

        return nearest;
    }

    private static boolean isSingleplayer() {
        return Minecraft.getInstance().isLocalServer();
    }

    @Nullable
    private static String getCurrentWorldOrServerId() {
        return StringUtils.getWorldOrServerName();
    }

    @Nullable
    private static String getCurrentDimensionId() {
        var level = Minecraft.getInstance().level;
        return level != null ? WorldUtils.getDimensionId(level) : null;
    }

    private void ensureWorldLoaded(String worldId) {
        if (Objects.equals(this.loadedWorldId, worldId) && this.storagePointsByWorld.containsKey(worldId)) {
            return;
        }

        this.loadedWorldId = worldId;

        if (this.storagePointsByWorld.containsKey(worldId) == false) {
            Map<String, List<StoragePoint>> loaded = StoragePointPersistence.loadWorldIndex(isSingleplayer(), worldId);
            this.storagePointsByWorld.put(worldId, loaded);
            this.restoreSelectedStoragePoint(loaded);
        }
    }

    /** Re-selects whichever point (if any) was flagged "selected" the last time this world was saved. */
    private void restoreSelectedStoragePoint(Map<String, List<StoragePoint>> data) {
        for (List<StoragePoint> points : data.values()) {
            for (StoragePoint point : points) {
                if (point.isSelected()) {
                    this.selectedStoragePoint = point;
                    return;
                }
            }
        }
    }

    @Nullable
    private List<StoragePoint> getCurrentListOrNull() {
        String worldId = getCurrentWorldOrServerId();
        String dimensionId = getCurrentDimensionId();

        if (worldId == null || dimensionId == null) {
            return null;
        }

        this.ensureWorldLoaded(worldId);

        return this.storagePointsByWorld.get(worldId).computeIfAbsent(dimensionId, d -> new ArrayList<>());
    }

    public List<StoragePoint> getStoragePoints() {
        List<StoragePoint> list = this.getCurrentListOrNull();
        return list != null ? list : Collections.emptyList();
    }

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

    @Nullable
    public StoragePoint addStoragePoint(String name, int x, int y, int z, BlockPos corner1, BlockPos corner2) {
        List<StoragePoint> list = this.getCurrentListOrNull();

        if (list == null) {
            return null;
        }

        StoragePoint point = new StoragePoint(name, true);
        point.setX(x);
        point.setY(y);
        point.setZ(z);
        point.setCorner1(corner1.getX(), corner1.getY(), corner1.getZ());
        point.setCorner2(corner2.getX(), corner2.getY(), corner2.getZ());
        point.setSnapshotFileName(StoragePointPersistence.createSnapshotFile(name));
        list.add(point);
        this.save();
        return point;
    }

    public void renameStoragePoint(StoragePoint point, String newName) {
        String newFileName = StoragePointPersistence.renameSnapshotFile(point.getSnapshotFileName(), newName);
        point.setSnapshotFileName(newFileName);
        point.setName(newName);
        this.save();
    }

    public void removeStoragePoint(StoragePoint point) {
        List<StoragePoint> list = this.getCurrentListOrNull();

        if (list != null && list.remove(point)) {
            if (this.selectedStoragePoint == point) {
                this.selectedStoragePoint = null;
            }

            this.save();
        }
    }

    /**
     * Persists the currently loaded world/server's storage points to its per-world JSON index.
     * Call this after any change to a storage point's fields so the change survives a GUI/game restart.
     */
    public void save() {
        String worldId = getCurrentWorldOrServerId();
        Map<String, List<StoragePoint>> data = worldId != null ? this.storagePointsByWorld.get(worldId) : null;

        if (worldId != null && data != null) {
            StoragePointPersistence.saveWorldIndex(isSingleplayer(), worldId, data);

            for (List<StoragePoint> points : data.values()) {
                for (StoragePoint point : points) {
                    StoragePointPersistence.writeSnapshotFile(point);
                }
            }
        }
    }
}

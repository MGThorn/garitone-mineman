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
        this.selectedStoragePoint = point;
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
            this.storagePointsByWorld.put(worldId, StoragePointPersistence.loadWorldIndex(isSingleplayer(), worldId));
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
        point.setLitematicFileName(StoragePointPersistence.createLitematicFile(name));
        list.add(point);
        this.save();
        return point;
    }

    public void renameStoragePoint(StoragePoint point, String newName) {
        String newFileName = StoragePointPersistence.renameLitematicFile(point.getLitematicFileName(), newName);
        point.setLitematicFileName(newFileName);
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
        }
    }
}

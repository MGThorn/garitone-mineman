package io.github.mgthorn.garitonemineman.client.storage;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A storage area: two corners, an origin (where Baritone walks to) and the storage blocks found
 * inside. Saved as its own {@code .storagepoint} file in the world's folder (see
 * {@link StoragePointPersistence}). Which one is the world's active storage point is kept by
 * {@link StoragePointManager}, not here.
 */
public class StoragePoint {
    private String name;
    private boolean enabled;
    private boolean locked;
    private String dimensionId = "";
    private int x;
    private int y;
    private int z;
    private int corner1X;
    private int corner1Y;
    private int corner1Z;
    private int corner2X;
    private int corner2Y;
    private int corner2Z;
    /** The .storagepoint file name inside the world's folder; null until first saved. */
    @Nullable private String fileName;
    private List<StorageBlockEntry> storageBlocks = new ArrayList<>();

    public StoragePoint(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }
    /** Rendering of the area outline and highlighted blocks. */
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggleEnabled() { this.enabled = !this.enabled; }
    public boolean isLocked() { return this.locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    /** The dimension it lives in, for example "minecraft:overworld". */
    public String getDimensionId() { return this.dimensionId; }
    public void setDimensionId(String dimensionId) { this.dimensionId = dimensionId; }
    public int getX() { return this.x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return this.y; }
    public void setY(int y) { this.y = y; }
    public int getZ() { return this.z; }
    public void setZ(int z) { this.z = z; }
    public int getCorner1X() { return this.corner1X; }
    public int getCorner1Y() { return this.corner1Y; }
    public int getCorner1Z() { return this.corner1Z; }
    public void setCorner1(int x, int y, int z) { this.corner1X = x; this.corner1Y = y; this.corner1Z = z; }
    public int getCorner2X() { return this.corner2X; }
    public int getCorner2Y() { return this.corner2Y; }
    public int getCorner2Z() { return this.corner2Z; }
    public void setCorner2(int x, int y, int z) { this.corner2X = x; this.corner2Y = y; this.corner2Z = z; }
    @Nullable public String getFileName() { return this.fileName; }
    public void setFileName(@Nullable String fileName) { this.fileName = fileName; }
    public List<StorageBlockEntry> getStorageBlocks() { return this.storageBlocks; }
    public void setStorageBlocks(List<StorageBlockEntry> storageBlocks) { this.storageBlocks = storageBlocks; }

    /** Squared distance from the area (0 inside it) to a block position. */
    public double distanceSqToArea(int px, int py, int pz) {
        double dx = axisDistance(px, this.corner1X, this.corner2X);
        double dy = axisDistance(py, this.corner1Y, this.corner2Y);
        double dz = axisDistance(pz, this.corner1Z, this.corner2Z);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(int value, int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return value < min ? min - value : value > max ? value - max : 0;
    }
}

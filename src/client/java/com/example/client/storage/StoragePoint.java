package com.example.client.storage;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class StoragePoint {
    private String name;
    private boolean enabled;
    private boolean locked;
    private int x;
    private int y;
    private int z;
    private int corner1X;
    private int corner1Y;
    private int corner1Z;
    private int corner2X;
    private int corner2Y;
    private int corner2Z;
    @Nullable private String litematicFileName;
    private List<StorageBlockEntry> storageBlocks = new ArrayList<>();

    public StoragePoint(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggleEnabled() { this.enabled = !this.enabled; }
    public boolean isLocked() { return this.locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
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
    @Nullable
    public String getLitematicFileName() { return this.litematicFileName; }
    public void setLitematicFileName(@Nullable String litematicFileName) { this.litematicFileName = litematicFileName; }
    public List<StorageBlockEntry> getStorageBlocks() { return this.storageBlocks; }
    public void setStorageBlocks(List<StorageBlockEntry> storageBlocks) { this.storageBlocks = storageBlocks; }
}

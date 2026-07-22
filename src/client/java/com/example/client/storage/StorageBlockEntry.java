package com.example.client.storage;

/**
 * A single storage block (chest, copper chest, shulker box, trapped chest, or barrel) detected
 * inside a StoragePoint's selection. Double chests are represented by a single entry.
 */
public class StorageBlockEntry {
    private final int x;
    private final int y;
    private final int z;
    private final String blockId;
    private final boolean isDouble;
    private boolean useEnabled;

    public StorageBlockEntry(int x, int y, int z, String blockId, boolean isDouble, boolean useEnabled) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
        this.isDouble = isDouble;
        this.useEnabled = useEnabled;
    }

    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    public String getBlockId() { return this.blockId; }
    public boolean isDouble() { return this.isDouble; }
    public boolean isUseEnabled() { return this.useEnabled; }
    public void setUseEnabled(boolean useEnabled) { this.useEnabled = useEnabled; }

    public String getDisplayName() {
        String path = this.blockId.contains(":") ? this.blockId.substring(this.blockId.indexOf(':') + 1) : this.blockId;
        StringBuilder sb = new StringBuilder();

        if (this.isDouble) {
            sb.append("Double ");
        }

        for (String part : path.split("_")) {
            if (part.isEmpty() == false) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }

                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }

        return sb.toString();
    }
}

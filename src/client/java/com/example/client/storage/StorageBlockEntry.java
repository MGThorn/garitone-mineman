package com.example.client.storage;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

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

    /** The other half's position, for a double chest; null if not double or not yet known. */
    @Nullable private Integer secondX;
    @Nullable private Integer secondY;
    @Nullable private Integer secondZ;

    /** Per-slot enabled mask; null means no slot is disabled. */
    @Nullable private boolean[] slotEnabled;
    /** Per-slot item restriction (item id, or null for that slot = no restriction); null means none set. */
    @Nullable private String[] slotItemFilter;
    /**
     * Last-known contents, one entry per slot (flat across both halves of a double chest, same
     * convention as {@link #slotEnabled}/{@link #slotItemFilter}). Never loaded from or written to the
     * combined per-world index — only ever populated at runtime (from an open container closing, or a
     * singleplayer-only direct read) and exported into the per-point {@code .storagepoint} snapshot
     * file. Starts null every session until first populated.
     */
    @Nullable private ContentSlot[] contents;

    /** One chest slot's last-known contents: an item id, or null for an empty slot. */
    public record ContentSlot(@Nullable String itemId, int count) {
        public static final ContentSlot EMPTY = new ContentSlot(null, 0);

        public static ContentSlot fromStack(ItemStack stack) {
            return stack.isEmpty() ? EMPTY
                    : new ContentSlot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
        }
    }

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

    public boolean hasSecondPosition() { return this.secondX != null; }
    @Nullable public Integer getSecondX() { return this.secondX; }
    @Nullable public Integer getSecondY() { return this.secondY; }
    @Nullable public Integer getSecondZ() { return this.secondZ; }

    public void setSecondPosition(int x, int y, int z) {
        this.secondX = x;
        this.secondY = y;
        this.secondZ = z;
    }

    public void clearSecondPosition() {
        this.secondX = null;
        this.secondY = null;
        this.secondZ = null;
    }

    @Nullable public boolean[] getSlotEnabled() { return this.slotEnabled; }
    public void setSlotEnabled(@Nullable boolean[] slotEnabled) { this.slotEnabled = slotEnabled; }
    public boolean isSlotEnabled(int slotIndex) {
        return this.slotEnabled == null || slotIndex < 0 || slotIndex >= this.slotEnabled.length || this.slotEnabled[slotIndex];
    }

    @Nullable public String[] getSlotItemFilter() { return this.slotItemFilter; }
    public void setSlotItemFilter(@Nullable String[] slotItemFilter) { this.slotItemFilter = slotItemFilter; }

    /** The item id this slot is restricted to, or null if the slot accepts anything. */
    @Nullable
    public String getSlotItem(int slotIndex) {
        return this.slotItemFilter != null && slotIndex >= 0 && slotIndex < this.slotItemFilter.length
                ? this.slotItemFilter[slotIndex] : null;
    }

    public boolean isItemAllowedInSlot(int slotIndex, String itemId) {
        String required = this.getSlotItem(slotIndex);
        return required == null || required.equals(itemId);
    }

    /** Whether any slot in this chest is restricted to a specific item. */
    public boolean hasSlotItemFilter() {
        if (this.slotItemFilter == null) {
            return false;
        }

        for (String itemId : this.slotItemFilter) {
            if (itemId != null) {
                return true;
            }
        }

        return false;
    }

    /** Whether this chest has any rule restriction active (per-slot mask and/or per-slot item filter). */
    public boolean hasRules() { return this.slotEnabled != null || this.hasSlotItemFilter(); }

    @Nullable public ContentSlot[] getContents() { return this.contents; }
    public void setContents(@Nullable ContentSlot[] contents) { this.contents = contents; }
    public boolean hasContents() { return this.contents != null; }

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

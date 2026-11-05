package io.github.mgthorn.garitonemineman.client.storage;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A single storage block (any block in the storageBlockTypes setting: chests, barrels, shulker
 * boxes, ...) detected inside a StoragePoint's area. Double chests are represented by a single entry.
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
     * convention as {@link #slotEnabled}/{@link #slotItemFilter}). Populated when the container's
     * screen closes (or read directly in singleplayer) and saved in the storage point's file, so it
     * survives restarts. Null until the chest has been seen once.
     */
    @Nullable private ContentSlot[] contents;
    /** When {@link #contents} was last updated (epoch millis), 0 if never. */
    private long lastSeen;

    /**
     * One chest slot's last-known contents: an item id, or null for an empty slot. {@code inner} holds
     * the items inside a shulker box (or any item with a container component), so they count as stored.
     */
    public record ContentSlot(@Nullable String itemId, int count, @Nullable List<ContentSlot> inner) {
        public static final ContentSlot EMPTY = new ContentSlot(null, 0, null);

        public static ContentSlot fromStack(ItemStack stack) {
            if (stack.isEmpty()) {
                return EMPTY;
            }

            List<ContentSlot> inner = null;
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);

            if (container != null) {
                List<ContentSlot> items = container.nonEmptyItemCopyStream().map(ContentSlot::fromStack).toList();
                inner = items.isEmpty() ? null : items;
            }

            return new ContentSlot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), inner);
        }

        public boolean isEmpty() {
            return this.itemId == null;
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
    /** Sets freshly seen contents and stamps {@link #getLastSeen()} with now. */
    public void setContents(@Nullable ContentSlot[] contents) {
        this.contents = contents;
        this.lastSeen = contents != null ? System.currentTimeMillis() : 0;
    }
    /** Restores contents loaded from disk (or carried over by a rescan) with their original time. */
    public void restoreContents(@Nullable ContentSlot[] contents, long lastSeen) {
        this.contents = contents;
        this.lastSeen = lastSeen;
    }
    public long getLastSeen() { return this.lastSeen; }
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

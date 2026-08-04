package com.example.client.gui;

import java.util.Arrays;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.render.InventoryOverlayType;
import fi.dy.masa.malilib.render.RenderUtils;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Renders a chest's slot grid using the real vanilla single/double chest background texture.
 * Right-click toggles a slot on/off (shown with the vanilla "locked slot" overlay). Dropping an item
 * dragged in from the search row onto a slot restricts that slot to only that item, previewed as a
 * half-opaque icon sitting in the slot. Slots with last-known real contents (see
 * {@link StorageBlockEntry#getContents()}) render that item solidly, like a normal inventory — purely
 * a draw call with no backing {@code Slot}/menu, so there is nothing to click or drag here.
 */
public class WidgetChestSlotGrid extends WidgetBase {
    private final StorageBlockEntry entry;
    private final InventoryOverlayType type;
    private final int totalSlots;
    private final int slotsPerRow;
    private final int slotOffsetX;
    private final int slotOffsetY;
    private final Runnable onChange;
    private final Supplier<ItemStack> draggedStackSupplier;
    private boolean showRealItems;

    public WidgetChestSlotGrid(int x, int y, StorageBlockEntry entry, Runnable onChange,
            Supplier<ItemStack> draggedStackSupplier, boolean showRealItems) {
        super(x, y, 0, 0);
        this.entry = entry;
        this.onChange = onChange;
        this.draggedStackSupplier = draggedStackSupplier;
        this.showRealItems = showRealItems;
        this.type = entry.isDouble() ? InventoryOverlayType.FIXED_54 : InventoryOverlayType.FIXED_27;
        this.totalSlots = entry.isDouble() ? 54 : 27;

        InventoryOverlay.InventoryProperties props = InventoryOverlay.getInventoryPropsTemp(this.type, this.totalSlots);
        this.slotsPerRow = props.slotsPerRow;
        this.slotOffsetX = props.slotOffsetX;
        this.slotOffsetY = props.slotOffsetY;
        this.setWidth(props.width);
        this.setHeight(props.height);
    }

    public void setShowRealItems(boolean showRealItems) {
        this.showRealItems = showRealItems;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        super.render(ctx, mouseX, mouseY, selected);

        InventoryOverlay.renderInventoryBackground(ctx, this.type, this.x, this.y, this.slotsPerRow, this.totalSlots);

        ContentSlot[] contents = this.entry.getContents();

        for (int i = 0; i < this.totalSlots; i++) {
            int sx = this.x + this.slotOffsetX + (i % this.slotsPerRow) * 18;
            int sy = this.y + this.slotOffsetY + (i / this.slotsPerRow) * 18;

            ContentSlot known = contents != null && i < contents.length ? contents[i] : null;
            boolean hasKnownContent = known != null && known.itemId() != null && this.showRealItems;

            if (hasKnownContent) {
                ItemStack real = resolveStack(known.itemId());

                if (real != null) {
                    real.setCount(Math.max(1, known.count()));
                    InventoryOverlay.renderStackAt(ctx, real, sx, sy, 1f);
                }
            }

            String restrictedItemId = this.entry.getSlotItem(i);

            // Only show the rule's faded "target item" ghost when we don't already know what's really
            // sitting in the slot — otherwise the two icons would just sit on top of each other. Hiding
            // real items (showRealItems off) frees this up to always reveal the configured ghost.
            if (restrictedItemId != null && hasKnownContent == false) {
                ItemStack preview = resolveStack(restrictedItemId);

                if (preview != null) {
                    InventoryOverlay.renderStackAt(ctx, preview, sx, sy, 1f);
                    RenderUtils.drawRect(ctx, sx, sy, 16, 16, 0x80FFFFFF);
                }
            }

            if (this.entry.isSlotEnabled(i) == false) {
                InventoryOverlay.renderLockedSlotAt(ctx, sx - 1, sy - 1, 1f, mouseX, mouseY);
            }
        }
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() != 1) {
            return false;
        }

        int index = this.slotIndexAt((int) click.x(), (int) click.y());

        if (index < 0) {
            return false;
        }

        boolean[] mask = this.entry.getSlotEnabled();

        if (mask == null || mask.length != this.totalSlots) {
            boolean[] newMask = new boolean[this.totalSlots];
            Arrays.fill(newMask, true);

            if (mask != null) {
                System.arraycopy(mask, 0, newMask, 0, Math.min(mask.length, newMask.length));
            }

            mask = newMask;
        }

        mask[index] = !mask[index];
        this.entry.setSlotEnabled(mask);
        this.onChange.run();
        return true;
    }

    @Override
    public void onMouseReleasedImpl(MouseButtonEvent click) {
        ItemStack dragged = this.draggedStackSupplier.get();

        if (dragged == null || dragged.isEmpty()) {
            return;
        }

        int index = this.slotIndexAt((int) click.x(), (int) click.y());

        if (index < 0) {
            return;
        }

        String[] filter = this.entry.getSlotItemFilter();

        if (filter == null || filter.length != this.totalSlots) {
            String[] newFilter = new String[this.totalSlots];

            if (filter != null) {
                System.arraycopy(filter, 0, newFilter, 0, Math.min(filter.length, newFilter.length));
            }

            filter = newFilter;
        }

        filter[index] = BuiltInRegistries.ITEM.getKey(dragged.getItem()).toString();
        this.entry.setSlotItemFilter(filter);
        this.onChange.run();
    }

    @Nullable
    private static ItemStack resolveStack(String itemId) {
        Identifier id = Identifier.tryParse(itemId);

        if (id == null) {
            return null;
        }

        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(null);
    }

    private int slotIndexAt(int mouseX, int mouseY) {
        int localX = mouseX - this.x - this.slotOffsetX;
        int localY = mouseY - this.y - this.slotOffsetY;

        if (localX < 0 || localY < 0) {
            return -1;
        }

        int col = localX / 18;
        int row = localY / 18;

        if (col >= this.slotsPerRow) {
            return -1;
        }

        int index = row * this.slotsPerRow + col;
        return index < this.totalSlots ? index : -1;
    }
}

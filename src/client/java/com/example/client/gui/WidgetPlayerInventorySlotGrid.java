package com.example.client.gui;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.render.InventoryOverlayType;
import fi.dy.masa.malilib.render.RenderUtils;
import com.example.client.storage.InventorySortConfig;

/**
 * Renders the player's own 36 inventory slots (hotbar 0-8, main 9-35) as two separate background
 * blocks laid out like the real vanilla inventory screen — the 3-row main inventory on top, then the
 * hotbar row below it separated by a small gap — showing the player's real live items as a view-only
 * reference — purely a draw call with no backing Slot/menu, so a real item here can't be clicked or
 * dragged. Dropping an item dragged in from the search row onto a slot sets that slot's configured
 * target item ({@link InventorySortConfig}), previewed as a half-opaque icon when the real slot is
 * empty. Right-click clears a single slot's configured item.
 */
public class WidgetPlayerInventorySlotGrid extends WidgetBase {
    private static final int MAIN_SLOTS = 27;
    private static final int HOTBAR_SLOTS = 9;
    private static final int MAIN_ROWS = 3;
    private static final int HOTBAR_GAP = 4;

    private final int slotsPerRow;
    private final int slotOffsetX;
    private final int mainSlotOffsetY;
    private final int hotbarBlockY;
    private final int hotbarSlotOffsetY;
    private final Runnable onChange;
    private final Supplier<ItemStack> draggedStackSupplier;
    private boolean showRealItems;

    public WidgetPlayerInventorySlotGrid(int x, int y, Runnable onChange, Supplier<ItemStack> draggedStackSupplier,
            boolean showRealItems) {
        super(x, y, 0, 0);
        this.onChange = onChange;
        this.draggedStackSupplier = draggedStackSupplier;
        this.showRealItems = showRealItems;

        InventoryOverlay.InventoryProperties mainProps =
                InventoryOverlay.getInventoryPropsTemp(InventoryOverlayType.GENERIC, MAIN_SLOTS);
        this.slotsPerRow = mainProps.slotsPerRow;
        this.slotOffsetX = mainProps.slotOffsetX;
        this.mainSlotOffsetY = mainProps.slotOffsetY;
        this.hotbarBlockY = mainProps.height + HOTBAR_GAP;

        InventoryOverlay.InventoryProperties hotbarProps =
                InventoryOverlay.getInventoryPropsTemp(InventoryOverlayType.GENERIC, HOTBAR_SLOTS);
        this.hotbarSlotOffsetY = hotbarProps.slotOffsetY;

        this.setWidth(Math.max(mainProps.width, hotbarProps.width));
        this.setHeight(this.hotbarBlockY + hotbarProps.height);
    }

    public void setShowRealItems(boolean showRealItems) {
        this.showRealItems = showRealItems;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        super.render(ctx, mouseX, mouseY, selected);

        InventoryOverlay.renderInventoryBackground(ctx, InventoryOverlayType.GENERIC, this.x, this.y, this.slotsPerRow, MAIN_SLOTS);
        InventoryOverlay.renderInventoryBackground(ctx, InventoryOverlayType.GENERIC,
                this.x, this.y + this.hotbarBlockY, this.slotsPerRow, HOTBAR_SLOTS);

        Player player = Minecraft.getInstance().player;

        // Main inventory (indices 9-35): 3 rows on top.
        for (int mainI = 0; mainI < MAIN_SLOTS; mainI++) {
            int sx = this.x + this.slotOffsetX + (mainI % this.slotsPerRow) * 18;
            int sy = this.y + this.mainSlotOffsetY + (mainI / this.slotsPerRow) * 18;
            this.renderInventorySlot(ctx, player, 9 + mainI, sx, sy);
        }

        // Hotbar (indices 0-8): one row below the gap.
        for (int hotbarI = 0; hotbarI < HOTBAR_SLOTS; hotbarI++) {
            int sx = this.x + this.slotOffsetX + hotbarI * 18;
            int sy = this.y + this.hotbarBlockY + this.hotbarSlotOffsetY;
            this.renderInventorySlot(ctx, player, hotbarI, sx, sy);
        }
    }

    private void renderInventorySlot(GuiContext ctx, @Nullable Player player, int invIndex, int sx, int sy) {
        ItemStack real = player != null ? player.getInventory().getItem(invIndex) : ItemStack.EMPTY;
        boolean hasRealContent = real.isEmpty() == false && this.showRealItems;

        if (hasRealContent) {
            InventoryOverlay.renderStackAt(ctx, real, sx, sy, 1f);
        }

        String configuredItemId = InventorySortConfig.getSlotItem(invIndex);

        // Only show the configured "target item" ghost when the slot isn't already showing a real
        // item — otherwise the two icons would just sit on top of each other. Hiding real items
        // (showRealItems off) frees this up to always reveal the configured ghost.
        if (configuredItemId != null && hasRealContent == false) {
            ItemStack preview = resolveStack(configuredItemId);

            if (preview != null) {
                InventoryOverlay.renderStackAt(ctx, preview, sx, sy, 1f);
                RenderUtils.drawRect(ctx, sx, sy, 16, 16, 0x80FFFFFF);
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

        InventorySortConfig.setSlotItem(index, null);
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

        InventorySortConfig.setSlotItem(index, BuiltInRegistries.ITEM.getKey(dragged.getItem()).toString());
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

    /** Returns the player-inventory index (0-8 hotbar, 9-35 main) under the mouse, or -1. */
    private int slotIndexAt(int mouseX, int mouseY) {
        int localX = mouseX - this.x;
        int localY = mouseY - this.y;

        int mainX = localX - this.slotOffsetX;
        int mainY = localY - this.mainSlotOffsetY;

        if (mainX >= 0 && mainY >= 0 && mainY < MAIN_ROWS * 18) {
            int col = mainX / 18;
            int row = mainY / 18;

            if (col >= this.slotsPerRow) {
                return -1;
            }

            int mainIndex = row * this.slotsPerRow + col;
            return mainIndex < MAIN_SLOTS ? 9 + mainIndex : -1;
        }

        int hotbarX = localX - this.slotOffsetX;
        int hotbarY = localY - this.hotbarBlockY - this.hotbarSlotOffsetY;

        if (hotbarX >= 0 && hotbarY >= 0 && hotbarY < 18) {
            int col = hotbarX / 18;
            return col < HOTBAR_SLOTS ? col : -1;
        }

        return -1;
    }
}

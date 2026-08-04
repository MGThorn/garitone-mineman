package com.example.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.render.InventoryOverlayType;

/**
 * A single row of item icons matching a search query, styled like the creative inventory's search
 * tab but limited to one row; scroll to page through further matches. Left-click-dragging an icon
 * out and dropping it on a chest slot is how the owning screen adds that item to the whitelist.
 */
public class WidgetItemSearchRow extends WidgetBase {
    private static final int VISIBLE_SLOTS = 9;

    private final int slotOffsetX;
    private final int slotOffsetY;
    private final Consumer<ItemStack> onDragStart;
    private List<ItemStack> matches = List.of();
    private int scrollOffset;

    public WidgetItemSearchRow(int x, int y, Consumer<ItemStack> onDragStart) {
        super(x, y, 0, 0);
        this.onDragStart = onDragStart;

        InventoryOverlay.InventoryProperties props =
                InventoryOverlay.getInventoryPropsTemp(InventoryOverlayType.GENERIC, VISIBLE_SLOTS);
        this.slotOffsetX = props.slotOffsetX;
        this.slotOffsetY = props.slotOffsetY;
        this.setWidth(props.width);
        this.setHeight(props.height);
    }

    public void setSearchText(String query) {
        this.scrollOffset = 0;
        String trimmed = query.trim().toLowerCase();

        if (trimmed.isEmpty()) {
            this.matches = List.of();
            return;
        }

        List<ItemStack> found = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);

            if (stack.isEmpty()) {
                continue;
            }

            String id = BuiltInRegistries.ITEM.getKey(item).toString();

            if (id.toLowerCase().contains(trimmed) || stack.getHoverName().getString().toLowerCase().contains(trimmed)) {
                found.add(stack);
            }
        }

        this.matches = found;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        super.render(ctx, mouseX, mouseY, selected);

        InventoryOverlay.renderInventoryBackground(ctx, InventoryOverlayType.GENERIC, this.x, this.y, VISIBLE_SLOTS, VISIBLE_SLOTS);

        ItemStack hovered = null;

        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            int matchIndex = this.scrollOffset + i;

            if (matchIndex >= this.matches.size()) {
                break;
            }

            ItemStack stack = this.matches.get(matchIndex);
            int sx = this.x + this.slotOffsetX + i * 18;
            int sy = this.y + this.slotOffsetY;
            InventoryOverlay.renderStackAt(ctx, stack, sx, sy, 1f, mouseX, mouseY);

            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                hovered = stack;
            }
        }

        if (hovered != null) {
            InventoryOverlay.renderStackToolTip(ctx, mouseX, mouseY, hovered);
        }
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() != 0) {
            return false;
        }

        int localX = (int) click.x() - this.x - this.slotOffsetX;
        int localY = (int) click.y() - this.y - this.slotOffsetY;

        if (localX < 0 || localY < 0 || localY >= 18) {
            return false;
        }

        int slot = localX / 18;
        int matchIndex = this.scrollOffset + slot;

        if (slot >= VISIBLE_SLOTS || matchIndex >= this.matches.size()) {
            return false;
        }

        this.onDragStart.accept(this.matches.get(matchIndex).copy());
        return true;
    }

    @Override
    public boolean onMouseScrolledImpl(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.matches.isEmpty()) {
            return false;
        }

        int maxOffset = Math.max(0, this.matches.size() - VISIBLE_SLOTS);
        int delta = verticalAmount > 0 ? -1 : 1;
        this.scrollOffset = Math.max(0, Math.min(maxOffset, this.scrollOffset + delta));
        return true;
    }
}

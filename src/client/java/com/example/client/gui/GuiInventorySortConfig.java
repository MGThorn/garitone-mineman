package com.example.client.gui;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.InventoryOverlay;
import com.example.client.storage.InventorySortConfig;

/**
 * Configure which item "belongs" in each of the player's own inventory slots. Same drag-from-search-
 * row-onto-a-slot mechanic as {@link GuiChestRules}, but against the player's own live inventory
 * (view-only — nothing here can be clicked to move a real item) instead of a chest. See
 * {@code InventorySorter}/{@code MixinItemScrollerSort} for how these slots get used: whenever Item
 * Scroller's own sortInventory hotkey fires, each configured slot's item is moved into place first,
 * before Item Scroller's own generic priority-list sort runs on whatever's left over.
 */
public class GuiInventorySortConfig extends GuiBase {
    private GuiTextFieldGeneric searchField;
    private WidgetPlayerInventorySlotGrid slotGrid;
    private WidgetItemSearchRow searchRow;
    @Nullable private ItemStack draggedStack;
    private boolean showRealItems = true;

    public GuiInventorySortConfig(@Nullable Screen parent) {
        this.title = "Sorting Settings";
        this.setParent(parent);
    }

    @Override
    public void initGui() {
        super.initGui();

        int y = 40;

        String infoLine1 = "Configure which item belongs in each inventory slot below.";
        String infoLine2 = "Item Scroller's sortInventory hotkey fills these in first (best tool tier wins for tool slots), then sorts the rest normally.";
        this.addLabel(this.centerX(this.getStringWidth(infoLine1)), y, 400, 10, 0xFFFFFFFF, infoLine1);
        this.addLabel(this.centerX(this.getStringWidth(infoLine2)), y + 10, 500, 10, 0xFFAAAAAA, infoLine2);
        y += 34;

        this.searchField = new GuiTextFieldGeneric(this.centerX(200), y, 200, 16, this.font);
        this.searchField.setMaxLengthWrapper(128);
        this.addTextField(this.searchField, tf -> {
            if (this.searchRow != null) {
                this.searchRow.setSearchText(tf.getValueWrapper());
            }
            return false;
        }, TextFieldType.STRING);
        y += 22;

        this.searchRow = this.addWidget(new WidgetItemSearchRow(0, y, stack -> this.draggedStack = stack));
        this.searchRow.setPosition(this.centerX(this.searchRow.getWidth()), y);
        this.searchRow.setSearchText(this.searchField.getValueWrapper());
        y += this.searchRow.getHeight() + 22;

        String slotsLabel = "Your inventory — right-click: clear a slot, drop an item: set its priority";
        this.addLabel(this.centerX(this.getStringWidth(slotsLabel)), y - 12, 300, 10, 0xFFFFFFFF, slotsLabel);
        this.slotGrid = this.addWidget(
                new WidgetPlayerInventorySlotGrid(0, y, () -> {}, () -> this.draggedStack, this.showRealItems));
        this.slotGrid.setPosition(this.centerX(this.slotGrid.getWidth()), y);
        y += this.slotGrid.getHeight() + 14;

        ButtonGeneric clearAllButton = new ButtonGeneric(this.centerX(130), y, 130, 20, "Clear All Slots");
        this.addButton(clearAllButton, (btn, mb) -> this.clearAll());

        int showItemsWidth = 130;
        ButtonGeneric showItemsButton = new ButtonGeneric(
                this.getScreenWidth() - showItemsWidth - 10, 10, showItemsWidth, 20, this.showItemsButtonText());
        this.addButton(showItemsButton, (btn, mb) -> {
            this.showRealItems = !this.showRealItems;
            btn.setDisplayString(this.showItemsButtonText());
            this.slotGrid.setShowRealItems(this.showRealItems);
        });

        String backLabel = "Back";
        int backWidth = this.getStringWidth(backLabel) + 10;
        ButtonGeneric backButton = new ButtonGeneric(
                this.getScreenWidth() - backWidth - 10, this.getScreenHeight() - 26, backWidth, 20, backLabel);
        this.addButton(backButton, (btn, mb) -> this.closeGui(true));
    }

    private int centerX(int width) {
        return (this.getScreenWidth() - width) / 2;
    }

    private String showItemsButtonText() {
        return "Items: " + (this.showRealItems ? "§aON§r" : "§cOFF§r");
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        if (this.draggedStack != null) {
            InventoryOverlay.renderStackAt(ctx, this.draggedStack, mouseX - 8, mouseY - 8, 1f);
        }
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        boolean result = super.onMouseReleased(click);
        this.draggedStack = null;
        return result;
    }

    private void clearAll() {
        InventorySortConfig.clearAll();
        this.reopen();
    }

    private void reopen() {
        this.removeWidget(this.slotGrid);
        this.removeWidget(this.searchRow);
        this.clearElements();
        this.initGui();
    }
}

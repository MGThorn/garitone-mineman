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
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StoragePointManager;

/**
 * Per-chest rule editor: a slot grid rendered like the chest's real in-game GUI (single or double
 * chest background). Right-click a slot to enable/disable it. Drag an icon from the one-row item
 * search strip (like the creative inventory's search tab, but a single row) and drop it on a chest
 * slot to restrict that slot to only that item — shown as a half-opaque preview icon sitting in the
 * slot. A chest with any rule active blocks non-matching items via MixinAbstractContainerMenu
 * (best-effort, client-click-path only).
 */
public class GuiChestRules extends GuiBase {
    private final StorageBlockEntry entry;
    private GuiTextFieldGeneric searchField;
    private WidgetChestSlotGrid slotGrid;
    private WidgetItemSearchRow searchRow;
    @Nullable private ItemStack draggedStack;
    private boolean showRealItems = true;

    public GuiChestRules(StorageBlockEntry entry, @Nullable Screen parent) {
        this.entry = entry;
        this.title = "Chest Rules: " + entry.getDisplayName();
        this.setParent(parent);
    }

    @Override
    public void initGui() {
        super.initGui();

        int y = 46;

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

        String slotsLabel = "Slots — right-click: enable/disable, drop an item: restrict to it";
        this.addLabel(this.centerX(this.getStringWidth(slotsLabel)), y - 12, 300, 10, 0xFFFFFFFF, slotsLabel);
        this.slotGrid = this.addWidget(new WidgetChestSlotGrid(0, y, this.entry,
                () -> StoragePointManager.getInstance().save(),
                () -> this.draggedStack, this.showRealItems));
        this.slotGrid.setPosition(this.centerX(this.slotGrid.getWidth()), y);
        y += this.slotGrid.getHeight() + 14;

        ButtonGeneric clearRulesButton = new ButtonGeneric(this.centerX(130), y, 130, 20, "Clear All Rules");
        this.addButton(clearRulesButton, (btn, mb) -> this.clearRules());

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

    private void clearRules() {
        this.entry.setSlotItemFilter(null);
        this.entry.setSlotEnabled(null);
        StoragePointManager.getInstance().save();
        this.reopen();
    }

    private void reopen() {
        this.removeWidget(this.slotGrid);
        this.removeWidget(this.searchRow);
        this.clearElements();
        this.initGui();
    }
}

package com.example.client.gui;

import net.minecraft.client.input.MouseButtonEvent;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

public class WidgetStoragePointEntry extends WidgetListEntryBase<StoragePoint> {
    private final WidgetListStoragePoints parentList;
    private final boolean isOdd;
    private final int buttonsStartX;

    public WidgetStoragePointEntry(int x, int y, int width, int height,
            int listIndex, boolean isOdd, StoragePoint entry, WidgetListStoragePoints parentList) {
        super(x, y, width, height, entry, listIndex);
        this.parentList = parentList;
        this.isOdd = isOdd;

        // Layout buttons from right to left
        int by = y + 2;
        int bx = x + width;

        bx -= 54;
        this.addButton(new ButtonGeneric(bx, by, 52, 18, "Remove"),
                new ActionListener(ActionType.REMOVE, entry, parentList));

        String enabledText = "Rendering: " + (entry.isEnabled() ? "§aON§r" : "§cOFF§r");
        int enabledWidth = this.getStringWidth(enabledText) + 10;
        bx -= enabledWidth + 2;
        this.addButton(new ButtonGeneric(bx, by, enabledWidth, 18, enabledText),
                new ActionListener(ActionType.TOGGLE, entry, parentList));

        bx -= 70; // 68px button + 2px gap
        this.addButton(new ButtonGeneric(bx, by, 68, 18, "Configure"),
                new ActionListener(ActionType.CONFIGURE, entry, parentList));

        this.buttonsStartX = bx;
    }

    @Override
    public boolean canSelectAt(MouseButtonEvent click) {
        // Only allow selection when the click lands on empty entry space, not on a button
        return this.hoveredSubWidget == null;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        // Alternating row background
        RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height,
                this.isOdd ? 0x20FFFFFF : 0x30FFFFFF);
        // "SP" icon: red square with white letters
        RenderUtils.drawRect(ctx, this.x + 2, this.y + 3, 16, 16, 0xFFCC0000);
        this.drawCenteredStringWithShadow(ctx, this.x + 10, this.y + 7, 0xFFFFFFFF, "SP");
        // Name — drawn directly (not as a label widget) so it doesn't block row selection via canSelectAt
        this.drawStringWithShadow(ctx, this.x + 20, this.y + 7, 0xFFFFFFFF, this.entry.getName());
        // Lock icon: shown left of the Configure button while the storage point is locked
        if (this.entry.isLocked()) {
            int lockX = this.buttonsStartX - 15;
            int lockY = this.y + 6;
            RenderUtils.drawRect(ctx, lockX + 2, lockY, 1, 3, 0xFFDDDDDD);
            RenderUtils.drawRect(ctx, lockX + 7, lockY, 1, 3, 0xFFDDDDDD);
            RenderUtils.drawRect(ctx, lockX + 2, lockY, 6, 1, 0xFFDDDDDD);
            RenderUtils.drawRect(ctx, lockX, lockY + 3, 10, 7, 0xFFE6B800);
            RenderUtils.drawRect(ctx, lockX + 4, lockY + 5, 2, 3, 0xFF554400);
        }
        // Buttons
        super.render(ctx, mouseX, mouseY, selected);
        // White selection outline drawn last so it appears on top
        if (this.parentList.isSelected(this.entry)) {
            RenderUtils.drawRect(ctx, this.x,                      this.y,                       this.width, 1,           0xFFFFFFFF);
            RenderUtils.drawRect(ctx, this.x,                      this.y + this.height - 1,     this.width, 1,           0xFFFFFFFF);
            RenderUtils.drawRect(ctx, this.x,                      this.y,                       1,          this.height, 0xFFFFFFFF);
            RenderUtils.drawRect(ctx, this.x + this.width - 1,     this.y,                       1,          this.height, 0xFFFFFFFF);
        }
    }

    private enum ActionType { CONFIGURE, TOGGLE, REMOVE }

    private record ActionListener(ActionType type, StoragePoint entry,
            WidgetListStoragePoints parentList) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            switch (this.type) {
                case TOGGLE -> {
                    this.entry.toggleEnabled();
                    StoragePointManager.getInstance().save();
                    this.parentList.refreshEntries();
                }
                case REMOVE -> {
                    if (this.entry.isLocked() && GuiBase.isShiftDown() == false) {
                        this.parentList.getParentGui().addMessage(MessageType.ERROR,
                                "The storage point is locked!\n- Hold Shift to force remove it");
                    }
                    else {
                        StoragePointManager.getInstance().removeStoragePoint(this.entry);
                        this.parentList.refreshEntries();
                    }
                }
                case CONFIGURE -> {
                    GuiStoragePointConfiguration gui = new GuiStoragePointConfiguration(this.entry);
                    gui.setParent(this.parentList.getParentGui());
                    GuiBase.openGui(gui);
                }
            }
        }
    }
}

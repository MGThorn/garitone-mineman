package io.github.mgthorn.garitonemineman.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.client.storage.StorageInventory;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/**
 * One storage point, two lines: active marker, name, dimension, distance, storage blocks
 * (usable/total) and fill bar on top; Set active / Configure / Remove below on the right.
 */
public class WidgetStoragePointEntry extends WidgetListEntryBase<StoragePoint> {
    private static final int BAR_WIDTH = 60;

    private final boolean isOdd;
    private final boolean active;

    public WidgetStoragePointEntry(int x, int y, int width, int height,
            int listIndex, boolean isOdd, StoragePoint entry, WidgetListStoragePoints parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = isOdd;
        this.active = StoragePointManager.getInstance().isActive(entry);

        int by = y + height - 19;
        int bx = x + width - 2;

        bx -= 52;
        this.addButton(new ButtonGeneric(bx, by, 52, 16, "Remove"), (btn, mb) -> {
            if (entry.isLocked() && GuiBase.isShiftDown() == false) {
                parentList.getParentGui().addMessage(MessageType.ERROR,
                        "The storage point is locked!\n- Hold Shift to force remove it");
                return;
            }

            StoragePointManager.getInstance().removeStoragePoint(entry);
            parentList.refreshEntries();
        });

        bx -= 66;
        this.addButton(new ButtonGeneric(bx, by, 64, 16, "Configure"), (btn, mb) -> {
            GuiStoragePointContents gui = new GuiStoragePointContents(entry);
            gui.setParent(parentList.getParentGui());
            GuiBase.openGui(gui);
        });

        bx -= 70;
        ButtonGeneric activeButton = new ButtonGeneric(bx, by, 68, 16, this.active ? "§aActive§r" : "Set active",
                this.active ? "Click to have no active storage point" : "Smart Mineman stores here, other mods count this one");
        this.addButton(activeButton, (btn, mb) -> {
            StoragePointManager.getInstance().setActiveStoragePoint(this.active ? null : entry);
            parentList.refreshEntries();
        });
    }

    @Override
    public boolean canSelectAt(MouseButtonEvent click) {
        return false; // the buttons do everything
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        StoragePoint point = this.entry;
        RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, this.isOdd ? 0x20FFFFFF : 0x30FFFFFF);

        if (this.active) {
            RenderUtils.drawRect(ctx, this.x, this.y, 2, this.height, 0xFF55FF55);
        }

        int ty = this.y + 5;
        int tx = this.x + 6;
        String name = (this.active ? "§a★ §r" : "") + point.getName() + (point.isLocked() ? " §6(locked)§r" : "");
        this.drawStringWithShadow(ctx, tx, ty, 0xFFFFFFFF, name);

        StorageInventory.Summary summary = StorageInventory.getSummary(point);
        String info = "§7" + dimensionName(point.getDimensionId()) + "   " + distanceText(point)
                + "   " + summary.usableChests() + "/" + summary.chests() + " storage blocks§r";
        this.drawStringWithShadow(ctx, tx, ty + 12, 0xFFFFFFFF, info);

        // Fill bar, top right
        int barX = this.x + this.width - BAR_WIDTH - 40;
        RenderUtils.drawRect(ctx, barX, ty, BAR_WIDTH, 8, 0xFF303030);

        if (summary.knownChests() > 0) {
            float fill = summary.fillLevel();
            int color = fill >= 0.9f ? 0xFFE04040 : fill >= 0.7f ? 0xFFE0C040 : 0xFF40C040;
            RenderUtils.drawRect(ctx, barX, ty, Math.round(BAR_WIDTH * fill), 8, color);
            this.drawStringWithShadow(ctx, barX + BAR_WIDTH + 4, ty, 0xFFFFFFFF, Math.round(fill * 100) + "%");
        }
        else {
            this.drawStringWithShadow(ctx, barX + BAR_WIDTH + 4, ty, 0xFF808080, "?");
        }

        super.render(ctx, mouseX, mouseY, selected);
    }

    private static String distanceText(StoragePoint point) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || StoragePointManager.getInstance().isInCurrentDimension(point) == false) {
            return "-";
        }

        var pos = mc.player.blockPosition();
        return Math.round(Math.sqrt(point.distanceSqToArea(pos.getX(), pos.getY(), pos.getZ()))) + "m";
    }

    static String dimensionName(String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dimensionId;
        };
    }
}

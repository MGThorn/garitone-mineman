package com.example.client.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import com.example.client.feature.StorageBlockHighlightController;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StoragePointManager;

public class WidgetStorageBlockEntry extends WidgetListEntryBase<StorageBlockEntry> {
    private final boolean isOdd;
    private final int priorityRank;
    private final ButtonGeneric useButton;
    private final ItemStack iconStack;

    public WidgetStorageBlockEntry(int x, int y, int width, int height, int listIndex, boolean isOdd,
            StorageBlockEntry entry, WidgetListStorageBlocks parentList) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = isOdd;
        this.priorityRank = listIndex + 1;
        this.iconStack = createIconStack(entry);

        int by = y + 1;
        int useWidth = 90;
        int bx = x + width - useWidth - 4;

        this.useButton = new ButtonGeneric(bx, by, useWidth, 18, useText(entry.isUseEnabled()));
        this.addButton(this.useButton, (btn, mouseButton) -> {
            entry.setUseEnabled(!entry.isUseEnabled());
            this.useButton.setDisplayString(useText(entry.isUseEnabled()));
            StoragePointManager.getInstance().save();
        });

        bx -= 20;
        ButtonGeneric downButton = new ButtonGeneric(bx, by, 18, 18, "v");
        downButton.setEnabled(parentList.isLast(entry) == false);
        this.addButton(downButton, (btn, mouseButton) -> parentList.moveEntry(entry, 1));

        bx -= 20;
        ButtonGeneric upButton = new ButtonGeneric(bx, by, 18, 18, "^");
        upButton.setEnabled(parentList.isFirst(entry) == false);
        this.addButton(upButton, (btn, mouseButton) -> parentList.moveEntry(entry, -1));

        bx -= 58;
        String rulesLabel = entry.hasRules() ? "Rules §a*§r" : "Rules";
        ButtonGeneric rulesButton = new ButtonGeneric(bx, by, 54, 18, rulesLabel);
        this.addButton(rulesButton, (btn, mouseButton) -> {
            GuiChestRules gui = new GuiChestRules(entry, parentList.getParentGui());
            GuiBase.openGui(gui);
        });
    }

    private static ItemStack createIconStack(StorageBlockEntry entry) {
        Identifier id = Identifier.parse(entry.getBlockId());
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block != null ? new ItemStack(block.asItem()) : ItemStack.EMPTY;
    }

    private static String useText(boolean isOn) {
        return "Use: " + (isOn ? "§aON§r" : "§cOFF§r");
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        StorageBlockEntry entry = this.getEntry();
        boolean highlighted = StorageBlockHighlightController.isHighlighted(entry);
        int backgroundColor = highlighted ? 0x80FFD700 : (this.isOdd ? 0x20FFFFFF : 0x30FFFFFF);
        RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, backgroundColor);

        ctx.renderItem(this.iconStack, this.x + 4, this.y + (this.height - 16) / 2);

        String label = "#" + this.priorityRank + "  " + entry.getDisplayName()
                + "  (" + entry.getX() + ", " + entry.getY() + ", " + entry.getZ() + ")";
        this.drawStringWithShadow(ctx, this.x + 24, this.y + 6, 0xFFFFFFFF, label);

        super.render(ctx, mouseX, mouseY, selected);
    }
}

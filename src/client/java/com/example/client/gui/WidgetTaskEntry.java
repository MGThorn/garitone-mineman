package com.example.client.gui;

import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import com.example.client.feature.TaskManager.TaskInfo;

public class WidgetTaskEntry extends WidgetListEntryBase<TaskInfo> {
    private static final int INDENT_PER_DEPTH = 16;

    private final boolean isOdd;

    public WidgetTaskEntry(int x, int y, int width, int height, int listIndex, boolean isOdd, TaskInfo entry) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = isOdd;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        TaskInfo task = this.getEntry();
        int backgroundColor = this.isOdd ? 0x20FFFFFF : 0x30FFFFFF;
        RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, backgroundColor);

        if (task != null) {
            int textX = this.x + 6 + task.depth * INDENT_PER_DEPTH;
            String label = "#" + task.order + "  " + task.label;
            this.drawStringWithShadow(ctx, textX, this.y + 6, 0xFFFFFFFF, label);

            String statusText = task.paused ? "§ePAUSED§r" : "§aRUNNING§r";
            int statusWidth = this.getStringWidth(statusText);
            this.drawStringWithShadow(ctx, this.x + this.width - statusWidth - 6, this.y + 6, 0xFFFFFFFF, statusText);
        }

        super.render(ctx, mouseX, mouseY, selected);
    }
}

package com.example.client.gui;

import java.util.Collection;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.GuiContext;
import com.example.client.feature.TaskManager;
import com.example.client.feature.TaskManager.TaskInfo;

public class WidgetListTasks extends WidgetListBase<TaskInfo, WidgetTaskEntry> {
    public WidgetListTasks(int x, int y, int width, int height) {
        super(x, y, width, height, null);
        this.browserEntryHeight = 18;
    }

    @Override
    protected Collection<TaskInfo> getAllEntries() {
        return TaskManager.getTasks();
    }

    @Override
    protected WidgetTaskEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, TaskInfo entry) {
        return new WidgetTaskEntry(x, y, this.browserEntryWidth, this.browserEntryHeight, listIndex, isOdd, entry);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // Tasks appear/finish on their own every tick, so refresh the live snapshot every frame
        // rather than only on explicit user actions like the other list GUIs do.
        this.refreshEntries();
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}

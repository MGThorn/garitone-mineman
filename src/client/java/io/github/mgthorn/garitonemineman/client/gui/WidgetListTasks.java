package io.github.mgthorn.garitonemineman.client.gui;

import java.util.Collection;
import java.util.List;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.mgthorn.garitonemineman.client.feature.TaskManager;
import io.github.mgthorn.garitonemineman.client.feature.TaskManager.TaskInfo;

public class WidgetListTasks extends WidgetListBase<TaskInfo, WidgetTaskEntry> {
    private List<TaskInfo> snapshot = List.of();

    public WidgetListTasks(int x, int y, int width, int height) {
        super(x, y, width, height, null);
        this.browserEntryHeight = 20;
    }

    public boolean isEmpty() {
        return this.snapshot.isEmpty();
    }

    @Override
    protected Collection<TaskInfo> getAllEntries() {
        return this.snapshot;
    }

    @Override
    protected WidgetTaskEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, TaskInfo entry) {
        return new WidgetTaskEntry(x, y, this.browserEntryWidth, this.browserEntryHeight, listIndex, isOdd, entry);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // Tasks appear, change and finish on their own every tick. Rebuild the rows only when the
        // snapshot actually changed, so the buttons aren't recreated every frame.
        List<TaskInfo> current = TaskManager.getTasks();

        if (current.equals(this.snapshot) == false) {
            this.snapshot = current;
            this.refreshEntries();
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);
    }
}

package com.example.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

public class WidgetListStoragePoints extends WidgetListBase<StoragePoint, WidgetStoragePointEntry> {
    private final GuiStoragePoints parentGui;

    public WidgetListStoragePoints(int x, int y, int width, int height, GuiStoragePoints parentGui) {
        super(x, y, width, height, null);
        this.parentGui = parentGui;
        this.browserEntryHeight = 22;
    }

    public GuiStoragePoints getParentGui() {
        return this.parentGui;
    }

    public boolean isSelected(StoragePoint entry) {
        return StoragePointManager.getInstance().getSelectedStoragePoint() == entry;
    }

    @Override
    protected boolean onEntryClicked(@Nullable StoragePoint entry, int listIndex) {
        StoragePoint current = StoragePointManager.getInstance().getSelectedStoragePoint();
        StoragePointManager.getInstance().setSelectedStoragePoint(current == entry ? null : entry);
        this.refreshEntries();
        return true;
    }

    @Override
    protected Collection<StoragePoint> getAllEntries() {
        List<StoragePoint> all = StoragePointManager.getInstance().getStoragePoints();
        String filter = this.parentGui.getSearchFilter().trim().toLowerCase();

        if (filter.isEmpty()) {
            return all;
        }

        return all.stream()
                .filter(point -> point.getName().toLowerCase().contains(filter))
                .collect(Collectors.toList());
    }

    @Override
    protected WidgetStoragePointEntry createListEntryWidget(int x, int y, int listIndex,
            boolean isOdd, StoragePoint entry) {
        return new WidgetStoragePointEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
                listIndex, isOdd, entry, this);
    }
}

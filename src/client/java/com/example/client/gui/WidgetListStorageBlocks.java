package com.example.client.gui;

import java.util.Collection;
import java.util.List;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import com.example.client.storage.StorageBlockEntry;

public class WidgetListStorageBlocks extends WidgetListBase<StorageBlockEntry, WidgetStorageBlockEntry> {
    private final List<StorageBlockEntry> entries;

    public WidgetListStorageBlocks(int x, int y, int width, int height, List<StorageBlockEntry> entries) {
        super(x, y, width, height, null);
        this.entries = entries;
        this.browserEntryHeight = 20;
        this.browserEntriesOffsetY = 6;
    }

    @Override
    protected Collection<StorageBlockEntry> getAllEntries() {
        return this.entries;
    }

    @Override
    protected WidgetStorageBlockEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, StorageBlockEntry entry) {
        return new WidgetStorageBlockEntry(x, y, this.browserEntryWidth, this.browserEntryHeight, listIndex, isOdd, entry);
    }
}

package com.example.client.gui;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import com.example.client.feature.StorageBlockHighlightController;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StoragePointManager;

public class WidgetListStorageBlocks extends WidgetListBase<StorageBlockEntry, WidgetStorageBlockEntry> {
    private final List<StorageBlockEntry> entries;
    private final GuiStoragePointConfiguration parentGui;

    public WidgetListStorageBlocks(int x, int y, int width, int height, List<StorageBlockEntry> entries,
            GuiStoragePointConfiguration parentGui) {
        super(x, y, width, height, null);
        this.entries = entries;
        this.parentGui = parentGui;
        this.browserEntryHeight = 20;
        this.browserEntriesOffsetY = 6;
    }

    public GuiStoragePointConfiguration getParentGui() {
        return this.parentGui;
    }

    @Override
    protected Collection<StorageBlockEntry> getAllEntries() {
        return this.entries;
    }

    @Override
    protected WidgetStorageBlockEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, StorageBlockEntry entry) {
        return new WidgetStorageBlockEntry(x, y, this.browserEntryWidth, this.browserEntryHeight, listIndex, isOdd, entry, this);
    }

    /** Clicking a row (outside its buttons) toggles that block's world-highlight. */
    @Override
    protected boolean onEntryClicked(StorageBlockEntry entry, int listIndex) {
        if (entry != null) {
            StorageBlockHighlightController.toggle(entry);
        }

        return super.onEntryClicked(entry, listIndex);
    }

    public boolean isFirst(StorageBlockEntry entry) {
        return this.entries.indexOf(entry) == 0;
    }

    public boolean isLast(StorageBlockEntry entry) {
        return this.entries.indexOf(entry) == this.entries.size() - 1;
    }

    /**
     * Moves the entry up/down (delta -1/+1) in the priority-ordered chest list — list order is
     * "use first" priority order, consulted by the deposit logic built in Phase 4.
     */
    public void moveEntry(StorageBlockEntry entry, int delta) {
        int index = this.entries.indexOf(entry);

        if (index == -1) {
            return;
        }

        int target = index + delta;

        if (target < 0 || target >= this.entries.size()) {
            return;
        }

        Collections.swap(this.entries, index, target);
        StoragePointManager.getInstance().save();
        this.refreshEntries();
    }
}

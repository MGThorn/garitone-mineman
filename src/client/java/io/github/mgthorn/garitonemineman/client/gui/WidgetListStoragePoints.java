package io.github.mgthorn.garitonemineman.client.gui;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/** Every storage point of the world (all dimensions), filtered by the screen's search text. */
public class WidgetListStoragePoints extends WidgetListBase<StoragePoint, WidgetStoragePointEntry> {
    private final GuiStoragePoints parentGui;

    public WidgetListStoragePoints(int x, int y, int width, int height, GuiStoragePoints parentGui) {
        super(x, y, width, height, null);
        this.parentGui = parentGui;
        this.browserEntryHeight = 34;
    }

    public GuiStoragePoints getParentGui() {
        return this.parentGui;
    }

    @Override
    protected Collection<StoragePoint> getAllEntries() {
        List<StoragePoint> all = StoragePointManager.getInstance().getAllStoragePoints();
        String filter = this.parentGui.getSearchFilter().trim().toLowerCase(Locale.ROOT);

        if (filter.isEmpty()) {
            return all;
        }

        return all.stream()
                .filter(point -> point.getName().toLowerCase(Locale.ROOT).contains(filter))
                .collect(Collectors.toList());
    }

    @Override
    protected WidgetStoragePointEntry createListEntryWidget(int x, int y, int listIndex,
            boolean isOdd, StoragePoint entry) {
        return new WidgetStoragePointEntry(x, y, this.browserEntryWidth, this.browserEntryHeight,
                listIndex, isOdd, entry, this);
    }
}

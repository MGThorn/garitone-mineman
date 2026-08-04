package com.example.client.feature;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import com.example.client.storage.StorageBlockEntry;

/**
 * Tracks which storage block (if any) is currently selected for world-highlighting, so it can be
 * located through walls. Tracked by position rather than by {@link StorageBlockEntry} identity,
 * since {@code GuiStoragePointConfiguration} rescans and replaces its entries' objects every time
 * it's opened. For a double chest, both halves' positions are tracked so both get highlighted.
 */
public final class StorageBlockHighlightController {
    private StorageBlockHighlightController() {}

    @Nullable private static List<BlockPos> highlightedPositions;

    /** Selects/highlights the given entry's position(s) (or clears the highlight if null). */
    public static void setHighlighted(@Nullable StorageBlockEntry entry) {
        highlightedPositions = entry != null ? positionsOf(entry) : null;
    }

    /** Highlights the entry, or clears the highlight if it's already the one highlighted. */
    public static void toggle(StorageBlockEntry entry) {
        List<BlockPos> positions = positionsOf(entry);
        highlightedPositions = positions.equals(highlightedPositions) ? null : positions;
    }

    public static boolean isHighlighted(StorageBlockEntry entry) {
        return highlightedPositions != null && highlightedPositions.equals(positionsOf(entry));
    }

    @Nullable
    public static List<BlockPos> getHighlightedPositions() {
        return highlightedPositions;
    }

    private static List<BlockPos> positionsOf(StorageBlockEntry entry) {
        BlockPos primary = new BlockPos(entry.getX(), entry.getY(), entry.getZ());

        if (entry.hasSecondPosition() == false) {
            return List.of(primary);
        }

        return List.of(primary, new BlockPos(entry.getSecondX(), entry.getSecondY(), entry.getSecondZ()));
    }
}

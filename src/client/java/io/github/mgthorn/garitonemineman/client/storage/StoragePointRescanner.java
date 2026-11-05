package io.github.mgthorn.garitonemineman.client.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Keeps storage points up to date while the player is near them: every {@link #INTERVAL_TICKS} it
 * rescans the area of each storage point within {@link #RANGE} blocks (in this dimension, only when
 * every chunk of the area is loaded, so unloaded chests aren't dropped). New storage blocks are added,
 * removed ones drop out. In singleplayer it also reads the contents of single storage blocks directly
 * (and of double chests that were never seen), so counts stay fresh without opening anything.
 *
 * {@link #rescan(StoragePoint)} does the same for one point right away (Rescan button, configuration screen).
 */
public final class StoragePointRescanner {
    private static final int INTERVAL_TICKS = 100; // ~5s
    private static final double RANGE = 32;

    private static int ticks;

    private StoragePointRescanner() {}

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null || ++ticks < INTERVAL_TICKS) {
            return;
        }

        ticks = 0;
        BlockPos pos = mc.player.blockPosition();
        boolean changed = false;

        for (StoragePoint point : StoragePointManager.getInstance().getStoragePoints()) {
            if (point.distanceSqToArea(pos.getX(), pos.getY(), pos.getZ()) <= RANGE * RANGE) {
                changed |= rescanNoSave(mc.level, point);
            }
        }

        if (changed) {
            StoragePointManager.getInstance().save();
        }
    }

    /**
     * Rescans the point now if it's in this dimension and loaded. Returns false (nothing done) otherwise.
     */
    public static boolean rescan(StoragePoint point) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || StoragePointManager.getInstance().isInCurrentDimension(point) == false || isLoaded(mc.level, point) == false) {
            return false;
        }

        if (rescanNoSave(mc.level, point)) {
            StoragePointManager.getInstance().save();
        }

        return true;
    }

    /** @return whether anything changed */
    private static boolean rescanNoSave(Level level, StoragePoint point) {
        if (isLoaded(level, point) == false) {
            return false;
        }

        BlockPos corner1 = new BlockPos(point.getCorner1X(), point.getCorner1Y(), point.getCorner1Z());
        BlockPos corner2 = new BlockPos(point.getCorner2X(), point.getCorner2Y(), point.getCorner2Z());
        List<StorageBlockEntry> previous = point.getStorageBlocks();
        List<StorageBlockEntry> scanned = StorageBlockScanner.scan(level, corner1, corner2, previous);
        boolean changed = sameBlocks(previous, scanned) == false;

        if (changed) {
            point.setStorageBlocks(scanned);
        }

        for (StorageBlockEntry entry : point.getStorageBlocks()) {
            // Direct reads merge double chests in a guessed slot order, so only use them until the chest is opened once
            if (entry.isDouble() && entry.hasContents()) {
                continue;
            }

            ContentSlot[] read = SingleplayerContentReader.readDirectMerged(entry);

            if (read != null && Arrays.equals(read, entry.getContents()) == false) {
                entry.setContents(read);
                changed = true;
            }
        }

        return changed;
    }

    private static boolean isLoaded(Level level, StoragePoint point) {
        return level.hasChunksAt(
                Math.min(point.getCorner1X(), point.getCorner2X()), Math.min(point.getCorner1Z(), point.getCorner2Z()),
                Math.max(point.getCorner1X(), point.getCorner2X()), Math.max(point.getCorner1Z(), point.getCorner2Z()));
    }

    private static boolean sameBlocks(List<StorageBlockEntry> a, List<StorageBlockEntry> b) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            StorageBlockEntry x = a.get(i);
            StorageBlockEntry y = b.get(i);

            if (x.getX() != y.getX() || x.getY() != y.getY() || x.getZ() != y.getZ()
                    || x.isDouble() != y.isDouble() || x.getBlockId().equals(y.getBlockId()) == false
                    || Objects.equals(x.getSecondX(), y.getSecondX()) == false) {
                return false;
            }
        }

        return true;
    }
}

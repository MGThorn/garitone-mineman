package io.github.mgthorn.garitonemineman.client.storage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Keeps storage points up to date while the player is near them: every {@link #INTERVAL_TICKS} it
 * rescans the area of each storage point within {@link #RANGE} blocks (in this dimension, only when
 * every chunk of the area is loaded, so unloaded chests aren't dropped). New storage blocks are added,
 * removed ones drop out, and the contents are read through {@link StorageContentReader} (singleplayer,
 * Servux or op query) without opening anything. When Servux or the op query answer later, only the
 * contents are read again right away.
 *
 * {@link #rescan(StoragePoint)} does the same for one point right away (Rescan button, configuration screen).
 */
public final class StoragePointRescanner {
    private static final int INTERVAL_TICKS = 100; // ~5s
    private static final double RANGE = 32;

    /** What one rescan did. {@code contentsRead} counts the storage blocks whose contents could be read now. */
    public record Result(int added, int removed, int storageBlocks, int contentsRead, boolean changed) {}

    private static final long MANUAL_ANSWER_WINDOW_MS = 10_000;

    private static final Map<StoragePoint, Long> LAST_RESCAN = new WeakHashMap<>();
    private static final Map<StoragePoint, Long> LAST_MANUAL_RESCAN = new WeakHashMap<>();
    private static int ticks;

    private StoragePointRescanner() {}

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        StorageContentReader.onClientTick();

        if (mc.player == null || mc.level == null) {
            return;
        }

        boolean full = ++ticks >= INTERVAL_TICKS;
        boolean fresh = StorageContentReader.consumeFreshResults();

        if (full == false && fresh == false) {
            return;
        }

        if (full) {
            ticks = 0;
        }

        BlockPos pos = mc.player.blockPosition();
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (StoragePoint point : StoragePointManager.getInstance().getStoragePoints()) {
            boolean inRange = point.distanceSqToArea(pos.getX(), pos.getY(), pos.getZ()) <= RANGE * RANGE;
            // A Rescan button press from further away still picks up the answers that come in afterwards
            boolean pressedRescan = fresh && now - LAST_MANUAL_RESCAN.getOrDefault(point, 0L) < MANUAL_ANSWER_WINDOW_MS;

            if (inRange || pressedRescan) {
                Result result = rescanNoSave(mc.level, point, full && inRange);
                changed |= result != null && result.changed();
            }
        }

        if (changed) {
            StoragePointManager.getInstance().save();
        }
    }

    /**
     * Rescans the point now. Returns null (nothing done) if it's in another dimension or not loaded.
     */
    @Nullable
    public static Result rescan(StoragePoint point) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || StoragePointManager.getInstance().isInCurrentDimension(point) == false) {
            return null;
        }

        Result result = rescanNoSave(mc.level, point, true);
        LAST_MANUAL_RESCAN.put(point, System.currentTimeMillis());

        if (result != null && result.changed()) {
            StoragePointManager.getInstance().save();
        }

        return result;
    }

    /** When this point was last rescanned (ms), or 0 if not since the game started. */
    public static long getLastRescan(StoragePoint point) {
        return LAST_RESCAN.getOrDefault(point, 0L);
    }

    /** @param scanBlocks also look for new and removed storage blocks (false: only read contents again) */
    @Nullable
    private static Result rescanNoSave(Level level, StoragePoint point, boolean scanBlocks) {
        if (isLoaded(level, point) == false) {
            return null;
        }

        boolean changed = false;
        int added = 0;
        int removed = 0;

        if (scanBlocks) {
            BlockPos corner1 = new BlockPos(point.getCorner1X(), point.getCorner1Y(), point.getCorner1Z());
            BlockPos corner2 = new BlockPos(point.getCorner2X(), point.getCorner2Y(), point.getCorner2Z());
            List<StorageBlockEntry> previous = point.getStorageBlocks();
            List<StorageBlockEntry> scanned = StorageBlockScanner.scan(level, corner1, corner2, previous);

            if (sameBlocks(previous, scanned) == false) {
                Set<BlockPos> before = positions(previous);
                Set<BlockPos> after = positions(scanned);
                added = (int) after.stream().filter(p -> before.contains(p) == false).count();
                removed = (int) before.stream().filter(p -> after.contains(p) == false).count();
                point.setStorageBlocks(scanned);
                changed = true;
            }

            LAST_RESCAN.put(point, System.currentTimeMillis());
        }

        int contentsRead = 0;

        for (StorageBlockEntry entry : point.getStorageBlocks()) {
            ContentSlot[] read = StorageContentReader.read(level, entry);

            if (read == null) {
                continue;
            }

            contentsRead++;

            if (Arrays.equals(read, entry.getContents()) == false) {
                entry.setContents(read);
                changed = true;
            }
        }

        return new Result(added, removed, point.getStorageBlocks().size(), contentsRead, changed);
    }

    private static Set<BlockPos> positions(List<StorageBlockEntry> entries) {
        Set<BlockPos> set = new HashSet<>();

        for (StorageBlockEntry entry : entries) {
            set.add(new BlockPos(entry.getX(), entry.getY(), entry.getZ()));
        }

        return set;
    }

    private static boolean isLoaded(Level level, StoragePoint point) {
        int minX = Math.min(point.getCorner1X(), point.getCorner2X()) >> 4;
        int maxX = Math.max(point.getCorner1X(), point.getCorner2X()) >> 4;
        int minZ = Math.min(point.getCorner1Z(), point.getCorner2Z()) >> 4;
        int maxZ = Math.max(point.getCorner1Z(), point.getCorner2Z()) >> 4;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (level.hasChunk(cx, cz) == false) {
                    return false;
                }
            }
        }

        return true;
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

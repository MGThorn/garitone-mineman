package io.github.mgthorn.garitonemineman.client.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Counts what's stored in a storage point, from the last-known contents of its storage blocks.
 * Items inside shulker boxes (or anything else with a container component) count too, as well as the
 * box itself. Chests that were never opened (or read in singleplayer) aren't known and count as empty;
 * {@link Summary#knownChests()} tells how many are known.
 *
 * Results are cached per storage point until {@link StoragePointManager#getRevision()} changes.
 */
public final class StorageInventory {
    /** Fill level and coverage of a storage point. */
    public record Summary(int chests, int usableChests, int knownChests, int usedSlots, int totalSlots) {
        /** Used slots / slots of the known chests, 0 to 1 (0 when nothing is known). */
        public float fillLevel() {
            return this.totalSlots == 0 ? 0 : (float) this.usedSlots / this.totalSlots;
        }
    }

    private record Cached(int revision, Map<String, Long> counts, Summary summary) {}

    private static final Map<StoragePoint, Cached> CACHE = new WeakHashMap<>();

    private StorageInventory() {}

    /** Item id → count, largest first. */
    public static Map<String, Long> getCounts(StoragePoint point) {
        return get(point).counts();
    }

    public static long count(StoragePoint point, String itemId) {
        return get(point).counts().getOrDefault(itemId, 0L);
    }

    public static Summary getSummary(StoragePoint point) {
        return get(point).summary();
    }

    private static Cached get(StoragePoint point) {
        int revision = StoragePointManager.getInstance().getRevision();
        Cached cached = CACHE.get(point);

        if (cached == null || cached.revision() != revision) {
            cached = compute(point, revision);
            CACHE.put(point, cached);
        }

        return cached;
    }

    private static Cached compute(StoragePoint point, int revision) {
        Map<String, Long> counts = new LinkedHashMap<>();
        int usable = 0;
        int known = 0;
        int used = 0;
        int total = 0;

        for (StorageBlockEntry entry : point.getStorageBlocks()) {
            if (entry.isUseEnabled()) {
                usable++;
            }

            ContentSlot[] contents = entry.getContents();

            if (contents == null) {
                continue;
            }

            known++;
            total += contents.length;

            for (ContentSlot slot : contents) {
                if (slot != null && slot.isEmpty() == false) {
                    used++;
                    add(counts, slot);
                }
            }
        }

        Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        return new Cached(revision, Collections.unmodifiableMap(sorted),
                new Summary(point.getStorageBlocks().size(), usable, known, used, total));
    }

    private static void add(Map<String, Long> counts, ContentSlot slot) {
        counts.merge(slot.itemId(), (long) slot.count(), Long::sum);
        List<ContentSlot> inner = slot.inner();

        if (inner != null) {
            for (ContentSlot innerSlot : inner) {
                if (innerSlot != null && innerSlot.isEmpty() == false) {
                    // A stack of boxes would hold count x the contents, but boxes with contents don't stack
                    add(counts, innerSlot);
                }
            }
        }
    }
}

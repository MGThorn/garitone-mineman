package io.github.mgthorn.garitonemineman.client.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.garitonemineman.Reference;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Saving and loading storage points.
 *
 * <pre>
 * config/mineman/storage_points/&lt;sp|mp&gt;/&lt;world or server&gt;/
 *     world.json                  active storage point and the list order
 *     &lt;name&gt;.storagepoint         one file per storage point
 * </pre>
 *
 * A {@code .storagepoint} file holds everything about one storage point: name, flags, dimension,
 * origin, area, storage blocks with their slot rules, and their last-known contents (shulker box
 * contents included). Positions inside it are relative to the origin ({@code absolute = origin + relative}),
 * so a file describes a storage layout independent of where it stands.
 *
 * The old format (one index per world in {@code per_world_storage_points/}, plus write-only snapshot
 * files in one shared {@code storagepoints/} folder) is converted on first load; the old files are
 * left untouched.
 */
final class StoragePointPersistence {
    private static final int FORMAT_VERSION = 2;
    private static final String WORLD_FILE = "world.json";
    private static final String EXTENSION = ".storagepoint";

    private StoragePointPersistence() {}

    /** Everything saved for one world/server. */
    static final class WorldData {
        final Map<String, List<StoragePoint>> byDimension = new LinkedHashMap<>();
        @Nullable StoragePoint active;
    }

    private static Path baseDir() {
        return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID);
    }

    static Path worldDir(boolean singleplayer, String worldId) {
        return baseDir().resolve("storage_points").resolve(singleplayer ? "sp" : "mp").resolve(sanitize(worldId));
    }

    // ---- Loading ----

    static WorldData loadWorld(boolean singleplayer, String worldId) {
        Path dir = worldDir(singleplayer, worldId);

        if (Files.exists(dir.resolve(WORLD_FILE)) == false) {
            WorldData migrated = migrateOldFormat(singleplayer, worldId);

            if (migrated != null) {
                saveWorld(singleplayer, worldId, migrated);
                return migrated;
            }
        }

        WorldData data = new WorldData();
        Map<String, StoragePoint> byFile = new LinkedHashMap<>();

        try (Stream<Path> files = Files.exists(dir) ? Files.list(dir) : Stream.empty()) {
            files.filter(f -> f.getFileName().toString().endsWith(EXTENSION)).sorted().forEach(file -> {
                JsonElement element = JsonUtils.parseJsonFile(file);

                if (element != null && element.isJsonObject()) {
                    StoragePoint point = pointFromJson(element.getAsJsonObject());
                    point.setFileName(file.getFileName().toString());
                    byFile.put(point.getFileName(), point);
                }
            });
        }
        catch (IOException ignored) {}

        // world.json gives the list order and the active storage point; files it doesn't know go last
        String activeFile = null;
        List<String> order = new ArrayList<>();
        JsonElement worldElement = JsonUtils.parseJsonFile(dir.resolve(WORLD_FILE));

        if (worldElement != null && worldElement.isJsonObject()) {
            JsonObject worldObj = worldElement.getAsJsonObject();
            activeFile = JsonUtils.getStringOrDefault(worldObj, "active", null);

            if (worldObj.has("order") && worldObj.get("order").isJsonArray()) {
                for (JsonElement el : worldObj.getAsJsonArray("order")) {
                    order.add(el.getAsString());
                }
            }
        }

        List<StoragePoint> ordered = new ArrayList<>();

        for (String file : order) {
            StoragePoint point = byFile.remove(file);

            if (point != null) {
                ordered.add(point);
            }
        }

        ordered.addAll(byFile.values());

        for (StoragePoint point : ordered) {
            data.byDimension.computeIfAbsent(point.getDimensionId(), d -> new ArrayList<>()).add(point);

            if (point.getFileName() != null && point.getFileName().equals(activeFile)) {
                data.active = point;
            }
        }

        return data;
    }

    // ---- Saving ----

    static void saveWorld(boolean singleplayer, String worldId, WorldData data) {
        Path dir = worldDir(singleplayer, worldId);
        FileUtils.createDirectoriesIfMissing(dir);
        JsonArray order = new JsonArray();

        for (List<StoragePoint> points : data.byDimension.values()) {
            for (StoragePoint point : points) {
                savePoint(dir, point);
                order.add(point.getFileName());
            }
        }

        JsonObject worldObj = new JsonObject();
        worldObj.addProperty("formatVersion", FORMAT_VERSION);

        if (data.active != null && data.active.getFileName() != null) {
            worldObj.addProperty("active", data.active.getFileName());
        }

        worldObj.add("order", order);
        JsonUtils.writeJsonToFile(worldObj, dir.resolve(WORLD_FILE));
    }

    private static void savePoint(Path dir, StoragePoint point) {
        if (point.getFileName() == null) {
            point.setFileName(allocateFileName(dir, point.getName(), null));
        }

        JsonUtils.writeJsonToFile(pointToJson(point), dir.resolve(point.getFileName()));
    }

    /** Renames the point's file to match its new name; returns the new file name. */
    static String renamePointFile(boolean singleplayer, String worldId, StoragePoint point, String newName) {
        Path dir = worldDir(singleplayer, worldId);
        String oldFile = point.getFileName();
        String newFile = allocateFileName(dir, newName, oldFile);

        if (oldFile != null && oldFile.equals(newFile) == false && Files.exists(dir.resolve(oldFile))) {
            try {
                Files.move(dir.resolve(oldFile), dir.resolve(newFile));
            }
            catch (IOException e) {
                return oldFile;
            }
        }

        return newFile;
    }

    static void deletePointFile(boolean singleplayer, String worldId, StoragePoint point) {
        if (point.getFileName() != null) {
            try {
                Files.deleteIfExists(worldDir(singleplayer, worldId).resolve(point.getFileName()));
            }
            catch (IOException ignored) {}
        }
    }

    /** "name.storagepoint", or "name_1.storagepoint", ... if taken by another file. */
    private static String allocateFileName(Path dir, String name, @Nullable String ownFile) {
        String base = sanitize(name);
        String candidate = base + EXTENSION;
        int suffix = 1;

        while (candidate.equals(ownFile) == false && Files.exists(dir.resolve(candidate))) {
            candidate = base + "_" + suffix++ + EXTENSION;
        }

        return candidate;
    }

    private static String sanitize(String name) {
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9 _.\\-]", "_");
        return cleaned.isEmpty() ? "storage_point" : cleaned;
    }

    // ---- JSON ----

    private static JsonObject pointToJson(StoragePoint point) {
        int ox = point.getX();
        int oy = point.getY();
        int oz = point.getZ();

        JsonObject obj = new JsonObject();
        obj.addProperty("formatVersion", FORMAT_VERSION);
        obj.addProperty("name", point.getName());
        obj.addProperty("dimension", point.getDimensionId());
        obj.addProperty("enabled", point.isEnabled());
        obj.addProperty("locked", point.isLocked());
        obj.addProperty("originX", ox);
        obj.addProperty("originY", oy);
        obj.addProperty("originZ", oz);
        obj.add("corner1", relativePos(point.getCorner1X() - ox, point.getCorner1Y() - oy, point.getCorner1Z() - oz));
        obj.add("corner2", relativePos(point.getCorner2X() - ox, point.getCorner2Y() - oy, point.getCorner2Z() - oz));

        JsonArray blocks = new JsonArray();

        for (StorageBlockEntry block : point.getStorageBlocks()) {
            JsonObject blockObj = new JsonObject();
            blockObj.addProperty("x", block.getX() - ox);
            blockObj.addProperty("y", block.getY() - oy);
            blockObj.addProperty("z", block.getZ() - oz);
            blockObj.addProperty("blockId", block.getBlockId());
            blockObj.addProperty("isDouble", block.isDouble());
            blockObj.addProperty("useEnabled", block.isUseEnabled());

            if (block.hasSecondPosition()) {
                blockObj.addProperty("secondX", block.getSecondX() - ox);
                blockObj.addProperty("secondY", block.getSecondY() - oy);
                blockObj.addProperty("secondZ", block.getSecondZ() - oz);
            }

            addRules(blockObj, block);

            if (block.getContents() != null) {
                blockObj.add("contents", contentsToJson(block.getContents()));
                blockObj.addProperty("lastSeen", block.getLastSeen());
            }

            blocks.add(blockObj);
        }

        obj.add("storageBlocks", blocks);
        return obj;
    }

    private static StoragePoint pointFromJson(JsonObject obj) {
        StoragePoint point = new StoragePoint(JsonUtils.getStringOrDefault(obj, "name", "Storage Point"),
                JsonUtils.getBooleanOrDefault(obj, "enabled", true));
        point.setLocked(JsonUtils.getBooleanOrDefault(obj, "locked", false));
        point.setDimensionId(JsonUtils.getStringOrDefault(obj, "dimension", "minecraft:overworld"));
        int ox = JsonUtils.getIntegerOrDefault(obj, "originX", 0);
        int oy = JsonUtils.getIntegerOrDefault(obj, "originY", 0);
        int oz = JsonUtils.getIntegerOrDefault(obj, "originZ", 0);
        point.setX(ox);
        point.setY(oy);
        point.setZ(oz);
        int[] c1 = readPos(obj, "corner1");
        int[] c2 = readPos(obj, "corner2");
        point.setCorner1(ox + c1[0], oy + c1[1], oz + c1[2]);
        point.setCorner2(ox + c2[0], oy + c2[1], oz + c2[2]);

        List<StorageBlockEntry> blocks = new ArrayList<>();

        if (obj.has("storageBlocks") && obj.get("storageBlocks").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("storageBlocks")) {
                if (el.isJsonObject()) {
                    blocks.add(blockFromJson(el.getAsJsonObject(), ox, oy, oz, true));
                }
            }
        }

        point.setStorageBlocks(blocks);
        return point;
    }

    /** @param withContents false for the old index, which never had contents */
    private static StorageBlockEntry blockFromJson(JsonObject obj, int ox, int oy, int oz, boolean withContents) {
        StorageBlockEntry block = new StorageBlockEntry(
                ox + JsonUtils.getIntegerOrDefault(obj, "x", 0),
                oy + JsonUtils.getIntegerOrDefault(obj, "y", 0),
                oz + JsonUtils.getIntegerOrDefault(obj, "z", 0),
                JsonUtils.getStringOrDefault(obj, "blockId", "minecraft:chest"),
                JsonUtils.getBooleanOrDefault(obj, "isDouble", false),
                JsonUtils.getBooleanOrDefault(obj, "useEnabled", true));

        if (obj.has("secondX") && obj.has("secondY") && obj.has("secondZ")) {
            block.setSecondPosition(ox + obj.get("secondX").getAsInt(), oy + obj.get("secondY").getAsInt(), oz + obj.get("secondZ").getAsInt());
        }

        readRules(obj, block);

        if (withContents && obj.has("contents") && obj.get("contents").isJsonArray()) {
            block.restoreContents(contentsFromJson(obj.getAsJsonArray("contents")),
                    obj.has("lastSeen") ? obj.get("lastSeen").getAsLong() : 0);
        }

        return block;
    }

    private static void addRules(JsonObject blockObj, StorageBlockEntry block) {
        String[] filter = block.getSlotItemFilter();

        if (filter != null) {
            JsonArray arr = new JsonArray();

            for (String itemId : filter) {
                if (itemId != null) {
                    arr.add(itemId);
                }
                else {
                    arr.add(JsonNull.INSTANCE);
                }
            }

            blockObj.add("slotItemFilter", arr);
        }

        boolean[] enabled = block.getSlotEnabled();

        if (enabled != null) {
            JsonArray arr = new JsonArray();

            for (boolean value : enabled) {
                arr.add(value);
            }

            blockObj.add("slotEnabled", arr);
        }
    }

    private static void readRules(JsonObject obj, StorageBlockEntry block) {
        if (obj.has("slotItemFilter") && obj.get("slotItemFilter").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("slotItemFilter");
            String[] filter = new String[arr.size()];

            for (int i = 0; i < arr.size(); i++) {
                filter[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
            }

            block.setSlotItemFilter(filter);
        }

        if (obj.has("slotEnabled") && obj.get("slotEnabled").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("slotEnabled");
            boolean[] enabled = new boolean[arr.size()];

            for (int i = 0; i < arr.size(); i++) {
                enabled[i] = arr.get(i).getAsBoolean();
            }

            block.setSlotEnabled(enabled);
        }
    }

    private static JsonArray contentsToJson(ContentSlot[] contents) {
        JsonArray arr = new JsonArray();

        for (ContentSlot slot : contents) {
            arr.add(slotToJson(slot));
        }

        return arr;
    }

    private static JsonElement slotToJson(@Nullable ContentSlot slot) {
        if (slot == null || slot.itemId() == null) {
            return JsonNull.INSTANCE;
        }

        JsonObject obj = new JsonObject();
        obj.addProperty("itemId", slot.itemId());
        obj.addProperty("count", slot.count());

        if (slot.inner() != null) {
            JsonArray inner = new JsonArray();

            for (ContentSlot innerSlot : slot.inner()) {
                inner.add(slotToJson(innerSlot));
            }

            obj.add("inner", inner);
        }

        return obj;
    }

    private static ContentSlot[] contentsFromJson(JsonArray arr) {
        ContentSlot[] contents = new ContentSlot[arr.size()];

        for (int i = 0; i < arr.size(); i++) {
            contents[i] = slotFromJson(arr.get(i));
        }

        return contents;
    }

    private static ContentSlot slotFromJson(JsonElement el) {
        if (el == null || el.isJsonObject() == false) {
            return ContentSlot.EMPTY;
        }

        JsonObject obj = el.getAsJsonObject();
        String itemId = JsonUtils.getStringOrDefault(obj, "itemId", null);

        if (itemId == null) {
            return ContentSlot.EMPTY;
        }

        List<ContentSlot> inner = null;

        if (obj.has("inner") && obj.get("inner").isJsonArray()) {
            inner = new ArrayList<>();

            for (JsonElement innerEl : obj.getAsJsonArray("inner")) {
                ContentSlot innerSlot = slotFromJson(innerEl);

                if (innerSlot.isEmpty() == false) {
                    inner.add(innerSlot);
                }
            }
        }

        return new ContentSlot(itemId, JsonUtils.getIntegerOrDefault(obj, "count", 1), inner);
    }

    private static JsonObject relativePos(int x, int y, int z) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        return obj;
    }

    private static int[] readPos(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            JsonObject obj = parent.getAsJsonObject(key);
            return new int[] { JsonUtils.getIntegerOrDefault(obj, "x", 0), JsonUtils.getIntegerOrDefault(obj, "y", 0),
                    JsonUtils.getIntegerOrDefault(obj, "z", 0) };
        }

        return new int[3];
    }

    // ---- Migration from the old format ----

    /**
     * Reads the old per-world index ({@code per_world_storage_points/<sp|mp>/<world>.json}: dimension →
     * points with absolute positions, "selected" flag, snapshot file name) and takes the chest contents
     * from the old snapshot files ({@code storagepoints/<name>.storagepoint}, relative positions).
     * Returns null when there is nothing to convert.
     */
    @Nullable
    private static WorldData migrateOldFormat(boolean singleplayer, String worldId) {
        Path oldIndex = baseDir().resolve("per_world_storage_points").resolve(singleplayer ? "sp" : "mp").resolve(worldId + ".json");
        JsonElement element = JsonUtils.parseJsonFile(oldIndex);

        if (element == null || element.isJsonObject() == false) {
            return null;
        }

        WorldData data = new WorldData();
        Set<String> usedNames = new HashSet<>();

        for (Map.Entry<String, JsonElement> dimEntry : element.getAsJsonObject().entrySet()) {
            if (dimEntry.getValue().isJsonArray() == false) {
                continue;
            }

            for (JsonElement el : dimEntry.getValue().getAsJsonArray()) {
                if (el.isJsonObject() == false) {
                    continue;
                }

                JsonObject obj = el.getAsJsonObject();
                StoragePoint point = new StoragePoint(JsonUtils.getStringOrDefault(obj, "name", "Storage Point"),
                        JsonUtils.getBooleanOrDefault(obj, "enabled", true));
                point.setDimensionId(dimEntry.getKey());
                point.setLocked(JsonUtils.getBooleanOrDefault(obj, "locked", false));
                point.setX(JsonUtils.getIntegerOrDefault(obj, "x", 0));
                point.setY(JsonUtils.getIntegerOrDefault(obj, "y", 0));
                point.setZ(JsonUtils.getIntegerOrDefault(obj, "z", 0));
                point.setCorner1(JsonUtils.getIntegerOrDefault(obj, "corner1X", 0), JsonUtils.getIntegerOrDefault(obj, "corner1Y", 0),
                        JsonUtils.getIntegerOrDefault(obj, "corner1Z", 0));
                point.setCorner2(JsonUtils.getIntegerOrDefault(obj, "corner2X", 0), JsonUtils.getIntegerOrDefault(obj, "corner2Y", 0),
                        JsonUtils.getIntegerOrDefault(obj, "corner2Z", 0));

                List<StorageBlockEntry> blocks = new ArrayList<>();

                if (obj.has("storageBlocks") && obj.get("storageBlocks").isJsonArray()) {
                    for (JsonElement blockEl : obj.getAsJsonArray("storageBlocks")) {
                        if (blockEl.isJsonObject()) {
                            blocks.add(blockFromJson(blockEl.getAsJsonObject(), 0, 0, 0, false)); // absolute positions
                        }
                    }
                }

                point.setStorageBlocks(blocks);
                copyOldContents(point, JsonUtils.getStringOrDefault(obj, "snapshotFile", null));

                // Unique file names within the new world folder
                String base = sanitize(point.getName());
                String fileName = base + EXTENSION;
                int suffix = 1;

                while (usedNames.add(fileName) == false) {
                    fileName = base + "_" + suffix++ + EXTENSION;
                }

                point.setFileName(fileName);
                data.byDimension.computeIfAbsent(dimEntry.getKey(), d -> new ArrayList<>()).add(point);

                if (JsonUtils.getBooleanOrDefault(obj, "selected", false)) {
                    data.active = point;
                }
            }
        }

        return data;
    }

    /** Takes the contents of matching chests (same position) from an old snapshot file. */
    private static void copyOldContents(StoragePoint point, @Nullable String snapshotFile) {
        if (snapshotFile == null) {
            return;
        }

        JsonElement element = JsonUtils.parseJsonFile(baseDir().resolve("storagepoints").resolve(snapshotFile));

        if (element == null || element.isJsonObject() == false) {
            return;
        }

        JsonObject obj = element.getAsJsonObject();
        int ox = JsonUtils.getIntegerOrDefault(obj, "originX", point.getX());
        int oy = JsonUtils.getIntegerOrDefault(obj, "originY", point.getY());
        int oz = JsonUtils.getIntegerOrDefault(obj, "originZ", point.getZ());
        Map<Long, ContentSlot[]> contentsByPos = new HashMap<>();

        if (obj.has("storageBlocks") && obj.get("storageBlocks").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("storageBlocks")) {
                if (el.isJsonObject() && el.getAsJsonObject().has("contents")) {
                    StorageBlockEntry old = blockFromJson(el.getAsJsonObject(), ox, oy, oz, true);
                    contentsByPos.put(key(old.getX(), old.getY(), old.getZ()), old.getContents());
                }
            }
        }

        for (StorageBlockEntry block : point.getStorageBlocks()) {
            ContentSlot[] contents = contentsByPos.get(key(block.getX(), block.getY(), block.getZ()));

            if (contents != null) {
                block.restoreContents(contents, 0);
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }
}

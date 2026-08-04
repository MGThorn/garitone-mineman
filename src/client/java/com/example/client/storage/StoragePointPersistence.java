package com.example.client.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import com.example.Reference;
import com.example.client.storage.StorageBlockEntry.ContentSlot;

final class StoragePointPersistence {
    private StoragePointPersistence() {}

    private static Path storagePointsDir() {
        return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("storagepoints");
    }

    private static Path perWorldDir(boolean singleplayer) {
        return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("per_world_storage_points")
                .resolve(singleplayer ? "sp" : "mp");
    }

    /**
     * Allocates a new .storagepoint snapshot file name for a newly created storage point, picking a
     * name_1, name_2, ... suffix if the plain name is already taken, and writes it immediately (empty
     * selection/no blocks yet — it gets rewritten with real data the next time {@link #writeSnapshotFile}
     * runs, e.g. once blocks are scanned).
     * @return the resulting file name including the .storagepoint extension
     */
    static String createSnapshotFile(String storagePointName) {
        Path dir = storagePointsDir();
        String candidate = storagePointName;
        int suffix = 1;

        while (Files.exists(dir.resolve(candidate + ".storagepoint"))) {
            candidate = storagePointName + "_" + suffix;
            suffix++;
        }

        String fileName = candidate + ".storagepoint";
        FileUtils.createDirectoriesIfMissing(dir);
        JsonUtils.writeJsonToFileAsPath(new JsonObject(), dir.resolve(fileName));
        return fileName;
    }

    /**
     * Renames a storage point's .storagepoint file to match its new display name, picking a
     * name_1, name_2, ... suffix if another storage point's file already uses that name.
     * If the current file can't be found on disk (e.g. it was deleted manually), the rename
     * is skipped and the old file name is returned unchanged.
     * @return the resulting file name including the .storagepoint extension
     */
    @Nullable
    static String renameSnapshotFile(@Nullable String oldFileName, String newName) {
        Path dir = storagePointsDir();
        Path oldPath = oldFileName != null ? dir.resolve(oldFileName) : null;

        if (oldPath == null || Files.exists(oldPath) == false) {
            return oldFileName;
        }

        String candidate = newName;
        int suffix = 1;
        Path candidatePath = dir.resolve(candidate + ".storagepoint");

        while (candidatePath.equals(oldPath) == false && Files.exists(candidatePath)) {
            candidate = newName + "_" + suffix;
            suffix++;
            candidatePath = dir.resolve(candidate + ".storagepoint");
        }

        if (candidatePath.equals(oldPath)) {
            return oldFileName;
        }

        try {
            Files.move(oldPath, candidatePath);
            return candidate + ".storagepoint";
        }
        catch (java.io.IOException e) {
            return oldFileName;
        }
    }

    /**
     * Writes the rich per-point snapshot file: selection and storage block positions relative to the
     * point's own origin (x/y/z), rules, order, on/off state, and last-known contents. Purely additive —
     * this file is never read back; the combined per-world index (see {@link #loadWorldIndex}) remains
     * the sole source of truth for loading. Lazily allocates a file name if the point doesn't have one
     * yet (e.g. it was loaded from a pre-upgrade combined index that only knew about the old .litematic
     * marker).
     */
    static void writeSnapshotFile(StoragePoint point) {
        if (point.getSnapshotFileName() == null) {
            point.setSnapshotFileName(createSnapshotFile(point.getName()));
        }

        Path dir = storagePointsDir();
        FileUtils.createDirectoriesIfMissing(dir);
        JsonUtils.writeJsonToFileAsPath(pointToSnapshotJson(point), dir.resolve(point.getSnapshotFileName()));
    }

    /**
     * Builds the snapshot JSON with every position expressed relative to the point's own origin
     * (its x/y/z, which stays absolute) — i.e. {@code relative = absolute - origin}. The inverse, for a
     * future reader, is simply {@code absolute = origin + relative} once originX/Y/Z have been read
     * from the same file; no reader exists today since nothing needs to load this file back.
     */
    private static JsonObject pointToSnapshotJson(StoragePoint point) {
        int originX = point.getX();
        int originY = point.getY();
        int originZ = point.getZ();

        JsonObject obj = new JsonObject();
        obj.addProperty("formatVersion", 1);
        obj.addProperty("name", point.getName());
        obj.addProperty("enabled", point.isEnabled());
        obj.addProperty("locked", point.isLocked());
        obj.addProperty("originX", originX);
        obj.addProperty("originY", originY);
        obj.addProperty("originZ", originZ);
        obj.add("corner1", relativePos(point.getCorner1X(), point.getCorner1Y(), point.getCorner1Z(), originX, originY, originZ));
        obj.add("corner2", relativePos(point.getCorner2X(), point.getCorner2Y(), point.getCorner2Z(), originX, originY, originZ));

        JsonArray storageBlocks = new JsonArray();
        for (StorageBlockEntry block : point.getStorageBlocks()) {
            JsonObject blockObj = new JsonObject();
            blockObj.addProperty("x", block.getX() - originX);
            blockObj.addProperty("y", block.getY() - originY);
            blockObj.addProperty("z", block.getZ() - originZ);
            blockObj.addProperty("blockId", block.getBlockId());
            blockObj.addProperty("isDouble", block.isDouble());
            blockObj.addProperty("useEnabled", block.isUseEnabled());

            if (block.hasSecondPosition()) {
                blockObj.addProperty("secondX", block.getSecondX() - originX);
                blockObj.addProperty("secondY", block.getSecondY() - originY);
                blockObj.addProperty("secondZ", block.getSecondZ() - originZ);
            }

            String[] slotItemFilter = block.getSlotItemFilter();
            if (slotItemFilter != null) {
                JsonArray filterArray = new JsonArray();
                for (String itemId : slotItemFilter) {
                    if (itemId != null) {
                        filterArray.add(itemId);
                    }
                    else {
                        filterArray.add(JsonNull.INSTANCE);
                    }
                }
                blockObj.add("slotItemFilter", filterArray);
            }

            boolean[] slotEnabled = block.getSlotEnabled();
            if (slotEnabled != null) {
                JsonArray slotArray = new JsonArray();
                for (boolean enabled : slotEnabled) {
                    slotArray.add(enabled);
                }
                blockObj.add("slotEnabled", slotArray);
            }

            ContentSlot[] contents = block.getContents();
            if (contents != null) {
                JsonArray contentsArray = new JsonArray();
                for (ContentSlot slot : contents) {
                    if (slot == null || slot.itemId() == null) {
                        contentsArray.add(JsonNull.INSTANCE);
                    }
                    else {
                        JsonObject slotObj = new JsonObject();
                        slotObj.addProperty("itemId", slot.itemId());
                        slotObj.addProperty("count", slot.count());
                        contentsArray.add(slotObj);
                    }
                }
                blockObj.add("contents", contentsArray);
            }

            storageBlocks.add(blockObj);
        }
        obj.add("storageBlocks", storageBlocks);

        return obj;
    }

    private static JsonObject relativePos(int x, int y, int z, int originX, int originY, int originZ) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", x - originX);
        obj.addProperty("y", y - originY);
        obj.addProperty("z", z - originZ);
        return obj;
    }

    static Map<String, List<StoragePoint>> loadWorldIndex(boolean singleplayer, String worldOrServerId) {
        Map<String, List<StoragePoint>> result = new HashMap<>();
        Path file = perWorldDir(singleplayer).resolve(worldOrServerId + ".json");
        JsonElement element = JsonUtils.parseJsonFileAsPath(file);

        if (element != null && element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                List<StoragePoint> points = new ArrayList<>();

                if (entry.getValue().isJsonArray()) {
                    for (JsonElement el : entry.getValue().getAsJsonArray()) {
                        if (el.isJsonObject()) {
                            points.add(pointFromJson(el.getAsJsonObject()));
                        }
                    }
                }

                result.put(entry.getKey(), points);
            }
        }

        return result;
    }

    static void saveWorldIndex(boolean singleplayer, String worldOrServerId, Map<String, List<StoragePoint>> data) {
        Path dir = perWorldDir(singleplayer);
        FileUtils.createDirectoriesIfMissing(dir);

        JsonObject root = new JsonObject();

        for (Map.Entry<String, List<StoragePoint>> entry : data.entrySet()) {
            JsonArray array = new JsonArray();

            for (StoragePoint point : entry.getValue()) {
                array.add(pointToJson(point));
            }

            root.add(entry.getKey(), array);
        }

        JsonUtils.writeJsonToFileAsPath(root, dir.resolve(worldOrServerId + ".json"));
    }

    private static JsonObject pointToJson(StoragePoint point) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", point.getName());
        obj.addProperty("enabled", point.isEnabled());
        obj.addProperty("locked", point.isLocked());
        obj.addProperty("selected", point.isSelected());
        obj.addProperty("x", point.getX());
        obj.addProperty("y", point.getY());
        obj.addProperty("z", point.getZ());
        obj.addProperty("corner1X", point.getCorner1X());
        obj.addProperty("corner1Y", point.getCorner1Y());
        obj.addProperty("corner1Z", point.getCorner1Z());
        obj.addProperty("corner2X", point.getCorner2X());
        obj.addProperty("corner2Y", point.getCorner2Y());
        obj.addProperty("corner2Z", point.getCorner2Z());

        String snapshotFileName = point.getSnapshotFileName();
        if (snapshotFileName != null) {
            obj.addProperty("snapshotFile", snapshotFileName);
        }

        JsonArray storageBlocks = new JsonArray();
        for (StorageBlockEntry block : point.getStorageBlocks()) {
            JsonObject blockObj = new JsonObject();
            blockObj.addProperty("x", block.getX());
            blockObj.addProperty("y", block.getY());
            blockObj.addProperty("z", block.getZ());
            blockObj.addProperty("blockId", block.getBlockId());
            blockObj.addProperty("isDouble", block.isDouble());
            blockObj.addProperty("useEnabled", block.isUseEnabled());

            if (block.hasSecondPosition()) {
                blockObj.addProperty("secondX", block.getSecondX());
                blockObj.addProperty("secondY", block.getSecondY());
                blockObj.addProperty("secondZ", block.getSecondZ());
            }

            String[] slotItemFilter = block.getSlotItemFilter();
            if (slotItemFilter != null) {
                JsonArray filterArray = new JsonArray();
                for (String itemId : slotItemFilter) {
                    if (itemId != null) {
                        filterArray.add(itemId);
                    }
                    else {
                        filterArray.add(JsonNull.INSTANCE);
                    }
                }
                blockObj.add("slotItemFilter", filterArray);
            }

            boolean[] slotEnabled = block.getSlotEnabled();
            if (slotEnabled != null) {
                JsonArray slotArray = new JsonArray();
                for (boolean enabled : slotEnabled) {
                    slotArray.add(enabled);
                }
                blockObj.add("slotEnabled", slotArray);
            }

            storageBlocks.add(blockObj);
        }
        obj.add("storageBlocks", storageBlocks);

        return obj;
    }

    private static StoragePoint pointFromJson(JsonObject obj) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "Storage Point";
        boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();

        StoragePoint point = new StoragePoint(name, enabled);
        point.setLocked(obj.has("locked") && obj.get("locked").getAsBoolean());
        point.setSelected(obj.has("selected") && obj.get("selected").getAsBoolean());
        point.setX(obj.has("x") ? obj.get("x").getAsInt() : 0);
        point.setY(obj.has("y") ? obj.get("y").getAsInt() : 0);
        point.setZ(obj.has("z") ? obj.get("z").getAsInt() : 0);
        point.setCorner1(
                obj.has("corner1X") ? obj.get("corner1X").getAsInt() : 0,
                obj.has("corner1Y") ? obj.get("corner1Y").getAsInt() : 0,
                obj.has("corner1Z") ? obj.get("corner1Z").getAsInt() : 0);
        point.setCorner2(
                obj.has("corner2X") ? obj.get("corner2X").getAsInt() : 0,
                obj.has("corner2Y") ? obj.get("corner2Y").getAsInt() : 0,
                obj.has("corner2Z") ? obj.get("corner2Z").getAsInt() : 0);

        @Nullable String snapshotFileName = obj.has("snapshotFile") ? obj.get("snapshotFile").getAsString() : null;
        point.setSnapshotFileName(snapshotFileName);

        if (obj.has("storageBlocks") && obj.get("storageBlocks").isJsonArray()) {
            List<StorageBlockEntry> storageBlocks = new ArrayList<>();

            for (JsonElement el : obj.getAsJsonArray("storageBlocks")) {
                if (el.isJsonObject()) {
                    JsonObject blockObj = el.getAsJsonObject();
                    StorageBlockEntry block = new StorageBlockEntry(
                            blockObj.has("x") ? blockObj.get("x").getAsInt() : 0,
                            blockObj.has("y") ? blockObj.get("y").getAsInt() : 0,
                            blockObj.has("z") ? blockObj.get("z").getAsInt() : 0,
                            blockObj.has("blockId") ? blockObj.get("blockId").getAsString() : "minecraft:chest",
                            blockObj.has("isDouble") && blockObj.get("isDouble").getAsBoolean(),
                            blockObj.has("useEnabled") == false || blockObj.get("useEnabled").getAsBoolean());

                    if (blockObj.has("secondX") && blockObj.has("secondY") && blockObj.has("secondZ")) {
                        block.setSecondPosition(
                                blockObj.get("secondX").getAsInt(),
                                blockObj.get("secondY").getAsInt(),
                                blockObj.get("secondZ").getAsInt());
                    }

                    if (blockObj.has("slotItemFilter") && blockObj.get("slotItemFilter").isJsonArray()) {
                        JsonArray filterArray = blockObj.getAsJsonArray("slotItemFilter");
                        String[] slotItemFilter = new String[filterArray.size()];
                        for (int i = 0; i < filterArray.size(); i++) {
                            JsonElement filterEl = filterArray.get(i);
                            slotItemFilter[i] = filterEl.isJsonNull() ? null : filterEl.getAsString();
                        }
                        block.setSlotItemFilter(slotItemFilter);
                    }

                    if (blockObj.has("slotEnabled") && blockObj.get("slotEnabled").isJsonArray()) {
                        JsonArray slotArray = blockObj.getAsJsonArray("slotEnabled");
                        boolean[] slotEnabled = new boolean[slotArray.size()];
                        for (int i = 0; i < slotArray.size(); i++) {
                            slotEnabled[i] = slotArray.get(i).getAsBoolean();
                        }
                        block.setSlotEnabled(slotEnabled);
                    }

                    storageBlocks.add(block);
                }
            }

            point.setStorageBlocks(storageBlocks);
        }

        return point;
    }
}

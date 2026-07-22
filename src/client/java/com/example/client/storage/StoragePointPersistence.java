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
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import com.example.Reference;

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
     * Writes a new placeholder .litematic file (readable by Litematica) for a newly created storage point,
     * picking a name_1, name_2, ... suffix if the plain name is already taken.
     * @return the resulting file name including the .litematic extension
     */
    static String createLitematicFile(String storagePointName) {
        Path dir = storagePointsDir();
        String candidate = storagePointName;
        int suffix = 1;

        while (Files.exists(dir.resolve(candidate + ".storagepoint.litematic"))) {
            candidate = storagePointName + "_" + suffix;
            suffix++;
        }

        Box box = new Box(BlockPos.ZERO, BlockPos.ZERO, "Main");
        AreaSelection area = new AreaSelection();
        area.setName(candidate);
        area.addSubRegionBox(box, true);

        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, Reference.MOD_NAME);

        if (schematic != null) {
            schematic.getMetadata().setDescription("Mineman storage point marker");
            schematic.writeToFile(dir, candidate + ".storagepoint", true);
        }

        return candidate + ".storagepoint.litematic";
    }

    /**
     * Renames a storage point's .litematic file to match its new display name, picking a
     * name_1, name_2, ... suffix if another storage point's file already uses that name.
     * If the current file can't be found on disk (e.g. it was deleted manually), the rename
     * is skipped and the old file name is returned unchanged.
     * @return the resulting file name including the .litematic extension
     */
    @Nullable
    static String renameLitematicFile(@Nullable String oldFileName, String newName) {
        Path dir = storagePointsDir();
        Path oldPath = oldFileName != null ? dir.resolve(oldFileName) : null;

        if (oldPath == null || Files.exists(oldPath) == false) {
            return oldFileName;
        }

        String candidate = newName;
        int suffix = 1;
        Path candidatePath = dir.resolve(candidate + ".storagepoint.litematic");

        while (candidatePath.equals(oldPath) == false && Files.exists(candidatePath)) {
            candidate = newName + "_" + suffix;
            suffix++;
            candidatePath = dir.resolve(candidate + ".storagepoint.litematic");
        }

        if (candidatePath.equals(oldPath)) {
            return oldFileName;
        }

        try {
            Files.move(oldPath, candidatePath);
            return candidate + ".storagepoint.litematic";
        }
        catch (java.io.IOException e) {
            return oldFileName;
        }
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
        obj.addProperty("x", point.getX());
        obj.addProperty("y", point.getY());
        obj.addProperty("z", point.getZ());
        obj.addProperty("corner1X", point.getCorner1X());
        obj.addProperty("corner1Y", point.getCorner1Y());
        obj.addProperty("corner1Z", point.getCorner1Z());
        obj.addProperty("corner2X", point.getCorner2X());
        obj.addProperty("corner2Y", point.getCorner2Y());
        obj.addProperty("corner2Z", point.getCorner2Z());

        String litematicFileName = point.getLitematicFileName();
        if (litematicFileName != null) {
            obj.addProperty("litematicFile", litematicFileName);
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

        @Nullable String litematicFileName = obj.has("litematicFile") ? obj.get("litematicFile").getAsString() : null;
        point.setLitematicFileName(litematicFileName);

        if (obj.has("storageBlocks") && obj.get("storageBlocks").isJsonArray()) {
            List<StorageBlockEntry> storageBlocks = new ArrayList<>();

            for (JsonElement el : obj.getAsJsonArray("storageBlocks")) {
                if (el.isJsonObject()) {
                    JsonObject blockObj = el.getAsJsonObject();
                    storageBlocks.add(new StorageBlockEntry(
                            blockObj.has("x") ? blockObj.get("x").getAsInt() : 0,
                            blockObj.has("y") ? blockObj.get("y").getAsInt() : 0,
                            blockObj.has("z") ? blockObj.get("z").getAsInt() : 0,
                            blockObj.has("blockId") ? blockObj.get("blockId").getAsString() : "minecraft:chest",
                            blockObj.has("isDouble") && blockObj.get("isDouble").getAsBoolean(),
                            blockObj.has("useEnabled") == false || blockObj.get("useEnabled").getAsBoolean()));
                }
            }

            point.setStorageBlocks(storageBlocks);
        }

        return point;
    }
}

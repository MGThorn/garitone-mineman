package com.example.client.storage;

import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import com.example.Reference;

/**
 * Global (not per-world) per-slot item-priority configuration for the player's own 36 inventory slots
 * (hotbar 0-8, main 9-35 — the same numbering {@link com.example.client.feature.ChestQuickDepositHandler}
 * already uses for {@code quickDepositDisabledSlots}). Consumed by {@code InventorySorter} to fill in
 * "this item belongs here" slots before Item Scroller's own generic sortInventory hotkey runs. Unlike
 * storage points, this isn't tied to any particular world/server, so it's a single global JSON file
 * rather than following {@link StoragePointPersistence}'s per-world split.
 */
public final class InventorySortConfig {
    private static final int SLOT_COUNT = 36;

    @Nullable private static String[] slotItemFilter;
    private static boolean loaded;

    private InventorySortConfig() {}

    @Nullable
    public static String getSlotItem(int slot) {
        ensureLoaded();
        return slotItemFilter != null && slot >= 0 && slot < slotItemFilter.length ? slotItemFilter[slot] : null;
    }

    @Nullable
    public static String[] getSlotItemFilter() {
        ensureLoaded();
        return slotItemFilter;
    }

    public static void setSlotItem(int slot, @Nullable String itemId) {
        ensureLoaded();

        if (slotItemFilter == null || slotItemFilter.length != SLOT_COUNT) {
            String[] newFilter = new String[SLOT_COUNT];

            if (slotItemFilter != null) {
                System.arraycopy(slotItemFilter, 0, newFilter, 0, Math.min(slotItemFilter.length, newFilter.length));
            }

            slotItemFilter = newFilter;
        }

        slotItemFilter[slot] = itemId;
        save();
    }

    public static void clearAll() {
        ensureLoaded();
        slotItemFilter = null;
        save();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        loaded = true;
        JsonElement element = JsonUtils.parseJsonFileAsPath(filePath());

        if (element != null && element.isJsonObject() && element.getAsJsonObject().has("slotItemFilter")) {
            JsonArray array = element.getAsJsonObject().getAsJsonArray("slotItemFilter");
            String[] filter = new String[array.size()];

            for (int i = 0; i < array.size(); i++) {
                JsonElement el = array.get(i);
                filter[i] = el.isJsonNull() ? null : el.getAsString();
            }

            slotItemFilter = filter;
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();

        if (slotItemFilter != null) {
            JsonArray array = new JsonArray();

            for (String itemId : slotItemFilter) {
                if (itemId != null) {
                    array.add(itemId);
                }
                else {
                    array.add(JsonNull.INSTANCE);
                }
            }

            root.add("slotItemFilter", array);
        }

        FileUtils.createDirectoriesIfMissing(filePath().getParent());
        JsonUtils.writeJsonToFileAsPath(root, filePath());
    }

    private static Path filePath() {
        return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("inventory_sort.json");
    }
}

package io.github.mgthorn.garitonemineman.client.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.garitonemineman.Reference;

/**
 * The sort profiles (global, not per world): each profile maps inventory slots to {@link SortRule}s.
 * Slot numbers: hotbar 0-8, main inventory 9-35 (the numbering {@code quickDepositDisabledSlots} uses)
 * and {@link #OFFHAND} for the offhand.
 *
 * Saved in {@code config/mineman/inventory_sort.json}. The old format (one {@code slotItemFilter} array of
 * item ids) becomes the profile "Default".
 */
public final class InventorySortConfig {
    public static final int OFFHAND = 40;
    private static final int FORMAT = 2;
    private static final String DEFAULT_NAME = "Default";

    public static final class Profile {
        private String name;
        private final Map<Integer, SortRule> rules = new TreeMap<>();

        Profile(String name) {
            this.name = name;
        }

        public String getName() { return this.name; }
        /** Slot → rule, ordered by slot. */
        public Map<Integer, SortRule> getRules() { return Collections.unmodifiableMap(this.rules); }
        @Nullable public SortRule getRule(int slot) { return this.rules.get(slot); }
    }

    private static final List<Profile> PROFILES = new ArrayList<>();
    private static int activeIndex;
    private static boolean loaded;

    private InventorySortConfig() {}

    public static List<Profile> getProfiles() {
        ensureLoaded();
        return Collections.unmodifiableList(PROFILES);
    }

    public static Profile getActiveProfile() {
        ensureLoaded();
        return PROFILES.get(activeIndex);
    }

    public static void setActiveProfile(Profile profile) {
        ensureLoaded();
        int index = PROFILES.indexOf(profile);

        if (index >= 0) {
            activeIndex = index;
            save();
        }
    }

    /** Switches to the next profile and returns it. */
    public static Profile cycleProfile() {
        ensureLoaded();
        activeIndex = (activeIndex + 1) % PROFILES.size();
        save();
        return PROFILES.get(activeIndex);
    }

    /** New empty profile (or a copy of {@code copyFrom}), made active. */
    public static Profile addProfile(String name, @Nullable Profile copyFrom) {
        ensureLoaded();
        Profile profile = new Profile(uniqueName(name));

        if (copyFrom != null) {
            copyFrom.rules.forEach((slot, rule) -> profile.rules.put(slot, rule.copy()));
        }

        PROFILES.add(profile);
        activeIndex = PROFILES.size() - 1;
        save();
        return profile;
    }

    public static void renameProfile(Profile profile, String name) {
        ensureLoaded();
        String trimmed = name.trim();

        if (trimmed.isEmpty() == false && trimmed.equals(profile.name) == false) {
            profile.name = uniqueName(trimmed);
            save();
        }
    }

    /** Removes the profile; the last one can't be removed. */
    public static boolean removeProfile(Profile profile) {
        ensureLoaded();

        if (PROFILES.size() <= 1 || PROFILES.remove(profile) == false) {
            return false;
        }

        activeIndex = Math.min(activeIndex, PROFILES.size() - 1);
        save();
        return true;
    }

    // ---- Rules of the active profile ----

    @Nullable
    public static SortRule getRule(int slot) {
        return getActiveProfile().rules.get(slot);
    }

    public static void setRule(int slot, @Nullable SortRule rule) {
        Profile profile = getActiveProfile();

        if (rule == null) {
            profile.rules.remove(slot);
        }
        else {
            profile.rules.put(slot, rule);
        }

        save();
    }

    /** Call after changing a rule object in place. */
    public static void ruleChanged() {
        save();
    }

    public static void clearAll() {
        getActiveProfile().rules.clear();
        save();
    }

    /** Makes the active profile a copy of the current inventory (one rule per non-empty slot). */
    public static void copyFromInventory(List<ItemStack> slots) {
        Profile profile = getActiveProfile();
        profile.rules.clear();

        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);

            if (stack.isEmpty() == false) {
                SortRule rule = new SortRule(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                profile.rules.put(slot < 36 ? slot : OFFHAND, rule);
            }
        }

        save();
    }

    // ---- Saving ----

    private static String uniqueName(String name) {
        String base = name.isBlank() ? "Profile" : name.trim();
        String candidate = base;

        for (int i = 2; nameTaken(candidate); i++) {
            candidate = base + " " + i;
        }

        return candidate;
    }

    private static boolean nameTaken(String name) {
        return PROFILES.stream().anyMatch(p -> p.name.equalsIgnoreCase(name));
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        loaded = true;
        PROFILES.clear();
        activeIndex = 0;
        JsonElement element = JsonUtils.parseJsonFile(filePath());

        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();

            if (root.has("profiles") && root.get("profiles").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("profiles")) {
                    if (el.isJsonObject() == false) {
                        continue;
                    }

                    JsonObject obj = el.getAsJsonObject();
                    Profile profile = new Profile(obj.has("name") ? obj.get("name").getAsString() : "Profile");

                    if (obj.has("slots") && obj.get("slots").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("slots").entrySet()) {
                            SortRule rule = SortRule.fromJson(e.getValue());

                            try {
                                if (rule != null) {
                                    profile.rules.put(Integer.parseInt(e.getKey()), rule);
                                }
                            }
                            catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    PROFILES.add(profile);
                }

                String active = root.has("active") ? root.get("active").getAsString() : null;

                for (int i = 0; i < PROFILES.size(); i++) {
                    if (PROFILES.get(i).name.equals(active)) {
                        activeIndex = i;
                    }
                }
            }
            else if (root.has("slotItemFilter") && root.get("slotItemFilter").isJsonArray()) {
                // Format 1: one item id (or null) per slot
                Profile profile = new Profile(DEFAULT_NAME);
                JsonArray array = root.getAsJsonArray("slotItemFilter");

                for (int i = 0; i < array.size() && i < 36; i++) {
                    if (array.get(i).isJsonNull() == false) {
                        profile.rules.put(i, new SortRule(array.get(i).getAsString()));
                    }
                }

                PROFILES.add(profile);
            }
        }

        if (PROFILES.isEmpty()) {
            PROFILES.add(new Profile(DEFAULT_NAME));
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("active", PROFILES.get(activeIndex).name);
        JsonArray profiles = new JsonArray();

        for (Profile profile : PROFILES) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", profile.name);
            JsonObject slots = new JsonObject();
            profile.rules.forEach((slot, rule) -> slots.add(String.valueOf(slot), rule.toJson()));
            obj.add("slots", slots);
            profiles.add(obj);
        }

        root.add("profiles", profiles);
        FileUtils.createDirectoriesIfMissing(filePath().getParent());
        JsonUtils.writeJsonToFile(root, filePath());
    }

    private static Path filePath() {
        return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("inventory_sort.json");
    }
}

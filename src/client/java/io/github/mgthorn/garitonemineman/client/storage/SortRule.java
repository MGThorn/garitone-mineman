package io.github.mgthorn.garitonemineman.client.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * What belongs in one fixed inventory slot of a sort profile.
 *
 * <ul>
 *   <li>{@code itemId}: the item shown in the slot. For pickaxes, axes, shovels, hoes, swords and spears
 *       it only stands for the tool type while {@code anyOfType} is on (any pickaxe matches).</li>
 *   <li>{@code required} / {@code forbidden}: enchantments the item must have (at least that level) or
 *       must not have, e.g. pickaxe with Silk Touch vs. pickaxe without it.</li>
 *   <li>{@code bestOf}: which matching item wins when several fit.</li>
 *   <li>{@code reserved}: keep the slot empty when nothing fits; otherwise other items may go there.</li>
 * </ul>
 */
public final class SortRule {
    /** Tool types whose items are interchangeable in a slot (the rule's item only marks the type). */
    public static final List<TagKey<Item>> TOOL_TYPES = List.of(
            ItemTags.PICKAXES, ItemTags.AXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.SWORDS, ItemTags.SPEARS);

    public enum BestOf {
        BEST_TIER  ("best_tier",   "Best tier"),
        ENCHANTED  ("enchanted",   "Most enchanted"),
        DURABILITY ("durability",  "Most durability left"),
        LEAST_DURABILITY("least_durability", "Least durability left");

        public final String id;
        public final String displayName;

        BestOf(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public BestOf next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        static BestOf fromId(@Nullable String id) {
            for (BestOf b : values()) {
                if (b.id.equals(id)) {
                    return b;
                }
            }

            return BEST_TIER;
        }
    }

    private final String itemId;
    private boolean anyOfType = true;
    private final Map<String, Integer> required = new LinkedHashMap<>();
    private final List<String> forbidden = new ArrayList<>();
    private BestOf bestOf = BestOf.BEST_TIER;
    private boolean reserved = true;

    public SortRule(String itemId) {
        this.itemId = itemId;
    }

    public SortRule copy() {
        SortRule copy = new SortRule(this.itemId);
        copy.anyOfType = this.anyOfType;
        copy.required.putAll(this.required);
        copy.forbidden.addAll(this.forbidden);
        copy.bestOf = this.bestOf;
        copy.reserved = this.reserved;
        return copy;
    }

    public String getItemId() { return this.itemId; }
    public boolean isAnyOfType() { return this.anyOfType; }
    public void setAnyOfType(boolean anyOfType) { this.anyOfType = anyOfType; }
    /** Enchantment id → minimum level. */
    public Map<String, Integer> getRequired() { return this.required; }
    public List<String> getForbidden() { return this.forbidden; }
    public BestOf getBestOf() { return this.bestOf; }
    public void setBestOf(BestOf bestOf) { this.bestOf = bestOf; }
    public boolean isReserved() { return this.reserved; }
    public void setReserved(boolean reserved) { this.reserved = reserved; }

    @Nullable
    public Item getItem() {
        Identifier id = Identifier.tryParse(this.itemId);
        return id != null ? BuiltInRegistries.ITEM.getOptional(id).orElse(null) : null;
    }

    /** The tool type tag of the rule's item, or null when it isn't one of {@link #TOOL_TYPES}. */
    @Nullable
    public TagKey<Item> getToolType() {
        Item item = this.getItem();

        if (item == null) {
            return null;
        }

        ItemStack probe = new ItemStack(item);

        for (TagKey<Item> tag : TOOL_TYPES) {
            if (probe.is(tag)) {
                return tag;
            }
        }

        return null;
    }

    public boolean matches(ItemStack stack) {
        Item item = this.getItem();

        if (stack.isEmpty() || item == null) {
            return false;
        }

        TagKey<Item> type = this.anyOfType ? this.getToolType() : null;

        if (type != null ? stack.is(type) == false : stack.is(item) == false) {
            return false;
        }

        if (this.required.isEmpty() && this.forbidden.isEmpty()) {
            return true;
        }

        Map<String, Integer> levels = enchantmentLevels(stack);

        for (Map.Entry<String, Integer> e : this.required.entrySet()) {
            if (levels.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }

        for (String id : this.forbidden) {
            if (levels.containsKey(id)) {
                return false;
            }
        }

        return true;
    }

    /** Orders matching stacks so the one this rule prefers comes first. */
    public Comparator<ItemStack> preference() {
        Comparator<ItemStack> tier = Comparator.comparingDouble(SortRule::miningSpeed).reversed();
        Comparator<ItemStack> enchanted = Comparator.comparingInt(SortRule::enchantmentLevelSum).reversed();
        Comparator<ItemStack> durability = Comparator.comparingInt(SortRule::durabilityLeft).reversed();
        Comparator<ItemStack> count = Comparator.comparingInt(ItemStack::getCount).reversed();

        return switch (this.bestOf) {
            case BEST_TIER -> tier.thenComparing(enchanted).thenComparing(durability).thenComparing(count);
            case ENCHANTED -> enchanted.thenComparing(tier).thenComparing(durability).thenComparing(count);
            case DURABILITY -> durability.thenComparing(tier).thenComparing(enchanted).thenComparing(count);
            case LEAST_DURABILITY -> durability.reversed().thenComparing(tier).thenComparing(enchanted).thenComparing(count);
        };
    }

    /** Short description for tooltips, e.g. "Any pickaxe, Silk Touch, best tier, reserved". */
    public String describe() {
        List<String> parts = new ArrayList<>();
        TagKey<Item> type = this.getToolType();
        Item item = this.getItem();

        if (type != null && this.anyOfType) {
            parts.add("Any " + type.location().getPath().replace('_', ' ').replaceAll("s$", ""));
        }
        else {
            parts.add(item != null ? new ItemStack(item).getHoverName().getString() : this.itemId);
        }

        this.required.forEach((id, level) -> parts.add("with " + shortName(id) + (level > 1 ? " " + level : "")));
        this.forbidden.forEach(id -> parts.add("without " + shortName(id)));
        parts.add(this.bestOf.displayName.toLowerCase());
        parts.add(this.reserved ? "kept free when missing" : "may be filled when missing");
        return String.join(", ", parts);
    }

    private static String shortName(String enchantmentId) {
        String path = enchantmentId.contains(":") ? enchantmentId.substring(enchantmentId.indexOf(':') + 1) : enchantmentId;
        StringBuilder sb = new StringBuilder();

        for (String word : path.split("_")) {
            if (word.isEmpty() == false) {
                sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }

        return sb.toString();
    }

    // ---- Item helpers ----

    public static Map<String, Integer> enchantmentLevels(ItemStack stack) {
        Map<String, Integer> map = new LinkedHashMap<>();
        ItemEnchantments enchantments = stack.getEnchantments();

        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            holder.unwrapKey().ifPresent(key -> map.put(key.identifier().toString(), entry.getIntValue()));
        }

        return map;
    }

    private static int enchantmentLevelSum(ItemStack stack) {
        int sum = 0;

        for (var entry : stack.getEnchantments().entrySet()) {
            sum += entry.getIntValue();
        }

        return sum;
    }

    private static double miningSpeed(ItemStack stack) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null ? tool.defaultMiningSpeed() : 0;
    }

    private static int durabilityLeft(ItemStack stack) {
        return stack.getMaxDamage() > 0 ? stack.getMaxDamage() - stack.getDamageValue() : 0;
    }

    // ---- JSON ----

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("item", this.itemId);
        obj.addProperty("anyOfType", this.anyOfType);

        if (this.required.isEmpty() == false) {
            JsonObject req = new JsonObject();
            this.required.forEach(req::addProperty);
            obj.add("required", req);
        }

        if (this.forbidden.isEmpty() == false) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            this.forbidden.forEach(arr::add);
            obj.add("forbidden", arr);
        }

        obj.addProperty("bestOf", this.bestOf.id);
        obj.addProperty("reserved", this.reserved);
        return obj;
    }

    @Nullable
    public static SortRule fromJson(JsonElement element) {
        if (element == null || element.isJsonObject() == false) {
            return null;
        }

        JsonObject obj = element.getAsJsonObject();

        if (obj.has("item") == false) {
            return null;
        }

        SortRule rule = new SortRule(obj.get("item").getAsString());

        if (obj.has("anyOfType")) rule.anyOfType = obj.get("anyOfType").getAsBoolean();
        if (obj.has("bestOf")) rule.bestOf = BestOf.fromId(obj.get("bestOf").getAsString());
        if (obj.has("reserved")) rule.reserved = obj.get("reserved").getAsBoolean();

        if (obj.has("required") && obj.get("required").isJsonObject()) {
            obj.getAsJsonObject("required").entrySet().forEach(e -> rule.required.put(e.getKey(), e.getValue().getAsInt()));
        }

        if (obj.has("forbidden") && obj.get("forbidden").isJsonArray()) {
            obj.getAsJsonArray("forbidden").forEach(e -> rule.forbidden.add(e.getAsString()));
        }

        return rule;
    }
}

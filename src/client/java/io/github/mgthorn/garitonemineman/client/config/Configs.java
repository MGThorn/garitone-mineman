package io.github.mgthorn.garitonemineman.client.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.mgthorn.garitonemineman.client.compat.BaritoneCompat;
import io.github.mgthorn.garitonemineman.client.compat.TweakerooCompat;

public class Configs {
    private static final String GENERIC_KEY = "mineman.config.generic";
    private static final String ESP_EDITOR_KEY = "mineman.config.esp_editor";

    public static class Generic {
        public static final ConfigBoolean    AUTO_EAT                      = new ConfigBoolean("autoEat", false).apply(GENERIC_KEY);
        public static final ConfigString     AUTO_EAT_PICKABLE_SLOTS       = new ConfigString("autoEatPickableSlots", "9").apply(GENERIC_KEY);
        public static final ConfigStringList AUTOEAT_BLACKLIST             = new ConfigStringList("autoEatBlacklist", ImmutableList.of(
                "minecraft:glow_berries",
                "minecraft:enchanted_golden_apple",
                "minecraft:golden_apple",
                "minecraft:chorus_fruit",
                "minecraft:suspicious_stew",
                "minecraft:poisonous_potato",
                "minecraft:pufferfish",
                "minecraft:spider_eye",
                "minecraft:rotten_flesh"
        )).apply(GENERIC_KEY);
        public static final ConfigOptionList  AUTO_EAT_PICK_ORDER           = new ConfigOptionList("autoEatPickOrder", PickOrder.MOST_SATURATION).apply(GENERIC_KEY);
        public static final ConfigInteger    AUTO_EAT_THRESHOLD            = new ConfigInteger("autoEatThreshold", 6, 1, 20, true).apply(GENERIC_KEY);
        public static final ConfigBoolean    MOVE_MATCHING_IGNORE_METADATA = new ConfigBoolean("moveMatchingIgnoreMetadata", false).apply(GENERIC_KEY);
        public static final ConfigBoolean    BERND_DAS_BROT                = new ConfigBoolean("berndDasBrot", false).apply(GENERIC_KEY);
        public static final ConfigString     SELECTION_TOOL_ITEM           = new ConfigString("selectionToolItem", "minecraft:flint").apply(GENERIC_KEY);
        public static final ConfigInteger    MAX_REGION_SIZE               = new ConfigInteger("maxRegionSize", 200_000, 1, 10_000_000, false).apply(GENERIC_KEY);
        public static final ConfigBoolean    SMART_MINEMAN                 = baritoneOption("smartMineman", false);
        public static final ConfigBoolean    HUNGRY_MINEMAN                = baritoneOption("hungryMineman", false);
        public static final ConfigInteger    SMART_MINEMAN_FREE_SLOTS_THRESHOLD = new ConfigInteger("smartMinemanFreeSlotsThreshold", 1, 0, 36, true).apply(GENERIC_KEY);
        public static final ConfigString     QUICK_DEPOSIT_DISABLED_SLOTS  = new ConfigString("quickDepositDisabledSlots", "0-8,9,17,18,26,27,35").apply(GENERIC_KEY);
        public static final ConfigBoolean    PROTECT_STORAGE_POINTS        = baritoneOption("protectStoragePoints", true);
        public static final ConfigStringList PROTECTED_STORAGE_BLOCKS      = new ConfigStringList("protectedStorageBlocks", ImmutableList.of(
                "minecraft:chest",
                "minecraft:trapped_chest",
                "minecraft:barrel",
                "minecraft:ender_chest",
                "minecraft:hopper",
                "minecraft:dropper",
                "minecraft:dispenser",
                "minecraft:shulker_box",
                "minecraft:white_shulker_box",
                "minecraft:orange_shulker_box",
                "minecraft:magenta_shulker_box",
                "minecraft:light_blue_shulker_box",
                "minecraft:yellow_shulker_box",
                "minecraft:lime_shulker_box",
                "minecraft:pink_shulker_box",
                "minecraft:gray_shulker_box",
                "minecraft:light_gray_shulker_box",
                "minecraft:cyan_shulker_box",
                "minecraft:purple_shulker_box",
                "minecraft:blue_shulker_box",
                "minecraft:brown_shulker_box",
                "minecraft:green_shulker_box",
                "minecraft:red_shulker_box",
                "minecraft:black_shulker_box",
                "minecraft:copper_chest",
                "minecraft:exposed_copper_chest",
                "minecraft:weathered_copper_chest",
                "minecraft:oxidized_copper_chest",
                "minecraft:waxed_copper_chest",
                "minecraft:waxed_exposed_copper_chest",
                "minecraft:waxed_weathered_copper_chest",
                "minecraft:waxed_oxidized_copper_chest"
        )).apply(GENERIC_KEY);
        // Blocks a storage point picks up as storage blocks when scanning its area (edited in its picker screen)
        public static final ConfigStringList STORAGE_BLOCK_TYPES           = new ConfigStringList("storageBlockTypes", ImmutableList.of(
                "minecraft:chest",
                "minecraft:trapped_chest",
                "minecraft:barrel",
                "minecraft:shulker_box",
                "minecraft:white_shulker_box",
                "minecraft:orange_shulker_box",
                "minecraft:magenta_shulker_box",
                "minecraft:light_blue_shulker_box",
                "minecraft:yellow_shulker_box",
                "minecraft:lime_shulker_box",
                "minecraft:pink_shulker_box",
                "minecraft:gray_shulker_box",
                "minecraft:light_gray_shulker_box",
                "minecraft:cyan_shulker_box",
                "minecraft:purple_shulker_box",
                "minecraft:blue_shulker_box",
                "minecraft:brown_shulker_box",
                "minecraft:green_shulker_box",
                "minecraft:red_shulker_box",
                "minecraft:black_shulker_box",
                "minecraft:copper_chest",
                "minecraft:exposed_copper_chest",
                "minecraft:weathered_copper_chest",
                "minecraft:oxidized_copper_chest",
                "minecraft:waxed_copper_chest",
                "minecraft:waxed_exposed_copper_chest",
                "minecraft:waxed_weathered_copper_chest",
                "minecraft:waxed_oxidized_copper_chest"
        )).apply(GENERIC_KEY);
        public static final ConfigBoolean    MUTE_ALL_SOUNDS               = new ConfigBoolean("muteAllSounds", false).apply(GENERIC_KEY);
        public static final ConfigBoolean    ITEM_ESP                      = new ConfigBoolean("itemEsp", false).apply(GENERIC_KEY);
        public static final ConfigBoolean    BLOCK_ESP                     = new ConfigBoolean("blockEsp", false).apply(GENERIC_KEY);
        public static final ConfigStringList BLOCK_ESP_BLOCKS              = new ConfigStringList("blockEspBlocks", ImmutableList.of(
                "minecraft:diamond_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:ancient_debris",
                "minecraft:emerald_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:gold_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:nether_gold_ore",
                "minecraft:iron_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:redstone_ore",
                "minecraft:deepslate_redstone_ore",
                "minecraft:lapis_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:copper_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:nether_quartz_ore"
        )).apply(GENERIC_KEY);
        public static final ConfigInteger    BLOCK_ESP_RADIUS               = new ConfigInteger("blockEspRadius", 24, 4, 64, true).apply(GENERIC_KEY);
        public static final ConfigBoolean    ENTITY_ESP                     = new ConfigBoolean("entityEsp", false).apply(GENERIC_KEY);
        public static final ConfigStringList ENTITY_ESP_ENTITIES            = new ConfigStringList("entityEspEntities", ImmutableList.of(
                "minecraft:player",
                "minecraft:zombie",
                "minecraft:zombie_villager",
                "minecraft:skeleton",
                "minecraft:stray",
                "minecraft:wither_skeleton",
                "minecraft:creeper",
                "minecraft:spider",
                "minecraft:cave_spider",
                "minecraft:enderman",
                "minecraft:witch",
                "minecraft:drowned",
                "minecraft:husk",
                "minecraft:phantom",
                "minecraft:pillager",
                "minecraft:vindicator",
                "minecraft:evoker",
                "minecraft:vex",
                "minecraft:ravager",
                "minecraft:blaze",
                "minecraft:ghast",
                "minecraft:slime",
                "minecraft:magma_cube",
                "minecraft:silverfish",
                "minecraft:endermite",
                "minecraft:guardian",
                "minecraft:elder_guardian",
                "minecraft:shulker",
                "minecraft:piglin",
                "minecraft:piglin_brute",
                "minecraft:hoglin",
                "minecraft:zoglin"
        )).apply(GENERIC_KEY);
        public static final ConfigBoolean    DISABLE_PUMPKIN_OVERLAY        = new ConfigBoolean("disablePumpkinOverlay", false).apply(GENERIC_KEY);
        // Only undoes Tweakeroo's switches. Without Tweakeroo it does nothing, and the hover text shows a red warning.
        public static final ConfigBoolean    TOOL_SWITCH_BACK               = new ConfigBoolean("toolSwitchBack", false) {
            @Override
            public String getComment() {
                if (TweakerooCompat.isLoaded()) {
                    return super.getComment();
                }

                return super.getComment() + "\n" + GuiBase.TXT_RED + StringUtils.translate("mineman.config.warning.tweakeroo_missing") + GuiBase.TXT_RST;
            }
        }.apply(GENERIC_KEY);
        public static final ConfigInteger    WEAPON_SWITCH_BACK_DELAY       = new ConfigInteger("weaponSwitchBackDelay", 20, 0, 200, true).apply(GENERIC_KEY);
        public static final ConfigInteger    STRUCTURE_SEARCH_RADIUS        = new ConfigInteger("structureSearchRadius", 5000, 256, 30000, false).apply(GENERIC_KEY);

        // The config screen tabs. The ESP block and entity lists are edited in the Block ESP / Entity ESP screens instead.
        public static final List<IConfigBase> OPTIONS = ImmutableList.of(MOVE_MATCHING_IGNORE_METADATA, BERND_DAS_BROT, SELECTION_TOOL_ITEM, MAX_REGION_SIZE, MUTE_ALL_SOUNDS, DISABLE_PUMPKIN_OVERLAY, TOOL_SWITCH_BACK, WEAPON_SWITCH_BACK_DELAY, STRUCTURE_SEARCH_RADIUS);
        // Sorted by topic: Smart Mineman, Hungry Mineman, protection, auto eat. The slot lists and the auto eat
        // blacklist are edited in their own screens (buttons above the list), see HIDDEN.
        public static final List<IConfigBase> GARITONE = ImmutableList.of(SMART_MINEMAN, SMART_MINEMAN_FREE_SLOTS_THRESHOLD, HUNGRY_MINEMAN, PROTECT_STORAGE_POINTS, PROTECTED_STORAGE_BLOCKS, AUTO_EAT, AUTO_EAT_PICK_ORDER, AUTO_EAT_THRESHOLD);
        // Saved, but not shown in any tab: edited in their own picker screens
        public static final List<IConfigBase> HIDDEN = ImmutableList.of(QUICK_DEPOSIT_DISABLED_SLOTS, AUTO_EAT_PICKABLE_SLOTS, AUTOEAT_BLACKLIST, STORAGE_BLOCK_TYPES);
        public static final List<IConfigBase> ESP = ImmutableList.of(ITEM_ESP, BLOCK_ESP, BLOCK_ESP_RADIUS, ENTITY_ESP);
        // Needs Baritone (the API build). Without it the option does nothing, and the hover text shows a red warning.
        private static ConfigBoolean baritoneOption(String name, boolean defaultValue) {
            return new ConfigBoolean(name, defaultValue) {
                @Override
                public String getComment() {
                    String warningKey = BaritoneCompat.getWarningKey();

                    if (warningKey == null) {
                        return super.getComment();
                    }

                    return super.getComment() + "\n" + GuiBase.TXT_RED + StringUtils.translate(warningKey) + GuiBase.TXT_RST;
                }
            }.apply(GENERIC_KEY);
        }

        // Saved under "Generic": all three tabs plus the ESP lists, which stay in this section so existing configs keep them
        public static final List<IConfigBase> CONFIG_LIST = ImmutableList.<IConfigBase>builder().addAll(OPTIONS).addAll(GARITONE).addAll(HIDDEN).addAll(ESP).add(BLOCK_ESP_BLOCKS, ENTITY_ESP_ENTITIES).build();
    }

    /**
     * Data of the Block ESP / Entity ESP picker screens. Saved with the other configs, but not shown in the config screen.
     */
    public static class EspEditor {
        // "<block id>=#AARRGGBB" for every block with a color other than the default yellow
        public static final ConfigStringList BLOCK_ESP_COLORS  = new ConfigStringList("blockEspColors", ImmutableList.of()).apply(ESP_EDITOR_KEY);
        // The same for entity types, overriding the automatic per-type color
        public static final ConfigStringList ENTITY_ESP_COLORS = new ConfigStringList("entityEspColors", ImmutableList.of()).apply(ESP_EDITOR_KEY);
        // IDs in the order they were last picked, newest first; the picker grids show these first
        public static final ConfigStringList BLOCK_ESP_RECENT  = new ConfigStringList("blockEspRecent", ImmutableList.of()).apply(ESP_EDITOR_KEY);
        public static final ConfigStringList ENTITY_ESP_RECENT = new ConfigStringList("entityEspRecent", ImmutableList.of()).apply(ESP_EDITOR_KEY);
        public static final ConfigStringList AUTO_EAT_BLACKLIST_RECENT = new ConfigStringList("autoEatBlacklistRecent", ImmutableList.of()).apply(ESP_EDITOR_KEY);
        public static final ConfigStringList STORAGE_BLOCK_TYPES_RECENT = new ConfigStringList("storageBlockTypesRecent", ImmutableList.of()).apply(ESP_EDITOR_KEY);

        public static final List<IConfigBase> CONFIG_LIST = ImmutableList.of(BLOCK_ESP_COLORS, ENTITY_ESP_COLORS, BLOCK_ESP_RECENT, ENTITY_ESP_RECENT, AUTO_EAT_BLACKLIST_RECENT, STORAGE_BLOCK_TYPES_RECENT);
    }
}

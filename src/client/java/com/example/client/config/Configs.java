package com.example.client.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;

public class Configs {
    private static final String GENERIC_KEY = "mineman.config.generic";

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
        public static final ConfigOptionList FILL_STRATEGY                 = new ConfigOptionList("fillStrategy", FillStrategy.FIRST_AVAILABLE_SLOT).apply(GENERIC_KEY);
        public static final ConfigBoolean    SMART_MINEMAN                 = new ConfigBoolean("smartMineman", false).apply(GENERIC_KEY);
        public static final ConfigBoolean    HUNGRY_MINEMAN                = new ConfigBoolean("hungryMineman", false).apply(GENERIC_KEY);
        public static final ConfigInteger    SMART_MINEMAN_FREE_SLOTS_THRESHOLD = new ConfigInteger("smartMinemanFreeSlotsThreshold", 1, 0, 36, true).apply(GENERIC_KEY);
        public static final ConfigString     QUICK_DEPOSIT_DISABLED_SLOTS  = new ConfigString("quickDepositDisabledSlots", "0-8,9,17,18,26,27,35").apply(GENERIC_KEY);
        public static final ConfigBoolean    PROTECT_STORAGE_POINTS        = new ConfigBoolean("protectStoragePoints", true).apply(GENERIC_KEY);
        public static final ConfigBoolean    HIDE_BARITONE_CHAT_FEEDBACK   = new ConfigBoolean("hideBaritoneChatFeedback", true).apply(GENERIC_KEY);
        public static final ConfigBoolean    ADVANCED_AUTO_STORE_MODUS     = new ConfigBoolean("advancedAutoStoreModus", true).apply(GENERIC_KEY);
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

        public static final List<IConfigBase> CONFIG_LIST = ImmutableList.of(AUTO_EAT, AUTO_EAT_PICKABLE_SLOTS, AUTOEAT_BLACKLIST, AUTO_EAT_PICK_ORDER, AUTO_EAT_THRESHOLD, MOVE_MATCHING_IGNORE_METADATA, BERND_DAS_BROT, SELECTION_TOOL_ITEM, MAX_REGION_SIZE, FILL_STRATEGY, SMART_MINEMAN, HUNGRY_MINEMAN, SMART_MINEMAN_FREE_SLOTS_THRESHOLD, QUICK_DEPOSIT_DISABLED_SLOTS, PROTECT_STORAGE_POINTS, HIDE_BARITONE_CHAT_FEEDBACK, ADVANCED_AUTO_STORE_MODUS, MUTE_ALL_SOUNDS, ITEM_ESP, BLOCK_ESP, BLOCK_ESP_BLOCKS, BLOCK_ESP_RADIUS);
    }
}

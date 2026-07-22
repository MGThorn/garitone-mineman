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

        public static final List<IConfigBase> CONFIG_LIST = ImmutableList.of(AUTO_EAT, AUTO_EAT_PICKABLE_SLOTS, AUTOEAT_BLACKLIST, AUTO_EAT_PICK_ORDER, AUTO_EAT_THRESHOLD, MOVE_MATCHING_IGNORE_METADATA, BERND_DAS_BROT);
    }
}

package com.example.client.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

public class Hotkeys {
    private static final String HOTKEYS_KEY = "mineman.config.hotkeys";

    public static final ConfigHotkey OPEN_GUI                          = new ConfigHotkey("openGuiMainMenu",                  "B").apply(HOTKEYS_KEY);
    public static final ConfigHotkey AUTO_EAT_TOGGLE                   = new ConfigHotkey("autoEatToggle",                     "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey EASY_EAT                          = new ConfigHotkey("easyEat",                           "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey MOVE_MATCHING_IGNORE_METADATA_TOGGLE = new ConfigHotkey("moveMatchingIgnoreMetadataToggle", "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey SELECTION_SET_POS1                = new ConfigHotkey("selectionSetPos1", "BUTTON_1", KeybindSettings.PRESS_ALLOWEXTRA).apply(HOTKEYS_KEY);
    public static final ConfigHotkey SELECTION_SET_POS2                = new ConfigHotkey("selectionSetPos2", "BUTTON_2", KeybindSettings.PRESS_ALLOWEXTRA).apply(HOTKEYS_KEY);
    public static final ConfigHotkey PAUSE_RESUME_TOGGLE                = new ConfigHotkey("pauseResumeToggle",           "P").apply(HOTKEYS_KEY);
    public static final ConfigHotkey CANCEL_ALL_TASKS                  = new ConfigHotkey("cancelAllTasks",              "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey STORAGE_SET_GOAL                  = new ConfigHotkey("storageSetGoal",              "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey STORAGE_GO_TO                     = new ConfigHotkey("storageGoTo",                 "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey STORAGE_STORE_ITEMS               = new ConfigHotkey("storageStoreItems",           "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey STORAGE_CHEST_QUICK_DEPOSIT       = new ConfigHotkey("storageChestQuickDeposit",    "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey OPEN_TASK_MANAGER                 = new ConfigHotkey("openTaskManager",            "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey OPEN_STORAGE_POINTS               = new ConfigHotkey("openStoragePoints",         "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey MUTE_ALL_SOUNDS_TOGGLE             = new ConfigHotkey("muteAllSoundsToggle",       "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey ITEM_ESP_TOGGLE                     = new ConfigHotkey("itemEspToggle",             "").apply(HOTKEYS_KEY);
    public static final ConfigHotkey BLOCK_ESP_TOGGLE                    = new ConfigHotkey("blockEspToggle",            "").apply(HOTKEYS_KEY);

    public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(OPEN_GUI, AUTO_EAT_TOGGLE, EASY_EAT, MOVE_MATCHING_IGNORE_METADATA_TOGGLE, SELECTION_SET_POS1, SELECTION_SET_POS2, PAUSE_RESUME_TOGGLE, CANCEL_ALL_TASKS, STORAGE_SET_GOAL, STORAGE_GO_TO, STORAGE_STORE_ITEMS, STORAGE_CHEST_QUICK_DEPOSIT, OPEN_TASK_MANAGER, OPEN_STORAGE_POINTS, MUTE_ALL_SOUNDS_TOGGLE, ITEM_ESP_TOGGLE, BLOCK_ESP_TOGGLE);
}

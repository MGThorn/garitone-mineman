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

    public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(OPEN_GUI, AUTO_EAT_TOGGLE, EASY_EAT, MOVE_MATCHING_IGNORE_METADATA_TOGGLE, SELECTION_SET_POS1, SELECTION_SET_POS2);
}

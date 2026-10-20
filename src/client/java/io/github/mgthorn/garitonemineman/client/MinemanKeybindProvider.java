package io.github.mgthorn.garitonemineman.client;

import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import io.github.mgthorn.garitonemineman.Reference;
import io.github.mgthorn.garitonemineman.client.config.Hotkeys;

public class MinemanKeybindProvider implements IKeybindProvider {
    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (fi.dy.masa.malilib.config.options.ConfigHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(Reference.MOD_ID, "mineman.config.hotkeys", Hotkeys.HOTKEY_LIST);
    }
}

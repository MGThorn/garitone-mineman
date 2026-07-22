package com.example.client;

import net.minecraft.client.Minecraft;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.data.ModInfo;
import com.example.Reference;
import com.example.client.config.MinemanConfigHandler;
import com.example.client.config.Configs;
import com.example.client.config.Hotkeys;
import com.example.client.feature.SelectionToolHandler;
import com.example.client.gui.GuiMinemanMain;

public class InitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new MinemanConfigHandler());

        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
            new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiMinemanMain::new)
        );

        Hotkeys.OPEN_GUI.getKeybind().setCallback(
            (action, key) -> {
                GuiBase.openGui(new GuiMinemanMain());
                return true;
            }
        );

        Hotkeys.AUTO_EAT_TOGGLE.getKeybind().setCallback(
            (action, key) -> {
                Configs.Generic.AUTO_EAT.toggleBooleanValue();
                boolean newValue = Configs.Generic.AUTO_EAT.getBooleanValue();
                InfoUtils.printBooleanConfigToggleMessage(
                    Configs.Generic.AUTO_EAT.getPrettyName(), newValue);
                return true;
            }
        );

        Hotkeys.MOVE_MATCHING_IGNORE_METADATA_TOGGLE.getKeybind().setCallback(
            (action, key) -> {
                Configs.Generic.MOVE_MATCHING_IGNORE_METADATA.toggleBooleanValue();
                boolean newValue = Configs.Generic.MOVE_MATCHING_IGNORE_METADATA.getBooleanValue();
                InfoUtils.printBooleanConfigToggleMessage(
                    Configs.Generic.MOVE_MATCHING_IGNORE_METADATA.getPrettyName(), newValue);
                return true;
            }
        );

        Hotkeys.SELECTION_SET_POS1.getKeybind().setCallback(
            (action, key) -> {
                var player = Minecraft.getInstance().player;
                return player != null && SelectionToolHandler.trySetPos1(player);
            }
        );

        Hotkeys.SELECTION_SET_POS2.getKeybind().setCallback(
            (action, key) -> {
                var player = Minecraft.getInstance().player;
                return player != null && SelectionToolHandler.trySetPos2(player);
            }
        );

        InputEventHandler.getKeybindManager().registerKeybindProvider(new MinemanKeybindProvider());
    }
}

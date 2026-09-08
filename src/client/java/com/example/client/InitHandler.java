package com.example.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.data.ModInfo;
import com.example.Reference;
import com.example.client.compat.baritone.BaritoneController;
import com.example.client.config.MinemanConfigHandler;
import com.example.client.config.Configs;
import com.example.client.config.Hotkeys;
import com.example.client.feature.ChestQuickDepositHandler;
import com.example.client.feature.MinemanCancelController;
import com.example.client.feature.MinemanPauseController;
import com.example.client.feature.SelectionToolHandler;
import com.example.client.feature.SmartMinemanHandler;
import com.example.client.gui.GuiMinemanMain;
import com.example.client.gui.GuiStoragePoints;
import com.example.client.gui.GuiTaskManager;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

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

        Hotkeys.MUTE_ALL_SOUNDS_TOGGLE.getKeybind().setCallback(
            (action, key) -> {
                Configs.Generic.MUTE_ALL_SOUNDS.toggleBooleanValue();
                boolean newValue = Configs.Generic.MUTE_ALL_SOUNDS.getBooleanValue();
                InfoUtils.printBooleanConfigToggleMessage(
                    Configs.Generic.MUTE_ALL_SOUNDS.getPrettyName(), newValue);
                if (newValue) {
                    Minecraft.getInstance().getSoundManager().stop();
                }
                return true;
            }
        );

        Hotkeys.ITEM_ESP_TOGGLE.getKeybind().setCallback(
            (action, key) -> {
                Configs.Generic.ITEM_ESP.toggleBooleanValue();
                boolean newValue = Configs.Generic.ITEM_ESP.getBooleanValue();
                InfoUtils.printBooleanConfigToggleMessage(
                    Configs.Generic.ITEM_ESP.getPrettyName(), newValue);
                return true;
            }
        );

        Hotkeys.BLOCK_ESP_TOGGLE.getKeybind().setCallback(
            (action, key) -> {
                Configs.Generic.BLOCK_ESP.toggleBooleanValue();
                boolean newValue = Configs.Generic.BLOCK_ESP.getBooleanValue();
                InfoUtils.printBooleanConfigToggleMessage(
                    Configs.Generic.BLOCK_ESP.getPrettyName(), newValue);
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

        Hotkeys.PAUSE_RESUME_TOGGLE.getKeybind().setCallback(
            (action, key) -> {
                MinemanPauseController.toggle();
                boolean pausedNow = MinemanPauseController.isPaused();

                if (pausedNow) {
                    BaritoneController.pause();
                }
                else {
                    BaritoneController.resume();
                }

                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(
                        Component.literal(pausedNow ? "Mineman: PAUSED" : "Mineman: RESUMED"), true);
                }
                return true;
            }
        );

        Hotkeys.CANCEL_ALL_TASKS.getKeybind().setCallback(
            (action, key) -> {
                MinemanCancelController.cancelAll();

                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(Component.literal("Mineman: CANCELLED"), true);
                }
                return true;
            }
        );

        Hotkeys.STORAGE_SET_GOAL.getKeybind().setCallback(
            (action, key) -> {
                var player = Minecraft.getInstance().player;
                if (player == null) return true;

                StoragePoint point = StoragePointManager.getInstance()
                        .getSelectedOrNearestStoragePoint(player.blockPosition());
                if (point == null) {
                    player.displayClientMessage(Component.literal("No storage point available"), true);
                    return true;
                }

                BaritoneController.sendGoal(new BlockPos(point.getX(), point.getY(), point.getZ()));
                return true;
            }
        );

        Hotkeys.STORAGE_GO_TO.getKeybind().setCallback(
            (action, key) -> {
                var player = Minecraft.getInstance().player;
                if (player == null) return true;

                StoragePoint point = StoragePointManager.getInstance()
                        .getSelectedOrNearestStoragePoint(player.blockPosition());
                if (point == null) {
                    player.displayClientMessage(Component.literal("No storage point available"), true);
                    return true;
                }

                BaritoneController.sendGoTo(new BlockPos(point.getX(), point.getY(), point.getZ()));
                return true;
            }
        );

        Hotkeys.STORAGE_STORE_ITEMS.getKeybind().setCallback(
            (action, key) -> {
                SmartMinemanHandler.triggerManualStoreItems();
                return true;
            }
        );

        Hotkeys.STORAGE_CHEST_QUICK_DEPOSIT.getKeybind().setCallback(
            (action, key) -> {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    ChestQuickDepositHandler.tryQuickDeposit(player);
                }
                return true;
            }
        );

        Hotkeys.OPEN_TASK_MANAGER.getKeybind().setCallback(
            (action, key) -> {
                GuiBase.openGui(new GuiTaskManager(null));
                return true;
            }
        );

        Hotkeys.OPEN_STORAGE_POINTS.getKeybind().setCallback(
            (action, key) -> {
                if (Minecraft.getInstance().player == null) {
                    return true;
                }

                GuiBase.openGui(new GuiStoragePoints(null));
                return true;
            }
        );

        InputEventHandler.getKeybindManager().registerKeybindProvider(new MinemanKeybindProvider());
    }
}

package com.example.client.gui;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.malilib.MaLiLibConfigs;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.data.ModInfo;
import com.example.Reference;
import com.example.client.config.Configs;
import com.example.client.config.Hotkeys;

public class GuiMinemanMain extends GuiConfigsBase {
    private Tab activeTab = Tab.ALL;

    public GuiMinemanMain() {
        this(null);
    }

    public GuiMinemanMain(@Nullable Screen parent) {
        super(10, 50, Reference.MOD_ID, parent, "Mineman");
    }

    @Override
    public void initGui() {
        super.initGui();

        int x = 10;
        int y = 26;

        for (Tab tab : Tab.values()) {
            int w = this.getStringWidth(tab.displayName) + 12;
            ButtonGeneric btn = new ButtonGeneric(x, y, w, 20, tab.displayName, tab.hoverLines);
            boolean disabled = tab == this.activeTab
                    || ((tab == Tab.STORAGE_POINTS || tab == Tab.TASK_MANAGER || tab == Tab.SORTING_SETTINGS) && this.mc.player == null);
            btn.setEnabled(!disabled);
            this.addButton(btn, new TabListener(tab));
            x += w + 2;
        }
    }

    @Override
    protected void buildConfigSwitcher() {
        if (!MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue()) return;

        List<ModInfo> allMods = Registry.CONFIG_SCREEN.getAllModsWithConfigScreens();
        ModInfo thisMod = null;
        for (ModInfo mod : allMods) {
            if (Reference.MOD_ID.equals(mod.modId())) {
                thisMod = mod;
                break;
            }
        }
        if (thisMod == null) return;

        final ModInfo selectedMod = thisMod;
        this.modSwitchWidget = new WidgetDropDownList<ModInfo>(
                GuiUtils.getScaledWindowWidth() - 155, 6, 130, 18, 200, 10, allMods) {
            {
                this.selectedEntry = selectedMod;
            }

            @Override
            protected void setSelectedEntry(int index) {
                super.setSelectedEntry(index);
                if (this.selectedEntry != null && minecraft != null
                        && this.selectedEntry.configScreenSupplier() != null) {
                    minecraft.setScreen(this.selectedEntry.configScreenSupplier().get());
                }
            }

            @Override
            protected String getDisplayString(ModInfo entry) {
                return entry.modName();
            }
        };
        this.addWidget(this.modSwitchWidget);
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        switch (this.activeTab) {
            case GENERIC:
                return ConfigOptionWrapper.createFor(Configs.Generic.CONFIG_LIST);
            case HOTKEYS:
                return ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST);
            case ALL: {
                List<ConfigOptionWrapper> list = new java.util.ArrayList<>();
                list.addAll(ConfigOptionWrapper.createFor(Configs.Generic.CONFIG_LIST));
                list.addAll(ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST));
                return list;
            }
            default:
                return Collections.emptyList();
        }
    }

    private void switchTab(Tab tab) {
        if (tab == Tab.STORAGE_POINTS) {
            if (this.mc.player == null) {
                return;
            }
            GuiBase.openGui(new GuiStoragePoints(this));
            return;
        }
        if (tab == Tab.TASK_MANAGER) {
            if (this.mc.player == null) {
                return;
            }
            GuiBase.openGui(new GuiTaskManager(this));
            return;
        }
        if (tab == Tab.SORTING_SETTINGS) {
            if (this.mc.player == null) {
                return;
            }
            GuiBase.openGui(new GuiInventorySortConfig(this));
            return;
        }
        this.activeTab = tab;
        this.clearElements();
        this.reCreateListWidget();
        this.initGui();
    }

    private enum Tab {
        ALL("All"),
        GENERIC("Generic"),
        HOTKEYS("Hotkeys"),
        STORAGE_POINTS("Storage Points"),
        TASK_MANAGER("Task Manager"),
        SORTING_SETTINGS("Sorting Settings",
                "Configure which item belongs in each of your inventory slots.",
                "When Item Scroller's sortInventory hotkey is pressed, those slots",
                "are filled in first (the best tool tier wins for tool slots),",
                "then the rest of your inventory is sorted normally.");

        final String displayName;
        final String[] hoverLines;

        Tab(String displayName, String... hoverLines) {
            this.displayName = displayName;
            this.hoverLines = hoverLines;
        }
    }

    private class TabListener implements IButtonActionListener {
        private final Tab tab;

        TabListener(Tab tab) {
            this.tab = tab;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            switchTab(this.tab);
        }
    }
}

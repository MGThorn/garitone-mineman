package com.example.client.gui;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.util.GuiUtils;
import com.example.client.feature.SelectionToolHandler;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

public class GuiStoragePoints extends GuiListBase<StoragePoint, WidgetStoragePointEntry, WidgetListStoragePoints> {
    private String searchFilter = "";
    private GuiTextFieldGeneric searchField;

    public GuiStoragePoints(@Nullable Screen parent) {
        super(6, 30);
        this.title = "Storage Points";
        this.setParent(parent);
    }

    public String getSearchFilter() {
        return this.searchFilter;
    }

    @Override
    protected WidgetListStoragePoints createListWidget(int listX, int listY) {
        return new WidgetListStoragePoints(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
    }

    @Override
    protected int getBrowserWidth() {
        return GuiUtils.getScaledWindowWidth() - 20;
    }

    @Override
    protected int getBrowserHeight() {
        return GuiUtils.getScaledWindowHeight() - 58;
    }

    @Override
    public void initGui() {
        this.reCreateListWidget();
        super.initGui();

        int searchWidth = 150;
        int searchX = GuiUtils.getScaledWindowWidth() - searchWidth - 10;
        int searchY = 6;
        String searchLabel = "Search:";
        int searchLabelWidth = this.getStringWidth(searchLabel) + 4;
        this.addLabel(searchX - searchLabelWidth, searchY + 2, searchLabelWidth, 14, 0xFFFFFFFF, searchLabel);

        this.searchField = new GuiTextFieldGeneric(searchX, searchY, searchWidth, 16, this.font);
        this.searchField.setMaxLengthWrapper(64);
        this.searchField.setValueWrapper(this.searchFilter);
        this.addTextField(this.searchField, tf -> {
            this.searchFilter = tf.getValueWrapper();
            if (this.getListWidget() != null) {
                this.getListWidget().refreshEntries();
            }
            return false;
        }, TextFieldType.STRING);

        int y = GuiUtils.getScaledWindowHeight() - 26;
        int x = 6;

        ButtonGeneric addBtn = new ButtonGeneric(x, y, 130, 20, "Add Storage Point");
        this.addButton(addBtn, (btn, mb) -> {
            BlockPos corner1 = SelectionToolHandler.getPos1();
            BlockPos corner2 = SelectionToolHandler.getPos2();

            if (corner1 == null || corner2 == null) {
                this.addMessage(MessageType.ERROR, "No selection!\n- Use "
                        + SelectionToolHandler.getToolDisplayName() + " to select two corners first");
                return;
            }

            if (this.mc.player != null) {
                var pos = this.mc.player.blockPosition();
                StoragePoint created = StoragePointManager.getInstance().addStoragePoint("New Storage Point",
                        pos.getX(), pos.getY(), pos.getZ(), corner1, corner2);
                SelectionToolHandler.clearSelection();

                if (created != null) {
                    GuiStoragePointConfiguration gui = new GuiStoragePointConfiguration(created);
                    gui.setParent(this);
                    GuiBase.openGui(gui);
                    return;
                }
            }

            if (this.getListWidget() != null) this.getListWidget().refreshEntries();
        });
        x += 130 + 6;

        ButtonGeneric setPos1Btn = new ButtonGeneric(x, y, 90, 20, "Set Pos1");
        this.addButton(setPos1Btn, (btn, mb) -> {
            if (this.mc.player != null) {
                SelectionToolHandler.setPos1FromLookRay(this.mc.player);
            }
        });
        x += 90 + 4;

        ButtonGeneric setPos2Btn = new ButtonGeneric(x, y, 90, 20, "Set Pos2");
        this.addButton(setPos2Btn, (btn, mb) -> {
            if (this.mc.player != null) {
                SelectionToolHandler.setPos2FromLookRay(this.mc.player);
            }
        });
        x += 90 + 4;

        ButtonGeneric clearSelectionBtn = new ButtonGeneric(x, y, 110, 20, "Clear Selection");
        this.addButton(clearSelectionBtn, (btn, mb) -> SelectionToolHandler.clearSelection());
    }
}

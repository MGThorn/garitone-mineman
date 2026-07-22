package com.example.client.gui;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.GuiUtils;
import com.example.client.feature.SelectionToolHandler;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

public class GuiStoragePoints extends GuiListBase<StoragePoint, WidgetStoragePointEntry, WidgetListStoragePoints> {
    public GuiStoragePoints(@Nullable Screen parent) {
        super(6, 30);
        this.title = "Storage Points";
        this.setParent(parent);
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

        int y = GuiUtils.getScaledWindowHeight() - 26;
        ButtonGeneric addBtn = new ButtonGeneric(6, y, 130, 20, "Add Storage Point");
        this.addButton(addBtn, (btn, mb) -> {
            BlockPos corner1 = SelectionToolHandler.getPos1();
            BlockPos corner2 = SelectionToolHandler.getPos2();

            if (corner1 == null || corner2 == null) {
                this.addMessage(MessageType.ERROR, "No selection!\n- Use Flint to select two corners first");
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
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        this.drawStringWithShadow(ctx, this.title, 6, 6, COLOR_WHITE);
    }
}

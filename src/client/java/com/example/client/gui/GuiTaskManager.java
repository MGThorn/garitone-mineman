package com.example.client.gui;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.util.GuiUtils;
import com.example.client.feature.TaskManager.TaskInfo;

public class GuiTaskManager extends GuiListBase<TaskInfo, WidgetTaskEntry, WidgetListTasks> {
    public GuiTaskManager(@Nullable Screen parent) {
        super(6, 30);
        this.title = "Task Manager";
        this.setParent(parent);
    }

    @Override
    protected WidgetListTasks createListWidget(int listX, int listY) {
        return new WidgetListTasks(listX, listY, this.getBrowserWidth(), this.getBrowserHeight());
    }

    @Override
    protected int getBrowserWidth() {
        return GuiUtils.getScaledWindowWidth() - 20;
    }

    @Override
    protected int getBrowserHeight() {
        return GuiUtils.getScaledWindowHeight() - 40;
    }

    @Override
    public void initGui() {
        this.reCreateListWidget();
        super.initGui();
    }
}

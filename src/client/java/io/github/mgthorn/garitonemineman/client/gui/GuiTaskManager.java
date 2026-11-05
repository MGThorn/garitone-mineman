package io.github.mgthorn.garitonemineman.client.gui;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.GuiUtils;
import io.github.mgthorn.garitonemineman.client.compat.BaritoneCompat;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.feature.MinemanCancelController;
import io.github.mgthorn.garitonemineman.client.feature.MinemanPauseController;
import io.github.mgthorn.garitonemineman.client.feature.TaskManager;
import io.github.mgthorn.garitonemineman.client.feature.TaskManager.TaskInfo;

/**
 * Live list of the running Mineman tasks. Every row has Pause/Resume and Cancel; at the bottom a
 * status line (Baritone, Smart Mineman, Hungry Mineman) and Pause all / Cancel all / Back.
 */
public class GuiTaskManager extends GuiListBase<TaskInfo, WidgetTaskEntry, WidgetListTasks> {
    private static final int LIST_TOP = 24;
    private static final int BOTTOM_AREA = 48;

    private ButtonGeneric pauseAllButton;
    private boolean pauseShownAsPaused;

    public GuiTaskManager(@Nullable Screen parent) {
        super(10, LIST_TOP);
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
        return GuiUtils.getScaledWindowHeight() - LIST_TOP - BOTTOM_AREA;
    }

    @Override
    public void initGui() {
        this.reCreateListWidget();
        super.initGui();

        int y = GuiUtils.getScaledWindowHeight() - 26;
        int x = 10;

        this.pauseShownAsPaused = MinemanPauseController.isUserPaused();
        this.pauseAllButton = new ButtonGeneric(x, y, 80, 20, pauseAllText());
        this.pauseAllButton.setHoverStrings("Pauses or resumes all Mineman tasks (same as the pause/resume hotkey)");
        this.addButton(this.pauseAllButton, (button, mouseButton) -> TaskManager.togglePause());
        x += 82;

        ButtonGeneric cancelAllButton = new ButtonGeneric(x, y, 80, 20, "Cancel all");
        cancelAllButton.setHoverStrings("Cancels every task, Baritone's own job included (same as the cancel hotkey)");
        this.addButton(cancelAllButton, (button, mouseButton) -> MinemanCancelController.cancelAll());

        int backWidth = 60;
        ButtonGeneric backButton = new ButtonGeneric(GuiUtils.getScaledWindowWidth() - 10 - backWidth, y, backWidth, 20, "Back");
        this.addButton(backButton, (button, mouseButton) -> GuiBase.openGui(this.getParent()));
    }

    private static String pauseAllText() {
        return MinemanPauseController.isUserPaused() ? "Resume all" : "Pause all";
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        boolean paused = MinemanPauseController.isUserPaused();

        if (this.pauseAllButton != null && paused != this.pauseShownAsPaused) {
            this.pauseShownAsPaused = paused;
            this.pauseAllButton.setDisplayString(pauseAllText());
        }

        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        WidgetListTasks list = this.getListWidget();

        if (list != null && list.isEmpty()) {
            String empty = "No tasks running";
            int emptyX = (GuiUtils.getScaledWindowWidth() - this.getStringWidth(empty)) / 2;
            this.drawStringWithShadow(ctx, empty, emptyX, LIST_TOP + 20, 0xFFA0A0A0);
        }

        this.drawStringWithShadow(ctx, statusLine(), 10, GuiUtils.getScaledWindowHeight() - 40, 0xFFFFFFFF);
    }

    private static String statusLine() {
        String baritone = BaritoneCompat.isLoaded() ? "§aOK§r"
                : BaritoneCompat.isWrongBuild() ? "§cwrong build§r" : "§cmissing§r";
        return "Baritone: " + baritone
                + "    Smart Mineman: " + onOff(Configs.Generic.SMART_MINEMAN.getBooleanValue())
                + "    Hungry Mineman: " + onOff(Configs.Generic.HUNGRY_MINEMAN.getBooleanValue())
                + "    Active storage: " + activeStorage()
                + (MinemanPauseController.isUserPaused() ? "    §ePAUSED§r" : "");
    }

    private static String activeStorage() {
        var point = io.github.mgthorn.garitonemineman.client.storage.StoragePointManager.getInstance().getActiveStoragePoint();
        return point != null ? "§a" + point.getName() + "§r" : "§7none§r";
    }

    private static String onOff(boolean value) {
        return value ? "§aon§r" : "§7off§r";
    }
}

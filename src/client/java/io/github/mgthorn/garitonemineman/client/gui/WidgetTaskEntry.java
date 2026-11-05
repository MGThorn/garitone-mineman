package io.github.mgthorn.garitonemineman.client.gui;

import net.minecraft.client.input.MouseButtonEvent;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.client.feature.MinemanPauseController;
import io.github.mgthorn.garitonemineman.client.feature.TaskManager;
import io.github.mgthorn.garitonemineman.client.feature.TaskManager.TaskInfo;

/**
 * One task row: tree lines for nested tasks, the label, a coloured status, and Pause/Resume and
 * Cancel buttons on the right. The buttons act right away; the list picks up the new state on the
 * next frame (see {@link WidgetListTasks}).
 */
public class WidgetTaskEntry extends WidgetListEntryBase<TaskInfo> {
    private static final int INDENT_PER_DEPTH = 14;
    private static final int TREE_LINE_COLOR = 0xFF808080;
    private static final int PAUSE_BUTTON_WIDTH = 50;
    private static final int CANCEL_BUTTON_WIDTH = 20;

    private final boolean isOdd;
    private final int buttonsStartX;

    public WidgetTaskEntry(int x, int y, int width, int height, int listIndex, boolean isOdd, TaskInfo entry) {
        super(x, y, width, height, entry, listIndex);
        this.isOdd = isOdd;

        int by = y + 1;
        int bx = x + width - 2;

        bx -= CANCEL_BUTTON_WIDTH;
        ButtonGeneric cancelButton = new ButtonGeneric(bx, by, CANCEL_BUTTON_WIDTH, height - 2, "§cX§r");
        cancelButton.setHoverStrings(cancelHover(entry));
        this.addButton(cancelButton, (button, mouseButton) -> TaskManager.cancel(entry.kind()));

        bx -= PAUSE_BUTTON_WIDTH + 2;
        String pauseText = MinemanPauseController.isUserPaused() ? "Resume" : "Pause";
        ButtonGeneric pauseButton = new ButtonGeneric(bx, by, PAUSE_BUTTON_WIDTH, height - 2, pauseText);
        pauseButton.setHoverStrings("Pauses or resumes all Mineman tasks together (same as the pause/resume hotkey)");
        this.addButton(pauseButton, (button, mouseButton) -> TaskManager.togglePause());

        this.buttonsStartX = bx;
    }

    private static String cancelHover(TaskInfo entry) {
        return switch (entry.kind()) {
            case BARITONE_JOB -> "Cancel the Baritone job and everything under it";
            case STORE_TRIP -> "Stop the store trip; the job continues, no new automatic trip until it ends";
            case EATING, QUEUED_EAT -> "Stop eating; Hungry Mineman doesn't eat again until the job ends";
            case QUICK_DEPOSIT -> "Stop the quick deposit";
        };
    }

    @Override
    public boolean canSelectAt(MouseButtonEvent click) {
        return false; // rows aren't selectable, only their buttons react
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        TaskInfo task = this.getEntry();
        RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, this.isOdd ? 0x20FFFFFF : 0x30FFFFFF);

        if (task != null) {
            int textY = this.y + (this.height - 8) / 2;
            int textX = this.x + 6 + task.depth() * INDENT_PER_DEPTH;

            if (task.depth() > 0) {
                // "└─" from the parent's text column down to this row's label
                int lineX = textX - INDENT_PER_DEPTH + 3;
                int midY = this.y + this.height / 2;
                int bottom = task.lastChild() ? midY : this.y + this.height;
                RenderUtils.drawRect(ctx, lineX, this.y, 1, bottom - this.y, TREE_LINE_COLOR);
                RenderUtils.drawRect(ctx, lineX, midY, INDENT_PER_DEPTH - 6, 1, TREE_LINE_COLOR);
            }

            String statusText = task.status().text;
            int statusX = this.buttonsStartX - this.getStringWidth(statusText) - 6;
            this.drawStringWithShadow(ctx, statusX, textY, 0xFFFFFFFF, statusText);

            int labelColor = task.depth() == 0 ? 0xFFFFFFFF : 0xFFD0D0D0;
            this.drawStringWithShadow(ctx, textX, textY, labelColor, trimToWidth(task.label(), statusX - textX - 6));
        }

        super.render(ctx, mouseX, mouseY, selected);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }

        if (this.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int end = text.length();

        while (end > 0 && this.getStringWidth(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }

        return text.substring(0, end) + ellipsis;
    }
}

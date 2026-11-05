package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;

/**
 * Builds a fresh, read-only snapshot of every currently in-progress automation task each time it's
 * asked — no task list is stored anywhere, so a task simply stops appearing on its own once the
 * handler driving it goes back to idle. Sourced from {@link BaritoneController}'s running job (any kind,
 * started any way), {@link SmartMinemanHandler}'s store-items
 * loop (nested under the Baritone job when one is being paused for it, or standalone when triggered
 * manually with no qualifying job running), {@link HungryMinemanHandler}'s eat-wait (nested under the
 * Baritone job it's pausing), and {@link ChestQuickDepositHandler}'s manual quick-deposit attempts.
 */
public final class TaskManager {
    private TaskManager() {}

    public static final class TaskInfo {
        public final int order;
        public final int depth;
        public final String label;
        public final boolean paused;

        TaskInfo(int order, int depth, String label, boolean paused) {
            this.order = order;
            this.depth = depth;
            this.label = label;
            this.paused = paused;
        }
    }

    public static List<TaskInfo> getTasks() {
        List<TaskInfo> tasks = new ArrayList<>();
        int order = 1;

        boolean baritoneRootShown = BaritoneController.getPrimaryJobType() != BaritoneController.JobType.NONE;
        boolean storingItems = SmartMinemanHandler.isHandlingFullInventory();
        boolean waitingForFood = HungryMinemanHandler.isWaitingForFood();
        boolean pendingEat = HungryMinemanHandler.isPendingEat();
        boolean globalPaused = MinemanPauseController.isPaused();

        if (baritoneRootShown) {
            order = addTask(tasks, order, 0, describeBaritoneJob(), BaritoneController.isPrimaryJobPaused());

            if (waitingForFood) {
                order = addTask(tasks, order, 1, "Pause: waiting for food", globalPaused);
            }

            if (storingItems) {
                order = addTask(tasks, order, 1, "Pause: storing items" + SmartMinemanHandler.describeProgress(), globalPaused);

                if (pendingEat) {
                    order = addTask(tasks, order, 2, "Queued: eat after depositing", globalPaused);
                }
            }
            else if (pendingEat) {
                order = addTask(tasks, order, 1, "Queued: eat when not looking at a container", globalPaused);
            }
        }
        else if (storingItems) {
            order = addTask(tasks, order, 0, "Store items" + SmartMinemanHandler.describeProgress(), globalPaused);

            if (pendingEat) {
                order = addTask(tasks, order, 1, "Queued: eat after depositing", globalPaused);
            }
        }

        if (ChestQuickDepositHandler.isBusy() && storingItems == false) {
            addTask(tasks, order, 0, describeQuickDeposit(), globalPaused);
        }

        return tasks;
    }

    private static int addTask(List<TaskInfo> tasks, int order, int depth, String label, boolean paused) {
        tasks.add(new TaskInfo(order, depth, label, paused));
        return order + 1;
    }

    private static String describeBaritoneJob() {
        // Baritone's own name, for example "Mine diamond_ore" or "Building clear area"
        String name = BaritoneController.getJobName();
        return name == null ? "Baritone" : name;
    }

    private static String describeQuickDeposit() {
        BlockPos pos = ChestQuickDepositHandler.getTargetPos();
        return pos == null ? "Quick Deposit" : "Quick Deposit (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}

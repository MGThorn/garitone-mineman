package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;

/**
 * Builds a fresh, read-only snapshot of every currently in-progress automation task each time it's
 * asked. No task list is stored anywhere, so a task simply stops appearing on its own once the
 * handler driving it goes back to idle. Sourced from {@link BaritoneController}'s running job (any kind,
 * started any way), {@link SmartMinemanHandler}'s store trip (nested under the Baritone job it holds,
 * or standalone when started by hand with no job running), {@link HungryMinemanHandler}'s eating and
 * queued eat, and {@link ChestQuickDepositHandler}'s manual quick deposit.
 *
 * Every row can be paused/resumed and cancelled ({@link #togglePause()}, {@link #cancel(Kind)}).
 * Mineman has one global pause, so pausing any row freezes all of them together. Cancelling a row
 * only stops that task and the ones nested under it.
 */
public final class TaskManager {
    private TaskManager() {}

    public enum Kind { BARITONE_JOB, STORE_TRIP, EATING, QUEUED_EAT, QUICK_DEPOSIT }

    public enum Status {
        RUNNING("§aRUNNING§r"),
        PAUSED("§ePAUSED§r"),
        WAITING("§6WAITING§r"),
        QUEUED("§7QUEUED§r");

        public final String text;

        Status(String text) {
            this.text = text;
        }
    }

    /**
     * One row. A record, so two snapshots with the same rows are equal and the screen only rebuilds
     * its rows (and buttons) when something actually changed.
     *
     * @param lastChild the last row on its depth under the same parent, for drawing the tree lines
     */
    public record TaskInfo(Kind kind, int depth, String label, Status status, boolean lastChild) {}

    public static List<TaskInfo> getTasks() {
        List<TaskInfo> tasks = new ArrayList<>();

        boolean jobShown = BaritoneController.getPrimaryJobType() != BaritoneController.JobType.NONE;
        boolean storingItems = SmartMinemanHandler.isHandlingFullInventory();
        boolean eating = HungryMinemanHandler.isWaitingForFood();
        boolean pendingEat = HungryMinemanHandler.isPendingEat();
        boolean userPaused = MinemanPauseController.isUserPaused();
        Status running = userPaused ? Status.PAUSED : Status.RUNNING;

        if (jobShown) {
            Status jobStatus = userPaused ? Status.PAUSED
                    : BaritoneController.isPrimaryJobPaused() ? Status.WAITING : Status.RUNNING;
            tasks.add(new TaskInfo(Kind.BARITONE_JOB, 0, describeBaritoneJob(), jobStatus, true));

            if (eating) {
                tasks.add(new TaskInfo(Kind.EATING, 1, "Eating", running, storingItems == false && pendingEat == false));
            }

            if (storingItems) {
                tasks.add(new TaskInfo(Kind.STORE_TRIP, 1, "Storing items" + tripTarget() + SmartMinemanHandler.describeProgress(),
                        eating ? Status.WAITING : running, true));

                if (pendingEat) {
                    tasks.add(new TaskInfo(Kind.QUEUED_EAT, 2, "Queued: eat after depositing", userPaused ? Status.PAUSED : Status.QUEUED, true));
                }
            }
            else if (pendingEat) {
                tasks.add(new TaskInfo(Kind.QUEUED_EAT, 1, "Queued: eat when not looking at a container",
                        userPaused ? Status.PAUSED : Status.QUEUED, true));
            }
        }
        else if (storingItems) {
            tasks.add(new TaskInfo(Kind.STORE_TRIP, 0, "Store items" + tripTarget() + SmartMinemanHandler.describeProgress(),
                    eating ? Status.WAITING : running, true));

            if (eating) {
                tasks.add(new TaskInfo(Kind.EATING, 1, "Eating", running, pendingEat == false));
            }

            if (pendingEat) {
                tasks.add(new TaskInfo(Kind.QUEUED_EAT, 1, "Queued: eat after depositing", userPaused ? Status.PAUSED : Status.QUEUED, true));
            }
        }

        if (ChestQuickDepositHandler.isBusy() && storingItems == false) {
            tasks.add(new TaskInfo(Kind.QUICK_DEPOSIT, 0, describeQuickDeposit(), running, true));
        }

        return tasks;
    }

    /** Pause/Resume on any row: the global pause, same as the pause/resume hotkey. */
    public static void togglePause() {
        MinemanPauseController.toggle();
    }

    /** Cancel on a row: stops that task (and what's nested under it) only. */
    public static void cancel(Kind kind) {
        switch (kind) {
            case BARITONE_JOB -> MinemanCancelController.cancelAll();
            case STORE_TRIP -> SmartMinemanHandler.cancelTripForCurrentJob();
            case EATING, QUEUED_EAT -> HungryMinemanHandler.skipUntilJobEnds();
            case QUICK_DEPOSIT -> ChestQuickDepositHandler.cancelAll();
        }
    }

    private static String tripTarget() {
        String name = SmartMinemanHandler.getTripPointName();
        return name != null ? " in " + name : "";
    }

    private static String describeBaritoneJob() {
        // Baritone's own name, for example "Mine diamond_ore" or "Building clear area"
        String name = BaritoneController.getJobName();
        return name == null ? "Baritone" : name;
    }

    private static String describeQuickDeposit() {
        BlockPos pos = ChestQuickDepositHandler.getTargetPos();
        return pos == null ? "Quick deposit" : "Quick deposit (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}

package io.github.mgthorn.garitonemineman.client.compat.baritone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import io.github.mgthorn.garitonemineman.client.feature.MinemanPauseController;

/**
 * The only class that touches {@code baritone.api}. {@link BaritoneController} calls into it only when
 * {@code BaritoneCompat.isLoaded()} is true, so the mod still loads without Baritone.
 *
 * Pausing and store trips use Baritone's own process system instead of cancelling and re-issuing
 * commands. Baritone runs the highest-priority active process each tick; when a <em>temporary</em>
 * process takes over, the processes below it are not told they lost control, so a mine or build job
 * just waits underneath and carries on afterwards (Baritone's own {@code #pause} works the same way).
 * All three processes here are temporary, because a non-temporary one taking over would make
 * {@code MineProcess} drop its targets and {@code BuilderProcess} drop its schematic.
 * <ul>
 *   <li>global pause (highest): the pause/resume hotkey and Hungry Mineman; freezes everything, store trips too</li>
 *   <li>travel: walks next to {@link BaritoneController}'s nav goal (storage point or chest)</li>
 *   <li>job hold (lowest of ours): keeps the job waiting during a Smart Mineman store trip, between and after travels</li>
 * </ul>
 */
final class BaritoneBridge {
    private static final double PRIORITY_GLOBAL_PAUSE = IBaritoneProcess.DEFAULT_PRIORITY + 4;
    private static final double PRIORITY_TRAVEL = IBaritoneProcess.DEFAULT_PRIORITY + 3;
    private static final double PRIORITY_JOB_HOLD = IBaritoneProcess.DEFAULT_PRIORITY + 2;

    private static boolean registered;

    private BaritoneBridge() {}

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    /** Registers our processes with the primary Baritone once; it lives as long as the client. */
    static void ensureRegistered() {
        if (registered) {
            return;
        }

        IBaritone baritone = baritone();
        baritone.getPathingControlManager().registerProcess(new HoldProcess(
                "Mineman Pause", PRIORITY_GLOBAL_PAUSE, MinemanPauseController::isPaused));
        baritone.getPathingControlManager().registerProcess(new TravelProcess());
        baritone.getPathingControlManager().registerProcess(new HoldProcess(
                "Mineman Store Trip", PRIORITY_JOB_HOLD, BaritoneController::isJobHeld));
        registered = true;
    }

    static BaritoneController.JobType currentJobType() {
        IBaritone baritone = baritone();

        if (baritone.getMineProcess().isActive()) {
            return BaritoneController.JobType.MINE;
        }

        if (baritone.getBuilderProcess().isActive()) {
            return BaritoneController.JobType.BUILD;
        }

        if (baritone.getCustomGoalProcess().isActive()) {
            return BaritoneController.JobType.GOAL;
        }

        if (baritone.getFarmProcess().isActive()) {
            return BaritoneController.JobType.FARM;
        }

        if (baritone.getExploreProcess().isActive()
                || baritone.getGetToBlockProcess().isActive() || baritone.getFollowProcess().isActive()
                || baritone.getElytraProcess().isActive()) {
            return BaritoneController.JobType.OTHER;
        }

        return BaritoneController.JobType.NONE;
    }

    /** Baritone's own name for the running job, for example "Mine diamond_ore" or "Building clear area". */
    @Nullable
    static String currentJobName() {
        IBaritone baritone = baritone();
        IBaritoneProcess[] processes = {
                baritone.getMineProcess(), baritone.getBuilderProcess(), baritone.getCustomGoalProcess(),
                baritone.getFarmProcess(), baritone.getExploreProcess(), baritone.getGetToBlockProcess(),
                baritone.getFollowProcess(), baritone.getElytraProcess() };

        for (IBaritoneProcess process : processes) {
            if (process.isActive()) {
                return process.displayName();
            }
        }

        return null;
    }

    static boolean isBuilderPaused() {
        return baritone().getBuilderProcess().isPaused();
    }

    static void startMine(String... blockNames) {
        baritone().getMineProcess().mineByName(blockNames);
    }

    static void startClearArea(BlockPos corner1, BlockPos corner2) {
        baritone().getBuilderProcess().clearArea(corner1, corner2);
    }

    /** Sets Baritone's goal next to {@code pos} without walking there, like typing {@code #goal}. */
    static void setGoal(BlockPos pos) {
        baritone().getCustomGoalProcess().setGoal(new GoalGetToBlock(pos));
    }

    static boolean isNextTo(BlockPos pos) {
        return new GoalGetToBlock(pos).isInGoal(baritone().getPlayerContext().playerFeet());
    }

    static void cancelEverything() {
        baritone().getPathingBehavior().cancelEverything();
    }

    static boolean isBreakAllowed() {
        return BaritoneAPI.getSettings().allowBreak.value;
    }

    static boolean isPlaceAllowed() {
        return BaritoneAPI.getSettings().allowPlace.value;
    }

    static void setBreakPlaceAllowed(boolean allowBreak, boolean allowPlace) {
        BaritoneAPI.getSettings().allowBreak.value = allowBreak;
        BaritoneAPI.getSettings().allowPlace.value = allowPlace;
    }

    /**
     * Puts {@code wanted} into Baritone's {@code blocksToDisallowBreaking}, after taking out what we
     * added last time ({@code previouslyAdded}). Returns the blocks this call added, so entries the
     * player had there already are never removed later.
     */
    static Set<Block> replaceDisallowedBreaking(Set<Block> wanted, Set<Block> previouslyAdded) {
        List<Block> list = new ArrayList<>(BaritoneAPI.getSettings().blocksToDisallowBreaking.value);
        list.removeAll(previouslyAdded);
        Set<Block> added = new HashSet<>();

        for (Block block : wanted) {
            if (list.contains(block) == false) {
                list.add(block);
                added.add(block);
            }
        }

        BaritoneAPI.getSettings().blocksToDisallowBreaking.value = list;
        return added;
    }

    /** Stands still while active, like Baritone's own pause. */
    private static final class HoldProcess implements IBaritoneProcess {
        private final String name;
        private final double priority;
        private final BooleanSupplier active;

        HoldProcess(String name, double priority, BooleanSupplier active) {
            this.name = name;
            this.priority = priority;
            this.active = active;
        }

        @Override
        public boolean isActive() {
            return this.active.getAsBoolean();
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            baritone().getInputOverrideHandler().clearAllKeys();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public double priority() {
            return this.priority;
        }

        @Override
        public String displayName0() {
            return this.name;
        }
    }

    /** Walks next to the nav goal while one is set. Stays active after arriving, until the goal is cleared. */
    private static final class TravelProcess implements IBaritoneProcess {
        @Override
        public boolean isActive() {
            return BaritoneController.getNavGoal() != null;
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            BlockPos goal = BaritoneController.getNavGoal();

            if (goal == null) {
                return new PathingCommand(null, PathingCommandType.DEFER);
            }

            if (calcFailed) {
                BaritoneController.markNavCalcFailed();
            }

            return new PathingCommand(new GoalGetToBlock(goal), PathingCommandType.SET_GOAL_AND_PATH);
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public double priority() {
            return PRIORITY_TRAVEL;
        }

        @Override
        public String displayName0() {
            return "Mineman Travel";
        }
    }
}

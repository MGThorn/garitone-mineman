package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry;
import io.github.mgthorn.garitonemineman.client.storage.StorageContentIndex;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/**
 * Store trips: IDLE -&gt; WATCHING (a mine, build or farm job runs) -&gt; DEPOSITING (inventory full,
 * job held) -&gt; RESUMING (job released) -&gt; WATCHING.
 *
 * The job is never cancelled. {@link BaritoneController#holdJob()} keeps it waiting underneath while
 * the trip walks with Baritone's travel process, and {@link BaritoneController#releaseJob()} lets it
 * continue where it stopped, whatever kind of job it is.
 *
 * DEPOSITING goes through the storage point's chests in list order, skipping ones that are turned
 * off or known to have no room. For each chest it tries a quick deposit ({@link ChestQuickDepositHandler})
 * from where the player stands; if the chest is out of reach or not visible, it walks next to it.
 * While walking it tries again as soon as the chest comes within reach (standing still for that try).
 * Only tries made <em>after arriving</em> count towards {@link #MAX_FAILED_ARRIVALS}; a chest is also
 * given up when Baritone finds no path or the walk takes longer than {@link #MAX_CHEST_TRAVEL_TICKS}.
 * After a successful deposit it moves on to the next chest only while the selected slots still hold items.
 * If every chest is done and items are left, the job stays held and the global pause turns on until
 * the player makes room and presses pause/resume.
 *
 * DEPOSITING and RESUMING run whether or not the smartMineman setting is on, so
 * {@link #triggerManualStoreItems()} (Store Items hotkey and GUI button) can run a one-off trip; only
 * the automatic start on a full inventory needs the setting.
 */
public final class SmartMinemanHandler {
    private enum State { IDLE, WATCHING, DEPOSITING, RESUMING }
    private enum ChestStep { ATTEMPTING, TRAVELING }

    private static final int MAX_FAILED_ARRIVALS = 2;
    private static final int MAX_CHEST_TRAVEL_TICKS = 1200; // ~60s
    private static final int REACH_CHECK_INTERVAL_TICKS = 10;

    private static State state = State.IDLE;
    private static boolean manualTripRequested;
    private static List<StorageBlockEntry> depositQueue = List.of();
    private static int depositIndex;
    private static ChestStep chestStep = ChestStep.ATTEMPTING;
    private static boolean attemptInFlight;
    // The current try was made after Baritone reported arriving next to the chest
    private static boolean attemptAfterArrival;
    private static int failedArrivals;
    private static int travelTicks;
    // Every chest was full with items left over: the job stays held and the global pause is on until the player resumes
    private static boolean storageFull;
    // The player cancelled the store trip in the Task Manager: no automatic trip until the job ends
    private static boolean skipAutoTripsForJob;

    private SmartMinemanHandler() {}

    /** Starts a store trip right now, independent of the setting toggle. */
    public static void triggerManualStoreItems() {
        if (state == State.IDLE || state == State.WATCHING) {
            manualTripRequested = true;
        }
    }

    /** Aborts the store trip (if any) immediately and resets to idle. */
    public static void cancelAll() {
        reset();
        skipAutoTripsForJob = false;
    }

    /**
     * Task Manager "Cancel" on the store trip: stops the trip and lets the job continue. The inventory
     * is still full, so no automatic trip starts again until this job ends; the Store Items hotkey
     * still works.
     */
    public static void cancelTripForCurrentJob() {
        if (isHandlingFullInventory() == false) {
            return;
        }

        if (storageFull) {
            MinemanPauseController.setPaused(false); // that pause was only waiting for room in the chests
        }

        ChestQuickDepositHandler.cancelAll();
        BaritoneController.clearNavGoal();
        BaritoneController.releaseJob();
        skipAutoTripsForJob = BaritoneController.getPrimaryJobType() != BaritoneController.JobType.NONE;
        reset();
    }

    /** True while a store trip runs, from holding the job until releasing it. */
    public static boolean isHandlingFullInventory() {
        return state == State.DEPOSITING || state == State.RESUMING;
    }

    /** Every chest was full with items left over; waiting for the player to make room and resume. */
    public static boolean isStorageFull() {
        return storageFull;
    }

    /** Human-readable progress suffix (e.g. " (chest 2/5, walking)") for the current trip, or "". */
    public static String describeProgress() {
        return switch (state) {
            case DEPOSITING -> " (chest " + Math.min(depositIndex + 1, depositQueue.size()) + "/" + depositQueue.size()
                    + (chestStep == ChestStep.TRAVELING ? ", walking)" : ")");
            case RESUMING -> storageFull ? " (all chests full: make room, then resume)" : " (resuming)";
            default -> "";
        };
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            reset();
            return;
        }

        if (MinemanPauseController.isPaused()) {
            return; // frozen; Baritone itself is held by the global pause process
        }

        if (skipAutoTripsForJob && BaritoneController.getPrimaryJobType() == BaritoneController.JobType.NONE) {
            skipAutoTripsForJob = false; // the job whose trip was cancelled has ended
        }

        boolean settingEnabled = Configs.Generic.SMART_MINEMAN.getBooleanValue() && skipAutoTripsForJob == false;

        switch (state) {
            case IDLE -> {
                if (manualTripRequested) {
                    startTrip(mc);
                }
                else if (settingEnabled && BaritoneController.isBlockBreakingJobActive()) {
                    state = State.WATCHING;
                }
            }
            case WATCHING -> {
                // Staying here doesn't depend on the setting, only the automatic start does, so turning it
                // off and on again mid-job (or a manual trip with it off) keeps watching the same job.
                if (manualTripRequested) {
                    startTrip(mc);
                }
                else if (BaritoneController.isBlockBreakingJobActive() == false) {
                    state = State.IDLE;
                }
                else if (settingEnabled && isInventoryFull(mc.player)) {
                    startTrip(mc);
                }
            }
            case DEPOSITING -> advanceDeposit(mc);
            case RESUMING -> {
                storageFull = false;
                BaritoneController.releaseJob();
                state = BaritoneController.getPrimaryJobType().breaksBlocks() ? State.WATCHING : State.IDLE;
            }
        }
    }

    private static void startTrip(Minecraft mc) {
        manualTripRequested = false;
        BaritoneController.holdJob();
        StoragePoint point = StoragePointManager.getInstance().getSelectedOrNearestStoragePoint(mc.player.blockPosition());

        if (point == null) {
            mc.player.sendOverlayMessage(Component.literal("Smart Mineman: no storage point available, continuing"));
            state = State.RESUMING;
            return;
        }

        depositQueue = new ArrayList<>(point.getStorageBlocks());
        depositIndex = 0;
        resetChest();
        state = State.DEPOSITING;
    }

    private static void advanceDeposit(Minecraft mc) {
        if (depositIndex >= depositQueue.size()) {
            if (hasItemsInEnabledSlots(mc.player)) {
                // Continuing would lose every new drop, so wait for the player instead. RESUMING runs once
                // the global pause is lifted (pause/resume hotkey), which lets the job continue.
                storageFull = true;
                MinemanPauseController.setPaused(true);
                mc.player.sendOverlayMessage(
                        Component.literal("Smart Mineman: all chests are full. Make room, then press pause/resume"));
            }

            state = State.RESUMING;
            return;
        }

        StorageBlockEntry entry = depositQueue.get(depositIndex);
        BlockPos pos = new BlockPos(entry.getX(), entry.getY(), entry.getZ());

        if (attemptInFlight == false && chestStep == ChestStep.ATTEMPTING && isWorthTrying(entry, pos, mc.player) == false) {
            nextChest();
            return;
        }

        switch (chestStep) {
            case ATTEMPTING -> {
                if (attemptInFlight == false) {
                    ChestQuickDepositHandler.beginAttempt(entry);
                    attemptInFlight = true;
                    return;
                }

                if (ChestQuickDepositHandler.isBusy()) {
                    return;
                }

                ChestQuickDepositHandler.Result result = ChestQuickDepositHandler.consumeLastResult();
                attemptInFlight = false;

                if (result == null) {
                    return; // shouldn't happen once isBusy() is false; the next tick tries again
                }

                switch (result) {
                    case SUCCESS -> {
                        BaritoneController.clearNavGoal();
                        boolean stillHasItems = hasItemsInEnabledSlots(mc.player);
                        StorageContentIndex.recordFull(pos, stillHasItems);

                        if (stillHasItems) {
                            nextChest();
                        }
                        else {
                            state = State.RESUMING;
                        }
                    }
                    case TOO_FAR, BLOCKED -> {
                        // Tries while still on the way don't count; only failing after arriving does
                        if (attemptAfterArrival && ++failedArrivals > MAX_FAILED_ARRIVALS) {
                            giveUpOnChest(mc, pos, "can't open");
                            return;
                        }

                        BaritoneController.sendGoTo(pos);
                        travelTicks = 0;
                        chestStep = ChestStep.TRAVELING;
                    }
                    case FAILED -> nextChest();
                }
            }
            case TRAVELING -> {
                travelTicks++;

                if (BaritoneController.hasNavGoalFailed()) {
                    giveUpOnChest(mc, pos, "no path to");
                    return;
                }

                if (travelTicks > MAX_CHEST_TRAVEL_TICKS) {
                    giveUpOnChest(mc, pos, "took too long to reach");
                    return;
                }

                boolean arrived = BaritoneController.hasArrivedAtNavGoal();

                // Quick deposit reach is usually there before Baritone considers itself arrived. Stop walking
                // for the try, so the player stands still while opening; a failed try walks on.
                if (arrived || (travelTicks % REACH_CHECK_INTERVAL_TICKS == 0 && isWithinReach(mc.player, entry))) {
                    BaritoneController.clearNavGoal();
                    attemptAfterArrival = arrived;
                    chestStep = ChestStep.ATTEMPTING;
                }
            }
        }
    }

    /** Skips chests that are turned off, were full a moment ago, or (by their last seen contents) can't take anything. */
    private static boolean isWorthTrying(StorageBlockEntry entry, BlockPos pos, Player player) {
        return entry.isUseEnabled()
                && StorageContentIndex.isRecentlyKnownFull(pos) == false
                && ContainerDepositHelper.hasRoomForInventory(entry, player, ChestQuickDepositHandler.parseDisabledSlots());
    }

    private static boolean isWithinReach(Player player, StorageBlockEntry entry) {
        double reachSq = player.blockInteractionRange() * player.blockInteractionRange();

        if (new AABB(new BlockPos(entry.getX(), entry.getY(), entry.getZ())).distanceToSqr(player.getEyePosition()) <= reachSq) {
            return true;
        }

        return entry.hasSecondPosition()
                && new AABB(new BlockPos(entry.getSecondX(), entry.getSecondY(), entry.getSecondZ()))
                        .distanceToSqr(player.getEyePosition()) <= reachSq;
    }

    private static void giveUpOnChest(Minecraft mc, BlockPos pos, String reason) {
        mc.player.sendOverlayMessage(Component.literal("Smart Mineman: " + reason + " chest at "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ", trying the next one"));
        nextChest();
    }

    private static void nextChest() {
        BaritoneController.clearNavGoal(); // stop walking towards the chest being left
        depositIndex++;
        resetChest();
    }

    private static void resetChest() {
        chestStep = ChestStep.ATTEMPTING;
        attemptInFlight = false;
        attemptAfterArrival = false;
        failedArrivals = 0;
        travelTicks = 0;
    }

    /** True if any player-inventory slot outside {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS} still holds an item. */
    private static boolean hasItemsInEnabledSlots(Player player) {
        Set<Integer> disabledSlots = ChestQuickDepositHandler.parseDisabledSlots();

        for (int i = 0; i < 36; i++) {
            if (disabledSlots.contains(i) == false && player.getInventory().getItem(i).isEmpty() == false) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInventoryFull(Player player) {
        int threshold = Configs.Generic.SMART_MINEMAN_FREE_SLOTS_THRESHOLD.getIntegerValue();
        int emptyCount = 0;

        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                emptyCount++;
            }
        }

        return emptyCount <= threshold;
    }

    private static void reset() {
        state = State.IDLE;
        manualTripRequested = false;
        storageFull = false;
        depositQueue = List.of();
        depositIndex = 0;
        resetChest();
    }
}

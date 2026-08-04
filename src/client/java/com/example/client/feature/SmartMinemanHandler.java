package com.example.client.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.example.client.compat.baritone.BaritoneController;
import com.example.client.config.Configs;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StorageContentIndex;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

/**
 * State machine: IDLE -&gt; MINING -&gt; INVENTORY_FULL_DETECTED -&gt; PAUSING -&gt; TRAVELING_TO_STORAGE
 * -&gt; DEPOSITING -&gt; RESUMING -&gt; MINING. TRAVELING_TO_STORAGE is the coarse hop to the storage
 * point's origin only. DEPOSITING then walks the storage point's chest list in priority order: for
 * each one it first tries a quick deposit ({@link ChestQuickDepositHandler}) from wherever the player
 * already is; if the chest is out of reach, it sends Baritone an actual goal+path towards it and,
 * every {@link #CHEST_TRAVEL_RETRY_INTERVAL_TICKS} ticks while still travelling (or as soon as it
 * arrives, whichever first), tries the quick deposit again from wherever it's gotten to — up to
 * {@link #MAX_CHEST_TRAVEL_ATTEMPTS} distinct travel attempts before giving up on that chest and
 * moving to the next. The moment a quick deposit succeeds, Baritone's path to that chest is cancelled
 * immediately rather than left to finish walking there. After a successful deposit, it moves to the
 * next chest too unless the player's inventory (outside the slots configured in
 * {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS}) is now empty, in which case it's done.
 *
 * The PAUSING/TRAVELING_TO_STORAGE/DEPOSITING/RESUMING states run regardless of whether the
 * smartMineman setting is enabled, so {@link #triggerManualStoreItems()} (the Store Items hotkey and
 * GUI button) can drive the same deposit loop as a one-off action without the setting being on.
 * Only the automatic MINING -&gt; INVENTORY_FULL_DETECTED trigger is gated on the setting.
 */
public final class SmartMinemanHandler {
    private enum State { IDLE, MINING, INVENTORY_FULL_DETECTED, PAUSING, TRAVELING_TO_STORAGE, DEPOSITING, RESUMING }
    private enum DepositSubState { ATTEMPTING, TRAVELING_TO_CHEST }

    private static final int MAX_CHEST_TRAVEL_ATTEMPTS = 2;
    private static final int CHEST_TRAVEL_RETRY_INTERVAL_TICKS = 20;

    private static State state = State.IDLE;
    private static StoragePoint targetStoragePoint;
    private static List<StorageBlockEntry> depositQueue;
    private static int depositIndex;
    private static DepositSubState depositSubState = DepositSubState.ATTEMPTING;
    private static int chestTravelAttempts;
    private static int chestTravelRetryTicks;
    private static boolean depositAttemptInFlight;

    private SmartMinemanHandler() {}

    /** Starts the pause -> travel -> deposit -> resume loop right now, independent of the setting toggle. */
    public static void triggerManualStoreItems() {
        if (state == State.IDLE || state == State.MINING) {
            state = State.PAUSING;
        }
    }

    /** Aborts the store-items loop (if any) immediately and resets to idle. */
    public static void cancelAll() {
        reset();
    }

    /** True whenever the pause/travel/deposit/resume loop is actively handling a full inventory. */
    public static boolean isHandlingFullInventory() {
        return state != State.IDLE && state != State.MINING;
    }

    /** Human-readable progress suffix (e.g. " (chest 2/5)") for the current store-items step, or "". */
    public static String describeProgress() {
        return switch (state) {
            case DEPOSITING -> depositQueue == null ? "" : " (chest " + (depositIndex + 1) + "/" + depositQueue.size() + ")";
            case PAUSING, TRAVELING_TO_STORAGE, INVENTORY_FULL_DETECTED -> " (traveling to storage)";
            case RESUMING -> " (resuming)";
            default -> "";
        };
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            reset();
            return;
        }

        boolean settingEnabled = Configs.Generic.SMART_MINEMAN.getBooleanValue();

        switch (state) {
            case IDLE -> {
                if (settingEnabled && BaritoneController.isJobActive()) {
                    state = State.MINING;
                }
            }
            case MINING -> {
                // Unlike the IDLE -> MINING trigger below, staying in MINING (and thus keeping track
                // of whether the job is still active) does NOT depend on the setting — only the
                // isInventoryFull() auto-trigger itself does — so toggling the setting off mid-job (or
                // resuming here after a manually-triggered store-items run with the setting off, see
                // RESUMING below) doesn't strand this in IDLE, unable to ever watch for full inventory
                // again without the job restarting.
                if (MinemanPauseController.isPaused()) {
                    return; // frozen; BaritoneController.pause()/resume() handles the actual job
                }

                if (BaritoneController.isJobActive() == false) {
                    reset();
                    return;
                }

                if (settingEnabled && isInventoryFull(mc)) {
                    state = State.INVENTORY_FULL_DETECTED;
                }
            }
            case INVENTORY_FULL_DETECTED -> {
                if (MinemanPauseController.isPaused()) {
                    return;
                }

                state = State.PAUSING;
            }
            case PAUSING -> {
                if (MinemanPauseController.isPaused()) {
                    return;
                }

                BaritoneController.pause();
                targetStoragePoint = StoragePointManager.getInstance()
                        .getSelectedOrNearestStoragePoint(mc.player.blockPosition());

                if (targetStoragePoint == null) {
                    mc.player.displayClientMessage(
                            Component.literal("SmartMineman: no storage point available, resuming"), true);
                    state = State.RESUMING;
                    return;
                }

                BaritoneController.sendGoTo(
                        new BlockPos(targetStoragePoint.getX(), targetStoragePoint.getY(), targetStoragePoint.getZ()));
                state = State.TRAVELING_TO_STORAGE;
            }
            case TRAVELING_TO_STORAGE -> {
                if (MinemanPauseController.isPaused()) {
                    return;
                }

                // Unlike the per-chest travel in DEPOSITING (which retries on its own timer), this
                // otherwise just waits forever for hasArrivedAtNavGoal() with no recovery — if the nav
                // goal ever ends up missing (e.g. another feature like HungryMineman paused/resumed
                // Baritone around this and only the nav goal, not this hop, got reissued), resend it
                // instead of getting permanently stuck here.
                if (BaritoneController.hasActiveNavGoal() == false) {
                    BaritoneController.sendGoTo(new BlockPos(
                            targetStoragePoint.getX(), targetStoragePoint.getY(), targetStoragePoint.getZ()));
                    return;
                }

                if (BaritoneController.hasArrivedAtNavGoal()) {
                    BaritoneController.clearNavGoal();
                    depositQueue = new ArrayList<>(targetStoragePoint.getStorageBlocks());
                    depositIndex = 0;
                    depositSubState = DepositSubState.ATTEMPTING;
                    chestTravelAttempts = 0;
                    depositAttemptInFlight = false;
                    state = State.DEPOSITING;
                }
            }
            case DEPOSITING -> {
                if (MinemanPauseController.isPaused()) {
                    return;
                }

                advanceDeposit(mc);
            }
            case RESUMING -> {
                if (MinemanPauseController.isPaused()) {
                    return;
                }

                BaritoneController.resume();
                // Go back to watching (MINING) whenever there's still a primary job to watch, not
                // gated on the setting — the pause/travel/deposit/resume loop runs "regardless of
                // whether the smartMineman setting is enabled" (see class doc), so a manually-triggered
                // Store Items run with the setting off must resume mining too, not get stuck in IDLE.
                state = BaritoneController.getPrimaryJobType() != BaritoneController.JobType.NONE
                        ? State.MINING
                        : State.IDLE;
            }
        }
    }

    private static void advanceDeposit(Minecraft mc) {
        if (depositIndex >= depositQueue.size()) {
            state = State.RESUMING;
            return;
        }

        StorageBlockEntry entry = depositQueue.get(depositIndex);
        BlockPos pos = new BlockPos(entry.getX(), entry.getY(), entry.getZ());

        if (entry.isUseEnabled() == false || StorageContentIndex.isRecentlyKnownFull(pos)) {
            advanceToNextChest();
            return;
        }

        switch (depositSubState) {
            case ATTEMPTING -> {
                if (depositAttemptInFlight == false) {
                    ChestQuickDepositHandler.beginAttempt(entry);
                    depositAttemptInFlight = true;
                    return;
                }

                if (ChestQuickDepositHandler.isBusy()) {
                    return;
                }

                ChestQuickDepositHandler.Result result = ChestQuickDepositHandler.consumeLastResult();
                depositAttemptInFlight = false;

                if (result == null) {
                    return; // shouldn't happen once isBusy() is false, but just retry defensively
                }

                switch (result) {
                    case SUCCESS -> {
                        // Succeeded (possibly from partway down a path Baritone was still walking) —
                        // stop travelling there immediately rather than let it keep going pointlessly.
                        BaritoneController.cancelNavGoal();

                        boolean stillHasItems = hasItemsInEnabledSlots(mc);
                        StorageContentIndex.recordFull(pos, stillHasItems);

                        if (stillHasItems) {
                            advanceToNextChest();
                        }
                        else {
                            state = State.RESUMING;
                        }
                    }
                    case TOO_FAR, BLOCKED -> {
                        // BLOCKED (line of sight) gets the same retry-by-travelling treatment as
                        // TOO_FAR, rather than being abandoned outright — the spot Baritone parked at
                        // may just have a bad angle on the chest; travelling further can clear it.
                        chestTravelAttempts++;

                        if (chestTravelAttempts > MAX_CHEST_TRAVEL_ATTEMPTS) {
                            advanceToNextChest();
                        }
                        else {
                            BaritoneController.sendGoTo(pos);
                            chestTravelRetryTicks = 0;
                            depositSubState = DepositSubState.TRAVELING_TO_CHEST;
                        }
                    }
                    case FAILED -> advanceToNextChest();
                }
            }
            case TRAVELING_TO_CHEST -> {
                chestTravelRetryTicks++;

                // Peek every CHEST_TRAVEL_RETRY_INTERVAL_TICKS while still walking there — quick-deposit
                // reach is usually satisfied well before Baritone considers itself "arrived" — or as
                // soon as it does arrive, whichever comes first. The nav goal is deliberately left
                // running in the background here; it only gets cancelled once ATTEMPTING actually
                // succeeds (see the SUCCESS case above) or the chest is abandoned (advanceToNextChest).
                if (BaritoneController.hasArrivedAtNavGoal()
                        || chestTravelRetryTicks >= CHEST_TRAVEL_RETRY_INTERVAL_TICKS) {
                    chestTravelRetryTicks = 0;
                    depositSubState = DepositSubState.ATTEMPTING;
                }
            }
        }
    }

    private static void advanceToNextChest() {
        BaritoneController.cancelNavGoal(); // stop pathing towards whatever chest is being abandoned
        depositIndex++;
        depositSubState = DepositSubState.ATTEMPTING;
        chestTravelAttempts = 0;
        chestTravelRetryTicks = 0;
        depositAttemptInFlight = false;
    }

    /** True if any player-inventory slot outside {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS} still holds an item. */
    private static boolean hasItemsInEnabledSlots(Minecraft mc) {
        Set<Integer> disabledSlots = ChestQuickDepositHandler.parseDisabledSlots();

        for (int i = 0; i < 36; i++) {
            if (disabledSlots.contains(i)) {
                continue;
            }

            if (mc.player.getInventory().getItem(i).isEmpty() == false) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInventoryFull(Minecraft mc) {
        int threshold = Configs.Generic.SMART_MINEMAN_FREE_SLOTS_THRESHOLD.getIntegerValue();
        int emptyCount = 0;

        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                emptyCount++;
            }
        }

        return emptyCount <= threshold;
    }

    private static void reset() {
        state = State.IDLE;
        targetStoragePoint = null;
        depositQueue = null;
        depositIndex = 0;
        depositSubState = DepositSubState.ATTEMPTING;
        chestTravelAttempts = 0;
        chestTravelRetryTicks = 0;
        depositAttemptInFlight = false;
    }
}

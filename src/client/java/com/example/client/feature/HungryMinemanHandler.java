package com.example.client.feature;

import net.minecraft.client.Minecraft;
import com.example.client.compat.baritone.BaritoneController;
import com.example.client.config.Configs;

/**
 * While enabled and Baritone has something going on — mining/sel-fill-air, or an ad-hoc nav goal such
 * as travelling to a storage point — pauses it as soon as hunger drops to the auto-eat threshold, and
 * directly requests EasyEatHandler eat (via {@link EasyEatHandler#setExternalHold}) rather than
 * touching the autoEat setting. Does nothing while Baritone is fully idle (no job, no nav goal); that
 * case is what the plain autoEat setting is for. It also never tears any part of a storage interaction
 * out from under {@link ChestQuickDepositHandler}/{@link SmartMinemanHandler}'s whole storing phase —
 * not just an open container ({@link ChestQuickDepositHandler#isBusy()}), but also the travel between
 * chests and the pause/resume bookkeeping around it ({@link SmartMinemanHandler#isHandlingFullInventory()}) —
 * since storages can be right-clicked, and EasyEatHandler's own right-click-to-eat could open one by
 * accident if the player happens to be looking at it at any point mid-storing. Low hunger detected
 * during that window is recorded as {@link #isPendingEat() pending} rather than acted on immediately;
 * the moment the storing phase clears, the very next tick turns that into a real pause-and-eat.
 *
 * While waiting, {@link MinemanPauseController}'s mod-wide flag is held too (unless the user had
 * already paused manually), so {@link SmartMinemanHandler} and {@link ChestQuickDepositHandler}
 * freeze in place instead of continuing to fight EasyEatHandler for control of the player, and simply
 * resume exactly where they left off — travelling, depositing, whatever — once eating finishes.
 *
 * Resumes once full, once EasyEatHandler gives up (e.g. no food found), or once food stops rising
 * (rather than a flat time limit, which forcing a resume while still hungry — thereby immediately
 * re-triggering the same wait — is exactly what causes a pause/resume livelock; eating enough food to
 * go from empty to full can legitimately take longer than any fixed short timeout).
 */
public final class HungryMinemanHandler {
    // No improvement in food level for this long means eating is stuck (no food left mid-attempt,
    // key press not registering, etc.) rather than just still working through several food items.
    private static final int STUCK_TIMEOUT_TICKS = 120; // ~6s
    private static final int MAX_WAIT_TICKS = 2400; // ~2min absolute safety fallback

    private static boolean waitingForFood = false;
    private static boolean pendingEat = false;
    private static int waitTicks = 0;
    private static int lastFoodLevel = -1;
    private static int noProgressTicks = 0;
    private static boolean pausedGlobally = false;

    private HungryMinemanHandler() {}

    public static void onClientTick() {
        boolean enabled = Configs.Generic.HUNGRY_MINEMAN.getBooleanValue();

        if (enabled == false) {
            stopWaiting();
            pendingEat = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            stopWaiting();
            pendingEat = false;
            return;
        }

        if (waitingForFood) {
            waitTicks++;

            int food = mc.player.getFoodData().getFoodLevel();
            boolean stillHungry = food < 20;
            boolean gaveUp = EasyEatHandler.consumeJustFinishedEating();

            if (food > lastFoodLevel) {
                lastFoodLevel = food;
                noProgressTicks = 0;
            } else {
                noProgressTicks++;
            }

            if (stillHungry == false || gaveUp || noProgressTicks >= STUCK_TIMEOUT_TICKS || waitTicks >= MAX_WAIT_TICKS) {
                stopWaiting();
                BaritoneController.resume();
            }

            return;
        }

        // Nothing running to interrupt for — leave this to the plain autoEat setting instead.
        boolean baritoneActive = BaritoneController.getPrimaryJobType() != BaritoneController.JobType.NONE
                || BaritoneController.hasActiveNavGoal();

        if (baritoneActive == false) {
            pendingEat = false;
            return;
        }

        int food = mc.player.getFoodData().getFoodLevel();
        int threshold = Configs.Generic.AUTO_EAT_THRESHOLD.getIntegerValue();

        if (food > threshold) {
            pendingEat = false;
            return;
        }

        // Hungry enough to want to eat — but never tear any part of the storing phase out from under
        // it: not just an open container, but travel between chests too, since the player may be
        // standing right in front of one when this fires. Queue the intent instead of acting on it;
        // the moment the storing phase clears, the next tick's poll turns this into a real pause+eat.
        if (ChestQuickDepositHandler.isBusy() || SmartMinemanHandler.isHandlingFullInventory()) {
            pendingEat = true;
            return;
        }

        pendingEat = false;
        waitingForFood = true;
        waitTicks = 0;
        lastFoodLevel = food;
        noProgressTicks = 0;
        pausedGlobally = MinemanPauseController.isPaused() == false;

        if (pausedGlobally) {
            MinemanPauseController.setPaused(true);
        }

        BaritoneController.pause();
        // Discard any justFinished latch left over from an unrelated eat cycle (hotkey, plain
        // AUTO_EAT) that happened while we weren't waiting — otherwise the wait loop below would
        // read it as this attempt already concluding and resume Baritone instantly.
        EasyEatHandler.consumeJustFinishedEating();
        EasyEatHandler.setExternalHold(true);
    }

    /** Aborts the eat-wait (if any) immediately, releasing EasyEatHandler's hold. */
    public static void cancelAll() {
        stopWaiting();
        pendingEat = false;
    }

    public static boolean isWaitingForFood() {
        return waitingForFood;
    }

    /** Hunger dropped to the threshold while the storing phase was active; queued to act once it clears. */
    public static boolean isPendingEat() {
        return pendingEat;
    }

    private static void stopWaiting() {
        if (waitingForFood) {
            EasyEatHandler.setExternalHold(false);

            if (pausedGlobally) {
                MinemanPauseController.setPaused(false);
            }
        }

        waitingForFood = false;
        waitTicks = 0;
        noProgressTicks = 0;
        pausedGlobally = false;
    }
}

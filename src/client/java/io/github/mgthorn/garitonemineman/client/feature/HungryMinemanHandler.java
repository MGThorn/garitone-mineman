package io.github.mgthorn.garitonemineman.client.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * While enabled and Baritone has something going on — any job, or travel such as a store trip —
 * pauses it once {@link Configs.Generic#AUTO_EAT_THRESHOLD} hunger points are missing (the same
 * meaning as for autoEat), and directly requests EasyEatHandler eat (via
 * {@link EasyEatHandler#setExternalHold}) rather than touching the autoEat setting. Does nothing
 * while Baritone is fully idle (no job, no nav goal); that case is what the plain autoEat setting is for.
 *
 * Eating holds the use key, which opens a container instead of eating while the crosshair is on one.
 * So eating is only {@link #isPendingEat() queued} while a deposit is running
 * ({@link ChestQuickDepositHandler#isBusy()}) or the player is looking at a block that opens a screen
 * (chest, barrel, shulker box, furnace, crafting table…). It starts once both have been over for
 * {@link #QUEUED_EAT_DELAY_TICKS}, for example after Baritone turns away from the chest at the end of a
 * store trip; looking at a container again during that time starts the wait over. Travel between chests
 * doesn't queue it: the trip just pauses for eating and continues afterwards.
 *
 * While eating, {@link MinemanPauseController}'s eating hold is set, so Baritone, {@link SmartMinemanHandler}
 * and {@link ChestQuickDepositHandler} freeze in place and continue where they left off once eating
 * finishes. It's separate from the player's own pause, so pausing or resuming with the hotkey meanwhile
 * isn't undone.
 *
 * Resumes once full, once EasyEatHandler gives up (e.g. no food found), or once food stops rising
 * (rather than a flat time limit, which forcing a resume while still hungry — thereby immediately
 * re-triggering the same wait — is exactly what causes a pause/resume livelock; eating enough food to
 * go from empty to full can legitimately take longer than any fixed short timeout). With no food at
 * all it doesn't pause in the first place, and after an attempt that ended still hungry it waits
 * {@link #RETRY_COOLDOWN_TICKS} before trying again, so the job keeps running in between.
 */
public final class HungryMinemanHandler {
    // No improvement in food level for this long means eating is stuck (no food left mid-attempt,
    // key press not registering, etc.) rather than just still working through several food items.
    private static final int STUCK_TIMEOUT_TICKS = 120; // ~6s
    private static final int MAX_WAIT_TICKS = 2400; // ~2min absolute safety fallback
    // A queued eat waits this long after the deposit ends and the crosshair leaves the container (~1s)
    private static final int QUEUED_EAT_DELAY_TICKS = 20;
    // After eating ended while still hungry (food ran out, stuck), wait this long before pausing again (~30s)
    private static final int RETRY_COOLDOWN_TICKS = 600;

    private static boolean waitingForFood = false;
    private static boolean pendingEat = false;
    private static int queueClearTicks = 0;
    private static int waitTicks = 0;
    private static int lastFoodLevel = -1;
    private static int noProgressTicks = 0;
    private static int retryCooldownTicks = 0;
    // "No food" was already shown for this hungry spell; cleared once fed or food turns up
    private static boolean noFoodWarned = false;
    // The player cancelled eating in the Task Manager: don't eat again until the job ends or they eat themselves
    private static boolean skipForJob = false;

    private HungryMinemanHandler() {}

    public static void onClientTick() {
        boolean enabled = Configs.Generic.HUNGRY_MINEMAN.getBooleanValue();
        Minecraft mc = Minecraft.getInstance();

        if (enabled == false || mc.player == null) {
            stopWaiting();
            clearQueue();
            retryCooldownTicks = 0;
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
                if (stillHungry) {
                    retryCooldownTicks = RETRY_COOLDOWN_TICKS;
                }

                stopWaiting();
            }

            return;
        }

        if (retryCooldownTicks > 0) {
            retryCooldownTicks--;
        }

        // Nothing running to interrupt for — leave this to the plain autoEat setting instead.
        boolean baritoneActive = BaritoneController.getPrimaryJobType() != BaritoneController.JobType.NONE
                || BaritoneController.hasActiveNavGoal();
        int missing = 20 - mc.player.getFoodData().getFoodLevel();

        if (baritoneActive == false || missing < Configs.Generic.AUTO_EAT_THRESHOLD.getIntegerValue()) {
            clearQueue();
            noFoodWarned = false;
            skipForJob = false; // the job ended, or the player ate by themselves
            return;
        }

        if (skipForJob) {
            return;
        }

        if (retryCooldownTicks > 0) {
            return;
        }

        if (EasyEatHandler.hasFood(mc) == false) {
            // Pausing would only make EasyEat give up at once, over and over; keep the job running instead
            if (noFoodWarned == false) {
                mc.player.sendOverlayMessage(Component.literal("Hungry Mineman: no food, continuing without eating"));
                noFoodWarned = true;
            }

            clearQueue();
            return;
        }

        noFoodWarned = false;

        // Hungry enough to want to eat, but holding the use key now would open the container being used
        // or looked at. Queue it, and only eat once it has stayed clear of both for QUEUED_EAT_DELAY_TICKS.
        if (ChestQuickDepositHandler.isBusy() || isLookingAtContainer(mc)) {
            pendingEat = true;
            queueClearTicks = 0;
            return;
        }

        if (pendingEat && ++queueClearTicks < QUEUED_EAT_DELAY_TICKS) {
            return;
        }

        clearQueue();
        waitingForFood = true;
        waitTicks = 0;
        lastFoodLevel = mc.player.getFoodData().getFoodLevel();
        noProgressTicks = 0;
        // Holds Baritone too (see BaritoneBridge), so the job waits while eating.
        MinemanPauseController.setEatingHold(true);

        // Discard any justFinished latch left over from an unrelated eat cycle (hotkey, plain
        // AUTO_EAT) that happened while we weren't waiting — otherwise the wait loop below would
        // read it as this attempt already concluding and resume Baritone instantly.
        EasyEatHandler.consumeJustFinishedEating();
        EasyEatHandler.setExternalHold(true);
    }

    /** The crosshair is on a block that opens a screen when right-clicked, so the use key wouldn't eat. */
    private static boolean isLookingAtContainer(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        return mc.level.getBlockState(hit.getBlockPos()).getMenuProvider(mc.level, hit.getBlockPos()) != null;
    }

    /** Aborts the eat-wait (if any) immediately, releasing EasyEatHandler's hold. */
    public static void cancelAll() {
        stopWaiting();
        clearQueue();
        retryCooldownTicks = 0;
        skipForJob = false;
    }

    /** Task Manager "Cancel" on eating or a queued eat: stops it and doesn't eat again until the job ends. */
    public static void skipUntilJobEnds() {
        stopWaiting();
        clearQueue();
        skipForJob = true;
    }

    public static boolean isWaitingForFood() {
        return waitingForFood;
    }

    /** Hungry, but waiting for a deposit to end or the crosshair to leave a container. */
    public static boolean isPendingEat() {
        return pendingEat;
    }

    private static void clearQueue() {
        pendingEat = false;
        queueClearTicks = 0;
    }

    private static void stopWaiting() {
        if (waitingForFood) {
            EasyEatHandler.setExternalHold(false);
            MinemanPauseController.setEatingHold(false);
        }

        waitingForFood = false;
        waitTicks = 0;
        noProgressTicks = 0;
    }
}

package io.github.mgthorn.garitonemineman.client.feature;

import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;

/**
 * Immediately aborts every autonomous task (SmartMineman mining/store-items loop, the chest
 * quick-deposit attempt, HungryMineman's eat-wait, and Baritone's primary job/ad-hoc nav goal) and
 * resets them to idle, rather than just freezing them like {@link MinemanPauseController} does.
 */
public final class MinemanCancelController {
    private MinemanCancelController() {}

    /** Cancels everything, Baritone's own job included. Use for the hotkey/GUI button. */
    public static void cancelAll() {
        cancelAll(true);
    }

    /**
     * @param cancelBaritone whether to also cancel Baritone's own job. Pass false when the player just
     *                       typed "#cancel"/"#stop", which Baritone handles by itself.
     */
    public static void cancelAll(boolean cancelBaritone) {
        SmartMinemanHandler.cancelAll();
        ChestQuickDepositHandler.cancelAll();
        HungryMinemanHandler.cancelAll();
        BaritoneController.cancelAll(cancelBaritone);
        MinemanPauseController.setPaused(false);
    }
}

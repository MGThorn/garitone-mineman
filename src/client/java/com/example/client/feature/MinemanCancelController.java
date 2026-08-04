package com.example.client.feature;

import com.example.client.compat.baritone.BaritoneController;

/**
 * Immediately aborts every autonomous task (SmartMineman mining/store-items loop, the chest
 * quick-deposit attempt, HungryMineman's eat-wait, and Baritone's primary job/ad-hoc nav goal) and
 * resets them to idle, rather than just freezing them like {@link MinemanPauseController} does.
 */
public final class MinemanCancelController {
    private MinemanCancelController() {}

    /** Cancels everything and tells Baritone to stop via "#cancel". Use for the hotkey/GUI button. */
    public static void cancelAll() {
        cancelAll(true);
    }

    /**
     * @param sendCancelChat whether to send "#cancel" to Baritone. Pass false when the player just
     *                       manually typed "#cancel"/"#stop" themselves, since that message is about
     *                       to reach Baritone on its own and sending another would just spam chat.
     */
    public static void cancelAll(boolean sendCancelChat) {
        SmartMinemanHandler.cancelAll();
        ChestQuickDepositHandler.cancelAll();
        HungryMinemanHandler.cancelAll();
        BaritoneController.cancelAll(sendCancelChat);
        MinemanPauseController.setPaused(false);
    }
}

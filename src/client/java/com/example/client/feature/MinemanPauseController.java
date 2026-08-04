package com.example.client.feature;

/**
 * Single source of truth for the global Mineman pause/resume safety switch.
 * Any autonomous state machine (Baritone control, SmartMineman, HungryMineman, deposit helpers)
 * must poll {@link #isPaused()} before advancing or resuming, and hold in place while paused.
 */
public final class MinemanPauseController {
    private static boolean paused = false;

    private MinemanPauseController() {}

    public static boolean isPaused() {
        return paused;
    }

    public static void setPaused(boolean value) {
        paused = value;
    }

    public static void toggle() {
        paused = !paused;
    }
}

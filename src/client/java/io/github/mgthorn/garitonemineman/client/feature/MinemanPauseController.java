package io.github.mgthorn.garitonemineman.client.feature;

/**
 * Single source of truth for the global Mineman pause.
 * Any autonomous state machine (Baritone control, SmartMineman, HungryMineman, deposit helpers)
 * must poll {@link #isPaused()} before advancing or resuming, and hold in place while paused.
 *
 * Two independent reasons can pause, so neither undoes the other:
 * <ul>
 *   <li>the player's pause: the pause/resume hotkey, and Smart Mineman's "all chests full", which
 *       waits for the player to resume</li>
 *   <li>Hungry Mineman eating: set while eating and cleared when done, whatever the player did meanwhile</li>
 * </ul>
 */
public final class MinemanPauseController {
    private static boolean paused = false;
    private static boolean eatingHold = false;

    private MinemanPauseController() {}

    public static boolean isPaused() {
        return paused || eatingHold;
    }

    /** Paused by the player (or waiting for the player), as opposed to only eating. */
    public static boolean isUserPaused() {
        return paused;
    }

    public static boolean isEating() {
        return eatingHold;
    }

    public static void setPaused(boolean value) {
        paused = value;
    }

    public static void toggle() {
        paused = !paused;
    }

    public static void setEatingHold(boolean value) {
        eatingHold = value;
    }
}

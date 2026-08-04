package com.example.client.compat.baritone;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.example.client.config.Configs;
import com.example.client.feature.MinemanPauseController;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

/**
 * The single class allowed to talk to Baritone. Everything goes through its chat-command protocol
 * (the bundled jar's public API surface is too thin/obfuscated to call directly — see the plan's
 * Context section) — job state is tracked purely from what this mod itself last issued, or observed
 * from the player's own manually typed chat commands (see {@link #observeManualChatCommand}), rather
 * than queried from Baritone. Goal arrival is detected by polling the player's own position.
 *
 * Two independent things are tracked here, and {@link #pause()}/{@link #resume()} babysit both:
 * <ul>
 *   <li>The <b>primary job</b> (mine / sel fill air / a manually-typed goal walk), for
 *       SmartMineman/HungryMineman.</li>
 *   <li><b>Ad-hoc navigation</b> ({@link #sendGoal}/{@link #sendGoTo}), used for one-off storage-point
 *       travel (GUI buttons, hotkeys, and per-chest travel during Store Items) — this never touches
 *       the primary job's own state, so pausing to walk to a chest doesn't corrupt a paused mining
 *       job's resume data. If both are interrupted by the same {@link #pause()} (e.g. the primary job
 *       was suspended for a Store Items trip, and the trip's own travel gets caught by the global
 *       pause toggle too), {@link #resume()} continues the ad-hoc travel first and leaves the primary
 *       job remembered/paused until whatever finishes that trip resumes it explicitly.</li>
 * </ul>
 */
public final class BaritoneController {
    private BaritoneController() {}

    public enum JobType { NONE, MINE, SEL_FILL_AIR, GOAL }

    // ---- primary job: the one job pause()/resume() babysits ----
    private static JobType primaryJobType = JobType.NONE;
    @Nullable private static String primaryMineArgs;
    private static boolean paused;

    // ---- ad-hoc navigation: independent of the primary job above ----
    @Nullable private static BlockPos navGoal;
    @Nullable private static BlockPos navLastPos;
    private static int navStationaryTicks;
    private static boolean navPaused;
    private static final int ARRIVAL_STATIONARY_TICKS = 10;
    private static final double ARRIVAL_RADIUS_SQ = 4.0;

    // ---- storage point break/place protection: independent of both the above ----
    private static boolean insideProtectedStoragePoint = false;

    // ---- chat feedback suppression: independent of everything above ----
    // Baritone's command echo ("> #command") and its result confirmation ("Successfully set X to Y",
    // etc.) are printed unconditionally by Baritone itself, gated by none of its own settings — the
    // only way to hide them is to intercept them client-side (see MixinChatComponent). Every command
    // this mod sends goes through sendChat() below, so a short suppression window opened there catches
    // all of it, for every command (#mine, #cancel, #goal, #path, #sel, #set, ...), not just specific
    // ones. Baritone processes "#" commands synchronously within the same call, so a couple of ticks
    // of margin is more than enough.
    private static final int CHAT_FEEDBACK_SUPPRESS_TICKS = 3;
    private static int chatFeedbackSuppressTicksRemaining = 0;

    // ---- primary job control ----

    public static void startMine(String args) {
        primaryJobType = JobType.MINE;
        primaryMineArgs = args;
        paused = false;
        sendChat("#mine " + args);
    }

    public static void startSelFillAir() {
        primaryJobType = JobType.SEL_FILL_AIR;
        primaryMineArgs = null;
        paused = false;
        sendChat("#sel fill air");
    }

    public static boolean isJobActive() {
        return primaryJobType != JobType.NONE && paused == false;
    }

    public static JobType getPrimaryJobType() {
        return primaryJobType;
    }

    /** Whether an ad-hoc nav goal (storage-point travel) is currently tracked, paused or not. */
    public static boolean hasActiveNavGoal() {
        return navGoal != null;
    }

    @Nullable
    public static String getPrimaryMineArgs() {
        return primaryMineArgs;
    }

    public static boolean isPaused() {
        return paused;
    }

    /** Whether the primary job (if any) is currently suspended, e.g. by {@link #pause()}. */
    public static boolean isPrimaryJobPaused() {
        return primaryJobType != JobType.NONE && paused;
    }

    /**
     * Cancels whatever Baritone is currently doing — the primary job and/or an in-flight ad-hoc nav
     * goal — and remembers whichever was active so {@link #resume()} can reissue it. Always sends
     * "#cancel" regardless of tracked state, since this is the emergency-stop path — tracked state
     * might be stale, but the cancel command itself is harmless if nothing is running.
     */
    public static void pause() {
        if (primaryJobType != JobType.NONE) {
            paused = true;
        }

        if (navGoal != null) {
            navPaused = true;
        }

        sendChat("#cancel");
    }

    /**
     * Unlike {@link #pause()}, discards the primary job and ad-hoc nav goal entirely rather than
     * remembering them for {@link #resume()} — used by {@code MinemanCancelController} for a full,
     * immediate stop.
     *
     * @param sendCancelCommand whether to send "#cancel" to Baritone. Pass false when the player just
     *                          manually typed "#cancel"/"#stop" themselves (that message is already on
     *                          its way to Baritone), true otherwise.
     */
    public static void cancelAll(boolean sendCancelCommand) {
        primaryJobType = JobType.NONE;
        primaryMineArgs = null;
        paused = false;

        navGoal = null;
        navLastPos = null;
        navStationaryTicks = 0;
        navPaused = false;

        if (sendCancelCommand) {
            sendChat("#cancel");
        }
    }

    /**
     * Re-issues whichever of the primary job or an in-flight ad-hoc nav goal was interrupted by
     * {@link #pause()}, unless the global safety switch is engaged. If an ad-hoc nav goal was in
     * flight, only that is resumed — the primary job (if any) stays remembered/paused so it isn't
     * reissued out from under an in-progress storage-point trip; whoever actually finishes that trip
     * (e.g. {@code SmartMinemanHandler}'s own resume step) is expected to call this again afterward.
     */
    public static void resume() {
        if (MinemanPauseController.isPaused()) {
            return;
        }

        if (navPaused && navGoal != null) {
            navPaused = false;
            sendChat("#goal " + navGoal.getX() + " " + navGoal.getY() + " " + navGoal.getZ());
            sendPath();
            return;
        }

        if (paused == false) {
            return;
        }

        paused = false;

        switch (primaryJobType) {
            case MINE -> {
                if (primaryMineArgs != null) {
                    sendChat("#mine " + primaryMineArgs);
                }
            }
            case SEL_FILL_AIR -> sendChat("#sel fill air");
            case GOAL, NONE -> {
                // A manually-typed "#goal"'s exact target isn't parsed (may use relative "~"
                // coordinates) — resuming it precisely is a known best-effort gap.
            }
        }
    }

    /**
     * Called by MixinChatScreen when the player manually types a chat message, so commands issued
     * outside this mod's own controls (e.g. typing "#mine diamond_ore" directly) are still tracked.
     * Never called for this mod's own sends, since those call {@link #sendChat} directly.
     */
    public static void observeManualChatCommand(String message) {
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase();

        if (lower.startsWith("#mine")) {
            primaryJobType = JobType.MINE;
            primaryMineArgs = trimmed.length() > 5 ? trimmed.substring(5).trim() : "";
            paused = false;
        }
        else if (lower.startsWith("#sel") && lower.contains("fill") && lower.contains("air")) {
            primaryJobType = JobType.SEL_FILL_AIR;
            primaryMineArgs = null;
            paused = false;
        }
        else if (lower.startsWith("#goal")) {
            primaryJobType = JobType.GOAL;
            paused = false;
        }
        // "#cancel"/"#stop" are handled by MixinChatScreen calling MinemanCancelController directly,
        // since a manually typed cancel should abort everything (SmartMineman, HungryMineman, etc.),
        // not just this class's own job-tracking state.
    }

    // ---- ad-hoc navigation (storage point travel; independent of the primary job) ----

    public static void sendGoal(BlockPos pos) {
        navGoal = pos;
        navLastPos = null;
        navStationaryTicks = 0;
        sendChat("#goal " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    public static void sendPath() {
        sendChat("#path");
    }

    public static void sendGoTo(BlockPos pos) {
        sendGoal(pos);
        sendPath();
    }

    public static boolean hasArrivedAtNavGoal() {
        return navGoal != null && navLastPos != null
                && navStationaryTicks >= ARRIVAL_STATIONARY_TICKS
                && navLastPos.distSqr(navGoal) <= ARRIVAL_RADIUS_SQ;
    }

    public static void clearNavGoal() {
        navGoal = null;
        navLastPos = null;
        navStationaryTicks = 0;
        navPaused = false;
    }

    /**
     * Stops Baritone's ad-hoc nav goal immediately by sending "#cancel" (unlike {@link #clearNavGoal()},
     * which only wipes local tracking and assumes Baritone already stopped on its own upon arrival).
     * Use when the travel is no longer needed before Baritone would naturally finish it — e.g. a
     * quick-deposit attempt succeeded from within reach while still mid-path to the chest.
     */
    public static void cancelNavGoal() {
        if (navGoal != null) {
            sendChat("#cancel");
        }

        clearNavGoal();
    }

    /** Call once per client tick to update ad-hoc navigation arrival tracking. */
    public static void tick() {
        if (chatFeedbackSuppressTicksRemaining > 0) {
            chatFeedbackSuppressTicksRemaining--;
        }

        var player = Minecraft.getInstance().player;

        if (player == null) {
            navStationaryTicks = 0;
            navLastPos = null;
            insideProtectedStoragePoint = false; // nothing to restore once disconnected
            return;
        }

        updateStoragePointProtection(player);

        if (navGoal == null) {
            navStationaryTicks = 0;
            navLastPos = null;
            return;
        }

        BlockPos pos = player.blockPosition();

        if (navLastPos != null && navLastPos.equals(pos)) {
            navStationaryTicks++;
        }
        else {
            navStationaryTicks = 0;
        }

        navLastPos = pos;
    }

    // ---- storage point break/place protection ----

    // Storage points trigger protection even while disabled, and from a short distance out rather
    // than only once the player has actually crossed into the selection box — this way Baritone's
    // break/place is already off by the time it's pathing/mining right at the boundary.
    private static final int STORAGE_POINT_PROXIMITY_BLOCKS = 5;

    /**
     * Disables Baritone's {@code allowBreak}/{@code allowPlace} settings while the player is within
     * {@link #STORAGE_POINT_PROXIMITY_BLOCKS} of any storage point's area (enabled or not), restoring
     * them once clear, so pathing/mining never breaks into or bridges over a storage point's chests.
     * Only fires a "#set" pair on an actual enter/exit transition, never every tick. The commands
     * themselves and Baritone's own confirmation are kept out of chat (see MixinChatComponent); an
     * action-bar message is shown instead.
     */
    private static void updateStoragePointProtection(LocalPlayer player) {
        if (Configs.Generic.PROTECT_STORAGE_POINTS.getBooleanValue() == false) {
            if (insideProtectedStoragePoint) {
                insideProtectedStoragePoint = false;
                setBreakPlaceAllowed(true);
            }
            return;
        }

        boolean inside = isNearAnyStoragePoint(player.blockPosition());

        if (inside != insideProtectedStoragePoint) {
            insideProtectedStoragePoint = inside;
            setBreakPlaceAllowed(inside == false);
        }
    }

    private static boolean isNearAnyStoragePoint(BlockPos pos) {
        for (StoragePoint point : StoragePointManager.getInstance().getStoragePoints()) {
            int minX = Math.min(point.getCorner1X(), point.getCorner2X()) - STORAGE_POINT_PROXIMITY_BLOCKS;
            int maxX = Math.max(point.getCorner1X(), point.getCorner2X()) + STORAGE_POINT_PROXIMITY_BLOCKS;
            int minY = Math.min(point.getCorner1Y(), point.getCorner2Y()) - STORAGE_POINT_PROXIMITY_BLOCKS;
            int maxY = Math.max(point.getCorner1Y(), point.getCorner2Y()) + STORAGE_POINT_PROXIMITY_BLOCKS;
            int minZ = Math.min(point.getCorner1Z(), point.getCorner2Z()) - STORAGE_POINT_PROXIMITY_BLOCKS;
            int maxZ = Math.max(point.getCorner1Z(), point.getCorner2Z()) + STORAGE_POINT_PROXIMITY_BLOCKS;

            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                return true;
            }
        }

        return false;
    }

    private static void setBreakPlaceAllowed(boolean allowed) {
        sendChat("#set allowBreak " + allowed);
        sendChat("#set allowPlace " + allowed);

        var player = Minecraft.getInstance().player;

        if (player != null) {
            player.displayClientMessage(
                    Component.literal("allowPlace/Break: " + (allowed ? "enabled" : "disabled")), true);
        }
    }

    private static void sendChat(String command) {
        var player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        if (Configs.Generic.HIDE_BARITONE_CHAT_FEEDBACK.getBooleanValue()) {
            chatFeedbackSuppressTicksRemaining = CHAT_FEEDBACK_SUPPRESS_TICKS;
        }

        player.connection.sendChat(command);
    }

    /** Whether a chat message arriving right now is likely Baritone's echo/confirmation of a command
     *  this mod just sent, and should be dropped. Read by {@code MixinChatComponent}. */
    public static boolean isSuppressingChatFeedback() {
        return chatFeedbackSuppressTicksRemaining > 0;
    }
}

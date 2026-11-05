package io.github.mgthorn.garitonemineman.client.compat.baritone;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import io.github.mgthorn.garitonemineman.client.compat.BaritoneCompat;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.feature.MinemanPauseController;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/**
 * The one entry point to Baritone for the rest of the mod. Talks to Baritone through its API (via
 * {@link BaritoneBridge}), never through chat commands, and does nothing when Baritone isn't installed
 * or is the standalone build (see {@code BaritoneCompat}).
 *
 * Job state is read from Baritone itself, so jobs started any way (typed commands, Baritone's GUI,
 * Litematica) are seen. Three things are tracked here:
 * <ul>
 *   <li>The <b>job hold</b> ({@link #holdJob()}/{@link #releaseJob()}): keeps the running job waiting
 *       during a Smart Mineman store trip. The job is suspended, not cancelled, and continues by itself
 *       once released.</li>
 *   <li>The <b>nav goal</b> ({@link #sendGoTo}/{@link #travelTo}): storage point and chest travel, run
 *       above the job hold, so the player walks while the job waits.</li>
 *   <li>Storage point protection: the protected storage blocks are never broken anywhere, and during
 *       our own store trip nothing is broken or placed near a storage point.</li>
 * </ul>
 * The global pause hotkey only needs {@link MinemanPauseController}: our highest-priority Baritone
 * process reads it and freezes everything, travel included.
 */
public final class BaritoneController {
    private BaritoneController() {}

    public enum JobType {
        NONE, MINE, BUILD, FARM, GOAL, OTHER;

        /** Jobs that break blocks and fill the inventory, so Smart Mineman store trips make sense for them. */
        public boolean breaksBlocks() {
            return this == MINE || this == BUILD || this == FARM;
        }
    }

    private static boolean jobHeld;

    @Nullable private static BlockPos navGoal;
    // Manual travel (hotkey/GUI) ends by itself on arrival; Smart Mineman clears its own goals
    private static boolean navClearOnArrival;
    // Baritone couldn't calculate a path to the current nav goal
    private static boolean navFailed;

    private static boolean insideProtectedStoragePoint = false;
    private static boolean savedAllowBreak = true;
    private static boolean savedAllowPlace = true;

    // The protectedStorageBlocks IDs last put into Baritone's never-break list, and the blocks we added there
    @Nullable private static List<String> appliedProtectedIds;
    private static Set<Block> addedDisallowedBlocks = Set.of();

    // ---- jobs ----

    private static boolean isAvailable() {
        return BaritoneCompat.isLoaded() && Minecraft.getInstance().player != null;
    }

    /** Starts mining the given blocks, like {@code #mine}. */
    public static void startMine(String... blockNames) {
        if (isAvailable()) {
            BaritoneBridge.startMine(blockNames);
        }
    }

    /** Clears the area between the two corners, like {@code #sel cleararea}. */
    public static void startClearArea(BlockPos corner1, BlockPos corner2) {
        if (isAvailable()) {
            BaritoneBridge.startClearArea(corner1, corner2);
        }
    }

    public static JobType getPrimaryJobType() {
        return isAvailable() ? BaritoneBridge.currentJobType() : JobType.NONE;
    }

    /** Baritone's own name for the running job, or null if none is running. */
    @Nullable
    public static String getJobName() {
        return isAvailable() ? BaritoneBridge.currentJobName() : null;
    }

    /** A job is running and not held for a store trip. */
    public static boolean isJobActive() {
        return jobHeld == false && getPrimaryJobType() != JobType.NONE;
    }

    /** A mine, build or farm job is running and not held for a store trip (what Smart Mineman watches). */
    public static boolean isBlockBreakingJobActive() {
        return jobHeld == false && getPrimaryJobType().breaksBlocks();
    }

    /** Whether the running job (if any) is waiting: held, globally paused, or a paused build. */
    public static boolean isPrimaryJobPaused() {
        if (getPrimaryJobType() == JobType.NONE) {
            return false;
        }

        return jobHeld || MinemanPauseController.isPaused() || BaritoneBridge.isBuilderPaused();
    }

    // ---- job hold ----

    /** Keeps the running job waiting (without cancelling it) until {@link #releaseJob()}. */
    public static void holdJob() {
        jobHeld = true;
    }

    /** Lets the job continue. The store trip protection ends right here, before Baritone's next tick. */
    public static void releaseJob() {
        jobHeld = false;
        refreshTripProtection();
    }

    public static boolean isJobHeld() {
        return jobHeld;
    }

    /**
     * Drops the job hold and nav goal. Used by {@code MinemanCancelController} for a full stop.
     *
     * @param cancelBaritone whether to also cancel Baritone's own job. Pass false when the player just
     *                       typed {@code #cancel}/{@code #stop}, which Baritone handles by itself.
     */
    public static void cancelAll(boolean cancelBaritone) {
        jobHeld = false;
        clearNavGoal();

        if (cancelBaritone && isAvailable()) {
            BaritoneBridge.cancelEverything();
        }
    }

    // ---- travel ----

    /** Sets Baritone's goal next to {@code pos} without walking, like {@code #goal}. */
    public static void sendGoal(BlockPos pos) {
        if (isAvailable()) {
            BaritoneBridge.setGoal(pos);
        }
    }

    /** Walks next to {@code pos} and stays there until {@link #clearNavGoal()} (Smart Mineman). */
    public static void sendGoTo(BlockPos pos) {
        navGoal = pos;
        navClearOnArrival = false;
        navFailed = false;
    }

    /** Walks next to {@code pos} and ends the travel on arrival (hotkey and GUI). */
    public static void travelTo(BlockPos pos) {
        navGoal = pos;
        navClearOnArrival = true;
        navFailed = false;
    }

    @Nullable
    static BlockPos getNavGoal() {
        return navGoal;
    }

    public static boolean hasActiveNavGoal() {
        return navGoal != null;
    }

    /** Baritone found no path to the nav goal (or isn't usable at all), so waiting for arrival is pointless. */
    public static boolean hasNavGoalFailed() {
        return navGoal != null && (navFailed || BaritoneCompat.isLoaded() == false);
    }

    static void markNavCalcFailed() {
        navFailed = true;
    }

    public static boolean hasArrivedAtNavGoal() {
        return navGoal != null && isAvailable() && BaritoneBridge.isNextTo(navGoal);
    }

    /** Ends the travel; Baritone stops walking and the held or running job takes over again. */
    public static void clearNavGoal() {
        navGoal = null;
        navClearOnArrival = false;
        navFailed = false;
        refreshTripProtection();
    }

    /** Same as {@link #clearNavGoal()}: with the travel process gone, Baritone stops walking by itself. */
    public static void cancelNavGoal() {
        clearNavGoal();
    }

    /** Call once per client tick. */
    public static void tick() {
        if (BaritoneCompat.isLoaded() == false) {
            return;
        }

        updateProtectedBlocks();

        if (Minecraft.getInstance().player == null) {
            // Disconnected: nothing of the old world's trip or hold carries over, and allowBreak/allowPlace go back
            jobHeld = false;
            clearNavGoal();
            return;
        }

        BaritoneBridge.ensureRegistered();

        if (navClearOnArrival && hasArrivedAtNavGoal()) {
            clearNavGoal();
        }
        else if (navClearOnArrival && navFailed) {
            Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Mineman: no path to the storage point"));
            clearNavGoal();
        }

        refreshTripProtection();
    }

    // ---- storage point protection ----

    // Only during our own store trip (job held or nav goal set), and from a short distance out rather than
    // only once inside the storage point's box, so break/place is already off when Baritone walks up to it.
    // Outside a trip allowBreak is left alone: Baritone's mining refuses to run at all with allowBreak off
    // ("Unable to mine when allowBreak is false"), which used to cancel the job right after a store trip.
    private static final int STORAGE_POINT_PROXIMITY_BLOCKS = 5;

    /**
     * Keeps the blocks in {@code protectedStorageBlocks} in Baritone's {@code blocksToDisallowBreaking}
     * while {@code protectStoragePoints} is on, so it never breaks them anywhere, for mining, building
     * or pathing. Only touches the list when the setting or the IDs change, and only ever removes the
     * blocks it added itself.
     */
    private static void updateProtectedBlocks() {
        List<String> ids = Configs.Generic.PROTECT_STORAGE_POINTS.getBooleanValue()
                ? Configs.Generic.PROTECTED_STORAGE_BLOCKS.getStrings()
                : List.of();

        if (ids.equals(appliedProtectedIds)) {
            return;
        }

        Set<Block> blocks = new HashSet<>();

        for (String id : ids) {
            Identifier key = Identifier.tryParse(id.trim());

            if (key != null) {
                BuiltInRegistries.BLOCK.getOptional(key).ifPresent(blocks::add);
            }
        }

        addedDisallowedBlocks = BaritoneBridge.replaceDisallowedBreaking(blocks, addedDisallowedBlocks);
        appliedProtectedIds = List.copyOf(ids);
    }

    /**
     * Turns Baritone's {@code allowBreak}/{@code allowPlace} off while our store trip is within
     * {@link #STORAGE_POINT_PROXIMITY_BLOCKS} of any storage point's area (enabled or not), and puts back
     * the values they had before as soon as the trip ends or leaves the area. Only changes them on an
     * actual start/end, never every tick.
     */
    private static void refreshTripProtection() {
        if (BaritoneCompat.isLoaded() == false) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        boolean protect = player != null
                && Configs.Generic.PROTECT_STORAGE_POINTS.getBooleanValue()
                && (jobHeld || navGoal != null)
                && isNearAnyStoragePoint(player.blockPosition());

        if (protect == insideProtectedStoragePoint) {
            return;
        }

        insideProtectedStoragePoint = protect;

        if (protect) {
            savedAllowBreak = BaritoneBridge.isBreakAllowed();
            savedAllowPlace = BaritoneBridge.isPlaceAllowed();
            BaritoneBridge.setBreakPlaceAllowed(false, false);
            player.sendOverlayMessage(Component.literal("allowPlace/Break: disabled"));
        }
        else {
            BaritoneBridge.setBreakPlaceAllowed(savedAllowBreak, savedAllowPlace);

            if (player != null) {
                player.sendOverlayMessage(Component.literal("allowPlace/Break: restored"));
            }
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
}

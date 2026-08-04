package com.example.client.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.example.client.config.Configs;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

/**
 * Turns the player's view toward a target storage block, right-clicks it open, shift-clicks every
 * stack out of the player's inventory into it one slot per tick (skipping slots listed in
 * {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS}), then closes it. No travel is performed here.
 * Deliberately paced to one real click per tick rather than firing all of them in a single burst —
 * a real dedicated server can (and often does) silently reject a flood of container clicks sent
 * within the same tick, which otherwise looked fine locally (client-side prediction) right up until
 * the server's own resync quietly undid all of it.
 *
 * Two ways to drive it:
 * <ul>
 *   <li>{@link #tryQuickDeposit(Player)} — the hotkey/GUI entry point, working against the current
 *       storage point's own blocks (no travel). Its behavior depends on
 *       {@link Configs.Generic#ADVANCED_AUTO_STORE_MODUS}: enabled (default), it picks whichever
 *       single usable storage block looks best able to take the player's inventory (per its
 *       last-known contents) and makes one attempt against it. Disabled, it makes no such guess —
 *       it works through every usable storage block in list order, starting with the first, one
 *       attempt each, continuing regardless of each individual outcome until either the player's
 *       inventory is empty (outside {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS}) or every
 *       block has had a turn. Reports failures on the hotbar.</li>
 *   <li>{@link #beginAttempt(StorageBlockEntry)} / {@link #isBusy()} / {@link #consumeLastResult()} —
 *       for driving a specific, caller-chosen entry (used by {@link SmartMinemanHandler} to try a
 *       queue of chests, pathing to one with Baritone first if it's out of reach).</li>
 * </ul>
 * Both share the same underlying state machine (advanced every tick by {@link #onClientTick()}), so
 * only one attempt can be in flight at a time; {@link #beginAttempt} silently no-ops if one already is.
 */
public final class ChestQuickDepositHandler {
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;
    private static final int OPEN_WAIT_TIMEOUT_TICKS = 100; // ~5s
    private static final int SIGHT_BLOCKED_DEBOUNCE_TICKS = 5; // filters a single bad frame right on arrival

    public enum Result { SUCCESS, TOO_FAR, BLOCKED, FAILED }

    private enum State { IDLE, OPEN, WAIT_OPEN, DEPOSITING }

    private static State state = State.IDLE;
    private static List<BlockPos> targetPositions = List.of();
    private static int waitTicks;
    private static int sightBlockedTicks;
    @Nullable private static Result lastResult;

    // Slots (menu-index space) still queued to deposit, one per tick — see depositAllItems' doc comment
    // for why this can't just loop through them all in a single tick.
    private static List<Integer> depositQueue = List.of();
    private static int depositIndex;

    // "Simple" mode only (advancedAutoStoreModus disabled): the full ordered list of usable storage
    // blocks for the current tryQuickDeposit() call. Worked through one at a time regardless of each
    // one's own outcome, via advanceOrFinish(), until the player's inventory is empty or it runs out.
    private static List<StorageBlockEntry> sequentialQueue = List.of();
    private static int sequentialIndex;
    private static boolean sequentialMode;

    private ChestQuickDepositHandler() {}

    public static void tryQuickDeposit(Player player) {
        if (state != State.IDLE) {
            return;
        }

        StoragePoint point = StoragePointManager.getInstance()
                .getSelectedOrNearestStoragePoint(player.blockPosition());

        if (point == null) {
            showError(player, "No storage point available");
            return;
        }

        List<StorageBlockEntry> usable = new ArrayList<>();

        for (StorageBlockEntry entry : point.getStorageBlocks()) {
            if (entry.isUseEnabled()) {
                usable.add(entry);
            }
        }

        if (usable.isEmpty()) {
            showError(player, "No usable storage block in storage point");
            return;
        }

        if (Configs.Generic.ADVANCED_AUTO_STORE_MODUS.getBooleanValue()) {
            Set<Integer> disabledSlots = parseDisabledSlots();

            for (StorageBlockEntry entry : usable) {
                if (ContainerDepositHelper.hasRoomForInventory(entry, player, disabledSlots)) {
                    sequentialMode = false;
                    beginAttempt(entry);
                    return;
                }
            }

            showError(player, "No free slots in Storage point");
            return;
        }

        // Simple mode: no pre-analysis of which block is "best" — work through all of them in list
        // order, starting with the first, until the player's inventory is empty (outside
        // QUICK_DEPOSIT_DISABLED_SLOTS) or every one has had a turn. See advanceOrFinish().
        sequentialQueue = usable;
        sequentialIndex = 0;
        sequentialMode = true;
        beginAttempt(sequentialQueue.get(0));
    }

    /**
     * Starts a quick-deposit attempt against the given entry from the player's current position (no
     * travel). No-ops if an attempt is already in progress — check {@link #isBusy()} first.
     */
    public static void beginAttempt(StorageBlockEntry entry) {
        if (state != State.IDLE) {
            return;
        }

        targetPositions = orderedPositions(entry);
        lastResult = null;
        sightBlockedTicks = 0;
        state = State.OPEN;
    }

    public static boolean isBusy() {
        return state != State.IDLE;
    }

    @Nullable
    public static BlockPos getTargetPos() {
        return targetPositions.isEmpty() ? null : targetPositions.get(0);
    }

    /**
     * Both halves of a double chest, closer to the player first, so {@link #onClientTick()} tries the
     * nearer half and only falls through to the farther one if the nearer one can't be used. Just the
     * single position for a non-double entry, or if the halves' distances can't be compared right now.
     */
    private static List<BlockPos> orderedPositions(StorageBlockEntry entry) {
        BlockPos primary = new BlockPos(entry.getX(), entry.getY(), entry.getZ());

        if (entry.hasSecondPosition() == false) {
            return List.of(primary);
        }

        BlockPos second = new BlockPos(entry.getSecondX(), entry.getSecondY(), entry.getSecondZ());
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return List.of(primary, second);
        }

        Vec3 eyePos = player.getEyePosition();
        double distPrimary = new AABB(primary).distanceToSqr(eyePos);
        double distSecond = new AABB(second).distanceToSqr(eyePos);

        return distSecond < distPrimary ? List.of(second, primary) : List.of(primary, second);
    }

    /** Aborts an in-flight attempt (if any) immediately and resets to idle. */
    public static void cancelAll() {
        reset();
        lastResult = null;
    }

    /** Returns and clears the outcome of the most recently finished attempt, or null if none is ready. */
    @Nullable
    public static Result consumeLastResult() {
        Result result = lastResult;
        lastResult = null;
        return result;
    }

    public static void onClientTick() {
        if (state == State.IDLE) {
            return;
        }

        if (MinemanPauseController.isPaused()) {
            return; // frozen mid-attempt; state/targetPos/waitTicks are preserved for when it lifts
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) {
            reset();
            return;
        }

        switch (state) {
            case OPEN -> {
                // Both halves of a double chest open the same combined inventory, so try the closer
                // half first (targetPositions is pre-sorted by distance) and, if it can't be used, fall
                // through to the other one immediately — only once *neither* half is reachable/visible
                // does this give up with TOO_FAR/BLOCKED.
                BlockPos reachablePos = null;
                BlockPos visiblePos = null;

                for (BlockPos candidate : targetPositions) {
                    if (isWithinReach(player, candidate) == false) {
                        continue;
                    }

                    if (reachablePos == null) {
                        reachablePos = candidate;
                    }

                    if (hasLineOfSight(player, candidate)) {
                        visiblePos = candidate;
                        break;
                    }
                }

                if (reachablePos == null) {
                    advanceOrFinish(player, Result.TOO_FAR, "Chest too far away");
                    return;
                }

                BlockPos lookTarget = visiblePos != null ? visiblePos : reachablePos;
                player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(lookTarget));

                if (visiblePos == null) {
                    sightBlockedTicks++;

                    // A single blocked frame right as Baritone parks the player (still settling into
                    // position, a mob passing through, etc.) shouldn't burn a whole chest attempt —
                    // only give up once it's genuinely stayed blocked for a bit.
                    if (sightBlockedTicks < SIGHT_BLOCKED_DEBOUNCE_TICKS) {
                        return;
                    }

                    advanceOrFinish(player, Result.BLOCKED, "Line of sight to chest is blocked");
                    return;
                }

                sightBlockedTicks = 0;

                BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(visiblePos), Direction.UP, visiblePos, false);
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
                waitTicks = 0;
                state = State.WAIT_OPEN;
            }
            case WAIT_OPEN -> {
                waitTicks++;

                if (player.containerMenu != player.inventoryMenu) {
                    depositQueue = buildDepositQueue(player.containerMenu);
                    depositIndex = 0;
                    state = State.DEPOSITING;
                }
                else if (waitTicks >= OPEN_WAIT_TIMEOUT_TICKS) {
                    advanceOrFinish(player, Result.FAILED, "Chest didn't open");
                }
            }
            case DEPOSITING -> {
                if (player.containerMenu == player.inventoryMenu) {
                    // Container closed out from under us (server kicked us out, another mod/plugin
                    // closed it, etc.) — nothing more we can do here.
                    advanceOrFinish(player, Result.FAILED, null);
                    return;
                }

                if (depositIndex >= depositQueue.size()) {
                    player.closeContainer();

                    if (mc.screen != null) {
                        mc.setScreen(null);
                    }

                    advanceOrFinish(player, Result.SUCCESS, null);
                    return;
                }

                // One shift-click per tick, not all 36 in a single burst: a real dedicated server can
                // (and commonly does, via anti-cheat or its own click-rate limiting) silently reject a
                // flood of container clicks sent within the same tick, undoing the whole deposit right
                // back to how it started even though the client's own prediction briefly looked correct.
                // Pacing to real-click cadence survives that — it's the same reason this doesn't just
                // loop over depositQueue in WAIT_OPEN like it used to.
                int slotId = depositQueue.get(depositIndex);
                depositIndex++;
                mc.gameMode.handleInventoryMouseClick(player.containerMenu.containerId, slotId, 0, ClickType.QUICK_MOVE, player);
            }
            case IDLE -> {}
        }
    }

    private static boolean isWithinReach(Player player, BlockPos pos) {
        double reach = player.blockInteractionRange();
        return new AABB(pos).distanceToSqr(player.getEyePosition()) <= reach * reach;
    }

    private static boolean hasLineOfSight(Player player, BlockPos pos) {
        Vec3 start = player.getEyePosition();
        Vec3 end = Vec3.atCenterOf(pos);
        BlockHitResult result = player.level().clip(
                new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return result.getType() == HitResult.Type.BLOCK && pos.equals(result.getBlockPos());
    }

    /** Menu-index slots (player inventory range) worth shift-clicking, in order, skipping empty/disabled ones. */
    private static List<Integer> buildDepositQueue(AbstractContainerMenu menu) {
        int containerSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;

        if (containerSlotCount <= 0) {
            return List.of();
        }

        Set<Integer> disabledSlots = parseDisabledSlots();
        List<Integer> queue = new ArrayList<>();

        for (int i = containerSlotCount; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);

            if (disabledSlots.contains(slot.getContainerSlot()) || slot.getItem().isEmpty()) {
                continue;
            }

            queue.add(i);
        }

        return queue;
    }

    /**
     * Parses {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS} into a set of player-inventory
     * slot indices (0-8 hotbar, 9-35 main inventory), accepting both single numbers and "a-b" ranges,
     * comma-separated. Malformed tokens are ignored.
     */
    public static Set<Integer> parseDisabledSlots() {
        String raw = Configs.Generic.QUICK_DEPOSIT_DISABLED_SLOTS.getStringValue().trim();
        Set<Integer> disabled = new HashSet<>();

        for (String token : raw.split(",")) {
            token = token.trim();

            if (token.isEmpty()) {
                continue;
            }

            try {
                int dash = token.indexOf('-', 1);

                if (dash > 0) {
                    int from = Integer.parseInt(token.substring(0, dash).trim());
                    int to = Integer.parseInt(token.substring(dash + 1).trim());

                    for (int slot = from; slot <= to; slot++) {
                        disabled.add(slot);
                    }
                } else {
                    disabled.add(Integer.parseInt(token));
                }
            } catch (NumberFormatException ignored) {}
        }

        return disabled;
    }

    private static void showError(Player player, String message) {
        player.displayClientMessage(Component.literal("Mineman: " + message), true);
    }

    /**
     * The single choke point every per-block attempt (success or failure alike) ends at. In "simple"
     * mode ({@link #sequentialMode}), instead of finishing outright it moves on to the next storage
     * block in {@link #sequentialQueue} — regardless of whether this one succeeded, failed, or
     * couldn't be reached — as long as the player still has something left worth depositing and the
     * list isn't exhausted yet. Otherwise (advanced mode, or simple mode genuinely done) this behaves
     * exactly like the old unconditional finish: report {@code errorMessage} (if any) and go idle.
     */
    private static void advanceOrFinish(Player player, Result result, @Nullable String errorMessage) {
        if (sequentialMode && sequentialIndex + 1 < sequentialQueue.size() && hasEligibleItemsRemaining(player)) {
            sequentialIndex++;
            state = State.IDLE; // let beginAttempt's own guard pass
            beginAttempt(sequentialQueue.get(sequentialIndex));
            return;
        }

        if (errorMessage != null) {
            showError(player, errorMessage);
        }

        finish(result);
    }

    /** True if any player-inventory slot outside {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS} still holds an item. */
    private static boolean hasEligibleItemsRemaining(Player player) {
        Set<Integer> disabledSlots = parseDisabledSlots();

        for (int i = 0; i < 36; i++) {
            if (disabledSlots.contains(i)) {
                continue;
            }

            if (player.getInventory().getItem(i).isEmpty() == false) {
                return true;
            }
        }

        return false;
    }

    private static void finish(Result result) {
        lastResult = result;
        state = State.IDLE;
        targetPositions = List.of();
        waitTicks = 0;
        sightBlockedTicks = 0;
        depositQueue = List.of();
        depositIndex = 0;
        sequentialQueue = List.of();
        sequentialIndex = 0;
        sequentialMode = false;
    }

    private static void reset() {
        state = State.IDLE;
        targetPositions = List.of();
        waitTicks = 0;
        sightBlockedTicks = 0;
        depositQueue = List.of();
        depositIndex = 0;
        sequentialQueue = List.of();
        sequentialIndex = 0;
        sequentialMode = false;
    }
}

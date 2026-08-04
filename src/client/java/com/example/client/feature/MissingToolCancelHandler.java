package com.example.client.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.example.client.compat.baritone.BaritoneController;

/**
 * While a Baritone #mine or #sel fill air job is running, watches the block currently being broken
 * (the crosshair target while the attack key is held). Blocks that don't require a correct tool for
 * their normal drop (dirt, etc.) are left alone entirely. For ones that do (stone needing a pickaxe,
 * etc.), if the currently held item isn't a correct tool for it — typically because Baritone's own
 * itemSaver setting stepped down to bare hands or a wrong tool rather than break the last suitable one
 * — and there's no other suitable tool anywhere in the inventory to fall back to, the job is cancelled
 * with an explanatory message instead of silently grinding away breaking blocks that drop nothing.
 */
public final class MissingToolCancelHandler {
    // A few ticks of the same bad block before acting, so a single frame where the crosshair briefly
    // passes over a block mid-swing (or between blocks) doesn't cancel the whole job spuriously.
    private static final int TRIGGER_DEBOUNCE_TICKS = 10;

    private static BlockPos lastBadPos;
    private static int badTicks;

    private MissingToolCancelHandler() {}

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }

        BaritoneController.JobType jobType = BaritoneController.getPrimaryJobType();
        boolean relevantJob = jobType == BaritoneController.JobType.MINE
                || jobType == BaritoneController.JobType.SEL_FILL_AIR;

        if (relevantJob == false || BaritoneController.isPaused()) {
            reset();
            return;
        }

        if (mc.options.keyAttack.isDown() == false || (mc.hitResult instanceof BlockHitResult) == false) {
            reset();
            return;
        }

        BlockHitResult hitResult = (BlockHitResult) mc.hitResult;

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            reset();
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.requiresCorrectToolForDrops() == false) {
            reset();
            return;
        }

        ItemStack held = mc.player.getMainHandItem();

        if (held.isCorrectToolForDrops(state) || hasSuitableToolInInventory(mc, state)) {
            reset();
            return;
        }

        if (pos.equals(lastBadPos)) {
            badTicks++;
        }
        else {
            lastBadPos = pos;
            badTicks = 1;
        }

        if (badTicks >= TRIGGER_DEBOUNCE_TICKS) {
            String blockName = state.getBlock().getName().getString();
            mc.player.displayClientMessage(Component.literal(
                    "Mineman: no suitable tool left for " + blockName + " - cancelling"), true);
            MinemanCancelController.cancelAll();
            reset();
        }
    }

    private static boolean hasSuitableToolInInventory(Minecraft mc, BlockState state) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (stack.isEmpty() == false && stack.isCorrectToolForDrops(state)) {
                return true;
            }
        }

        return false;
    }

    private static void reset() {
        lastBadPos = null;
        badTicks = 0;
    }
}

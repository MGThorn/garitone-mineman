package com.example.client.compat;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Vanilla's container-open packet carries no block position, so there is no built-in way to know
 * which block a currently open container menu corresponds to. This tracks the last block the player
 * attempted to interact with (captured by a mixin on the client interaction call, before any packet
 * is sent) and associates it with whichever menu becomes the player's open menu right after.
 */
public final class OpenContainerTracker {
    @Nullable private static BlockPos lastInteractedPos;
    @Nullable private static BlockPos openContainerPos;
    private static int trackedContainerId = -1;

    private OpenContainerTracker() {}

    public static void onBlockInteractAttempt(BlockPos pos) {
        lastInteractedPos = pos;
    }

    /** Call once per client tick to associate the currently open menu with the last interacted block. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            trackedContainerId = -1;
            openContainerPos = null;
            return;
        }

        var menu = mc.player.containerMenu;

        if (menu == mc.player.inventoryMenu) {
            trackedContainerId = -1;
            openContainerPos = null;
            return;
        }

        if (menu.containerId != trackedContainerId) {
            trackedContainerId = menu.containerId;
            openContainerPos = lastInteractedPos;
        }
    }

    @Nullable
    public static BlockPos getOpenContainerPos() {
        return openContainerPos;
    }
}

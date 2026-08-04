package com.example.client.feature;

import org.jetbrains.annotations.Nullable;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.example.client.compat.OpenContainerTracker;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StorageBlockEntry.ContentSlot;
import com.example.client.storage.StoragePointManager;

/**
 * Snapshots a tracked Storage Point chest's contents into its {@link StorageBlockEntry} the moment its
 * container screen closes, then persists it (via the {@code .storagepoint} snapshot file written by
 * {@link StoragePointManager#save()}). Runs for every tracked chest unconditionally, regardless of its
 * rules/enabled state — this is plain content tracking, not rule enforcement.
 */
public final class StorageContentSnapshotter {
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    private StorageContentSnapshotter() {}

    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                // OpenContainerTracker clears its position on the next client tick once the screen
                // closes, and there's no ordering guarantee that hasn't already happened by the time
                // ScreenEvents.remove fires below — so track our own last-known-good position for this
                // screen's whole open lifetime instead of trusting a fresh read of it at close time.
                BlockPos[] lastKnownPos = new BlockPos[1];

                ScreenEvents.afterBackground(screen).register((s, drawContext, mouseX, mouseY, tickDelta) -> {
                    BlockPos pos = OpenContainerTracker.getOpenContainerPos();

                    if (pos != null) {
                        lastKnownPos[0] = pos;
                    }
                });

                ScreenEvents.remove(screen).register(closed -> onClosed(containerScreen, lastKnownPos[0]));
            }
        });
    }

    /**
     * Reads contents from the closing screen's own menu, not {@code player.containerMenu} — by the time
     * this fires, vanilla has already flipped the player back to their inventory menu, so only the
     * screen instance itself still references the chest's menu.
     */
    private static void onClosed(AbstractContainerScreen<?> screen, @Nullable BlockPos pos) {
        if (pos == null) {
            return;
        }

        StorageBlockEntry entry = StoragePointManager.getInstance().findStorageBlockEntry(pos);

        if (entry == null) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        int containerSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;

        if (containerSlotCount <= 0) {
            return;
        }

        ContentSlot[] contents = new ContentSlot[containerSlotCount];

        for (int i = 0; i < containerSlotCount; i++) {
            contents[i] = ContentSlot.fromStack(menu.getSlot(i).getItem());
        }

        entry.setContents(contents);
        StoragePointManager.getInstance().save();
    }
}

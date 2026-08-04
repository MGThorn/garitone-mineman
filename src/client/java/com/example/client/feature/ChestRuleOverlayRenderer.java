package com.example.client.feature;

import org.jetbrains.annotations.Nullable;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.example.client.compat.OpenContainerTracker;
import com.example.client.mixin.AbstractContainerScreenAccessor;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StoragePointManager;

/**
 * Overlays a rule-restricted Storage Point chest's slot markings onto the real, currently-open
 * container screen, so the same disabled/restricted slots configured in {@code GuiChestRules} are
 * visible while actually playing: disabled slots get a translucent red tint, and item-restricted
 * slots show a faded (~25% opacity) preview of the required item when empty.
 */
public final class ChestRuleOverlayRenderer {
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;
    private static final int DISABLED_SLOT_COLOR = 0x55FF0000;
    private static final int RESTRICTED_PREVIEW_FADE_COLOR = 0xBF8B8B8B;

    private ChestRuleOverlayRenderer() {}

    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                ScreenEvents.afterBackground(screen).register(
                        (s, drawContext, mouseX, mouseY, tickDelta) -> renderOverlay(containerScreen, drawContext));
            }
        });
    }

    private static void renderOverlay(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        BlockPos pos = OpenContainerTracker.getOpenContainerPos();

        if (pos == null) {
            return;
        }

        StorageBlockEntry entry = StoragePointManager.getInstance().findStorageBlockEntry(pos);

        if (entry == null || entry.hasRules() == false) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        int containerSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;

        if (containerSlotCount <= 0) {
            return;
        }

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        int leftPos = accessor.getLeftPos();
        int topPos = accessor.getTopPos();

        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = menu.getSlot(i);
            int x = leftPos + slot.x;
            int y = topPos + slot.y;

            if (entry.isSlotEnabled(i) == false) {
                graphics.fill(x, y, x + 16, y + 16, DISABLED_SLOT_COLOR);
                continue;
            }

            String restrictedItemId = entry.getSlotItem(i);

            if (restrictedItemId != null && slot.getItem().isEmpty()) {
                ItemStack preview = resolveStack(restrictedItemId);

                if (preview != null) {
                    graphics.renderItem(preview, x, y);
                    graphics.fill(x, y, x + 16, y + 16, RESTRICTED_PREVIEW_FADE_COLOR);
                }
            }
        }
    }

    @Nullable
    private static ItemStack resolveStack(String itemId) {
        Identifier id = Identifier.tryParse(itemId);

        if (id == null) {
            return null;
        }

        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(null);
    }
}

package com.example.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import fi.dy.masa.itemscroller.util.InventoryUtils;

/**
 * Exposes Item Scroller's own private {@code quickSort} so {@code InventorySorter} can run its exact
 * sorting algorithm over a caller-chosen slot range — used to sort the player's hotbar and main
 * inventory together as one combined range, rather than whichever single region Item Scroller's own
 * {@code sortInventory} would otherwise restrict itself to based on mouse position.
 */
@Mixin(InventoryUtils.class)
public interface ItemScrollerInventoryUtilsInvoker {
    @Invoker("quickSort")
    static void mineman$quickSort(AbstractContainerScreen<?> gui, int start, int end, boolean shulkerBoxFix, int swapSlot) {
        throw new AssertionError("Mixin invoker not applied");
    }
}

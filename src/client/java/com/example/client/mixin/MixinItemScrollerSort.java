package com.example.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import fi.dy.masa.itemscroller.util.InventoryUtils;
import com.example.client.feature.InventorySorter;

/**
 * Hooks Item Scroller's own sortInventory hotkey handler ({@code InventoryUtils#sortInventory}, called
 * from its {@code KeybindCallbacks} whenever its sortInventory hotkey fires).
 *
 * Item Scroller's own logic only ever sorts whichever single region — hotbar OR main inventory, never
 * both — the mouse happens to be hovering, and has no notion of "this slot is already correctly placed,
 * leave it alone." Left as-is, that means: a configured slot in the region Item Scroller re-sorts gets
 * shuffled right back out of place, while a configured slot in the *other* region (never touched by
 * that pass) looks fine purely by omission.
 *
 * So whenever the hovered slot belongs to the player's own inventory, this takes over entirely (cancels
 * Item Scroller's own body): fills in {@code InventorySortConfig}'s configured slots first, then sorts
 * hotbar and main inventory together as one combined range via Item Scroller's own algorithm
 * ({@link InventorySorter#sortRemainderCombined}), which re-applies the configured slots once more
 * afterward to put back anything that combined sort just displaced. Sorting some other container (e.g.
 * an open chest) is untouched — Item Scroller's own body runs exactly as it always did.
 */
@Mixin(InventoryUtils.class)
public class MixinItemScrollerSort {
    @Inject(method = "sortInventory", at = @At("HEAD"), cancellable = true)
    private static void mineman$sortPlayerInventory(AbstractContainerScreen<?> gui, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        Slot hovered = ((AbstractContainerScreenAccessor) gui).getHoveredSlot();

        if (hovered == null || hovered.container != mc.player.getInventory()) {
            return; // not the player's own inventory (e.g. an open chest's own slots) — leave untouched
        }

        InventorySorter.applyConfiguredSlots(gui.getMenu(), mc.player);
        InventorySorter.sortRemainderCombined(gui, mc.player);
        ci.cancel();
    }
}

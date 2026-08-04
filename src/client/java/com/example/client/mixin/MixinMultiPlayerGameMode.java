package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.example.client.compat.OpenContainerTracker;
import com.example.client.config.FillStrategy;
import com.example.client.feature.ContainerDepositHelper;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StoragePointManager;

/**
 * Captures the block position of every right-click interaction attempt before any packet is sent,
 * since vanilla's container-open packet carries no position. See OpenContainerTracker.
 *
 * Also enforces Storage Point chest rules at the one choke point every real click funnels through.
 * {@code handleInventoryMouseClick} both drives the client's own local prediction (by calling
 * {@code AbstractContainerMenu#clicked}, see {@code MixinAbstractContainerMenu}) <em>and</em>
 * unconditionally sends the click to the server afterwards, using the click's original slot/button/
 * type regardless of what that local prediction did. Cancelling {@code clicked()} alone only skips the
 * client's own optimistic UI update — the packet still goes out, and a real dedicated server (running
 * none of this logic) processes it for real on its own authoritative menu, placing the item and
 * syncing the true state back down. That's why rule enforcement previously only held up on a
 * singleplayer/LAN world: there the "server" is the same mixed class in the same JVM, so blocking the
 * client's call happens to also block the integrated server's authoritative one. Cancelling here
 * instead — before the packet is ever built — works the same way against a real remote server, since
 * the server never receives the illegal click to begin with.
 */
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode {
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void mineman$captureInteractedBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {
        OpenContainerTracker.onBlockInteractAttempt(hitResult.getBlockPos());
    }

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void mineman$enforceChestRulesOverNetwork(int containerId, int slotId, int mouseButton,
            ClickType clickType, Player player, CallbackInfo ci) {
        BlockPos pos = OpenContainerTracker.getOpenContainerPos();

        if (pos == null) {
            return;
        }

        StorageBlockEntry entry = StoragePointManager.getInstance().findStorageBlockEntry(pos);

        if (entry == null || entry.hasRules() == false) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        int containerSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;

        if (containerSlotCount <= 0) {
            return;
        }

        if (clickType == ClickType.QUICK_MOVE) {
            if (slotId < 0 || slotId >= menu.slots.size()) {
                return;
            }

            if (slotId < containerSlotCount) {
                // Shift-click FROM the chest itself: a locked slot blocks withdrawal outright; a
                // configured slot only ever holds its one allowed item, so withdrawing it is fine.
                if (entry.isSlotEnabled(slotId) == false) {
                    ci.cancel();
                }

                return;
            }

            Slot sourceSlot = menu.getSlot(slotId);
            ItemStack stack = sourceSlot.getItem();

            if (stack.isEmpty()) {
                return;
            }

            ci.cancel();
            mineman$redistribute(containerId, player, menu, containerSlotCount, entry, slotId);
            return;
        }

        if (slotId < 0 || slotId >= containerSlotCount) {
            return; // not touching a chest slot
        }

        if (entry.isSlotEnabled(slotId) == false) {
            ci.cancel(); // locked slot: no interaction at all, either direction, any click type
            return;
        }

        ItemStack incoming = switch (clickType) {
            case PICKUP, QUICK_CRAFT -> menu.getCarried();
            case SWAP -> (mouseButton >= 0 && mouseButton <= 8) ? player.getInventory().getItem(mouseButton) : ItemStack.EMPTY;
            default -> ItemStack.EMPTY; // CLONE/THROW/PICKUP_ALL only take FROM the slot, nothing to restrict
        };

        if (incoming.isEmpty()) {
            return;
        }

        if (entry.isItemAllowedInSlot(slotId, itemId(incoming)) == false) {
            ci.cancel();
        }
    }

    /**
     * Replays a blocked shift-click as a real pickup-then-place sequence of ordinary clicks, each sent
     * to the server exactly like a manual click would be — unlike a single local slot mutation, this
     * survives a round trip to a real dedicated server. Mirrors {@code ContainerDepositHelper}'s own
     * destination priority, distributing across as many legal slots as needed (merging into existing
     * partial stacks before falling back to empty ones) since a single destination isn't always enough
     * to hold the whole incoming stack.
     */
    private static void mineman$redistribute(int containerId, Player player, AbstractContainerMenu menu,
            int containerSlotCount, StorageBlockEntry entry, int sourceSlotId) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryMouseClick(containerId, sourceSlotId, 0, ClickType.PICKUP, player);

        ItemStack carried = menu.getCarried();

        while (carried.isEmpty() == false) {
            int destSlot = ContainerDepositHelper.findDestinationSlot(
                    menu, containerSlotCount, entry, carried, FillStrategy.FIRST_AVAILABLE_SLOT);

            if (destSlot == -1) {
                break;
            }

            int beforeCount = carried.getCount();
            mc.gameMode.handleInventoryMouseClick(containerId, destSlot, 0, ClickType.PICKUP, player);
            carried = menu.getCarried();

            if (carried.getCount() == beforeCount) {
                break; // destination didn't actually accept anything; avoid looping forever
            }
        }

        if (menu.getCarried().isEmpty() == false) {
            // Nothing could take the rest: put it back where it came from.
            mc.gameMode.handleInventoryMouseClick(containerId, sourceSlotId, 0, ClickType.PICKUP, player);
        }
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}

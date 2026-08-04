package com.example.client.mixin;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
 * Local-prediction layer for per-chest rule enforcement: cancels this client's own optimistic click
 * handling when it would touch a rule-restricted Storage Point chest slot that fails the chest's
 * per-slot item restriction or per-slot enabled mask, so the GUI never even flashes the illegal
 * result for a frame. The actual authoritative enforcement — the one that also holds up against a
 * real dedicated server — lives one level up, in {@code MixinMultiPlayerGameMode}'s
 * {@code handleInventoryMouseClick} injection, since that's the point that decides whether a click's
 * packet gets sent to the server at all. This mixin alone cannot stop a different client or a
 * dedicated server not running this logic; it only ever fully enforced anything on its own for a
 * singleplayer/LAN-hosted integrated server, since that shares this same mixed class in the same JVM.
 *
 * A "locked" slot ({@code isSlotEnabled == false}) is fully blocked: no item can be placed there and
 * nothing can be taken out, by any click type. A "configured" slot ({@code getSlotItem != null}) only
 * restricts placement to its one configured item; that item (and only that item, since nothing else
 * can ever land there) can always be taken back out.
 *
 * PICKUP/SWAP/CLONE/THROW/QUICK_MOVE are handled here. QUICK_MOVE (shift-click) into the chest can't
 * rely on vanilla's own destination choice since it ignores the per-slot rules, so the original click
 * is always cancelled and replaced with a rule-aware destination lookup (mirroring
 * {@link ContainerDepositHelper}) re-issued as a manual pickup-then-place pair of clicks. If nothing
 * in the chest can take the item, the shift-click is simply blocked and the stack stays put.
 *
 * QUICK_CRAFT (click-and-drag) and PICKUP_ALL (double-click collect) don't go through per-slot
 * {@code clicked} calls for each affected slot — see {@link #mineman$restrictQuickReplace} below,
 * which hooks the shared {@code canItemQuickReplace} check vanilla itself uses to decide which slots
 * a drag/collect may touch, so a locked or mismatched slot is never added to the drag in the first
 * place regardless of where the mouse is released.
 */
@Mixin(AbstractContainerMenu.class)
public class MixinAbstractContainerMenu {
    private static final int PLAYER_INVENTORY_SLOT_COUNT = 36;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void mineman$enforceChestRules(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        BlockPos pos = OpenContainerTracker.getOpenContainerPos();

        if (pos == null) {
            return;
        }

        StorageBlockEntry entry = StoragePointManager.getInstance().findStorageBlockEntry(pos);

        if (entry == null || entry.hasRules() == false) {
            return;
        }

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        int containerSlotCount = self.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;

        if (containerSlotCount <= 0) {
            return;
        }

        if (clickType == ClickType.QUICK_MOVE) {
            if (slotId < 0 || slotId >= self.slots.size()) {
                return;
            }

            if (slotId < containerSlotCount) {
                // shift-click FROM the chest itself (withdrawal) — blocked only if the slot is locked;
                // a configured slot only ever holds its one allowed item, so withdrawal is always fine.
                if (entry.isSlotEnabled(slotId) == false) {
                    ci.cancel();
                }

                return;
            }

            Slot sourceSlot = self.getSlot(slotId);
            ItemStack stack = sourceSlot.getItem();

            if (stack.isEmpty()) {
                return;
            }

            ci.cancel();

            // Distribute across as many legal slots as needed (merging into existing partial stacks
            // before falling back to empty ones, per findDestinationSlot's own priority), the same way
            // vanilla's own quickMoveStack would — a single destination slot isn't always enough to fit
            // the whole incoming stack, e.g. depositing 64 onto an already-half-full matching stack.
            ItemStack remaining = stack.copy();

            while (remaining.isEmpty() == false) {
                int destSlot = ContainerDepositHelper.findDestinationSlot(
                        self, containerSlotCount, entry, remaining, FillStrategy.FIRST_AVAILABLE_SLOT);

                if (destSlot == -1) {
                    break;
                }

                int beforeCount = remaining.getCount();
                remaining = self.getSlot(destSlot).safeInsert(remaining, remaining.getCount());

                if (remaining.getCount() == beforeCount) {
                    break; // destination didn't actually accept anything; avoid looping forever
                }
            }

            if (remaining.getCount() != stack.getCount()) {
                sourceSlot.set(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
            }

            return;
        }

        if (slotId < 0 || slotId >= containerSlotCount) {
            return; // not touching a chest slot (QUICK_CRAFT/PICKUP_ALL are covered separately below)
        }

        if (entry.isSlotEnabled(slotId) == false) {
            ci.cancel(); // locked slot: no interaction at all, either direction, any click type
            return;
        }

        ItemStack incoming = switch (clickType) {
            case PICKUP -> self.getCarried();
            case SWAP -> (button >= 0 && button <= 8) ? player.getInventory().getItem(button) : ItemStack.EMPTY;
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
     * Vanilla's own gate for "can this stack quick-replace what's in this slot", used both by
     * click-and-drag (to decide, per slot the mouse passes over, whether to add it to the drag) and
     * by double-click PICKUP_ALL (to decide whether to sweep a matching stack out of it). Forcing this
     * to {@code false} for a locked or item-mismatched Storage Point slot stops a drag from ever
     * accumulating that slot — so releasing the mouse off of it doesn't matter, it was never a
     * candidate — and stops PICKUP_ALL from draining it, without having to special-case either feature
     * individually.
     */
    @Inject(method = "canItemQuickReplace", at = @At("HEAD"), cancellable = true)
    private static void mineman$restrictQuickReplace(@Nullable Slot slot, ItemStack itemStack, boolean bl,
            CallbackInfoReturnable<Boolean> cir) {
        if (slot == null) {
            return;
        }

        BlockPos pos = OpenContainerTracker.getOpenContainerPos();

        if (pos == null) {
            return;
        }

        StorageBlockEntry entry = StoragePointManager.getInstance().findStorageBlockEntry(pos);

        if (entry == null || entry.hasRules() == false) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        int containerSlotCount = menu.slots.size() - PLAYER_INVENTORY_SLOT_COUNT;

        if (containerSlotCount <= 0 || slot.index < 0 || slot.index >= containerSlotCount) {
            return; // not a slot belonging to the tracked chest
        }

        if (entry.isSlotEnabled(slot.index) == false || entry.isItemAllowedInSlot(slot.index, itemId(itemStack)) == false) {
            cir.setReturnValue(false);
        }
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}

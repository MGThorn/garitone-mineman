package com.example.client.feature;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import com.example.client.config.FillStrategy;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Rule-aware destination slot lookup shared by the mixin that redirects manual shift-clicks
 * ({@code MixinAbstractContainerMenu}) and (indirectly) anything else that needs to know which slot
 * of a rule-restricted Storage Point chest a given stack is actually allowed to land in.
 */
public final class ContainerDepositHelper {
    private ContainerDepositHelper() {}

    /**
     * Finds the best destination slot (0-based, within the chest's own slot range) for {@code stack}
     * according to the chest's per-slot enabled mask and item restriction, and the given fill
     * strategy. A slot counts as a valid merge target as soon as it has room for at least one more of
     * the item — not only when the entire incoming stack would fit — so callers distributing a stack
     * across multiple slots (see {@code MixinAbstractContainerMenu}'s quick-move handling) can call this
     * repeatedly, transferring as much as each returned slot can hold via {@link net.minecraft.world.inventory.Slot#safeInsert}
     * and re-querying for the remainder. Returns -1 if nothing in the chest has any room left at all.
     *
     * A slot configured for this exact item always wins over a plain unrestricted slot, regardless of
     * slot index or fill strategy — that's the whole point of configuring a slot for an item.
     */
    public static int findDestinationSlot(AbstractContainerMenu menu, int containerSlotCount,
            StorageBlockEntry entry, ItemStack stack, FillStrategy strategy) {
        int firstEmpty = -1;
        int firstMergeable = -1;
        int firstConfiguredEmpty = -1;
        int firstConfiguredMergeable = -1;
        String itemId = itemId(stack);

        for (int i = 0; i < containerSlotCount; i++) {
            if (entry.isSlotEnabled(i) == false || entry.isItemAllowedInSlot(i, itemId) == false) {
                continue;
            }

            boolean configuredForThisItem = itemId.equals(entry.getSlotItem(i));
            ItemStack existing = menu.getSlot(i).getItem();

            if (existing.isEmpty()) {
                if (configuredForThisItem && firstConfiguredEmpty == -1) {
                    firstConfiguredEmpty = i;
                }

                if (firstEmpty == -1) {
                    firstEmpty = i;
                }
            }
            else if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < existing.getMaxStackSize()) {
                if (configuredForThisItem && firstConfiguredMergeable == -1) {
                    firstConfiguredMergeable = i;
                }

                if (firstMergeable == -1) {
                    firstMergeable = i;
                }
            }
        }

        if (firstConfiguredMergeable != -1 || firstConfiguredEmpty != -1) {
            return strategy == FillStrategy.STACK_MERGE_FIRST
                    ? (firstConfiguredMergeable != -1 ? firstConfiguredMergeable : firstConfiguredEmpty)
                    : (firstConfiguredEmpty != -1 && firstConfiguredMergeable != -1
                            ? Math.min(firstConfiguredEmpty, firstConfiguredMergeable)
                            : (firstConfiguredEmpty != -1 ? firstConfiguredEmpty : firstConfiguredMergeable));
        }

        if (strategy == FillStrategy.STACK_MERGE_FIRST) {
            return firstMergeable != -1 ? firstMergeable : firstEmpty;
        }

        if (firstEmpty == -1) {
            return firstMergeable;
        }

        if (firstMergeable == -1) {
            return firstEmpty;
        }

        return Math.min(firstEmpty, firstMergeable);
    }

    /**
     * Whether this chest looks worth traveling to at all, based purely on its last-known cached
     * contents ({@link StorageBlockEntry#getContents()}) — no container needs to be open for this.
     * True if the chest's contents are unknown (never captured — treated as worth trying, same
     * "unknown = worth trying" convention {@link com.example.client.storage.StorageContentIndex}
     * already uses), or if at least one item in {@code player}'s inventory (outside
     * {@code disabledPlayerSlots}) would find a rule-legal destination there: either a completely
     * empty slot, or a slot already holding that same item with room left before its max stack size.
     */
    public static boolean hasRoomForInventory(StorageBlockEntry entry, Player player, Set<Integer> disabledPlayerSlots) {
        ContentSlot[] contents = entry.getContents();

        if (contents == null) {
            return true;
        }

        Inventory inventory = player.getInventory();

        for (int i = 0; i < 36; i++) {
            if (disabledPlayerSlots.contains(i)) {
                continue;
            }

            ItemStack stack = inventory.getItem(i);

            if (stack.isEmpty() == false && hasDestinationFor(entry, contents, stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasDestinationFor(StorageBlockEntry entry, ContentSlot[] contents, ItemStack stack) {
        String itemId = itemId(stack);

        for (int i = 0; i < contents.length; i++) {
            if (entry.isSlotEnabled(i) == false || entry.isItemAllowedInSlot(i, itemId) == false) {
                continue;
            }

            ContentSlot slot = contents[i];

            if (slot == null || slot.itemId() == null) {
                return true;
            }

            if (slot.itemId().equals(itemId) && slot.count() < stack.getMaxStackSize()) {
                return true;
            }
        }

        return false;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}

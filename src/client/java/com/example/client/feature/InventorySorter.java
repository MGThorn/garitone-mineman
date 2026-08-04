package com.example.client.feature;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import com.example.client.mixin.ItemScrollerInventoryUtilsInvoker;
import com.example.client.storage.InventorySortConfig;

/**
 * Fills in the player's "this item belongs in this slot" configured slots ({@link InventorySortConfig})
 * before Item Scroller's own generic sortInventory runs (see {@code MixinItemScrollerSort}) — moving
 * each configured slot's target item into place first, so Item Scroller's own priority-list sort only
 * has to deal with whatever's left over.
 *
 * Item id only; item components/NBT are deliberately ignored (no {@code isSameItemSameComponents}
 * anywhere here) — a configured slot matches by registry item id alone. The one exception is tools:
 * if the configured item belongs to one of vanilla's own tool tags ({@code #minecraft:pickaxes},
 * {@code axes}, {@code shovels}, {@code hoes}, {@code swords}), the configured item only marks the
 * *category* — any item sharing that tag matches the slot — and among the matches actually present in
 * the inventory, the one with the highest {@link Tool#defaultMiningSpeed()} wins, not necessarily the
 * exact configured item/tier (a stone pickaxe configured for a slot gets bumped by an iron one if
 * you're carrying both).
 */
public final class InventorySorter {
    private static final int SLOT_COUNT = 36;

    private static final List<TagKey<Item>> TOOL_CATEGORY_TAGS = List.of(
            ItemTags.PICKAXES, ItemTags.AXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.SWORDS);

    private InventorySorter() {}

    public static void applyConfiguredSlots(AbstractContainerMenu menu, Player player) {
        String[] filter = InventorySortConfig.getSlotItemFilter();

        if (filter == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        Map<Integer, Integer> menuSlotByInvIndex = mapMenuSlots(menu, inventory);
        Set<Integer> claimed = new HashSet<>();

        for (int targetIndex = 0; targetIndex < filter.length && targetIndex < SLOT_COUNT; targetIndex++) {
            String configuredId = filter[targetIndex];

            if (configuredId == null) {
                continue;
            }

            Item configuredItem = resolveItem(configuredId);

            if (configuredItem == null) {
                continue;
            }

            Integer targetMenuSlot = menuSlotByInvIndex.get(targetIndex);

            if (targetMenuSlot == null) {
                continue; // this inventory slot isn't part of the currently open menu
            }

            TagKey<Item> category = toolCategoryOf(configuredItem);
            ItemStack current = inventory.getItem(targetIndex);

            if (current.isEmpty() == false && matches(configuredItem, category, current)) {
                claimed.add(targetIndex);
                continue; // already correct, nothing to move
            }

            int sourceIndex = findBestSource(inventory, configuredItem, category, claimed, targetIndex);

            if (sourceIndex < 0) {
                continue; // nothing in the inventory belongs here right now
            }

            Integer sourceMenuSlot = menuSlotByInvIndex.get(sourceIndex);

            if (sourceMenuSlot == null) {
                continue;
            }

            swapSlots(menu, player, sourceMenuSlot, targetMenuSlot);
            claimed.add(targetIndex);
        }
    }

    /**
     * Sorts the player's hotbar and main inventory together as a single combined range, using Item
     * Scroller's own sorting algorithm (via {@link ItemScrollerInventoryUtilsInvoker}), then re-applies
     * {@link #applyConfiguredSlots} once more — this "re-locks" every configured slot back into place
     * in case the wider sort just moved one of them, which is otherwise exactly what would happen: Item
     * Scroller's own {@code sortInventory} only ever sorts whichever single region (hotbar OR main) the
     * mouse happens to be hovering, and has no notion of a slot being "already correctly placed" to
     * leave alone.
     */
    public static void sortRemainderCombined(AbstractContainerScreen<?> gui, Player player) {
        AbstractContainerMenu menu = gui.getMenu();
        Map<Integer, Integer> menuSlotByInvIndex = mapMenuSlots(menu, player.getInventory());

        if (menuSlotByInvIndex.isEmpty()) {
            return;
        }

        int start = Collections.min(menuSlotByInvIndex.values());
        int end = Collections.max(menuSlotByInvIndex.values()) + 1;

        ItemScrollerInventoryUtilsInvoker.mineman$quickSort(gui, start, end, false, 8);
        applyConfiguredSlots(menu, player);
    }

    /**
     * Maps inventory-index (0-35) -&gt; menu-slot-index for every menu slot backed by the player's own
     * inventory. {@link Inventory} is a single unified {@code Container} covering hotbar+main *and*
     * armor/offhand/body-armor (via its equipment-slot mapping, at container indices &gt;= 36) — armor
     * and offhand slots share the exact same backing {@code Inventory} instance, just at those higher
     * indices, so they must be explicitly excluded here or they'd otherwise get swept into
     * {@link #sortRemainderCombined}'s range and get sorted/touched right along with the real 36.
     */
    private static Map<Integer, Integer> mapMenuSlots(AbstractContainerMenu menu, Inventory inventory) {
        Map<Integer, Integer> map = new HashMap<>();

        for (Slot slot : menu.slots) {
            if (slot.container == inventory) {
                int containerSlot = slot.getContainerSlot();

                if (containerSlot >= 0 && containerSlot < SLOT_COUNT) {
                    map.put(containerSlot, slot.index);
                }
            }
        }

        return map;
    }

    /** Best (highest mining-speed, for tool categories) unclaimed inventory slot matching the configured item, or -1. */
    private static int findBestSource(Inventory inventory, Item configuredItem, @Nullable TagKey<Item> category,
            Set<Integer> claimed, int excludeIndex) {
        int best = -1;

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == excludeIndex || claimed.contains(i)) {
                continue;
            }

            ItemStack stack = inventory.getItem(i);

            if (stack.isEmpty() || matches(configuredItem, category, stack) == false) {
                continue;
            }

            if (best == -1) {
                best = i;
                continue;
            }

            if (category != null && compareTools(stack, inventory.getItem(best)) > 0) {
                best = i;
            }
        }

        return best;
    }

    private static boolean matches(Item configuredItem, @Nullable TagKey<Item> category, ItemStack actual) {
        if (category != null) {
            return actual.is(category);
        }

        return actual.is(configuredItem);
    }

    @Nullable
    private static TagKey<Item> toolCategoryOf(Item configuredItem) {
        ItemStack probe = new ItemStack(configuredItem);

        for (TagKey<Item> tag : TOOL_CATEGORY_TAGS) {
            if (probe.is(tag)) {
                return tag;
            }
        }

        return null;
    }

    /** Higher default mining speed wins; a stack with no Tool component at all loses to one that has it. */
    private static int compareTools(ItemStack a, ItemStack b) {
        Tool toolA = a.get(DataComponents.TOOL);
        Tool toolB = b.get(DataComponents.TOOL);
        float speedA = toolA != null ? toolA.defaultMiningSpeed() : Float.NEGATIVE_INFINITY;
        float speedB = toolB != null ? toolB.defaultMiningSpeed() : Float.NEGATIVE_INFINITY;
        return Float.compare(speedA, speedB);
    }

    /**
     * Pick up the source, place into the target (swapping if the target held something else), and put
     * whatever ends up back on the cursor back where the source came from. Real pickup clicks, the same
     * pickup/place chain used elsewhere in this mod (see {@code MixinMultiPlayerGameMode}'s
     * redistribute), so it survives a round trip to a real dedicated server rather than relying on any
     * local-only slot mutation.
     */
    private static void swapSlots(AbstractContainerMenu menu, Player player, int sourceMenuSlot, int targetMenuSlot) {
        Minecraft mc = Minecraft.getInstance();
        int containerId = menu.containerId;

        mc.gameMode.handleInventoryMouseClick(containerId, sourceMenuSlot, 0, ClickType.PICKUP, player);
        mc.gameMode.handleInventoryMouseClick(containerId, targetMenuSlot, 0, ClickType.PICKUP, player);

        if (menu.getCarried().isEmpty() == false) {
            mc.gameMode.handleInventoryMouseClick(containerId, sourceMenuSlot, 0, ClickType.PICKUP, player);
        }
    }

    @Nullable
    private static Item resolveItem(String itemId) {
        Identifier id = Identifier.tryParse(itemId);

        if (id == null) {
            return null;
        }

        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }
}

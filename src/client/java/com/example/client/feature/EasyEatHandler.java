package com.example.client.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import com.example.client.config.Configs;
import com.example.client.config.Hotkeys;
import com.example.client.config.PickOrder;
import com.example.client.mixin.InventoryAccessor;

public class EasyEatHandler {
    private static boolean active = false;
    private static int previousSlot = -1;
    private static int targetSlot = -1;
    private static int foodContainerSlot = -1;
    private static boolean targetSlotWasEmpty = false;

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean held = Hotkeys.EASY_EAT.getKeybind().isKeybindHeld() || isAutoEatHeld(mc);

        if (mc.player == null || mc.level == null) {
            if (active) { mc.options.keyUse.setDown(false); reset(); }
            return;
        }

        if (held && !active) {
            int[] slots = parseConfiguredSlots();
            previousSlot = ((InventoryAccessor) mc.player.getInventory()).getSelected();

            targetSlot = slots[0];
            for (int slot : slots) {
                if (mc.player.getInventory().getItem(slot).isEmpty()) { targetSlot = slot; break; }
            }

            PickOrder order = (PickOrder) Configs.Generic.AUTO_EAT_PICK_ORDER.getOptionListValue();
            boolean berndActive = Configs.Generic.BERND_DAS_BROT.getBooleanValue();
            ItemStack currentTargetStack = mc.player.getInventory().getItem(targetSlot);

            // FIRST_FOUND: if target already has food use it; ranked modes: find globally best food
            // (which may already be in the target slot, avoiding an unnecessary swap).
            // With Bernd das Brot active, the target is only kept as-is if it's already bread.
            if (order == PickOrder.FIRST_FOUND && isFood(currentTargetStack) && (!berndActive || isBread(currentTargetStack))) {
                targetSlotWasEmpty = false;
                foodContainerSlot = -1;
            } else {
                boolean includeTarget = order != PickOrder.FIRST_FOUND;
                int bestIndex = findBestFoodIndex(mc, targetSlot, includeTarget);
                if (bestIndex == -1) {
                    mc.player.displayClientMessage(
                        Component.literal("Action prevented: no food found"), true);
                    return;
                }
                if (bestIndex == targetSlot) {
                    // Best food is already sitting in the target slot — no swap needed.
                    targetSlotWasEmpty = false;
                    foodContainerSlot = -1;
                } else {
                    targetSlotWasEmpty = mc.player.getInventory().getItem(targetSlot).isEmpty();
                    foodContainerSlot = toContainerSlot(bestIndex);
                    swapWithHotbar(mc, foodContainerSlot, targetSlot);
                }
            }
            active = true;

        } else if (!held && active) {
            mc.options.keyUse.setDown(false);
            if (!targetSlotWasEmpty && foodContainerSlot != -1) swapWithHotbar(mc, foodContainerSlot, targetSlot);
            ((InventoryAccessor) mc.player.getInventory()).setSelected(previousSlot);
            reset();
            return;
        }

        if (active && mc.screen == null) {
            if (mc.player.getInventory().getItem(targetSlot).isEmpty()) {
                // Current food stack ran out — find the next best food.
                mc.options.keyUse.setDown(false);
                // Target is empty so it won't appear as a candidate regardless of includeTarget.
                int nextIndex = findBestFoodIndex(mc, targetSlot, false);
                if (nextIndex == -1) {
                    mc.player.displayClientMessage(
                        Component.literal("Action prevented: no food found"), true);
                    ((InventoryAccessor) mc.player.getInventory()).setSelected(previousSlot);
                    reset();
                    return;
                }
                swapWithHotbar(mc, toContainerSlot(nextIndex), targetSlot);
                return; // resume eating next tick once the swap is processed
            }
            ((InventoryAccessor) mc.player.getInventory()).setSelected(targetSlot);
            // setDown() is public in 1.21.11 — no mixin needed.
            // Vanilla aiStep() checks keyUse.isDown() and skips releaseUsingItem()
            // when true, so eating is never cancelled while the key is held.
            mc.options.keyUse.setDown(true);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static int[] parseConfiguredSlots() {
        String raw = Configs.Generic.AUTO_EAT_PICKABLE_SLOTS.getStringValue().trim();
        String[] parts = raw.split(",");
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (String part : parts) {
            try {
                int slot = Integer.parseInt(part.trim()) - 1;
                if (slot >= 0 && slot <= 8) slots.add(slot);
            } catch (NumberFormatException ignored) {}
        }
        if (slots.isEmpty()) slots.add(8);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    // Inventory index → container slot in InventoryMenu:
    //   hotbar 0-8  → container slot 36-44
    //   main  9-35  → container slot 9-35
    private static int toContainerSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private static boolean isFood(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return false;
        java.util.List<String> blacklist = Configs.Generic.AUTOEAT_BLACKLIST.getStrings();
        if (blacklist.isEmpty()) return true;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return !blacklist.contains(id);
    }

    private static boolean isBread(ItemStack stack) {
        return stack.is(Items.BREAD) && isFood(stack);
    }

    private static int findBreadIndex(Minecraft mc, int targetHotbarSlot, boolean includeTargetSlot) {
        for (int i = 0; i <= 35; i++) {
            if (!includeTargetSlot && i == targetHotbarSlot) continue;
            if (isBread(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    /**
     * Returns the inventory index (0-35) of the best food to eat, or -1 if none.
     *
     * FIRST_FOUND always excludes targetHotbarSlot (caller handles the "already has food" case).
     * Ranked modes include targetHotbarSlot when includeTargetSlot is true, so the food already
     * in the target slot can win and avoid an unnecessary swap.
     */
    private static int findBestFoodIndex(Minecraft mc, int targetHotbarSlot, boolean includeTargetSlot) {
        if (Configs.Generic.BERND_DAS_BROT.getBooleanValue()) {
            int breadIndex = findBreadIndex(mc, targetHotbarSlot, includeTargetSlot);
            if (breadIndex != -1) return breadIndex;
        }

        PickOrder order = (PickOrder) Configs.Generic.AUTO_EAT_PICK_ORDER.getOptionListValue();

        if (order == PickOrder.FIRST_FOUND) {
            for (int i = 9; i <= 35; i++) {
                if (isFood(mc.player.getInventory().getItem(i))) return i;
            }
            for (int i = 0; i <= 8; i++) {
                if (i == targetHotbarSlot) continue;
                if (isFood(mc.player.getInventory().getItem(i))) return i;
            }
            return -1;
        }

        java.util.List<Integer> candidates = new java.util.ArrayList<>();
        for (int i = 0; i <= 35; i++) {
            if (!includeTargetSlot && i == targetHotbarSlot) continue;
            if (isFood(mc.player.getInventory().getItem(i))) candidates.add(i);
        }
        if (candidates.isEmpty()) return -1;

        int best = -1;
        switch (order) {
            case MOST_SATURATION: {
                float bestVal = Float.NEGATIVE_INFINITY;
                for (int i : candidates) {
                    FoodProperties fp = mc.player.getInventory().getItem(i).get(DataComponents.FOOD);
                    if (fp != null && fp.saturation() > bestVal) { bestVal = fp.saturation(); best = i; }
                }
                break;
            }
            case MOST_HUNGER: {
                int bestVal = Integer.MIN_VALUE;
                for (int i : candidates) {
                    FoodProperties fp = mc.player.getInventory().getItem(i).get(DataComponents.FOOD);
                    if (fp != null && fp.nutrition() > bestVal) { bestVal = fp.nutrition(); best = i; }
                }
                break;
            }
            case MOST_ITEMS: {
                int bestCount = -1;
                for (int ci : candidates) {
                    Item item = mc.player.getInventory().getItem(ci).getItem();
                    int total = 0;
                    for (int i = 0; i <= 35; i++) {
                        ItemStack s = mc.player.getInventory().getItem(i);
                        if (s.getItem() == item) total += s.getCount();
                    }
                    if (total > bestCount) { bestCount = total; best = ci; }
                }
                break;
            }
            default:
                break;
        }
        return best;
    }

    // Returns true when autoEat should act as a held signal.
    // Two-phase: trigger when missing >= threshold; once active, keep going until full.
    // This prevents rapid on/off cycling at the boundary.
    private static boolean isAutoEatHeld(Minecraft mc) {
        if (!Configs.Generic.AUTO_EAT.getBooleanValue() || mc.player == null) return false;
        int food = mc.player.getFoodData().getFoodLevel();
        int threshold = Configs.Generic.AUTO_EAT_THRESHOLD.getIntegerValue();
        return active ? food < 20 : (20 - food) >= threshold;
    }

    private static void swapWithHotbar(Minecraft mc, int containerSlot, int hotbarSlot) {
        mc.gameMode.handleInventoryMouseClick(
            mc.player.inventoryMenu.containerId,
            containerSlot,
            hotbarSlot,
            ClickType.SWAP,
            mc.player
        );
    }

    private static void reset() {
        active = false;
        previousSlot = -1;
        targetSlot = -1;
        foodContainerSlot = -1;
        targetSlotWasEmpty = false;
    }
}

package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import io.github.mgthorn.garitonemineman.client.compat.ItemScrollerCompat;
import io.github.mgthorn.garitonemineman.client.mixin.AbstractContainerScreenAccessor;
import io.github.mgthorn.garitonemineman.client.storage.InventorySortConfig;
import io.github.mgthorn.garitonemineman.client.storage.SortRule;

/**
 * Mineman's own inventory sort (its own hotkey; Item Scroller's sort is left alone).
 *
 * <ol>
 *   <li>Partial stacks of the same item in the main inventory are merged.</li>
 *   <li>Every fixed slot of the active profile ({@link InventorySortConfig}) gets the item its
 *       {@link SortRule} prefers. Specific rules (enchantments, exact item) pick before general ones.
 *       A fixed slot without a match stays empty when the rule is "reserved", otherwise it's free.</li>
 *   <li>Everything else in the main inventory, plus whatever was pushed out of a fixed slot, is sorted
 *       into the free main inventory slots in a stable order (registry order, then name, then count),
 *       so sorting twice gives the same result. Hotbar slots without a rule and the offhand keep their
 *       item.</li>
 * </ol>
 *
 * The whole result is planned on copies first, then sent as clicks: mostly single SWAP clicks (number
 * key swaps, button 40 = offhand), paced by {@link ItemScrollerCompat#getClicksPerTick()}.
 */
public final class InventorySorter {
    private static final int HOTBAR = 9;
    private static final int MAIN_END = 36;

    private record Click(int containerId, int menuSlot, int button, ContainerInput type) {}

    private static final ArrayDeque<Click> QUEUE = new ArrayDeque<>();
    @Nullable private static String doneMessage;

    private InventorySorter() {}

    public static boolean isBusy() {
        return QUEUE.isEmpty() == false;
    }

    // ---- Entry point ----

    /** Sort hotkey: sorts the inventory of the open screen with the active profile. */
    public static void sortFromHotkey() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.gameMode == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        if (isBusy()) {
            return;
        }

        if (player.isCreative()) {
            message(player, "§cMineman sort doesn't work in creative mode");
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;

        if (menu.getCarried().isEmpty() == false) {
            message(player, "§cPut down the item on the cursor first");
            return;
        }

        Slot hovered = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();

        if (hovered != null && hovered.container != player.getInventory()) {
            // Over a chest's own slots: storage point chests with rules are sorted by StorageChestSorter (later)
            message(player, "§7Hover your inventory to sort it (chests: Item Scroller's sort)");
            return;
        }

        int clicks = sortInventory(menu, player);
        InventorySortConfig.Profile profile = InventorySortConfig.getActiveProfile();
        doneMessage = clicks == 0 ? "§7Already sorted (" + profile.getName() + ")" : "§aSorted (" + profile.getName() + ")";

        if (clicks == 0) {
            message(player, doneMessage);
            doneMessage = null;
        }
    }

    /** Plans and queues the sort of the player's inventory in {@code menu}. Returns the number of clicks. */
    public static int sortInventory(AbstractContainerMenu menu, LocalPlayer player) {
        Map<Integer, Integer> menuSlots = mapMenuSlots(menu, player.getInventory());

        if (menuSlots.size() < MAIN_END) {
            return 0; // a menu without the whole player inventory
        }

        Plan plan = new Plan(menu.containerId, menuSlots, player.getInventory());
        plan.mergeMainStacks();
        plan.arrange(InventorySortConfig.getActiveProfile());
        QUEUE.addAll(plan.clicks);
        return plan.clicks.size();
    }

    // ---- Paced sending ----

    public static void onClientTick() {
        if (QUEUE.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.gameMode == null || player.containerMenu.containerId != QUEUE.peek().containerId()) {
            // Screen closed or changed: stop, the rest no longer fits
            QUEUE.clear();
            doneMessage = null;
            return;
        }

        for (int i = ItemScrollerCompat.getClicksPerTick(); i > 0 && QUEUE.isEmpty() == false; i--) {
            Click click = QUEUE.poll();
            mc.gameMode.handleContainerInput(click.containerId(), click.menuSlot(), click.button(), click.type(), player);
        }

        if (QUEUE.isEmpty() && doneMessage != null) {
            message(player, doneMessage);
            doneMessage = null;
        }
    }

    public static void cancel() {
        QUEUE.clear();
        doneMessage = null;
    }

    private static void message(LocalPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }

    // ---- Planning ----

    /** Inventory index (0-35) → menu slot, for every menu slot backed by the player's own inventory. */
    static Map<Integer, Integer> mapMenuSlots(AbstractContainerMenu menu, Inventory inventory) {
        Map<Integer, Integer> map = new HashMap<>();

        for (Slot slot : menu.slots) {
            if (slot.container == inventory && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < MAIN_END) {
                map.put(slot.getContainerSlot(), slot.index);
            }
        }

        return map;
    }

    /** Stable order for items without a fixed slot. */
    static final Comparator<ItemStack> REST_ORDER = Comparator
            .comparingInt((ItemStack s) -> BuiltInRegistries.ITEM.getId(s.getItem()))
            .thenComparing(s -> s.getHoverName().getString())
            .thenComparing(s -> s.getComponentsPatch().toString())
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

    /**
     * Works on copies of the inventory and records the clicks that turn the real inventory into the result.
     * Positions are inventory indices: 0-8 hotbar, 9-35 main, 40 offhand.
     */
    private static final class Plan {
        private final int containerId;
        private final Map<Integer, Integer> menuSlots;
        private final Map<Integer, ItemStack> sim = new LinkedHashMap<>();
        final List<Click> clicks = new ArrayList<>();

        Plan(int containerId, Map<Integer, Integer> menuSlots, Inventory inventory) {
            this.containerId = containerId;
            this.menuSlots = menuSlots;

            for (int i = 0; i < MAIN_END; i++) {
                this.sim.put(i, inventory.getItem(i).copy());
            }

            this.sim.put(InventorySortConfig.OFFHAND, inventory.getItem(InventorySortConfig.OFFHAND).copy());
        }

        /** Fills partial stacks in the main inventory from later partial stacks of the same item. */
        void mergeMainStacks() {
            for (int i = HOTBAR; i < MAIN_END; i++) {
                ItemStack target = this.sim.get(i);

                for (int j = i + 1; j < MAIN_END && target.isEmpty() == false && target.getCount() < target.getMaxStackSize(); j++) {
                    ItemStack source = this.sim.get(j);

                    if (source.isEmpty() || ItemStack.isSameItemSameComponents(target, source) == false) {
                        continue;
                    }

                    int moved = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
                    this.pickup(j);
                    this.pickup(i);

                    if (moved < source.getCount()) {
                        this.pickup(j); // the rest goes back
                    }

                    target.grow(moved);
                    source.shrink(moved);

                    if (source.isEmpty()) {
                        this.sim.put(j, ItemStack.EMPTY);
                    }
                }
            }
        }

        void arrange(InventorySortConfig.Profile profile) {
            List<Integer> positions = new ArrayList<>(this.sim.keySet());
            Map<Integer, Integer> source = new HashMap<>(); // target position → position whose stack goes there
            Set<Integer> used = new HashSet<>();
            Set<Integer> reservedEmpty = new HashSet<>();
            List<Integer> freeMain = new ArrayList<>();
            List<Integer> freeHotbar = new ArrayList<>();

            // 1. Fixed slots, specific rules first
            List<Map.Entry<Integer, SortRule>> rules = new ArrayList<>(profile.getRules().entrySet());
            rules.removeIf(e -> this.sim.containsKey(e.getKey()) == false);
            rules.sort(Comparator.comparingInt((Map.Entry<Integer, SortRule> e) -> -specificity(e.getValue())).thenComparingInt(Map.Entry::getKey));

            for (Map.Entry<Integer, SortRule> e : rules) {
                int slot = e.getKey();
                SortRule rule = e.getValue();
                Comparator<ItemStack> preference = rule.preference();
                int best = -1;

                for (int p : positions) {
                    ItemStack stack = this.sim.get(p);

                    if (used.contains(p) || rule.matches(stack) == false) {
                        continue;
                    }

                    // Prefer the item already in the slot when it's as good as the best
                    if (best == -1 || preference.compare(stack, this.sim.get(best)) < 0
                            || (p == slot && preference.compare(stack, this.sim.get(best)) == 0)) {
                        best = p;
                    }
                }

                if (best >= 0) {
                    source.put(slot, best);
                    used.add(best);
                }
                else if (rule.isReserved()) {
                    reservedEmpty.add(slot);
                }
                else if (slot < HOTBAR) {
                    freeHotbar.add(slot);
                }
                else if (slot < MAIN_END) {
                    freeMain.add(slot);
                }
            }

            // 2. Hotbar slots without a rule and the offhand keep their item
            for (int p : positions) {
                boolean hasRule = profile.getRule(p) != null;

                if (hasRule || used.contains(p)) {
                    continue;
                }

                if (p < HOTBAR || p == InventorySortConfig.OFFHAND) {
                    if (this.sim.get(p).isEmpty()) {
                        if (p < HOTBAR) {
                            freeHotbar.add(p);
                        }
                    }
                    else {
                        source.put(p, p);
                        used.add(p);
                    }
                }
                else {
                    freeMain.add(p);
                }
            }

            // 3. The rest, in a stable order, into the free main slots, then free hotbar slots, then reserved ones
            List<Integer> rest = new ArrayList<>();

            for (int p : positions) {
                if (used.contains(p) == false && this.sim.get(p).isEmpty() == false) {
                    rest.add(p);
                }
            }

            rest.sort((a, b) -> REST_ORDER.compare(this.sim.get(a), this.sim.get(b)));
            freeMain.sort(null);
            freeHotbar.sort(null);
            List<Integer> free = new ArrayList<>(freeMain);
            free.addAll(freeHotbar);
            free.addAll(reservedEmpty.stream().sorted().toList()); // only when the inventory is too full otherwise

            for (int i = 0; i < rest.size() && i < free.size(); i++) {
                source.put(free.get(i), rest.get(i));
                used.add(rest.get(i));
            }

            // 4. The remaining targets get the empty slots (fixBijection pairs them up)
            this.applyPermutation(positions, fixBijection(positions, source));
        }

        /** Makes sure every position is the source of exactly one target. */
        private static Map<Integer, Integer> fixBijection(List<Integer> positions, Map<Integer, Integer> source) {
            Map<Integer, Integer> result = new HashMap<>();
            Set<Integer> taken = new HashSet<>();

            for (int p : positions) {
                Integer s = source.get(p);

                if (s != null && taken.add(s)) {
                    result.put(p, s);
                }
            }

            ArrayDeque<Integer> unusedSources = new ArrayDeque<>();

            for (int p : positions) {
                if (taken.contains(p) == false) {
                    unusedSources.add(p);
                }
            }

            for (int p : positions) {
                if (result.containsKey(p) == false) {
                    result.put(p, unusedSources.poll());
                }
            }

            return result;
        }

        /** Moves every stack to its target with swaps; identical stacks aren't swapped. */
        private void applyPermutation(List<Integer> positions, Map<Integer, Integer> source) {
            // Track where each original stack (by identity) currently is
            Map<ItemStack, Integer> where = new IdentityHashMap<>();
            Map<Integer, ItemStack> original = new HashMap<>();

            for (int p : positions) {
                ItemStack stack = this.sim.get(p);

                if (stack.isEmpty()) {
                    stack = new ItemStack(net.minecraft.world.item.Items.AIR); // a distinct marker per empty slot
                    this.sim.put(p, stack);
                }

                where.put(stack, p);
                original.put(p, stack);
            }

            for (int target : positions) {
                ItemStack wanted = original.get(source.get(target));
                int current = where.get(wanted);

                if (current == target) {
                    continue;
                }

                ItemStack displaced = this.sim.get(target);

                if (sameContents(displaced, wanted) == false) {
                    this.swap(target, current);
                }

                this.sim.put(target, wanted);
                this.sim.put(current, displaced);
                where.put(wanted, target);
                where.put(displaced, current);
            }
        }

        private static boolean sameContents(ItemStack a, ItemStack b) {
            return (a.isEmpty() && b.isEmpty()) || ItemStack.matches(a, b);
        }

        /** Clicks that exchange the stacks of two positions. */
        private void swap(int a, int b) {
            if (isButton(b) && a != InventorySortConfig.OFFHAND) {
                this.swapClick(a, b);
            }
            else if (isButton(a) && b != InventorySortConfig.OFFHAND) {
                this.swapClick(b, a);
            }
            else {
                // Two main inventory slots: through hotbar slot 0 and back
                int temp = 0;
                this.swapClick(a, temp);
                this.swapClick(b, temp);
                this.swapClick(a, temp);
            }
        }

        private static boolean isButton(int position) {
            return position < HOTBAR || position == InventorySortConfig.OFFHAND;
        }

        private void swapClick(int position, int buttonPosition) {
            this.clicks.add(new Click(this.containerId, this.menuSlots.get(position), buttonPosition, ContainerInput.SWAP));
        }

        private void pickup(int position) {
            this.clicks.add(new Click(this.containerId, this.menuSlots.get(position), 0, ContainerInput.PICKUP));
        }

        private static int specificity(SortRule rule) {
            int score = 0;
            score += rule.getRequired().size() * 4 + rule.getForbidden().size() * 2;
            score += rule.isAnyOfType() && rule.getToolType() != null ? 0 : 1;
            return score;
        }
    }
}

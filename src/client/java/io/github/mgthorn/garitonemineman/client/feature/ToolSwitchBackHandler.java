package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.mixin.InventoryAccessor;

/**
 * Undoes Tweakeroo's tweakToolSwitch and tweakWeaponSwitch once the attack key is released.
 *
 * Tweakeroo switches tools in exactly two ways: selecting another hotbar slot, or SWAP-clicking a tool
 * from the main inventory into a hotbar slot (see {@code MixinTweakerooInventoryUtils}). Every SWAP it
 * makes inside {@code trySwitchToEffectiveTool} or {@code trySwitchToWeapon} is logged here, along with
 * the hotbar slot that was
 * selected before the first switch. On release the logged swaps are replayed in reverse (a SWAP is its
 * own inverse) and the original slot is re-selected.
 *
 * tweakSwapAlmostBrokenTools swaps are deliberately <em>not</em> logged or undone. Undoing only the tool
 * switch swaps around them moves the replacement to wherever the worn tool originally came from, and
 * the worn tool stays wherever Tweakeroo put it. The player keeps a usable tool in the original spot,
 * and the nearly broken one never comes back.
 *
 * Any other inventory click during the session (the player, Item Scroller, this mod's own features)
 * makes the logged slot numbers unreliable, so swaps are then skipped and only the selection restored.
 * Sessions only start while the attack key is physically held, so Baritone's own mining never triggers
 * a restore.
 *
 * Changing the main-hand item resets the attack cooldown, so a session that included a weapon switch
 * waits weaponSwitchBackDelay ticks after release; clicking again in that window keeps the weapon out.
 */
public class ToolSwitchBackHandler {
    private enum Context { NONE, TOOL_SWITCH, DURABILITY, RESTORING }

    private static Context context = Context.NONE;
    private static boolean trackingCall = false;
    private static int selectedBeforeCall = -1;
    private static int swapsBeforeCall = 0;

    private static boolean active = false;
    private static boolean invalid = false;
    private static int originalSelected = -1;
    private static int expectedSelected = -1;
    private static boolean weaponInSession = false;
    private static int releasedTicks = 0;
    /** Each entry is {containerSlot, hotbarSlot}, in the order Tweakeroo performed them. */
    private static final List<int[]> swaps = new ArrayList<>();

    public static void onWeaponSwitchPre() {
        onToolSwitchPre();
    }

    public static void onWeaponSwitchPost() {
        if (endSwitchCall()) {
            weaponInSession = true;
        }
    }

    public static void onToolSwitchPre() {
        Minecraft mc = Minecraft.getInstance();
        context = Context.TOOL_SWITCH;
        trackingCall = Configs.Generic.TOOL_SWITCH_BACK.getBooleanValue()
                && mc.player != null
                && mc.options.keyAttack.isDown();

        if (trackingCall) {
            selectedBeforeCall = selected(mc);
            swapsBeforeCall = swaps.size();
        }
    }

    public static void onToolSwitchPost() {
        endSwitchCall();
    }

    /** @return whether this Tweakeroo call actually changed the selection or moved an item */
    private static boolean endSwitchCall() {
        Minecraft mc = Minecraft.getInstance();
        context = Context.NONE;

        if (!trackingCall || mc.player == null) {
            trackingCall = false;
            return false;
        }

        trackingCall = false;
        int now = selected(mc);
        boolean changed = now != selectedBeforeCall || swaps.size() != swapsBeforeCall;

        if (!active && changed) {
            active = true;
            originalSelected = selectedBeforeCall;
        }

        if (active) {
            expectedSelected = now;
        }

        return changed;
    }

    public static void onDurabilitySwapPre() {
        context = Context.DURABILITY;
    }

    public static void onDurabilitySwapPost() {
        context = Context.NONE;
        Minecraft mc = Minecraft.getInstance();

        if (active && mc.player != null) {
            // A hotbar-to-hotbar durability swap only changes the selection; that's still Tweakeroo, not the player.
            expectedSelected = selected(mc);
        }
    }

    /** Called for every {@code MultiPlayerGameMode#handleInventoryMouseClick}. */
    public static void onInventoryClick(int containerId, int slotId, int button, ClickType clickType) {
        switch (context) {
            case RESTORING, DURABILITY -> { }
            case TOOL_SWITCH -> {
                if (!trackingCall) {
                    return;
                }

                Minecraft mc = Minecraft.getInstance();

                if (clickType == ClickType.SWAP && button >= 0 && button <= 8
                        && mc.player != null && containerId == mc.player.inventoryMenu.containerId) {
                    swaps.add(new int[] { slotId, button });
                } else {
                    invalid = true;
                }
            }
            case NONE -> {
                if (active) {
                    invalid = true;
                }
            }
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (!active && swaps.isEmpty()) {
            return;
        }

        if (mc.player == null || mc.gameMode == null || !Configs.Generic.TOOL_SWITCH_BACK.getBooleanValue()) {
            reset();
            return;
        }

        if (!active) {
            return;
        }

        if (mc.options.keyAttack.isDown()) {
            releasedTicks = 0;
            return;
        }

        // Swapping the weapon out resets the attack cooldown, so give the player a moment to click again.
        int delay = weaponInSession ? Configs.Generic.WEAPON_SWITCH_BACK_DELAY.getIntegerValue() : 0;

        if (releasedTicks++ < delay) {
            return;
        }

        // Wait until no screen is open, so the SWAP clicks hit the player's own inventory menu.
        if (mc.screen != null || mc.player.containerMenu != mc.player.inventoryMenu) {
            return;
        }

        if (!invalid) {
            context = Context.RESTORING;
            int containerId = mc.player.inventoryMenu.containerId;

            for (int i = swaps.size() - 1; i >= 0; i--) {
                int[] swap = swaps.get(i);
                mc.gameMode.handleInventoryMouseClick(containerId, swap[0], swap[1], ClickType.SWAP, mc.player);
            }

            context = Context.NONE;
        }

        // Leave the selection alone if the player scrolled to another slot themselves.
        if (selected(mc) == expectedSelected && originalSelected >= 0 && originalSelected <= 8) {
            ((InventoryAccessor) mc.player.getInventory()).setSelected(originalSelected);
        }

        reset();
    }

    private static int selected(Minecraft mc) {
        return ((InventoryAccessor) mc.player.getInventory()).getSelected();
    }

    private static void reset() {
        active = false;
        invalid = false;
        originalSelected = -1;
        expectedSelected = -1;
        weaponInSession = false;
        releasedTicks = 0;
        swaps.clear();
    }
}

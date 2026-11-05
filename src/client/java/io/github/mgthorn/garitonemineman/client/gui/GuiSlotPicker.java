package io.github.mgthorn.garitonemineman.client.gui;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.Reference;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.feature.ChestQuickDepositHandler;

/**
 * Click-to-toggle picker for a set of player inventory slots (0-8 hotbar, 9-35 main inventory), laid
 * out like the vanilla inventory: the three main rows on top, the hotbar below. Shows the player's real
 * items (when in a world) under a coloured overlay for the marked slots. Saves on every click.
 *
 * Used instead of typing slot numbers into {@link Configs.Generic#QUICK_DEPOSIT_DISABLED_SLOTS} and
 * {@link Configs.Generic#AUTO_EAT_PICKABLE_SLOTS}; see {@link #quickDepositSlots} and {@link #autoEatSlots}.
 */
public class GuiSlotPicker extends GuiBase {
    private static final int SLOT = 20;
    private static final int GRID_Y = 56;
    private static final int HOTBAR_GAP = 6;

    private final String description;
    private final String markedMeaning;
    private final int markedColor;
    private final boolean hotbarOnly;
    private final Supplier<Set<Integer>> loader;
    private final Consumer<Set<Integer>> saver;
    private final Set<Integer> marked = new TreeSet<>();

    private GuiSlotPicker(String title, String description, String markedMeaning, int markedColor, boolean hotbarOnly,
                          Supplier<Set<Integer>> loader, Consumer<Set<Integer>> saver, @Nullable Screen parent) {
        this.title = title;
        this.description = description;
        this.markedMeaning = markedMeaning;
        this.markedColor = markedColor;
        this.hotbarOnly = hotbarOnly;
        this.loader = loader;
        this.saver = saver;
        this.setParent(parent);
        this.marked.addAll(loader.get());
    }

    /** Slots Quick Deposit and store trips never empty. */
    public static GuiSlotPicker quickDepositSlots(@Nullable Screen parent) {
        return new GuiSlotPicker("Quick deposit slots",
                "Click a slot to toggle it. Red slots are never put into storage (quick deposit and store trips).",
                "§cRed§r = never deposited", 0x90FF3030, false,
                ChestQuickDepositHandler::parseDisabledSlots,
                slots -> Configs.Generic.QUICK_DEPOSIT_DISABLED_SLOTS.setValueFromString(toRanges(slots)),
                parent);
    }

    /** Hotbar slots easyEat (and autoEat / Hungry Mineman) may switch to for eating. */
    public static GuiSlotPicker autoEatSlots(@Nullable Screen parent) {
        return new GuiSlotPicker("Auto eat slots",
                "Click a hotbar slot to toggle it. Food is moved into the green slots for eating, checked left to right.",
                "§aGreen§r = may be used for eating", 0x9030FF30, true,
                GuiSlotPicker::parseAutoEatSlots,
                slots -> Configs.Generic.AUTO_EAT_PICKABLE_SLOTS.setValueFromString(toAutoEatString(slots)),
                parent);
    }

    @Override
    public void initGui() {
        super.initGui();

        int x = this.gridX();
        int y = this.gridBottom() + 12;

        ButtonGeneric all = new ButtonGeneric(x, y, 50, 20, "All");
        this.addButton(all, (btn, mb) -> this.setAll(true));
        ButtonGeneric none = new ButtonGeneric(x + 52, y, 50, 20, "None");
        this.addButton(none, (btn, mb) -> this.setAll(false));

        int backWidth = 60;
        ButtonGeneric back = new ButtonGeneric(x + 9 * SLOT - backWidth, y, backWidth, 20, "Back");
        this.addButton(back, (btn, mb) -> GuiBase.openGui(this.getParent()));
    }

    private int gridX() {
        return (this.width - 9 * SLOT) / 2;
    }

    private int hotbarY() {
        return this.hotbarOnly ? GRID_Y : GRID_Y + 3 * SLOT + HOTBAR_GAP;
    }

    private int gridBottom() {
        return this.hotbarY() + SLOT;
    }

    /** Inventory index (0-35) at the screen position, or -1. */
    private int slotAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - this.gridX()) / SLOT);

        if (col < 0 || col > 8) {
            return -1;
        }

        int hotbarY = this.hotbarY();

        if (mouseY >= hotbarY && mouseY < hotbarY + SLOT) {
            return col;
        }

        if (this.hotbarOnly == false && mouseY >= GRID_Y && mouseY < GRID_Y + 3 * SLOT) {
            int row = (int) Math.floor((mouseY - GRID_Y) / SLOT);
            return 9 + row * 9 + col;
        }

        return -1;
    }

    private int slotX(int index) {
        return this.gridX() + (index % 9) * SLOT;
    }

    private int slotY(int index) {
        return index < 9 ? this.hotbarY() : GRID_Y + ((index - 9) / 9) * SLOT;
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        int slot = this.slotAt(click.x(), click.y());

        if (slot != -1 && (click.button() == InputConstants.MOUSE_BUTTON_LEFT || click.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            if (this.marked.remove(slot) == false) {
                this.marked.add(slot);
            }

            this.save();
            return true;
        }

        return super.onMouseClicked(click, doubleClick);
    }

    private void setAll(boolean value) {
        this.marked.clear();

        if (value) {
            for (int i = 0; i < (this.hotbarOnly ? 9 : 36); i++) {
                this.marked.add(i);
            }
        }

        this.save();
    }

    private void save() {
        this.saver.accept(this.marked);
        ConfigManager.getInstance().onConfigsChanged(Reference.MOD_ID);
        this.marked.clear();
        this.marked.addAll(this.loader.get()); // shows exactly what was stored
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        int gx = this.gridX();
        this.drawStringWithShadow(ctx, this.description, (this.width - this.getStringWidth(this.description)) / 2, 30, 0xFFE0E0E0);
        this.drawStringWithShadow(ctx, this.markedMeaning, (this.width - this.getStringWidth(this.markedMeaning)) / 2, 42, 0xFFFFFFFF);

        if (this.hotbarOnly == false) {
            RenderUtils.drawRect(ctx, gx - 2, GRID_Y - 2, 9 * SLOT + 4, 3 * SLOT + 4, 0xFF8B8B8B);
        }

        RenderUtils.drawRect(ctx, gx - 2, this.hotbarY() - 2, 9 * SLOT + 4, SLOT + 4, 0xFF8B8B8B);
        int hovered = this.slotAt(mouseX, mouseY);

        for (int i = 0; i < (this.hotbarOnly ? 9 : 36); i++) {
            int x = this.slotX(i);
            int y = this.slotY(i);
            RenderUtils.drawRect(ctx, x + 1, y + 1, SLOT - 2, SLOT - 2, 0xFF373737);

            if (this.mc.player != null) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);

                if (stack.isEmpty() == false) {
                    ctx.renderItem(stack, x + 2, y + 2);
                }
            }

            if (this.marked.contains(i)) {
                RenderUtils.drawRect(ctx, x + 1, y + 1, SLOT - 2, SLOT - 2, this.markedColor);
            }

            if (i == hovered) {
                RenderUtils.drawRect(ctx, x + 1, y + 1, SLOT - 2, SLOT - 2, 0x40FFFFFF);
            }

            if (i < 9) {
                // Hotbar number, as used by the hotbar keys
                this.drawStringWithShadow(ctx, String.valueOf(i + 1), x + 2, y + 2, 0xFFA0A0A0);
            }
        }
    }

    private static Set<Integer> parseAutoEatSlots() {
        Set<Integer> slots = new TreeSet<>();

        for (String part : Configs.Generic.AUTO_EAT_PICKABLE_SLOTS.getStringValue().split(",")) {
            try {
                int slot = Integer.parseInt(part.trim()) - 1;

                if (slot >= 0 && slot <= 8) {
                    slots.add(slot);
                }
            }
            catch (NumberFormatException ignored) {}
        }

        return slots;
    }

    /** Hotbar slots as 1-9 numbers, left to right ("7,8,9"). None marked stores slot 9, which EasyEat falls back to anyway. */
    private static String toAutoEatString(Set<Integer> slots) {
        if (slots.isEmpty()) {
            return "9";
        }

        StringBuilder sb = new StringBuilder();

        for (int slot : new TreeSet<>(slots)) {
            if (sb.length() > 0) {
                sb.append(',');
            }

            sb.append(slot + 1);
        }

        return sb.toString();
    }

    /** Inventory indices as compact ranges ("0-8,9,17,18,26,27,35"). */
    static String toRanges(Set<Integer> slots) {
        StringBuilder sb = new StringBuilder();
        Integer start = null;
        Integer prev = null;

        for (int slot : new TreeSet<>(slots)) {
            if (start == null) {
                start = slot;
            }
            else if (slot != prev + 1) {
                appendRange(sb, start, prev);
                start = slot;
            }

            prev = slot;
        }

        if (start != null) {
            appendRange(sb, start, prev);
        }

        return sb.toString();
    }

    private static void appendRange(StringBuilder sb, int from, int to) {
        if (sb.length() > 0) {
            sb.append(',');
        }

        sb.append(from == to ? String.valueOf(from) : from + "-" + to);
    }
}

package io.github.mgthorn.garitonemineman.client.gui;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.mgthorn.garitonemineman.client.compat.baritone.BaritoneController;
import io.github.mgthorn.garitonemineman.client.feature.SmartMinemanHandler;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry;
import io.github.mgthorn.garitonemineman.client.storage.StorageInventory;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointRescanner;

/**
 * One storage point: its storage blocks on the left (priority order, Use toggle, slot rules), and a
 * fixed panel on the right with name, Active, Rendering, Locked, area, origin and the actions. The
 * "Contents" tab (the one a storage point opens with) switches to {@link GuiStoragePointContents}.
 */
public class GuiStoragePointConfiguration extends GuiListBase<StorageBlockEntry, WidgetStorageBlockEntry, WidgetListStorageBlocks> {
    private static final int PANEL_WIDTH = 140;
    private static final int LIST_Y = 62;

    private final StoragePoint storagePoint;
    private GuiTextFieldGeneric textFieldRename;
    private GuiTextFieldInteger textFieldX;
    private GuiTextFieldInteger textFieldY;
    private GuiTextFieldInteger textFieldZ;
    private ButtonGeneric nudgeButtonX;
    private ButtonGeneric nudgeButtonY;
    private ButtonGeneric nudgeButtonZ;
    private ButtonGeneric moveToPlayerButton;

    public GuiStoragePointConfiguration(StoragePoint storagePoint) {
        super(10, LIST_Y);
        this.storagePoint = storagePoint;
        this.title = "Configure Storage Point";
        // Picks up new and removed chests (and, in singleplayer, the contents) before showing the list
        StoragePointRescanner.rescan(storagePoint);
    }

    private boolean isHere() {
        return StoragePointManager.getInstance().isInCurrentDimension(this.storagePoint);
    }

    @Override
    protected int getBrowserWidth() {
        return this.getScreenWidth() - PANEL_WIDTH - 30;
    }

    @Override
    protected int getBrowserHeight() {
        return this.getScreenHeight() - LIST_Y - 10;
    }

    @Override
    protected WidgetListStorageBlocks createListWidget(int listX, int listY) {
        return new WidgetListStorageBlocks(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.storagePoint::getStorageBlocks, this);
    }

    @Override
    public void initGui() {
        super.initGui();

        // Tabs, top left: Contents (opened first) | Storage blocks (this screen)
        this.createButton(10, 22, 70, "Contents", (btn, mb) -> {
            GuiStoragePointContents gui = new GuiStoragePointContents(this.storagePoint);
            gui.setParent(this.getParent());
            GuiBase.openGui(gui);
        });
        ButtonGeneric chestsTab = new ButtonGeneric(82, 22, 70, 20, "Storage blocks");
        chestsTab.setEnabled(false);
        this.addButton(chestsTab, (btn, mb) -> {});

        int listRight = 10 + this.getBrowserWidth();
        int x = listRight;
        x -= this.createButtonRightAligned(x, 22, "All §cOFF§r", (btn, mb) -> this.setAllUseEnabled(false)) + 2;
        this.createButtonRightAligned(x, 22, "All §aON§r", (btn, mb) -> this.setAllUseEnabled(true));

        this.createSidePanel(this.getScreenWidth() - PANEL_WIDTH - 10);
    }

    private void createSidePanel(int x) {
        int w = PANEL_WIDTH;
        int half = (w - 2) / 2;
        int y = 22;

        this.textFieldRename = new GuiTextFieldGeneric(x, y + 2, w - 44, 16, this.font);
        this.textFieldRename.setMaxLengthWrapper(256);
        this.textFieldRename.setValueWrapper(this.storagePoint.getName());
        this.addTextField(this.textFieldRename, null, TextFieldType.STRING);
        this.createButton(x + w - 42, y, 42, "Rename", (btn, mb) ->
                StoragePointManager.getInstance().renameStoragePoint(this.storagePoint, this.textFieldRename.getValueWrapper()));
        y += 24;

        boolean active = StoragePointManager.getInstance().isActive(this.storagePoint);
        ButtonGeneric activeButton = new ButtonGeneric(x, y, w, 20, active ? "§a★ Active storage point§r" : "Set as active storage point",
                "The active storage point is where Smart Mineman stores items,",
                "and the one other mods (Shopping Materialist) count. One per world.");
        this.addButton(activeButton, (btn, mb) -> {
            StoragePointManager.getInstance().setActiveStoragePoint(active ? null : this.storagePoint);
            this.initGui();
        });
        y += 22;

        this.createToggleButton(x, y, half, "Render", this.storagePoint.isEnabled(), enabled -> {
            this.storagePoint.setEnabled(enabled);
            StoragePointManager.getInstance().save();
        });
        this.createToggleButton(x + half + 2, y, half, "Locked", this.storagePoint.isLocked(), locked -> {
            this.storagePoint.setLocked(locked);
            this.setPositionControlsEnabled(!locked);
            StoragePointManager.getInstance().save();
        });
        y += 26;

        int sx = Math.abs(this.storagePoint.getCorner1X() - this.storagePoint.getCorner2X()) + 1;
        int sy = Math.abs(this.storagePoint.getCorner1Y() - this.storagePoint.getCorner2Y()) + 1;
        int sz = Math.abs(this.storagePoint.getCorner1Z() - this.storagePoint.getCorner2Z()) + 1;
        this.addLabel(x, y, w, 10, 0xFFAAAAAA, "Area: " + sx + " x " + sy + " x " + sz + "  ("
                + WidgetStoragePointEntry.dimensionName(this.storagePoint.getDimensionId()) + ")");
        y += 14;

        this.addLabel(x, y, w, 10, 0xFFFFFFFF, "Origin (where Baritone walks to)");
        y += 12;

        CoordinateWidgets cx = this.createCoordinateRow(x, y, "X:", this.storagePoint.getX(), v -> {
            this.storagePoint.setX(v);
            StoragePointManager.getInstance().save();
        });
        this.textFieldX = cx.textField();
        this.nudgeButtonX = cx.nudgeButton();
        y += 18;

        CoordinateWidgets cy = this.createCoordinateRow(x, y, "Y:", this.storagePoint.getY(), v -> {
            this.storagePoint.setY(v);
            StoragePointManager.getInstance().save();
        });
        this.textFieldY = cy.textField();
        this.nudgeButtonY = cy.nudgeButton();
        y += 18;

        CoordinateWidgets cz = this.createCoordinateRow(x, y, "Z:", this.storagePoint.getZ(), v -> {
            this.storagePoint.setZ(v);
            StoragePointManager.getInstance().save();
        });
        this.textFieldZ = cz.textField();
        this.nudgeButtonZ = cz.nudgeButton();
        y += 20;

        this.moveToPlayerButton = new ButtonGeneric(x, y, w, 20, "Move origin to player");
        this.addButton(this.moveToPlayerButton, (btn, mb) -> this.moveOriginToPlayer());
        this.setPositionControlsEnabled(!this.storagePoint.isLocked());
        y += 24;

        ButtonGeneric rescan = new ButtonGeneric(x, y, half, 20, "Rescan",
                "Look for new and removed storage blocks in the area now.",
                "Also happens by itself every few seconds while you're within 32 blocks.");
        this.addButton(rescan, (btn, mb) -> {
            if (StoragePointRescanner.rescan(this.storagePoint)) {
                this.getListWidget().refreshEntries();
                this.initGui();
            }
            else {
                this.addMessage(MessageType.WARNING, "Can't rescan: the area is in another dimension or not loaded");
            }
        });
        ButtonGeneric store = new ButtonGeneric(x + half + 2, y, half, 20, "Store Items",
                "Walk here now and put the inventory into this storage point.");
        store.setEnabled(this.isHere());
        this.addButton(store, (btn, mb) -> {
            SmartMinemanHandler.triggerManualStoreItems(this.storagePoint);
            this.closeGui(false);
        });
        y += 22;

        ButtonGeneric goTo = new ButtonGeneric(x, y, half, 20, "Go To");
        goTo.setEnabled(this.isHere());
        this.addButton(goTo, (btn, mb) -> {
            BaritoneController.travelTo(this.originPos());
            this.closeGui(false);
        });
        ButtonGeneric goal = new ButtonGeneric(x + half + 2, y, half, 20, "Set Goal");
        goal.setEnabled(this.isHere());
        this.addButton(goal, (btn, mb) -> BaritoneController.sendGoal(this.originPos()));

        String backLabel = "Back";
        int backWidth = this.getStringWidth(backLabel) + 20;
        ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - backWidth - 10, this.getScreenHeight() - 22, backWidth, 20, backLabel);
        this.addButton(backButton, (btn, mb) -> this.closeGui(true));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        StorageInventory.Summary summary = StorageInventory.getSummary(this.storagePoint);
        String info = summary.chests() + " storage blocks (" + summary.usableChests() + " used), "
                + (summary.knownChests() > 0 ? Math.round(summary.fillLevel() * 100) + "% full" : "contents unknown")
                + (summary.knownChests() < summary.chests() ? ", " + (summary.chests() - summary.knownChests()) + " never opened" : "");
        this.drawStringWithShadow(ctx, info, 12, 48, 0xFFAAAAAA);
    }

    private BlockPos originPos() {
        return new BlockPos(this.storagePoint.getX(), this.storagePoint.getY(), this.storagePoint.getZ());
    }

    private void moveOriginToPlayer() {
        if (this.mc.player == null || this.isHere() == false) {
            return;
        }

        var pos = this.mc.player.blockPosition();
        this.textFieldX.setValueWrapper(String.valueOf(pos.getX()));
        this.textFieldY.setValueWrapper(String.valueOf(pos.getY()));
        this.textFieldZ.setValueWrapper(String.valueOf(pos.getZ()));
        this.storagePoint.setX(pos.getX());
        this.storagePoint.setY(pos.getY());
        this.storagePoint.setZ(pos.getZ());
        StoragePointManager.getInstance().save();
    }

    private record CoordinateWidgets(GuiTextFieldInteger textField, ButtonGeneric nudgeButton) {}

    private CoordinateWidgets createCoordinateRow(int x, int y, String axisLabel, int value, IntConsumer onValueChange) {
        this.addLabel(x, y, 12, 16, 0xFFFFFFFF, axisLabel);
        int offset = 14;

        GuiTextFieldInteger textField = new GuiTextFieldInteger(x + offset, y + 1, PANEL_WIDTH - offset - 20, 14, this.font);
        textField.setValueWrapper(String.valueOf(value));
        this.addTextField(textField, tf -> {
            onValueChange.accept(parseIntSafe(tf.getValueWrapper()));
            return false;
        }, TextFieldType.STRING);

        ButtonGeneric nudge = new ButtonGeneric(x + PANEL_WIDTH - 16, y, MaLiLibIcons.BTN_PLUSMINUS_16,
                "Left click to increase", "Right click to decrease", "Shift and/or Alt to increase the step size");
        this.addButton(nudge, (btn, mouseButton) -> {
            int amount = mouseButton == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1;

            if (isShiftDown()) {
                amount *= 16;
            }
            if (isAltDown()) {
                amount *= 8;
            }

            int updated = parseIntSafe(textField.getValueWrapper()) + amount;
            textField.setValueWrapper(String.valueOf(updated));
            onValueChange.accept(updated);
        });

        return new CoordinateWidgets(textField, nudge);
    }

    private void setPositionControlsEnabled(boolean enabled) {
        this.textFieldX.setEditable(enabled);
        this.textFieldY.setEditable(enabled);
        this.textFieldZ.setEditable(enabled);
        this.nudgeButtonX.setEnabled(enabled);
        this.nudgeButtonY.setEnabled(enabled);
        this.nudgeButtonZ.setEnabled(enabled);
        this.moveToPlayerButton.setEnabled(enabled && this.isHere());
    }

    private static int parseIntSafe(String text) {
        try {
            return Integer.parseInt(text);
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String toggleButtonText(String label, boolean isOn) {
        return label + ": " + (isOn ? "§aON§r" : "§cOFF§r");
    }

    private void createToggleButton(int x, int y, int width, String label, boolean initialOn, Consumer<Boolean> onToggle) {
        boolean[] state = { initialOn };
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, toggleButtonText(label, state[0]));
        this.addButton(button, (btn, mouseButton) -> {
            state[0] = !state[0];
            button.setDisplayString(toggleButtonText(label, state[0]));
            onToggle.accept(state[0]);
        });
    }

    private int createButton(int x, int y, int width, String label, IButtonActionListener listener) {
        if (width == -1) {
            width = this.getStringWidth(label) + 10;
        }

        this.addButton(new ButtonGeneric(x, y, width, 20, label), listener);
        return width;
    }

    private int createButtonRightAligned(int xRight, int y, String label, IButtonActionListener listener) {
        int width = this.getStringWidth(label) + 10;
        this.addButton(new ButtonGeneric(xRight - width, y, width, 20, label), listener);
        return width;
    }

    private void setAllUseEnabled(boolean enabled) {
        for (StorageBlockEntry entry : this.storagePoint.getStorageBlocks()) {
            entry.setUseEnabled(enabled);
        }

        StoragePointManager.getInstance().save();

        if (this.getListWidget() != null) {
            this.getListWidget().refreshEntries();
        }
    }
}

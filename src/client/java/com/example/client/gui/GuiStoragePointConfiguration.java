package com.example.client.gui;

import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.wrappers.TextFieldType;
import fi.dy.masa.malilib.util.GuiUtils;
import com.example.client.compat.baritone.BaritoneController;
import com.example.client.feature.SmartMinemanHandler;
import com.example.client.storage.SingleplayerContentReader;
import com.example.client.storage.StorageBlockEntry;
import com.example.client.storage.StorageBlockScanner;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

/**
 * Visual clone of Litematica's "Configure schematic placement" screen (GuiPlacementConfiguration),
 * wired up to a StoragePoint instead of a SchematicPlacement. Every button/field is a dummy for now.
 */
public class GuiStoragePointConfiguration extends GuiListBase<StorageBlockEntry, WidgetStorageBlockEntry, WidgetListStorageBlocks> {
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
        super(10, 62);
        this.storagePoint = storagePoint;
        this.title = "Configure Storage Point";

        if (this.mc.level != null) {
            BlockPos corner1 = new BlockPos(storagePoint.getCorner1X(), storagePoint.getCorner1Y(), storagePoint.getCorner1Z());
            BlockPos corner2 = new BlockPos(storagePoint.getCorner2X(), storagePoint.getCorner2Y(), storagePoint.getCorner2Z());
            storagePoint.setStorageBlocks(StorageBlockScanner.scan(this.mc.level, corner1, corner2, storagePoint.getStorageBlocks()));

            // Populate contents for any block not opened yet this session (singleplayer only; a no-op
            // on a remote server or if the block entity isn't currently loaded).
            for (StorageBlockEntry entry : storagePoint.getStorageBlocks()) {
                if (entry.hasContents() == false) {
                    entry.setContents(SingleplayerContentReader.readDirectMerged(entry));
                }
            }

            StoragePointManager.getInstance().save();
        }
    }

    @Override
    protected int getBrowserWidth() {
        return this.getScreenWidth() - 150;
    }

    @Override
    protected int getBrowserHeight() {
        return this.getScreenHeight() - 84;
    }

    @Override
    protected WidgetListStorageBlocks createListWidget(int listX, int listY) {
        return new WidgetListStorageBlocks(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.storagePoint.getStorageBlocks(), this);
    }

    @Override
    public void initGui() {
        super.initGui();

        int scaledWidth = GuiUtils.getScaledWindowWidth();
        int width = Math.min(300, scaledWidth - 200);
        int x = 12;
        int y = 22;

        this.textFieldRename = new GuiTextFieldGeneric(x, y + 2, width, 16, this.font);
        this.textFieldRename.setMaxLengthWrapper(256);
        this.textFieldRename.setValueWrapper(this.storagePoint.getName());
        this.addTextField(this.textFieldRename, null, TextFieldType.STRING);
        ButtonGeneric renameButton = new ButtonGeneric(x + width + 4, y, -1, 20, "Rename");
        this.addButton(renameButton, (btn, mouseButton) ->
                StoragePointManager.getInstance().renameStoragePoint(this.storagePoint, this.textFieldRename.getValueWrapper()));

        this.addLabel(x + 2, y + 26, -1, 20, 0xFFFFFFFF, "Storage-Blocks : " + this.storagePoint.getStorageBlocks().size());

        x = scaledWidth - 154;
        x -= this.createButtonRightAligned(x, y + 26, "All §cOFF§r", (btn, mb) -> this.setAllUseEnabled(false)) + 2;
        this.createButtonRightAligned(x, y + 26, "All §aOn§r", (btn, mb) -> this.setAllUseEnabled(true));

        width = 120;
        x = this.getScreenWidth() - width - 10;

        this.createToggleButton(x, y, width, "Locked", this.storagePoint.isLocked(), locked -> {
            this.storagePoint.setLocked(locked);
            this.setPositionControlsEnabled(!locked);
            StoragePointManager.getInstance().save();
        });
        y += 21;

        this.createToggleButton(x, y, width, "Rendering", this.storagePoint.isEnabled(), enabled -> {
            this.storagePoint.setEnabled(enabled);
            StoragePointManager.getInstance().save();
        });
        y += 21;
        x += 2;

        y += 10;
        this.addLabel(x, y, width, 10, 0xFFAAAAAA, "Pos1: " + this.storagePoint.getCorner1X()
                + ", " + this.storagePoint.getCorner1Y() + ", " + this.storagePoint.getCorner1Z());
        y += 10;
        this.addLabel(x, y, width, 10, 0xFFAAAAAA, "Pos2: " + this.storagePoint.getCorner2X()
                + ", " + this.storagePoint.getCorner2Y() + ", " + this.storagePoint.getCorner2Z());
        y += 24;

        this.addLabel(x, y, width, 20, 0xFFFFFFFF, "StoragePoint origin");
        y += 14;

        CoordinateWidgets cx = this.createCoordinateRow(x, y, 70, "X:", this.storagePoint.getX(), v -> {
            this.storagePoint.setX(v);
            StoragePointManager.getInstance().save();
        });
        this.textFieldX = cx.textField();
        this.nudgeButtonX = cx.nudgeButton();
        y += 18;

        CoordinateWidgets cy = this.createCoordinateRow(x, y, 70, "Y:", this.storagePoint.getY(), v -> {
            this.storagePoint.setY(v);
            StoragePointManager.getInstance().save();
        });
        this.textFieldY = cy.textField();
        this.nudgeButtonY = cy.nudgeButton();
        y += 18;

        CoordinateWidgets cz = this.createCoordinateRow(x, y, 70, "Z:", this.storagePoint.getZ(), v -> {
            this.storagePoint.setZ(v);
            StoragePointManager.getInstance().save();
        });
        this.textFieldZ = cz.textField();
        this.nudgeButtonZ = cz.nudgeButton();
        y += 20;
        x -= 2;

        this.moveToPlayerButton = new ButtonGeneric(x, y, width, 20, "Move to player");
        this.addButton(this.moveToPlayerButton, (btn, mouseButton) -> {
            if (this.mc.player == null) {
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
        });

        this.setPositionControlsEnabled(!this.storagePoint.isLocked());

        // Move these buttons to the bottom (left) of the screen, if the height isn't enough for them to fit below the others
        if (GuiUtils.getScaledWindowHeight() < 328) {
            x = 10;
            y = this.getScreenHeight() - 22;

            x += this.createButton(x, y, -1, "Set Goal", (btn, mb) -> this.sendBaritoneGoal()) + 1;
            x += this.createButton(x, y, -1, "Go To", (btn, mb) -> this.sendBaritoneGoTo()) + 1;
            this.createButton(x, y, -1, "Store Items", (btn, mb) -> this.triggerStoreItems());
        }
        else {
            y += 32;
            this.createButton(x, y, width, "Set Goal", (btn, mb) -> this.sendBaritoneGoal());
            y += 21;

            this.createButton(x, y, width, "Go To", (btn, mb) -> this.sendBaritoneGoTo());
            y += 21;

            this.createButton(x, y, width, "Store Items", (btn, mb) -> this.triggerStoreItems());
        }

        String backLabel = "Storage Points";
        int backWidth = this.getStringWidth(backLabel) + 10;
        ButtonGeneric backButton = new ButtonGeneric(this.getScreenWidth() - backWidth - 10,
                this.getScreenHeight() - 22, backWidth, 20, backLabel);
        this.addButton(backButton, (btn, mouseButton) -> this.closeGui(true));
    }

    private record CoordinateWidgets(GuiTextFieldInteger textField, ButtonGeneric nudgeButton) {}

    private CoordinateWidgets createCoordinateRow(int x, int y, int width, String axisLabel, int value,
            java.util.function.IntConsumer onValueChange) {
        this.addLabel(x, y, width, 20, 0xFFFFFFFF, axisLabel);
        int offset = this.getStringWidth(axisLabel) + 4;

        GuiTextFieldInteger textField = new GuiTextFieldInteger(x + offset, y + 2, width, 14, this.font);
        textField.setValueWrapper(String.valueOf(value));
        this.addTextField(textField, tf -> {
            onValueChange.accept(parseIntSafe(tf.getValueWrapper()));
            return false;
        }, TextFieldType.STRING);

        ButtonGeneric nudge = new ButtonGeneric(x + 85, y + 1, MaLiLibIcons.BTN_PLUSMINUS_16,
                "Left click to increase", "Right click to decrease", "Shift and/or Alt to increase the step size");
        this.addButton(nudge, (btn, mouseButton) -> {
            int amount = mouseButton == 1 ? -1 : 1;

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
        this.moveToPlayerButton.setEnabled(enabled);
    }

    private static int parseIntSafe(String text) {
        try {
            return Integer.parseInt(text);
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private String toggleButtonText(String label, boolean isOn) {
        String status = isOn ? "§aON§r" : "§cOFF§r";
        return label + ": " + status;
    }

    private ButtonGeneric createToggleButton(int x, int y, int width, String label, boolean initialOn,
            java.util.function.Consumer<Boolean> onToggle) {
        boolean[] state = { initialOn };
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, this.toggleButtonText(label, state[0]));
        this.addButton(button, (btn, mouseButton) -> {
            state[0] = !state[0];
            button.setDisplayString(this.toggleButtonText(label, state[0]));

            if (onToggle != null) {
                onToggle.accept(state[0]);
            }
        });
        return button;
    }

    private int createButton(int x, int y, int width, String label, IButtonActionListener listener) {
        if (width == -1) {
            width = this.getStringWidth(label) + 10;
        }

        this.addButton(new ButtonGeneric(x, y, width, 20, label), listener);
        return width;
    }

    private void sendBaritoneGoal() {
        BaritoneController.sendGoal(new BlockPos(this.storagePoint.getX(), this.storagePoint.getY(), this.storagePoint.getZ()));
    }

    private void sendBaritoneGoTo() {
        BaritoneController.sendGoTo(new BlockPos(this.storagePoint.getX(), this.storagePoint.getY(), this.storagePoint.getZ()));
        this.closeGui(false);
    }

    private void triggerStoreItems() {
        StoragePointManager.getInstance().setSelectedStoragePoint(this.storagePoint);
        SmartMinemanHandler.triggerManualStoreItems();
        this.closeGui(false);
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

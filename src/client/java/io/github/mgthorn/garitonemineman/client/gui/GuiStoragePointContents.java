package io.github.mgthorn.garitonemineman.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.client.storage.StorageContentReader;
import io.github.mgthorn.garitonemineman.client.storage.StorageInventory;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/**
 * "Contents" tab of a storage point, the first thing its Configure button opens: every item stored in it with its count, summed over all storage
 * blocks, items inside shulker boxes included. Searchable, largest first. Based on the last-known
 * contents, so chests that were never opened aren't included (the line on top says how many).
 */
public class GuiStoragePointContents extends GuiBase {
    private static final int LIST_Y = 66;
    private static final int ROW = 18;

    private final StoragePoint storagePoint;
    private final List<Row> rows = new ArrayList<>();
    private GuiTextFieldGeneric searchField;
    private String searchText = "";
    private int scroll;
    private int builtRevision = -1;

    private record Row(ItemStack icon, String name, String id, long count) {}

    public GuiStoragePointContents(StoragePoint storagePoint) {
        this.storagePoint = storagePoint;
        this.title = "Storage Point: " + storagePoint.getName();
    }

    @Override
    public void initGui() {
        super.initGui();

        // Tabs, top left: Contents (this screen, opened first) | Storage blocks
        ButtonGeneric contentsTab = new ButtonGeneric(10, 22, 70, 20, "Contents");
        contentsTab.setEnabled(false);
        this.addButton(contentsTab, (btn, mb) -> {});
        ButtonGeneric chestsTab = new ButtonGeneric(82, 22, 70, 20, "Storage blocks");
        this.addButton(chestsTab, (btn, mb) -> {
            GuiStoragePointConfiguration gui = new GuiStoragePointConfiguration(this.storagePoint);
            gui.setParent(this.getParent());
            GuiBase.openGui(gui);
        });

        int searchWidth = 150;
        this.searchField = new GuiTextFieldGeneric(this.width - searchWidth - 10, 24, searchWidth, 16, this.font);
        this.searchField.setValue(this.searchText);
        this.searchField.setFocused(true);
        this.addTextField(this.searchField, field -> {
            if (field.getValue().equals(this.searchText) == false) {
                this.searchText = field.getValue();
                this.builtRevision = -1;
                this.scroll = 0;
            }

            return true;
        });

        ButtonGeneric back = new ButtonGeneric(this.width - 50, this.height - 22, 40, 20, "Back");
        this.addButton(back, (btn, mb) -> GuiBase.openGui(this.getParent()));
        ButtonGeneric rescan = GuiStoragePointConfiguration.createRescanButton(this.width - 112, this.height - 22, 60);
        this.addButton(rescan, (btn, mb) -> {
            if (GuiStoragePointConfiguration.rescanWithMessage(this, this.storagePoint)) {
                this.builtRevision = -1;
            }
        });
    }

    private void rebuildIfNeeded() {
        int revision = StoragePointManager.getInstance().getRevision();

        if (revision == this.builtRevision) {
            return;
        }

        this.builtRevision = revision;
        this.rows.clear();
        String filter = this.searchText.trim().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Long> e : StorageInventory.getCounts(this.storagePoint).entrySet()) {
            Identifier key = Identifier.tryParse(e.getKey());
            Item item = key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
            ItemStack icon = item != null ? new ItemStack(item) : ItemStack.EMPTY;
            String name = item != null ? icon.getHoverName().getString() : e.getKey();

            if (filter.isEmpty() || name.toLowerCase(Locale.ROOT).contains(filter) || e.getKey().contains(filter)) {
                this.rows.add(new Row(icon, name, e.getKey(), e.getValue()));
            }
        }
    }

    private int visibleRows() {
        return Math.max(1, (this.height - LIST_Y - 28) / ROW);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, this.rows.size() - this.visibleRows());
        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(verticalAmount) * 3));
        return true;
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        this.rebuildIfNeeded();
        StorageInventory.Summary summary = StorageInventory.getSummary(this.storagePoint);
        String info = this.rows.size() + " different items   " + summary.knownChests() + "/" + summary.chests()
                + " storage blocks known" + (summary.knownChests() > 0 ? "   " + Math.round(summary.fillLevel() * 100) + "% full" : "")
                + (summary.knownChests() < summary.chests() && StorageContentReader.getSource() == StorageContentReader.Source.NONE
                        ? "   §7(open the others once to count them)§r" : "");
        this.drawStringWithShadow(ctx, info, 12, 50, 0xFFAAAAAA);

        // Bottom line, left of the Rescan button: where contents come from and when the last rescan ran
        String status = StorageContentReader.describe() + "   " + GuiStoragePointConfiguration.lastRescanText(this.storagePoint);
        boolean noSource = StorageContentReader.getSource() == StorageContentReader.Source.NONE;
        this.drawStringWithShadow(ctx, status, this.width - 118 - this.getStringWidth(status), this.height - 16, noSource ? 0xFFFFAA55 : 0xFF808080);

        int x = 10;
        int w = this.width - 20;
        int visible = this.visibleRows();
        RenderUtils.drawRect(ctx, x, LIST_Y - 2, w, visible * ROW + 4, 0x80000000);

        if (this.rows.isEmpty()) {
            this.drawStringWithShadow(ctx, "Nothing known in this storage point yet", x + 6, LIST_Y + 4, 0xFF808080);
            return;
        }

        for (int i = 0; i < visible && this.scroll + i < this.rows.size(); i++) {
            Row row = this.rows.get(this.scroll + i);
            int y = LIST_Y + i * ROW;

            if ((this.scroll + i) % 2 == 0) {
                RenderUtils.drawRect(ctx, x, y, w, ROW, 0x20FFFFFF);
            }

            if (row.icon().isEmpty() == false) {
                ctx.renderItem(row.icon(), x + 2, y + 1);
            }

            this.drawStringWithShadow(ctx, row.name() + " §8" + row.id(), x + 22, y + 5, 0xFFFFFFFF);
            String count = formatCount(row.count(), row.icon().isEmpty() ? 64 : row.icon().getMaxStackSize());
            this.drawStringWithShadow(ctx, count, x + w - this.getStringWidth(count) - 6, y + 5, 0xFFFFFFFF);
        }

        if (this.rows.size() > visible) {
            String pos = (this.scroll + 1) + "-" + Math.min(this.rows.size(), this.scroll + visible) + " of " + this.rows.size();
            this.drawStringWithShadow(ctx, pos, x, this.height - 16, 0xFF808080);
        }
    }

    /** "1234  (19 x 64 + 18)" */
    private static String formatCount(long count, int stackSize) {
        if (stackSize <= 1 || count < stackSize) {
            return String.valueOf(count);
        }

        long stacks = count / stackSize;
        long rest = count % stackSize;
        return count + "  §7(" + stacks + " x " + stackSize + (rest > 0 ? " + " + rest : "") + ")§r";
    }
}

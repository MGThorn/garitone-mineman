package io.github.mgthorn.garitonemineman.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetColorIndicator;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.Reference;
import io.github.mgthorn.garitonemineman.client.feature.EspColors;

/**
 * Picker for an ESP ID list (blocks or entity types), adapted from Bundle Scroller's pick list editor:
 * a searchable grid of everything on the left, and the configured list on the right.
 * <p>
 * Grid: left-click adds an entry (or moves it back to the top), right-click removes it.
 * Newly selected entries go to the top of the list, so the most recent picks are always first.
 * The grid also shows the last picked entries first (remembered across restarts), so they are easy to find again.
 * With a color config, each list row gets a color button next to its remove button.
 * The list is stored in the existing {@link ConfigStringList}, so it stays editable as text in the config screen too.
 */
public abstract class GuiEspListEditor<T> extends GuiBase {
    private static final int CONTENT_Y = 50;
    private static final int ENTRY_HEIGHT = 20;
    private static final int MAX_RECENT = 256;

    private final ConfigStringList config;
    private final ConfigStringList recentConfig;
    @Nullable private final ConfigStringList colorConfig;
    private final ToIntFunction<String> defaultColor;
    private final int slotSize;
    private final List<T> filteredEntries = new ArrayList<>();
    @Nullable private List<T> allEntries;
    private GuiTextFieldGeneric searchField;
    private String searchText = "";
    private boolean searchFocused = true;
    private int gridScroll;
    private int entryScroll;

    /**
     * @param recentConfig the IDs in the order they were last picked, newest first
     * @param colorConfig the per-ID colors (see {@link EspColors}), or null for no color buttons
     * @param defaultColor the color of an ID that has no entry in {@code colorConfig}
     */
    protected GuiEspListEditor(String title, ConfigStringList config, ConfigStringList recentConfig,
                               @Nullable ConfigStringList colorConfig, ToIntFunction<String> defaultColor,
                               int slotSize, @Nullable Screen parent) {
        this.title = title;
        this.config = config;
        this.recentConfig = recentConfig;
        this.colorConfig = colorConfig;
        this.defaultColor = defaultColor;
        this.slotSize = slotSize;
        this.setParent(parent);
    }

    protected abstract List<T> createAllEntries();

    protected abstract String getId(T entry);

    protected abstract String getName(T entry);

    /** The grid entry for a configured ID, or null if the ID doesn't resolve (typo, removed mod). */
    @Nullable
    protected abstract T fromId(String id);

    /** Draws the icon of an entry (null: unknown ID) into a {@code size} x {@code size} square. */
    protected abstract void drawIcon(GuiContext ctx, @Nullable T entry, int x, int y, int size);

    private List<T> getAllEntries() {
        if (this.allEntries == null) {
            this.allEntries = this.createAllEntries();
        }

        return this.allEntries;
    }

    private void updateFilter() {
        this.updateFilter(false);
    }

    /** Filters by the search text, with the last picked entries first. */
    private void updateFilter(boolean keepScroll) {
        String filter = this.searchText.trim().toLowerCase(Locale.ROOT);
        this.filteredEntries.clear();

        for (T entry : this.getAllEntries()) {
            if (filter.isEmpty()
                    || this.getName(entry).toLowerCase(Locale.ROOT).contains(filter)
                    || this.getId(entry).contains(filter)) {
                this.filteredEntries.add(entry);
            }
        }

        Map<String, Integer> recentIndex = new HashMap<>();
        List<String> recent = this.recentConfig.getStrings();

        for (int i = 0; i < recent.size(); i++) {
            recentIndex.putIfAbsent(recent.get(i), i);
        }

        // Stable sort: entries never picked keep their registry order behind the picked ones
        this.filteredEntries.sort(Comparator.comparingInt(entry -> recentIndex.getOrDefault(this.getId(entry), Integer.MAX_VALUE)));

        this.gridScroll = keepScroll ? Math.min(this.gridScroll, this.getMaxGridScroll()) : 0;
    }

    /** Restores the default list. Counts as picking each default entry, so they move to the front of the grid. */
    private void resetToDefault() {
        List<String> defaults = this.config.getDefaultStrings();
        this.setIds(defaults);

        // In reverse, so the grid shows them in list order
        for (int i = defaults.size() - 1; i >= 0; i--) {
            this.addRecent(defaults.get(i));
        }

        this.updateFilter();
    }

    private void addRecent(String id) {
        List<String> recent = new ArrayList<>(this.recentConfig.getStrings());
        recent.remove(id);
        recent.add(0, id);

        if (recent.size() > MAX_RECENT) {
            recent.subList(MAX_RECENT, recent.size()).clear();
        }

        this.recentConfig.setStrings(recent);
    }

    // ---- The configured list ----

    private List<String> getIds() {
        return this.config.getStrings();
    }

    private boolean isSelected(T entry) {
        return this.getIds().contains(this.getId(entry));
    }

    /** Puts the ID at the top of the list (moving it there if it's already on it). */
    private void select(String id) {
        List<String> ids = new ArrayList<>(this.getIds());
        ids.remove(id);
        ids.add(0, id);
        this.setIds(ids);
    }

    private void deselect(String id) {
        List<String> ids = new ArrayList<>(this.getIds());

        if (ids.remove(id)) {
            this.setIds(ids);
        }
    }

    private void setIds(List<String> ids) {
        this.config.setStrings(ids);
        ConfigManager.getInstance().onConfigsChanged(Reference.MOD_ID);
    }

    // ---- Layout ----

    private int getGridColumns() {
        return Math.max(1, (int) ((this.getScreenWidth() - 30) * 0.55) / this.slotSize);
    }

    private int getGridRows() {
        return Math.max(1, (this.getScreenHeight() - CONTENT_Y - 36) / this.slotSize);
    }

    private int getGridWidth() {
        return this.getGridColumns() * this.slotSize;
    }

    private int getMaxGridScroll() {
        int rows = (this.filteredEntries.size() + this.getGridColumns() - 1) / this.getGridColumns();
        return Math.max(0, rows - this.getGridRows());
    }

    private int getEntriesX() {
        return 10 + this.getGridWidth() + 12;
    }

    private int getEntriesWidth() {
        return this.getScreenWidth() - this.getEntriesX() - 10;
    }

    private int getVisibleEntries() {
        return Math.max(1, (this.getScreenHeight() - CONTENT_Y - 36) / ENTRY_HEIGHT);
    }

    // ---- Widgets ----

    @Override
    public void initGui() {
        super.initGui();

        if (this.allEntries == null) {
            this.updateFilter();
        }

        this.searchField = new GuiTextFieldGeneric(10, 28, this.getGridWidth(), 16, this.font);
        this.searchField.setValue(this.searchText);
        this.searchField.setFocused(this.searchFocused);
        this.addTextField(this.searchField, field -> {
            if (!field.getValue().equals(this.searchText)) {
                this.searchText = field.getValue();
                this.updateFilter();
            }

            return true;
        });

        this.createEntryButtons();
        this.createBottomButtons();
    }

    private void createEntryButtons() {
        List<String> ids = this.getIds();
        this.entryScroll = Math.max(0, Math.min(this.entryScroll, ids.size() - this.getVisibleEntries()));
        int end = Math.min(ids.size(), this.entryScroll + this.getVisibleEntries());
        int right = this.getEntriesX() + this.getEntriesWidth();

        for (int i = this.entryScroll; i < end; i++) {
            String id = ids.get(i);
            int y = CONTENT_Y + (i - this.entryScroll) * ENTRY_HEIGHT + 1;

            this.addButton(new ButtonGeneric(right - 18, y, 18, 18, "x", "Remove"),
                    (btn, mouseButton) -> this.changeList(() -> this.deselect(id)));

            if (this.colorConfig != null) {
                // Opens the MaLiLib color picker; the config is saved when this screen closes
                ConfigStringList colors = this.colorConfig;
                int defaultColor = this.defaultColor.applyAsInt(id);
                Color4f color = Color4f.fromColor(EspColors.get(colors, id, defaultColor));
                this.addWidget(new WidgetColorIndicator(right - 38, y, 18, 18, color,
                        newColor -> EspColors.set(colors, id, newColor, defaultColor)));
            }
        }
    }

    private void createBottomButtons() {
        int y = this.getScreenHeight() - 26;
        ButtonGeneric done = new ButtonGeneric(10, y, this.getStringWidth("Done") + 10, 20, "Done");
        this.addButton(done, (btn, mouseButton) -> GuiBase.openGui(this.getParent()));

        String clearLabel = "Clear list";
        int clearWidth = this.getStringWidth(clearLabel) + 10;
        ButtonGeneric clear = new ButtonGeneric(this.getEntriesX(), y, clearWidth, 20, clearLabel);
        this.addButton(clear, (btn, mouseButton) -> this.changeList(() -> this.setIds(List.of())));

        String resetLabel = "Reset to default";
        ButtonGeneric reset = new ButtonGeneric(this.getEntriesX() + clearWidth + 4, y, this.getStringWidth(resetLabel) + 10, 20, resetLabel);
        this.addButton(reset, (btn, mouseButton) -> this.changeList(this::resetToDefault));
    }

    /**
     * Applies a change to the list and rebuilds the entry buttons, keeping the search state.
     */
    private void changeList(Runnable change) {
        change.run();
        this.searchFocused = this.searchField.isFocused();
        this.initGui();
    }

    // ---- Input ----

    @Nullable
    private T getGridEntryAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - 10) / this.slotSize);
        int row = (int) Math.floor((mouseY - CONTENT_Y) / this.slotSize);

        if (mouseX < 10 || mouseY < CONTENT_Y || col >= this.getGridColumns() || row >= this.getGridRows()) {
            return null;
        }

        int index = (this.gridScroll + row) * this.getGridColumns() + col;
        return index < this.filteredEntries.size() ? this.filteredEntries.get(index) : null;
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (super.onMouseClicked(click, doubleClick)) {
            return true;
        }

        T entry = this.getGridEntryAt(click.x(), click.y());

        if (entry != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            // A new entry is at the top, so scroll there to show it
            this.entryScroll = 0;
            this.changeList(() -> {
                this.select(this.getId(entry));
                this.addRecent(this.getId(entry));
                this.updateFilter(true);
            });
            return true;
        }

        if (entry != null && click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.changeList(() -> this.deselect(this.getId(entry)));
            return true;
        }

        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && mouseY >= CONTENT_Y) {
            int delta = -(int) Math.signum(verticalAmount);

            if (mouseX < this.getEntriesX()) {
                this.gridScroll = Math.max(0, Math.min(this.gridScroll + delta, this.getMaxGridScroll()));
            }
            else {
                this.entryScroll += delta;
                this.changeList(() -> {});
            }

            return true;
        }

        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---- Rendering ----

    // The entry rows are drawn as part of the background, so that their buttons render on top of them
    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);
        this.drawEntries(ctx);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        T hovered = this.drawGrid(ctx, mouseX, mouseY);

        if (hovered != null) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(this.getName(hovered), GuiBase.TXT_GRAY + this.getId(hovered)));
        }
    }

    @Nullable
    private T drawGrid(GuiContext ctx, int mouseX, int mouseY) {
        int cols = this.getGridColumns();
        int rows = this.getGridRows();
        int size = this.slotSize;
        T hovered = this.getGridEntryAt(mouseX, mouseY);

        RenderUtils.drawRect(ctx, 9, CONTENT_Y - 1, cols * size + 2, rows * size + 2, 0xA0000000);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = (this.gridScroll + row) * cols + col;

                if (index >= this.filteredEntries.size()) {
                    break;
                }

                T entry = this.filteredEntries.get(index);
                int x = 10 + col * size;
                int y = CONTENT_Y + row * size;

                if (entry == hovered) {
                    RenderUtils.drawRect(ctx, x, y, size, size, 0x60FFFFFF);
                }

                // Mark entries that are already in the list
                if (this.isSelected(entry)) {
                    RenderUtils.drawOutline(ctx, x, y, size, size, 0xFF55FF55);
                }

                this.drawIcon(ctx, entry, x + 1, y + 1, size - 2);
            }
        }

        String count = this.filteredEntries.size() + " entries - left-click: add, right-click: remove";
        this.drawString(ctx, count, 10, CONTENT_Y + rows * size + 4, 0xFFA0A0A0);

        return hovered;
    }

    private void drawEntries(GuiContext ctx) {
        int x = this.getEntriesX();
        int width = this.getEntriesWidth();
        List<String> ids = this.getIds();

        this.drawStringWithShadow(ctx, "Highlighted (" + ids.size() + "), last added first", x, 33, 0xFFFFFFFF);

        if (ids.isEmpty()) {
            this.drawString(ctx, "Nothing selected - click entries on the left", x + 2, CONTENT_Y + 6, 0xFFA0A0A0);
            return;
        }

        int end = Math.min(ids.size(), this.entryScroll + this.getVisibleEntries());

        for (int i = this.entryScroll; i < end; i++) {
            String id = ids.get(i);
            T entry = this.fromId(id);
            int y = CONTENT_Y + (i - this.entryScroll) * ENTRY_HEIGHT;

            RenderUtils.drawRect(ctx, x, y, width, ENTRY_HEIGHT, (i % 2 == 0) ? 0xA0303030 : 0xA0101010);
            this.drawIcon(ctx, entry, x + 2, y + 2, 16);

            // Unknown IDs stay on the list (in red) so they can still be removed here
            String name = entry != null ? this.getName(entry) : id + " (unknown)";
            int color = entry != null ? 0xFFFFFFFF : 0xFFFF5555;
            int buttonsWidth = this.colorConfig != null ? 42 : 22;
            this.drawStringWithShadow(ctx, this.trimToWidth((i + 1) + ". " + name, width - 22 - buttonsWidth), x + 22, y + 6, color);
        }
    }

    @Override
    public void removed() {
        super.removed();
        // Saves the colors and the pick order, which change without saving
        ConfigManager.getInstance().onConfigsChanged(Reference.MOD_ID);
    }

    /** Fallback icon: the first letters of the name on a dark square. */
    protected void drawTextIcon(GuiContext ctx, String name, int x, int y, int size) {
        RenderUtils.drawRect(ctx, x, y, size, size, 0xFF404040);
        String text = name.length() > 2 ? name.substring(0, 2) : name;
        this.drawString(ctx, text, x + (size - this.getStringWidth(text)) / 2, y + (size - 8) / 2, 0xFFFFFFFF);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.getStringWidth(text) <= maxWidth) {
            return text;
        }

        while (!text.isEmpty() && this.getStringWidth(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }

        return text + "...";
    }
}

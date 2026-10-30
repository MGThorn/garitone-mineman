package io.github.mgthorn.garitonemineman.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.structures.FoundStructure;
import io.github.mgthorn.garitonemineman.client.structures.StructureDimension;
import io.github.mgthorn.garitonemineman.client.structures.StructureGroups;
import io.github.mgthorn.garitonemineman.client.structures.StructureLocator;
import io.github.mgthorn.garitonemineman.client.structures.StructureWaypointManager;

/**
 * JourneyMap waypoints for structures, in three columns: Nether, Overworld, The End.
 * <p>
 * Each structure group has a row with "All" (waypoints for every found one, or "None" to turn them off),
 * "Near 5" (waypoints for the 5 nearest to the player) and an expand button that lists every found
 * structure with its own on/off button. Structures are only searched when the Search button is pressed.
 */
public class GuiStructureWaypoints extends GuiBase {
    private static final int TOP_Y = 24;
    private static final int COLUMNS_Y = 74;
    private static final int ROW_HEIGHT = 18;
    private static final int NEAREST_COUNT = 5;
    private static final int ALL_WIDTH = 28;
    private static final int NEAREST_WIDTH = 38;
    /** Space left of a group name: expand button and color square. */
    private static final int NAME_X = 23;

    private final StructureWaypointManager manager = StructureWaypointManager.getInstance();
    private final CompletableFuture<Map<StructureDimension, List<String>>> catalog = StructureLocator.catalog();
    private final Set<String> expanded = new HashSet<>();
    private final Map<StructureDimension, Integer> scroll = new EnumMap<>(StructureDimension.class);
    private final Map<StructureDimension, List<Row>> rows = new EnumMap<>(StructureDimension.class);
    @Nullable private GuiTextFieldGeneric seedField;
    private String seedText = "";
    private int builtRevision = -1;
    private boolean builtWithCatalog;

    public GuiStructureWaypoints(@Nullable Screen parent) {
        this.title = "Structure Waypoints";
        this.setParent(parent);
    }

    // ---- Layout ----

    private int getColumnWidth() {
        return (this.getScreenWidth() - 20 - 2 * 6) / 3;
    }

    private int getColumnX(StructureDimension dimension) {
        return 10 + dimension.ordinal() * (this.getColumnWidth() + 6);
    }

    private int getVisibleRows() {
        return Math.max(1, (this.getScreenHeight() - COLUMNS_Y - 14 - 32) / ROW_HEIGHT);
    }

    private int getRowsY() {
        return COLUMNS_Y + 14;
    }

    // ---- Rows ----

    /** A structure group (structure == null) or one found structure of an expanded group. */
    private record Row(StructureDimension dimension, String groupKey, @Nullable FoundStructure structure) {
    }

    private void buildRows() {
        this.rows.clear();
        Map<StructureDimension, List<String>> groups = this.catalog.getNow(null);

        for (StructureDimension dimension : StructureDimension.values()) {
            List<Row> list = new ArrayList<>();

            if (groups != null) {
                double[] pos = StructureWaypointManager.getPlayerPosIn(dimension);

                for (String group : groups.getOrDefault(dimension, List.of())) {
                    list.add(new Row(dimension, group, null));

                    if (this.expanded.contains(expandKey(dimension, group))) {
                        List<FoundStructure> found = new ArrayList<>(this.manager.getFound(dimension, group));

                        if (pos != null) {
                            found.sort(Comparator.comparingDouble(structure -> structure.distanceSqTo(pos[0], pos[1])));
                        }

                        for (FoundStructure structure : found) {
                            list.add(new Row(dimension, group, structure));
                        }
                    }
                }
            }

            this.rows.put(dimension, list);
            int maxScroll = Math.max(0, list.size() - this.getVisibleRows());
            this.scroll.put(dimension, Math.min(this.scroll.getOrDefault(dimension, 0), maxScroll));
        }
    }

    private static String expandKey(StructureDimension dimension, String group) {
        return dimension.id() + "|" + group;
    }

    // ---- Widgets ----

    @Override
    public void initGui() {
        super.initGui();

        this.builtRevision = this.manager.getRevision();
        this.builtWithCatalog = this.catalog.isDone();
        this.buildRows();
        this.createTopWidgets();

        for (StructureDimension dimension : StructureDimension.values()) {
            this.createRowButtons(dimension);
        }

        ButtonGeneric done = new ButtonGeneric(10, this.getScreenHeight() - 26, this.getStringWidth("Done") + 10, 20, "Done");
        this.addButton(done, (btn, mouseButton) -> GuiBase.openGui(this.getParent()));
    }

    private void createTopWidgets() {
        int radius = Configs.Generic.STRUCTURE_SEARCH_RADIUS.getIntegerValue();
        String searchLabel = "Search around me (" + radius + " blocks)";
        ButtonGeneric search = new ButtonGeneric(this.getScreenWidth() - 10 - this.getStringWidth(searchLabel) - 10, TOP_Y,
                this.getStringWidth(searchLabel) + 10, 20, searchLabel,
                "Finds structures around your position in all three dimensions",
                "(Nether coordinates are your position divided by 8).",
                "The radius is the structureSearchRadius option.");
        search.setEnabled(this.manager.getSeed().isPresent() && !this.manager.isSearching() && this.catalog.isDone());
        this.addButton(search, (btn, mouseButton) -> {
            this.manager.searchAroundPlayer(radius);
            this.initGui();
        });

        // A seed from MiniHUD or singleplayer can't be wrong, so the field is only there otherwise
        String source = this.manager.getSeedSource();

        if (source.equals("unknown") || source.equals("entered")) {
            int x = 10 + this.getStringWidth("Seed:") + 6;
            this.seedField = new GuiTextFieldGeneric(x, TOP_Y + 2, 140, 16, this.font);
            this.seedField.setValue(this.seedText);
            this.addTextField(this.seedField, field -> {
                this.seedText = field.getValue();
                return true;
            });

            ButtonGeneric set = new ButtonGeneric(x + 144, TOP_Y, this.getStringWidth("Set seed") + 10, 20, "Set seed",
                    "Remembers the seed for this world or server.",
                    "Numbers are used as they are; text is turned into",
                    "a number the same way Minecraft does. Empty forgets it.");
            this.addButton(set, (btn, mouseButton) -> {
                String text = this.seedText.trim();
                this.manager.setManualSeed(text.isEmpty() ? null : parseSeed(text));
                this.initGui();
            });
        }
        else {
            this.seedField = null;
        }
    }

    private void createRowButtons(StructureDimension dimension) {
        List<Row> list = this.rows.get(dimension);
        int start = this.scroll.getOrDefault(dimension, 0);
        int end = Math.min(list.size(), start + this.getVisibleRows());
        int right = this.getColumnX(dimension) + this.getColumnWidth();

        for (int i = start; i < end; i++) {
            Row row = list.get(i);
            int y = this.getRowsY() + (i - start) * ROW_HEIGHT;

            if (row.structure() == null) {
                List<FoundStructure> found = this.manager.getFound(dimension, row.groupKey());
                boolean allEnabled = !found.isEmpty() && found.stream().allMatch(this.manager::isEnabled);
                String key = expandKey(dimension, row.groupKey());
                boolean isExpanded = this.expanded.contains(key);

                ButtonGeneric expand = new ButtonGeneric(this.getColumnX(dimension), y, 12, 16, isExpanded ? "-" : "+",
                        isExpanded ? "Hide the single structures" : "Show every found structure");
                this.addButton(expand, (btn, mouseButton) -> {
                    if (!this.expanded.remove(key)) {
                        this.expanded.add(key);
                    }

                    this.initGui();
                });

                ButtonGeneric all = new ButtonGeneric(right - ALL_WIDTH, y, ALL_WIDTH, 16, allEnabled ? "None" : "All",
                        allEnabled ? "Turn off the waypoints of every found " + StructureGroups.displayName(row.groupKey())
                                   : "Waypoints for every found " + StructureGroups.displayName(row.groupKey()));
                all.setEnabled(!found.isEmpty());
                this.addButton(all, (btn, mouseButton) -> {
                    this.manager.setEnabled(found, !allEnabled);
                    this.initGui();
                });

                ButtonGeneric nearest = new ButtonGeneric(right - ALL_WIDTH - NEAREST_WIDTH - 1, y, NEAREST_WIDTH, 16, "Near " + NEAREST_COUNT,
                        "Waypoints for the " + NEAREST_COUNT + " nearest " + StructureGroups.displayName(row.groupKey()));
                nearest.setEnabled(!found.isEmpty());
                this.addButton(nearest, (btn, mouseButton) -> {
                    this.manager.enableNearest(dimension, row.groupKey(), NEAREST_COUNT);
                    this.initGui();
                });
            }
            else {
                FoundStructure structure = row.structure();
                boolean enabled = this.manager.isEnabled(structure);
                ButtonGeneric toggle = new ButtonGeneric(right - 26, y, 26, 16,
                        enabled ? GuiBase.TXT_GREEN + "On" + GuiBase.TXT_RST : GuiBase.TXT_GRAY + "Off" + GuiBase.TXT_RST,
                        enabled ? "Turn off this waypoint" : "Create a waypoint for this structure");
                this.addButton(toggle, (btn, mouseButton) -> {
                    this.manager.setEnabled(List.of(structure), !enabled);
                    this.initGui();
                });
            }
        }
    }

    private static long parseSeed(String text) {
        try {
            return Long.parseLong(text);
        }
        catch (NumberFormatException e) {
            // Like the "Seed for the World Generator" field
            return text.hashCode();
        }
    }

    // ---- Input ----

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && mouseY >= COLUMNS_Y) {
            for (StructureDimension dimension : StructureDimension.values()) {
                int x = this.getColumnX(dimension);

                if (mouseX >= x && mouseX < x + this.getColumnWidth()) {
                    int maxScroll = Math.max(0, this.rows.getOrDefault(dimension, List.of()).size() - this.getVisibleRows());
                    int newScroll = Math.max(0, Math.min(this.scroll.getOrDefault(dimension, 0) - (int) Math.signum(verticalAmount), maxScroll));
                    this.scroll.put(dimension, newScroll);
                    this.initGui();
                    return true;
                }
            }
        }

        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---- Rendering ----

    // The rows are drawn as part of the background, so that their buttons render on top of them
    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        super.drawScreenBackground(ctx, mouseX, mouseY);

        // Rebuild after a search finished, the catalog loaded, or the waypoints changed elsewhere
        if (this.builtRevision != this.manager.getRevision() || this.builtWithCatalog != this.catalog.isDone()) {
            if (this.seedField != null) {
                this.seedText = this.seedField.getValue();
            }

            this.initGui();
        }

        this.drawTopLine(ctx);

        for (StructureDimension dimension : StructureDimension.values()) {
            this.drawColumn(ctx, dimension);
        }
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        Row row = this.getRowAt(mouseX, mouseY);

        if (row != null && row.structure() == null && mouseX < this.getColumnX(row.dimension()) + this.getColumnWidth() - ALL_WIDTH - NEAREST_WIDTH - 1) {
            List<FoundStructure> found = this.manager.getFound(row.dimension(), row.groupKey());
            long enabled = found.stream().filter(this.manager::isEnabled).count();
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(StructureGroups.displayName(row.groupKey()),
                    GuiBase.TXT_GRAY + (found.isEmpty() ? "None found yet" : found.size() + " found, " + enabled + " with a waypoint"),
                    GuiBase.TXT_GRAY + "Waypoint folder: " + row.dimension().groupName));
        }
        else if (row != null && row.structure() != null) {
            FoundStructure structure = row.structure();
            List<String> lines = new ArrayList<>();
            String variant = StructureGroups.variantOf(structure.structureId());
            lines.add(StructureGroups.displayName(structure.groupKey()) + " #" + this.manager.getNumber(structure)
                    + (variant.isEmpty() ? "" : " (" + variant + ")"));
            lines.add(GuiBase.TXT_GRAY + biomeName(structure.biomeId()));
            lines.add("x " + structure.x() + "  y " + structure.y() + "  z " + structure.z());
            String distance = this.getDistanceText(structure);

            if (!distance.isEmpty()) {
                lines.add(GuiBase.TXT_GRAY + distance + " away");
            }

            RenderUtils.drawHoverText(ctx, mouseX, mouseY, lines);
        }
    }

    private void drawTopLine(GuiContext ctx) {
        OptionalLong seed = this.manager.getSeed();
        String source = this.manager.getSeedSource();
        this.drawStringWithShadow(ctx, "Seed:", 10, TOP_Y + 6, 0xFFFFFFFF);

        if (this.seedField == null) {
            String text = seed.isPresent() ? seed.getAsLong() + GuiBase.TXT_GRAY + " (from " + source + ")" : "unknown";
            this.drawStringWithShadow(ctx, text, 10 + this.getStringWidth("Seed:") + 6, TOP_Y + 6, 0xFFFFFFFF);
        }

        String info;
        int color;

        if (seed.isEmpty()) {
            info = "Seed unknown: run /seed (with MiniHUD) or type the seed in and press Set seed";
            color = 0xFFFF5555;
        }
        else if (!this.catalog.isDone()) {
            info = "Loading worldgen data...";
            color = 0xFFFFFF55;
        }
        else if (this.catalog.isCompletedExceptionally()) {
            info = "Couldn't load the worldgen data, see the log";
            color = 0xFFFF5555;
        }
        else {
            info = this.manager.getStatus().isEmpty() ? "Press Search to find structures around you" : this.manager.getStatus();
            color = this.manager.isSearching() ? 0xFFFFFF55 : 0xFFA0A0A0;
        }

        if (seed.isPresent() && source.equals("entered")) {
            info += GuiBase.TXT_GRAY + " - typed-in seed: wrong seed = wrong structures";
        }

        this.drawString(ctx, info, 10, TOP_Y + 28, color);
    }

    private void drawColumn(GuiContext ctx, StructureDimension dimension) {
        int x = this.getColumnX(dimension);
        int width = this.getColumnWidth();
        List<Row> list = this.rows.getOrDefault(dimension, List.of());
        int start = this.scroll.getOrDefault(dimension, 0);
        int end = Math.min(list.size(), start + this.getVisibleRows());

        String header = dimension.displayName + GuiBase.TXT_GRAY + "  (" + dimension.groupName + ")";

        if (this.getStringWidth(header) > width) {
            header = dimension.displayName;
        }

        this.drawStringWithShadow(ctx, header, x + (width - this.getStringWidth(header)) / 2, COLUMNS_Y, 0xFFFFFFFF);
        RenderUtils.drawRect(ctx, x, this.getRowsY() - 2, width, this.getVisibleRows() * ROW_HEIGHT + 2, 0xA0000000);

        for (int i = start; i < end; i++) {
            Row row = list.get(i);
            int y = this.getRowsY() + (i - start) * ROW_HEIGHT;

            if (row.structure() == null) {
                List<FoundStructure> found = this.manager.getFound(dimension, row.groupKey());
                long enabled = found.stream().filter(this.manager::isEnabled).count();
                String name = StructureGroups.displayName(row.groupKey());
                String count = found.isEmpty() ? "" : " " + enabled + "/" + found.size();
                RenderUtils.drawRect(ctx, x + 14, y + 5, 6, 6, 0xFF000000 | StructureGroups.color(row.groupKey()));
                this.drawStringWithShadow(ctx, this.trimToWidth(name + GuiBase.TXT_GRAY + count, width - NAME_X - ALL_WIDTH - NEAREST_WIDTH - 3),
                        x + NAME_X, y + 4, found.isEmpty() ? 0xFFA0A0A0 : 0xFFFFFFFF);
            }
            else {
                FoundStructure structure = row.structure();
                String variant = StructureGroups.variantOf(structure.structureId());
                String text = "#" + this.manager.getNumber(structure) + " " + (variant.isEmpty() ? "" : variant + ", ")
                        + biomeName(structure.biomeId());
                String distance = this.getDistanceText(structure);
                int distanceWidth = distance.isEmpty() ? 0 : this.getStringWidth(distance) + 4;
                this.drawString(ctx, this.trimToWidth(text, width - 30 - NAME_X - distanceWidth), x + NAME_X, y + 4, 0xFFE0E0E0);

                if (!distance.isEmpty()) {
                    this.drawString(ctx, distance, x + width - 30 - distanceWidth, y + 4, 0xFFA0A0A0);
                }
            }
        }

        if (list.isEmpty() && this.catalog.isDone()) {
            this.drawString(ctx, "No structures", x + 4, this.getRowsY() + 4, 0xFFA0A0A0);
        }

        if (list.size() > this.getVisibleRows()) {
            String more = (start + 1) + "-" + end + " of " + list.size() + " (scroll)";
            this.drawString(ctx, more, x, this.getRowsY() + this.getVisibleRows() * ROW_HEIGHT + 3, 0xFF808080);
        }
    }

    @Nullable
    private Row getRowAt(int mouseX, int mouseY) {
        if (mouseY < this.getRowsY()) {
            return null;
        }

        int index = (mouseY - this.getRowsY()) / ROW_HEIGHT;

        if (index >= this.getVisibleRows()) {
            return null;
        }

        for (StructureDimension dimension : StructureDimension.values()) {
            int x = this.getColumnX(dimension);

            if (mouseX >= x && mouseX < x + this.getColumnWidth()) {
                List<Row> list = this.rows.getOrDefault(dimension, List.of());
                int rowIndex = this.scroll.getOrDefault(dimension, 0) + index;
                return rowIndex < list.size() ? list.get(rowIndex) : null;
            }
        }

        return null;
    }

    private String getDistanceText(FoundStructure structure) {
        double[] pos = StructureWaypointManager.getPlayerPosIn(structure.dimension());

        if (pos == null) {
            return "";
        }

        long distance = Math.round(Math.sqrt(structure.distanceSqTo(pos[0], pos[1])));
        return distance >= 10000 ? String.format(Locale.ROOT, "%.1f km", distance / 1000.0) : distance + " m";
    }

    private static String biomeName(String biomeId) {
        int colon = biomeId.indexOf(':');
        String key = "biome." + (colon >= 0 ? biomeId.substring(0, colon) + "." + biomeId.substring(colon + 1) : "minecraft." + biomeId);
        return Component.translatable(key).getString();
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

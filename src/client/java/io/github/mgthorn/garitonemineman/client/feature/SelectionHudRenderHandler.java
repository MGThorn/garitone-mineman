package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * While the selection tool is held in the main hand, shows the selection above the hotbar (above the
 * action bar, so overlay messages don't cover it): both corners, the size, the block count (red when
 * above {@code maxRegionSize}, which a storage point can't be bigger than), a hint when nothing is
 * selected yet, and "No block in range" for a moment after a click into the air.
 */
public class SelectionHudRenderHandler {
    private static final int LINE_HEIGHT = 10;
    private static final int BOTTOM_OFFSET = 84; // above the action bar (~68) and the armor row

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mineman", "selection_hud"), (graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();

            if (mc.player == null || mc.gui.hud.isHidden() || SelectionToolHandler.isHoldingFlint(mc.player) == false) {
                return;
            }

            List<String> lines = buildLines();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int y = mc.getWindow().getGuiScaledHeight() - BOTTOM_OFFSET - (lines.size() - 1) * LINE_HEIGHT;

            for (String line : lines) {
                int x = screenWidth / 2 - mc.font.width(line) / 2;
                graphics.text(mc.font, line, x, y, 0xFFFFFFFF);
                y += LINE_HEIGHT;
            }
        });
    }

    private static List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        BlockPos pos1 = SelectionToolHandler.getPos1();
        BlockPos pos2 = SelectionToolHandler.getPos2();

        if (pos1 == null && pos2 == null) {
            lines.add("§7Left click: §cPos1§7   Right click: §9Pos2§7   Alt + scroll: move corner");
        }
        else {
            lines.add("§cPos1§r " + format(pos1) + "   §9Pos2§r " + format(pos2));

            if (pos1 != null && pos2 != null) {
                long volume = SelectionToolHandler.getVolume();
                int max = Configs.Generic.MAX_REGION_SIZE.getIntegerValue();
                String size = SelectionToolHandler.getSizeX() + " x " + SelectionToolHandler.getSizeY()
                        + " x " + SelectionToolHandler.getSizeZ();
                lines.add(volume > max
                        ? "§c" + size + " = " + volume + " blocks (more than maxRegionSize " + max + ")"
                        : "§7" + size + " = " + volume + " blocks");
            }
        }

        var active = io.github.mgthorn.garitonemineman.client.storage.StoragePointManager.getInstance().getActiveStoragePoint();
        lines.add("§7Active storage point: " + (active != null ? "§a" + active.getName() : "none"));

        if (SelectionToolHandler.hasRecentMiss()) {
            lines.add("§cNo block in range");
        }

        return lines;
    }

    private static String format(BlockPos pos) {
        return pos == null ? "§8-§r" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}

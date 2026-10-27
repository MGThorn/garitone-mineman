package io.github.mgthorn.garitonemineman.client.feature;

import java.util.List;
import com.mojang.blaze3d.vertex.BufferBuilder;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Adds a solid, translucent box over every block matched by {@link BlockEspHandler} to
 * {@link MinemanWorldRenderer}'s through-walls batch. The color is yellow unless it was changed
 * for that block in the Block ESP picker.
 */
public class BlockEspRenderHandler {
    public static boolean addBoxes(BufferBuilder buffer) {
        if (!Configs.Generic.BLOCK_ESP.getBooleanValue()) {
            return false;
        }

        List<BlockEspHandler.Match> matches = BlockEspHandler.getMatches();

        for (BlockEspHandler.Match match : matches) {
            RenderUtils.renderAreaSidesBatched(match.pos(), match.pos(), BlockEspHandler.getColor(match.block()), 0.002, buffer);
        }

        return !matches.isEmpty();
    }
}

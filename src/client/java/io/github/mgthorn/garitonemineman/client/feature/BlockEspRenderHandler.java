package io.github.mgthorn.garitonemineman.client.feature;

import java.util.List;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Adds a solid, translucent yellow box over every block matched by {@link BlockEspHandler} to
 * {@link MinemanWorldRenderer}'s through-walls batch.
 */
public class BlockEspRenderHandler {
    private static final Color4f COLOR = new Color4f(1f, 0.85f, 0f, 0.4f);

    public static boolean addBoxes(BufferBuilder buffer) {
        if (!Configs.Generic.BLOCK_ESP.getBooleanValue()) {
            return false;
        }

        List<BlockPos> matches = BlockEspHandler.getMatches();

        for (BlockPos pos : matches) {
            RenderUtils.renderAreaSidesBatched(pos, pos, COLOR, 0.002, buffer);
        }

        return !matches.isEmpty();
    }
}

package com.example.client.feature;

import java.util.List;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

/**
 * Renders each storage point's selection area and origin while its "Rendering" toggle is on:
 * a thin white outline around the selection area, and a thin green outline around the origin.
 * Also renders the currently highlighted storage block(s) (see {@link StorageBlockHighlightController}),
 * if any, as a solid translucent red box covering every side, visible through walls.
 */
public class StoragePointRenderHandler {
    private static final float LINE_WIDTH = 1f;
    private static final Color4f COLOR_AREA = new Color4f(1f, 1f, 1f, 1f);
    private static final Color4f COLOR_ORIGIN = new Color4f(0.2f, 1f, 0.2f, 1f);

    private static final Color4f COLOR_HIGHLIGHT = new Color4f(1f, 0f, 0f, 0.35f);

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            for (StoragePoint point : StoragePointManager.getInstance().getStoragePoints()) {
                if (point.isEnabled() == false) {
                    continue;
                }

                BlockPos corner1 = new BlockPos(point.getCorner1X(), point.getCorner1Y(), point.getCorner1Z());
                BlockPos corner2 = new BlockPos(point.getCorner2X(), point.getCorner2Y(), point.getCorner2Z());
                RenderUtils.renderAreaOutline(corner1, corner2, LINE_WIDTH, COLOR_AREA, COLOR_AREA, COLOR_AREA);

                BlockPos origin = new BlockPos(point.getX(), point.getY(), point.getZ());
                RenderUtils.renderBlockOutline(origin, 0.002f, LINE_WIDTH, COLOR_ORIGIN);
            }

            List<BlockPos> highlighted = StorageBlockHighlightController.getHighlightedPositions();

            if (highlighted != null) {
                for (BlockPos pos : highlighted) {
                    renderSolidBoxThroughWalls(pos, COLOR_HIGHLIGHT);
                }
            }
        });
    }

    /** Renders a solid, translucent box over every side of {@code pos}, visible through walls. */
    private static void renderSolidBoxThroughWalls(BlockPos pos, Color4f color) {
        RenderContext ctx = new RenderContext(
                () -> "mineman:storageBlockHighlight", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
        BufferBuilder buffer = ctx.getBuilder();

        RenderUtils.renderAreaSidesBatched(pos, pos, color, 0.002, buffer);

        try {
            MeshData meshData = buffer.build();

            if (meshData != null) {
                ctx.upload(meshData, false);
                meshData.close();
                ctx.drawPost();
            }

            ctx.close();
        }
        catch (Exception err) {
            // Rendering exceptions here shouldn't crash the frame, mirroring RenderUtils' own methods.
        }
    }
}

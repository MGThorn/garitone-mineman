package com.example.client.feature;

import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import com.example.client.storage.StoragePoint;
import com.example.client.storage.StoragePointManager;

/**
 * Renders each storage point's selection area and origin while its "Rendering" toggle is on:
 * a thin white outline around the selection area, and a thin green outline around the origin.
 */
public class StoragePointRenderHandler {
    private static final float LINE_WIDTH = 1f;
    private static final Color4f COLOR_AREA = new Color4f(1f, 1f, 1f, 1f);
    private static final Color4f COLOR_ORIGIN = new Color4f(0.2f, 1f, 0.2f, 1f);

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
        });
    }
}

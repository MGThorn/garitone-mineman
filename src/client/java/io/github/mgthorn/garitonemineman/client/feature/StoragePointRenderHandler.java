package io.github.mgthorn.garitonemineman.client.feature;

import java.util.List;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.client.storage.StoragePoint;
import io.github.mgthorn.garitonemineman.client.storage.StoragePointManager;

/**
 * Adds each enabled storage point's selection area and origin to {@link MinemanWorldRenderer}'s batches:
 * a thin white outline around the selection area (visible through walls), and a thin green outline
 * around the origin. Also adds the currently highlighted storage block(s) (see
 * {@link StorageBlockHighlightController}), if any, as a solid translucent red box covering every side,
 * visible through walls.
 */
public class StoragePointRenderHandler {
    private static final float LINE_WIDTH = 1f;
    private static final Color4f COLOR_AREA = new Color4f(1f, 1f, 1f, 1f);
    private static final Color4f COLOR_ORIGIN = new Color4f(0.2f, 1f, 0.2f, 1f);

    private static final Color4f COLOR_HIGHLIGHT = new Color4f(1f, 0f, 0f, 0.35f);

    public static boolean addAreaOutlines(BufferBuilder buffer, Vec3 cameraPos) {
        boolean any = false;

        for (StoragePoint point : StoragePointManager.getInstance().getStoragePoints()) {
            if (point.isEnabled() == false) {
                continue;
            }

            int minX = Math.min(point.getCorner1X(), point.getCorner2X());
            int minY = Math.min(point.getCorner1Y(), point.getCorner2Y());
            int minZ = Math.min(point.getCorner1Z(), point.getCorner2Z());
            int maxX = Math.max(point.getCorner1X(), point.getCorner2X()) + 1;
            int maxY = Math.max(point.getCorner1Y(), point.getCorner2Y()) + 1;
            int maxZ = Math.max(point.getCorner1Z(), point.getCorner2Z()) + 1;

            RenderUtils.drawBoxAllEdgesBatchedLines(
                    (float) (minX - cameraPos.x), (float) (minY - cameraPos.y), (float) (minZ - cameraPos.z),
                    (float) (maxX - cameraPos.x), (float) (maxY - cameraPos.y), (float) (maxZ - cameraPos.z),
                    COLOR_AREA, LINE_WIDTH, buffer);
            any = true;
        }

        return any;
    }

    public static boolean addOriginOutlines(BufferBuilder buffer) {
        boolean any = false;

        for (StoragePoint point : StoragePointManager.getInstance().getStoragePoints()) {
            if (point.isEnabled() == false) {
                continue;
            }

            BlockPos origin = new BlockPos(point.getX(), point.getY(), point.getZ());
            RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(origin, COLOR_ORIGIN, 0.002, LINE_WIDTH, buffer);
            any = true;
        }

        return any;
    }

    public static boolean addHighlights(BufferBuilder buffer) {
        List<BlockPos> highlighted = StorageBlockHighlightController.getHighlightedPositions();

        if (highlighted == null || highlighted.isEmpty()) {
            return false;
        }

        for (BlockPos pos : highlighted) {
            RenderUtils.renderAreaSidesBatched(pos, pos, COLOR_HIGHLIGHT, 0.002, buffer);
        }

        return true;
    }
}

package io.github.mgthorn.garitonemineman.client.feature;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.joml.Vector4f;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import io.github.mgthorn.garitonemineman.GaritoneMineman;

/**
 * The one place this mod draws into the world, registered as a MaLiLib world-last renderer (called after
 * the level has finished rendering, outside any render pass, the same hook MiniHUD and Litematica use).
 *
 * Every handler adds its geometry to one of three shared batches instead of creating its own
 * {@link RenderContext}: each context allocates native memory and a GPU buffer, which used to happen
 * several times per frame (once per storage point, highlighted block, ESP type, ...). Contexts are always
 * closed in a {@code finally}, so a failing draw can't leak them. Draw failures are logged once per batch
 * instead of every frame.
 */
public class MinemanWorldRenderer implements IRenderer {
    private static final Set<String> REPORTED_FAILURES = new HashSet<>();

    /** Adds vertices to a batch; returns whether anything was added. */
    @FunctionalInterface
    private interface BatchFiller {
        boolean fill(BufferBuilder buffer, Vec3 cameraPos);
    }

    @Override
    public void onRenderWorldLast(RenderTarget fb, CameraRenderState cameraState, Frustum culling, RenderBuffers buffers,
                                  GpuBufferSlice terrainFog, Vector4f fogColor, ProfilerFiller profiler) {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        Vec3 cameraPos = RenderUtils.camPos();

        // Solid translucent boxes, visible through walls
        drawBatch("mineman:boxesThroughWalls", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, false, cameraPos,
                (buffer, cam) -> ItemEspRenderHandler.addBoxes(buffer, cam)
                        | BlockEspRenderHandler.addBoxes(buffer)
                        | EntityEspRenderHandler.addBoxes(buffer, cam)
                        | StoragePointRenderHandler.addHighlights(buffer));

        // Outlines visible through walls
        drawBatch("mineman:linesThroughWalls", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, true, cameraPos,
                (buffer, cam) -> StoragePointRenderHandler.addAreaOutlines(buffer, cam)
                        | SelectionToolHandler.addAreaOutline(buffer, cam));

        // Outlines hidden behind blocks
        drawBatch("mineman:linesDepth", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_LEQUAL_DEPTH, true, cameraPos,
                (buffer, cam) -> StoragePointRenderHandler.addOriginOutlines(buffer)
                        | SelectionToolHandler.addCornerOutlines(buffer));

        // The selection's translucent walls pick their depth pipeline depending on whether the camera is inside
        SelectionToolHandler.renderSides();
    }

    @Override
    public Supplier<String> getProfilerSectionSupplier() {
        return () -> GaritoneMineman.MOD_ID + "_world";
    }

    private static void drawBatch(String name, RenderPipeline pipeline, boolean lines, Vec3 cameraPos, BatchFiller filler) {
        RenderContext ctx = new RenderContext(() -> name, pipeline, 0);
        MeshData meshData = null;

        try {
            if (filler.fill(ctx.getBuilder(), cameraPos) == false) {
                return;
            }

            meshData = ctx.getBuilder().build();

            if (meshData == null) {
                return;
            }

            if (lines) {
                ctx.draw(meshData, false, true);
            }
            else {
                ctx.upload(meshData, false);
                ctx.drawPost();
            }
        }
        catch (Exception err) {
            if (REPORTED_FAILURES.add(name)) {
                GaritoneMineman.LOGGER.error("Failed to draw {} (further failures are not logged)", name, err);
            }
        }
        finally {
            if (meshData != null) {
                meshData.close();
            }

            try {
                ctx.close();
            }
            catch (Exception ignored) {
            }
        }
    }
}

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
import com.example.client.config.Configs;

/** Renders a solid, translucent yellow box over every block matched by {@link BlockEspHandler}, visible through walls. */
public class BlockEspRenderHandler {
    private static final Color4f COLOR = new Color4f(1f, 0.85f, 0f, 0.4f);

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!Configs.Generic.BLOCK_ESP.getBooleanValue()) {
                return;
            }

            List<BlockPos> matches = BlockEspHandler.getMatches();

            if (matches.isEmpty()) {
                return;
            }

            RenderContext renderCtx = new RenderContext(
                    () -> "mineman:blockEsp", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
            BufferBuilder buffer = renderCtx.getBuilder();

            for (BlockPos pos : matches) {
                RenderUtils.renderAreaSidesBatched(pos, pos, COLOR, 0.002, buffer);
            }

            try {
                MeshData meshData = buffer.build();

                if (meshData != null) {
                    renderCtx.upload(meshData, false);
                    meshData.close();
                    renderCtx.drawPost();
                }

                renderCtx.close();
            }
            catch (Exception err) {
                // Rendering exceptions here shouldn't crash the frame, mirroring RenderUtils' own methods.
            }
        });
    }
}

package com.example.client.feature;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import com.example.client.config.Configs;

/** Renders a solid, translucent green box over every dropped item's hitbox, visible through walls. */
public class ItemEspRenderHandler {
    private static final Color4f COLOR = new Color4f(0f, 1f, 0f, 0.5f);

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!Configs.Generic.ITEM_ESP.getBooleanValue()) {
                return;
            }

            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            RenderContext renderCtx = new RenderContext(
                    () -> "mineman:itemEsp", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
            BufferBuilder buffer = renderCtx.getBuilder();
            Vec3 cameraPos = RenderUtils.camPos();
            boolean any = false;

            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof ItemEntity) {
                    AABB box = entity.getBoundingBox();
                    RenderUtils.drawBoxAllSidesBatchedQuads(
                            (float) (box.minX - cameraPos.x),
                            (float) (box.minY - cameraPos.y),
                            (float) (box.minZ - cameraPos.z),
                            (float) (box.maxX - cameraPos.x),
                            (float) (box.maxY - cameraPos.y),
                            (float) (box.maxZ - cameraPos.z),
                            COLOR, buffer);
                    any = true;
                }
            }

            try {
                if (any) {
                    MeshData meshData = buffer.build();

                    if (meshData != null) {
                        renderCtx.upload(meshData, false);
                        meshData.close();
                        renderCtx.drawPost();
                    }
                }

                renderCtx.close();
            }
            catch (Exception err) {
                // Rendering exceptions here shouldn't crash the frame, mirroring RenderUtils' own methods.
            }
        });
    }
}

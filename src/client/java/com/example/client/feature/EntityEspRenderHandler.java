package com.example.client.feature;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import com.example.client.config.Configs;

/**
 * Renders a solid, translucent box around every currently loaded entity's hitbox (whose type is listed
 * in {@link Configs.Generic#ENTITY_ESP_ENTITIES}), visible through walls. Dropped items are always excluded -
 * that's {@link ItemEspRenderHandler}'s job. Each entity type is assigned its own consistent color (derived
 * from its registry ID), so different mobs/players can be told apart at a glance. Range is whatever the
 * client already has loaded - matches {@link net.minecraft.client.multiplayer.ClientLevel#entitiesForRendering()},
 * no extra radius cap.
 */
public class EntityEspRenderHandler {
    private static final Map<EntityType<?>, Color4f> COLOR_CACHE = new HashMap<>();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            if (!Configs.Generic.ENTITY_ESP.getBooleanValue()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            var level = mc.level;
            var self = mc.player;

            if (level == null) {
                return;
            }

            Set<String> wantedEntities = new HashSet<>(Configs.Generic.ENTITY_ESP_ENTITIES.getStrings());

            if (wantedEntities.isEmpty()) {
                return;
            }

            RenderContext renderCtx = new RenderContext(
                    () -> "mineman:entityEsp", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
            BufferBuilder buffer = renderCtx.getBuilder();
            Vec3 cameraPos = RenderUtils.camPos();
            boolean any = false;

            for (Entity entity : level.entitiesForRendering()) {
                if (entity == self || entity instanceof ItemEntity) {
                    continue;
                }

                EntityType<?> type = entity.getType();

                if (!wantedEntities.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())) {
                    continue;
                }

                AABB box = entity.getBoundingBox();
                Color4f color = colorFor(type);
                RenderUtils.drawBoxAllSidesBatchedQuads(
                        (float) (box.minX - cameraPos.x),
                        (float) (box.minY - cameraPos.y),
                        (float) (box.minZ - cameraPos.z),
                        (float) (box.maxX - cameraPos.x),
                        (float) (box.maxY - cameraPos.y),
                        (float) (box.maxZ - cameraPos.z),
                        color, buffer);
                any = true;
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

    private static Color4f colorFor(EntityType<?> type) {
        return COLOR_CACHE.computeIfAbsent(type, t -> {
            int hash = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString().hashCode();
            float hue = (hash & 0x7fffffff) % 360 / 360f;
            int rgb = Color.HSBtoRGB(hue, 0.75f, 1f);
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            return new Color4f(r, g, b, 0.45f);
        });
    }
}

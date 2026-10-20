package io.github.mgthorn.garitonemineman.client.feature;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Adds a solid, translucent box around every currently loaded entity's hitbox (whose type is listed
 * in {@link Configs.Generic#ENTITY_ESP_ENTITIES}) to {@link MinemanWorldRenderer}'s through-walls batch.
 * Dropped items are always excluded - that's {@link ItemEspRenderHandler}'s job. Each entity type is
 * assigned its own consistent color (derived from its registry ID), so different mobs/players can be told
 * apart at a glance. Range is whatever the client already has loaded - matches
 * {@link net.minecraft.client.multiplayer.ClientLevel#entitiesForRendering()}, no extra radius cap.
 */
public class EntityEspRenderHandler {
    private static final Map<EntityType<?>, Color4f> COLOR_CACHE = new HashMap<>();

    // The configured IDs resolved to entity types, rebuilt only when the list changes
    private static List<String> cachedIds = List.of();
    private static Set<EntityType<?>> wantedTypes = Set.of();

    public static boolean addBoxes(BufferBuilder buffer, Vec3 cameraPos) {
        if (!Configs.Generic.ENTITY_ESP.getBooleanValue()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        var self = mc.player;

        if (level == null) {
            return false;
        }

        Set<EntityType<?>> wanted = resolveWantedTypes();

        if (wanted.isEmpty()) {
            return false;
        }

        boolean any = false;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == self || entity instanceof ItemEntity) {
                continue;
            }

            EntityType<?> type = entity.getType();

            if (!wanted.contains(type)) {
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

        return any;
    }

    private static Set<EntityType<?>> resolveWantedTypes() {
        List<String> ids = Configs.Generic.ENTITY_ESP_ENTITIES.getStrings();

        if (!ids.equals(cachedIds)) {
            Set<EntityType<?>> types = new HashSet<>();

            for (String id : ids) {
                Identifier key = Identifier.tryParse(id.trim());

                if (key != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(types::add);
                }
            }

            cachedIds = List.copyOf(ids);
            wantedTypes = types;
        }

        return wantedTypes;
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

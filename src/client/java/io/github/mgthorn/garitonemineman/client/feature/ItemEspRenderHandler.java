package io.github.mgthorn.garitonemineman.client.feature;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Adds a solid, translucent green box over every dropped item's hitbox to {@link MinemanWorldRenderer}'s
 * through-walls batch.
 */
public class ItemEspRenderHandler {
    private static final Color4f COLOR = new Color4f(0f, 1f, 0f, 0.5f);

    public static boolean addBoxes(BufferBuilder buffer, Vec3 cameraPos) {
        if (!Configs.Generic.ITEM_ESP.getBooleanValue()) {
            return false;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }

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

        return any;
    }
}

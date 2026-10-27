package io.github.mgthorn.garitonemineman.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.feature.EntityEspRenderHandler;

/**
 * Picker for {@link Configs.Generic#ENTITY_ESP_ENTITIES}: every entity type (except dropped items, which
 * entityEsp never shows), drawn as its 3D model. Without a world, or for entities without a model,
 * the spawn egg (or a text icon) is shown instead. Each entity type can have its own color; the default is
 * the automatic color entityEsp gives every type.
 */
public class GuiEntityEspEditor extends GuiEspListEditor<EntityType<?>> {
    public GuiEntityEspEditor(@Nullable Screen parent) {
        super("Entity ESP", Configs.Generic.ENTITY_ESP_ENTITIES, Configs.EspEditor.ENTITY_ESP_RECENT,
                Configs.EspEditor.ENTITY_ESP_COLORS, EntityEspRenderHandler::getDefaultColor, 26, parent);
    }

    @Override
    protected List<EntityType<?>> createAllEntries() {
        List<EntityType<?>> types = new ArrayList<>();

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type != EntityTypes.ITEM) {
                types.add(type);
            }
        }

        return types;
    }

    @Override
    protected String getId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    @Override
    protected String getName(EntityType<?> type) {
        return type.getDescription().getString();
    }

    @Override
    @Nullable
    protected EntityType<?> fromId(String id) {
        Identifier key = Identifier.tryParse(id.trim());
        return key != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null) : null;
    }

    @Override
    protected void drawIcon(GuiContext ctx, @Nullable EntityType<?> type, int x, int y, int size) {
        if (type == null) {
            this.drawTextIcon(ctx, "?", x, y, size);
            return;
        }

        if (EntityIconRenderer.draw(ctx, type, x, y, size)) {
            return;
        }

        var egg = SpawnEggItem.byId(type);

        if (egg.isPresent()) {
            ctx.renderItem(new ItemStack(egg.get()), x + (size - 16) / 2, y + (size - 16) / 2);
        }
        else {
            this.drawTextIcon(ctx, this.getName(type), x, y, size);
        }
    }

    @Override
    public void removed() {
        super.removed();
        EntityIconRenderer.clear();
    }
}

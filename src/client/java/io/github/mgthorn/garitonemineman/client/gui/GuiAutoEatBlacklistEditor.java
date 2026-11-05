package io.github.mgthorn.garitonemineman.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Picker for {@link Configs.Generic#AUTOEAT_BLACKLIST}, the same screen as the Block ESP picker: every
 * food item on the left (left click adds, right click removes), the blacklist on the right. Items on
 * the list are never eaten by easyEat, autoEat or Hungry Mineman.
 */
public class GuiAutoEatBlacklistEditor extends GuiEspListEditor<Item> {
    public GuiAutoEatBlacklistEditor(@Nullable Screen parent) {
        super("Auto eat blacklist", Configs.Generic.AUTOEAT_BLACKLIST, Configs.EspEditor.AUTO_EAT_BLACKLIST_RECENT,
                null, id -> 0, 18, parent);
    }

    @Override
    protected List<Item> createAllEntries() {
        List<Item> foods = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultInstance().has(DataComponents.FOOD)) {
                foods.add(item);
            }
        }

        return foods;
    }

    @Override
    protected String getId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    protected String getName(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
    }

    @Override
    @Nullable
    protected Item fromId(String id) {
        Identifier key = Identifier.tryParse(id.trim());
        return key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
    }

    @Override
    protected void drawIcon(GuiContext ctx, @Nullable Item item, int x, int y, int size) {
        if (item != null) {
            ctx.renderItem(new ItemStack(item), x + (size - 16) / 2, y + (size - 16) / 2);
        }
        else {
            this.drawTextIcon(ctx, "?", x, y, size);
        }
    }
}

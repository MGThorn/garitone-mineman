package io.github.mgthorn.garitonemineman.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Picker for {@link Configs.Generic#STORAGE_BLOCK_TYPES}, the same screen as the Block ESP picker.
 * Only blocks with a block entity are offered (everything that can hold items has one).
 */
public class GuiStorageBlockTypesEditor extends GuiEspListEditor<Block> {
    public GuiStorageBlockTypesEditor(@Nullable Screen parent) {
        super("Storage block types", Configs.Generic.STORAGE_BLOCK_TYPES, Configs.EspEditor.STORAGE_BLOCK_TYPES_RECENT,
                null, id -> 0, 18, parent);
    }

    @Override
    protected List<Block> createAllEntries() {
        List<Block> blocks = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof EntityBlock) {
                blocks.add(block);
            }
        }

        return blocks;
    }

    @Override
    protected String getId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    @Override
    protected String getName(Block block) {
        return block.getName().getString();
    }

    @Override
    @Nullable
    protected Block fromId(String id) {
        Identifier key = Identifier.tryParse(id.trim());
        return key != null ? BuiltInRegistries.BLOCK.getOptional(key).orElse(null) : null;
    }

    @Override
    protected void drawIcon(GuiContext ctx, @Nullable Block block, int x, int y, int size) {
        Item item = block != null ? block.asItem() : Items.AIR;

        if (item != Items.AIR) {
            ctx.renderItem(new ItemStack(item), x + (size - 16) / 2, y + (size - 16) / 2);
        }
        else {
            this.drawTextIcon(ctx, block != null ? this.getName(block) : "?", x, y, size);
        }
    }
}

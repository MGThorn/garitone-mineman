package com.example.client.storage;

import java.util.Arrays;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import fi.dy.masa.malilib.util.WorldUtils;
import com.example.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Reads a container's contents directly off the integrated server's level, bypassing the normal
 * client/server packet sync (vanilla only sends a container's full contents to the client while a
 * screen is actually open). This only works in singleplayer/LAN-hosted worlds, where the integrated
 * server shares this same JVM — mirrors the same trick MiniHUD's own "Inventory Preview" feature uses.
 * Returns null on a remote server, or if the target position isn't a loaded container.
 */
public final class SingleplayerContentReader {
    private SingleplayerContentReader() {}

    @Nullable
    public static ContentSlot[] readDirect(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.isLocalServer() == false) {
            return null;
        }

        Level level = WorldUtils.getBestWorld(mc);

        if ((level instanceof ServerLevel) == false) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!(blockEntity instanceof Container container)) {
            return null;
        }

        ContentSlot[] contents = new ContentSlot[container.getContainerSize()];

        for (int i = 0; i < contents.length; i++) {
            contents[i] = ContentSlot.fromStack(container.getItem(i));
        }

        return contents;
    }

    /**
     * Merges both halves of a double chest (primary half's slots followed by the second half's). This
     * ordering is only a best-effort approximation of vanilla's real merged slot order — it gets
     * superseded the next time the chest is actually opened for real via {@code StorageContentSnapshotter}.
     */
    @Nullable
    public static ContentSlot[] readDirectMerged(StorageBlockEntry entry) {
        ContentSlot[] primary = readDirect(new BlockPos(entry.getX(), entry.getY(), entry.getZ()));

        if (primary == null || entry.hasSecondPosition() == false) {
            return primary;
        }

        ContentSlot[] second = readDirect(new BlockPos(entry.getSecondX(), entry.getSecondY(), entry.getSecondZ()));

        if (second == null) {
            return null;
        }

        ContentSlot[] merged = Arrays.copyOf(primary, primary.length + second.length);
        System.arraycopy(second, 0, merged, primary.length, second.length);
        return merged;
    }
}

package com.example.client.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Scans the volume between a StoragePoint's two selection corners for storage blocks
 * (chest, copper chest, trapped chest, shulker box, barrel). Double chests are merged
 * into a single entry, and previously-set "use" toggle states are carried over by position.
 */
public final class StorageBlockScanner {
    private static final int MAX_VOLUME = 200_000;

    private StorageBlockScanner() {}

    public static List<StorageBlockEntry> scan(Level level, BlockPos corner1, BlockPos corner2, List<StorageBlockEntry> previous) {
        BlockPos min = new BlockPos(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ()));

        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);

        if (volume > MAX_VOLUME) {
            return previous;
        }

        Map<BlockPos, StorageBlockEntry> previousByPos = new HashMap<>();

        for (StorageBlockEntry entry : previous) {
            previousByPos.put(new BlockPos(entry.getX(), entry.getY(), entry.getZ()), entry);
        }

        List<StorageBlockEntry> result = new ArrayList<>();
        Set<BlockPos> skip = new HashSet<>();

        for (BlockPos mutablePos : BlockPos.betweenClosed(min, max)) {
            if (skip.contains(mutablePos)) {
                continue;
            }

            BlockState state = level.getBlockState(mutablePos);
            Block block = state.getBlock();

            if (isStorageBlock(block) == false) {
                continue;
            }

            BlockPos pos = mutablePos.immutable();
            boolean isDouble = block instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;

            if (isDouble) {
                skip.add(ChestBlock.getConnectedBlockPos(pos, state));
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            StorageBlockEntry previousEntry = previousByPos.get(pos);
            boolean useEnabled = previousEntry != null ? previousEntry.isUseEnabled() : true;
            result.add(new StorageBlockEntry(pos.getX(), pos.getY(), pos.getZ(), blockId, isDouble, useEnabled));
        }

        return result;
    }

    private static boolean isStorageBlock(Block block) {
        return block instanceof ChestBlock || block instanceof ShulkerBoxBlock || block instanceof BarrelBlock;
    }
}

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
import com.example.client.config.Configs;

/**
 * Scans the volume between a StoragePoint's two selection corners for storage blocks
 * (chest, copper chest, trapped chest, shulker box, barrel). Double chests are merged
 * into a single entry, and previously-set "use" toggle states are carried over by position.
 */
public final class StorageBlockScanner {
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

        if (volume > Configs.Generic.MAX_REGION_SIZE.getIntegerValue()) {
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
            BlockPos secondPos = null;

            if (isDouble) {
                secondPos = ChestBlock.getConnectedBlockPos(pos, state);
                skip.add(secondPos);
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            StorageBlockEntry previousEntry = previousByPos.get(pos);
            boolean useEnabled = previousEntry != null ? previousEntry.isUseEnabled() : true;
            StorageBlockEntry newEntry = new StorageBlockEntry(pos.getX(), pos.getY(), pos.getZ(), blockId, isDouble, useEnabled);

            if (secondPos != null) {
                newEntry.setSecondPosition(secondPos.getX(), secondPos.getY(), secondPos.getZ());
            }

            if (previousEntry != null) {
                newEntry.setSlotItemFilter(previousEntry.getSlotItemFilter());
                newEntry.setSlotEnabled(previousEntry.getSlotEnabled());
                newEntry.setContents(previousEntry.getContents());
            }

            result.add(newEntry);
        }

        return preserveOrder(previous, result);
    }

    /**
     * Rebuilds the scanned list in the same relative order as {@code previous} (the user's manually
     * set priority order), keeping newly discovered entries appended at the end in scan order.
     */
    private static List<StorageBlockEntry> preserveOrder(List<StorageBlockEntry> previous, List<StorageBlockEntry> scanned) {
        Map<BlockPos, StorageBlockEntry> scannedByPos = new HashMap<>();

        for (StorageBlockEntry entry : scanned) {
            scannedByPos.put(new BlockPos(entry.getX(), entry.getY(), entry.getZ()), entry);
        }

        List<StorageBlockEntry> ordered = new ArrayList<>();
        Set<BlockPos> placed = new HashSet<>();

        for (StorageBlockEntry previousEntry : previous) {
            BlockPos pos = new BlockPos(previousEntry.getX(), previousEntry.getY(), previousEntry.getZ());
            StorageBlockEntry match = scannedByPos.get(pos);

            if (match != null && placed.add(pos)) {
                ordered.add(match);
            }
        }

        for (StorageBlockEntry entry : scanned) {
            BlockPos pos = new BlockPos(entry.getX(), entry.getY(), entry.getZ());

            if (placed.add(pos)) {
                ordered.add(entry);
            }
        }

        return ordered;
    }

    private static boolean isStorageBlock(Block block) {
        return block instanceof ChestBlock || block instanceof ShulkerBoxBlock || block instanceof BarrelBlock;
    }
}

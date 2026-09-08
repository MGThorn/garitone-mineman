package com.example.client.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.example.client.config.Configs;

/**
 * Periodically scans a cube around the player for blocks whose registry ID is in
 * {@link Configs.Generic#BLOCK_ESP_BLOCKS}, within {@link Configs.Generic#BLOCK_ESP_RADIUS}.
 * The scan is throttled (rather than run every frame) since it walks every block in the volume;
 * {@link BlockEspRenderHandler} renders the cached result every frame.
 */
public final class BlockEspHandler {
    private BlockEspHandler() {}

    private static final int RESCAN_INTERVAL_TICKS = 20;
    private static final int MAX_MATCHES = 2000;

    private static List<BlockPos> matches = List.of();
    private static int ticksUntilRescan = 0;

    public static void onClientTick() {
        if (!Configs.Generic.BLOCK_ESP.getBooleanValue()) {
            if (!matches.isEmpty()) {
                matches = List.of();
            }
            return;
        }

        if (ticksUntilRescan > 0) {
            ticksUntilRescan--;
            return;
        }

        ticksUntilRescan = RESCAN_INTERVAL_TICKS;
        rescan();
    }

    public static List<BlockPos> getMatches() {
        return matches;
    }

    private static void rescan() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) {
            matches = List.of();
            return;
        }

        Set<String> wantedBlocks = new HashSet<>(Configs.Generic.BLOCK_ESP_BLOCKS.getStrings());

        if (wantedBlocks.isEmpty()) {
            matches = List.of();
            return;
        }

        int radius = Configs.Generic.BLOCK_ESP_RADIUS.getIntegerValue();
        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);

        List<BlockPos> found = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();

            if (wantedBlocks.contains(blockId)) {
                found.add(pos.immutable());

                if (found.size() >= MAX_MATCHES) {
                    break;
                }
            }
        }

        matches = found;
    }
}

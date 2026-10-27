package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import fi.dy.masa.malilib.util.data.Color4f;
import io.github.mgthorn.garitonemineman.client.config.Configs;

/**
 * Periodically scans a cube around the player for blocks whose registry ID is in
 * {@link Configs.Generic#BLOCK_ESP_BLOCKS}, within {@link Configs.Generic#BLOCK_ESP_RADIUS}.
 * The scan is throttled (rather than run every frame) since it can cover up to 129³ blocks;
 * {@link BlockEspRenderHandler} renders the cached result every frame.
 *
 * To keep the scan cheap it walks chunk sections directly: sections that are all air, or whose palette
 * can't contain any wanted block ({@link LevelChunkSection#maybeHas}), are skipped without looking at a
 * single block, and blocks are compared as {@link Block} objects (resolved once from the configured IDs)
 * instead of building a registry ID string per block.
 */
public final class BlockEspHandler {
    private BlockEspHandler() {}

    private static final int RESCAN_INTERVAL_TICKS = 20;
    private static final int MAX_MATCHES = 2000;

    public record Match(BlockPos pos, Block block) {}

    private static List<Match> matches = List.of();
    private static int ticksUntilRescan = 0;

    // The configured IDs resolved to blocks, rebuilt only when the list changes
    private static List<String> cachedIds = List.of();
    private static Set<Block> wantedBlocks = Set.of();

    // The per-block colors from the Block ESP picker, rebuilt only when they change
    private static List<String> cachedColorEntries = List.of();
    private static Map<Block, Color4f> blockColors = Map.of();
    private static final Color4f DEFAULT_COLOR = Color4f.fromColor(EspColors.DEFAULT_BLOCK_COLOR);

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

    public static List<Match> getMatches() {
        return matches;
    }

    public static Color4f getColor(Block block) {
        List<String> entries = Configs.EspEditor.BLOCK_ESP_COLORS.getStrings();

        if (!entries.equals(cachedColorEntries)) {
            Map<Block, Color4f> colors = new HashMap<>();

            EspColors.parse(entries).forEach((id, color) -> {
                Identifier key = Identifier.tryParse(id);

                if (key != null) {
                    BuiltInRegistries.BLOCK.getOptional(key).ifPresent(b -> colors.put(b, Color4f.fromColor(color)));
                }
            });

            cachedColorEntries = List.copyOf(entries);
            blockColors = colors;
        }

        return blockColors.getOrDefault(block, DEFAULT_COLOR);
    }

    private static Set<Block> resolveWantedBlocks() {
        List<String> ids = Configs.Generic.BLOCK_ESP_BLOCKS.getStrings();

        if (!ids.equals(cachedIds)) {
            Set<Block> blocks = new HashSet<>();

            for (String id : ids) {
                Identifier key = Identifier.tryParse(id.trim());

                if (key != null) {
                    BuiltInRegistries.BLOCK.getOptional(key).ifPresent(blocks::add);
                }
            }

            cachedIds = List.copyOf(ids);
            wantedBlocks = blocks;
        }

        return wantedBlocks;
    }

    private static void rescan() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null) {
            matches = List.of();
            return;
        }

        Set<Block> wanted = resolveWantedBlocks();

        if (wanted.isEmpty()) {
            matches = List.of();
            return;
        }

        boolean wantsAir = wanted.contains(Blocks.AIR) || wanted.contains(Blocks.CAVE_AIR) || wanted.contains(Blocks.VOID_AIR);
        int radius = Configs.Generic.BLOCK_ESP_RADIUS.getIntegerValue();
        BlockPos center = player.blockPosition();
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(center.getY() - radius, level.getMinY());
        int maxY = Math.min(center.getY() + radius, level.getMaxY());
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        List<Match> found = new ArrayList<>();

        scan:
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);

                if (chunk == null) {
                    continue;
                }

                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sy));

                    if ((section.hasOnlyAir() && !wantsAir) || !section.maybeHas(state -> wanted.contains(state.getBlock()))) {
                        continue;
                    }

                    int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                    int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
                    int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);

                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                Block block = section.getBlockState(x & 15, y & 15, z & 15).getBlock();

                                if (wanted.contains(block)) {
                                    found.add(new Match(new BlockPos(x, y, z), block));

                                    if (found.size() >= MAX_MATCHES) {
                                        break scan;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        matches = found;
    }
}

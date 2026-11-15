package io.github.mgthorn.garitonemineman.client.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.fabricmc.loader.api.FabricLoader;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import fi.dy.masa.malilib.util.data_syncer.EntityDataPairEntry;
import fi.dy.masa.minihud.data.EntityDataManager;
import fi.dy.masa.minihud.data.HudDataManager;

/**
 * Reads what MiniHUD knows: the world seed (singleplayer, Servux, or the output of {@code /seed}), and
 * block entity data the server sent through Servux (the same data MiniHUD's inventory preview uses).
 * MiniHUD's classes are only touched when MiniHUD is installed.
 */
public class MiniHudCompat {
    public static final String MOD_ID = "minihud";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    /** When we first asked MiniHUD for a position that wasn't cached yet (see {@link #readServerBlockEntityTag}). */
    private static final Map<BlockPos, Long> REQUESTED_AT = new HashMap<>();

    public static boolean isLoaded() {
        return LOADED;
    }

    public static OptionalLong getWorldSeed(Level level) {
        if (!LOADED) {
            return OptionalLong.empty();
        }

        try {
            HudDataManager data = HudDataManager.getInstance();
            return data.isWorldSeedKnown(level) ? OptionalLong.of(data.getWorldSeed(level)) : OptionalLong.empty();
        }
        catch (Throwable e) {
            // A MiniHUD version with a different API: fall back to the other seed sources
            return OptionalLong.empty();
        }
    }

    /** MiniHUD's entityDataSync option is on. */
    public static boolean isEntityDataSyncEnabled() {
        if (!LOADED) {
            return false;
        }

        try {
            return EntityDataManager.getInstance().isEnabled();
        }
        catch (Throwable e) {
            return false;
        }
    }

    /** MiniHUD is installed, its entityDataSync is on, and the server answered with Servux. */
    public static boolean isServuxAvailable() {
        if (!LOADED) {
            return false;
        }

        try {
            EntityDataManager data = EntityDataManager.getInstance();
            return data.isEnabled() && data.hasServuxServer();
        }
        catch (Throwable e) {
            return false;
        }
    }

    /**
     * Returns the block entity data the server sent for {@code pos}, or null while it hasn't arrived yet.
     * Every call also asks MiniHUD to fetch (or refresh) it, so calling this again later returns it.
     *
     * When nothing is cached yet, MiniHUD first caches the client's own copy of the block entity (a chest
     * on the client is always empty) until the server's answer replaces it. That copy is ignored here: only
     * data cached after our first request counts.
     */
    @Nullable
    public static CompoundTag readServerBlockEntityTag(Level level, BlockPos pos) {
        if (!LOADED) {
            return null;
        }

        try {
            EntityDataManager data = EntityDataManager.getInstance();
            EntityDataPairEntry pair = data.getCache().getBlockEntityPairFromCache(pos);

            if (pair == null) {
                REQUESTED_AT.put(pos.immutable(), System.currentTimeMillis());
            }

            data.requestBlockEntity(level, pos);

            if (pair == null || pair.data() == null) {
                return null;
            }

            Long requested = REQUESTED_AT.get(pos);

            // The server's answer takes at least a tick; anything cached right at the request is the client's copy
            if (requested != null && pair.time() < requested + 20) {
                return null;
            }

            return DataConverterNbt.toVanillaCompound(pair.data());
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static void reset() {
        REQUESTED_AT.clear();
    }
}

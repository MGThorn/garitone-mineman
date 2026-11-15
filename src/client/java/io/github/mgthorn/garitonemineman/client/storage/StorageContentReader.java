package io.github.mgthorn.garitonemineman.client.storage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import fi.dy.masa.malilib.util.InventoryUtils;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import io.github.mgthorn.garitonemineman.client.compat.MiniHudCompat;
import io.github.mgthorn.garitonemineman.client.config.Configs;
import io.github.mgthorn.garitonemineman.client.config.ContentsSource;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Reads a storage block's contents without opening it. Where they come from ({@link #getSource()}):
 * <ul>
 *   <li>Singleplayer: straight off the integrated server, on its thread ({@link SingleplayerContentReader}).</li>
 *   <li>Servux: the block entity data MiniHUD's entity data sync gets from a Servux server.</li>
 *   <li>Op query: vanilla's block entity query (what F3 + I and {@code /data} use), operators only.</li>
 *   <li>None: contents only update when a chest is opened ({@code StorageContentSnapshotter}).</li>
 * </ul>
 * All three answer a moment later: {@link #read} returns null until the answer is there,
 * and {@link #consumeFreshResults()} tells the rescanner to read again once something arrived.
 */
public final class StorageContentReader {
    public enum Source {
        SINGLEPLAYER("read directly (singleplayer)", true),
        SERVUX      ("live via Servux (MiniHUD)",    true),
        OP_QUERY    ("live via op query",            true),
        NONE        ("only when a chest is opened",  false);

        private final String label;
        private final boolean async;

        Source(String label, boolean async) {
            this.label = label;
            this.async = async;
        }

        public String getLabel() { return this.label; }
        /** Answers arrive a few ticks after asking. */
        public boolean isAsync() { return this.async; }
    }

    private static final int OP_QUERIES_PER_TICK = 2;
    /** A position isn't asked again while its last answer is younger than this. */
    private static final long OP_REQUERY_MS = 2000;
    /** Without a single answer after this many ticks the server is treated as refusing the op query. */
    private static final int OP_NO_ANSWER_TICKS = 100;

    private static final LinkedHashSet<BlockPos> OP_QUEUE = new LinkedHashSet<>();
    private static final Set<BlockPos> OP_IN_FLIGHT = new HashSet<>();
    private static final Map<BlockPos, ContentSlot[]> OP_RESULTS = new HashMap<>();
    private static final Map<BlockPos, Long> OP_RESULT_TIME = new HashMap<>();
    private static int opAnswers;
    private static int opWaitingTicks;
    private static boolean opNoAnswer;

    private static boolean servuxWaiting;
    private static int ticks;
    private static boolean fresh;
    @Nullable private static Level lastLevel;
    @Nullable private static ContentsSource lastSetting;

    private StorageContentReader() {}

    public static Source getSource() {
        Minecraft mc = Minecraft.getInstance();
        ContentsSource setting = getSetting();

        if (setting == ContentsSource.OPENING_ONLY || mc.player == null) {
            return Source.NONE;
        }

        if (mc.isLocalServer()) {
            return Source.SINGLEPLAYER;
        }

        boolean servux = MiniHudCompat.isServuxAvailable();
        boolean op = canUseOpQuery();

        return switch (setting) {
            case SERVUX -> servux ? Source.SERVUX : Source.NONE;
            case OP_QUERY -> op ? Source.OP_QUERY : Source.NONE;
            default -> servux ? Source.SERVUX : op ? Source.OP_QUERY : Source.NONE;
        };
    }

    /** One line for the screens: where contents come from, and when nothing works, why. */
    public static String describe() {
        Source source = getSource();

        if (source != Source.NONE) {
            return "Contents: " + source.getLabel();
        }

        return "Contents: " + source.getLabel() + " (" + getNoSourceReason() + ")";
    }

    /** Why there's no source, e.g. "MiniHUD's entityDataSync is off, not op". */
    public static String getNoSourceReason() {
        ContentsSource setting = getSetting();

        if (setting == ContentsSource.OPENING_ONLY) {
            return "storageContentsSource";
        }

        String servuxReason = !MiniHudCompat.isLoaded() ? "MiniHUD not installed"
                : !MiniHudCompat.isEntityDataSyncEnabled() ? "MiniHUD's entityDataSync is off"
                : "no Servux on this server";
        String opReason = opNoAnswer ? "op query got no answer" : "not op";

        return switch (setting) {
            case SERVUX -> servuxReason;
            case OP_QUERY -> opReason;
            default -> servuxReason + ", " + opReason;
        };
    }

    /**
     * The block's contents (both halves of a double chest in vanilla's slot order), or null when they
     * can't be read (yet). Asks Servux / the server again on every call, so call it again later.
     */
    @Nullable
    public static ContentSlot[] read(Level level, StorageBlockEntry entry) {
        Source source = getSource();
        Function<BlockPos, ContentSlot[]> reader = switch (source) {
            case SINGLEPLAYER -> SingleplayerContentReader::read;
            case SERVUX -> pos -> parse(level, pos, MiniHudCompat.readServerBlockEntityTag(level, pos));
            case OP_QUERY -> StorageContentReader::readOpQuery;
            case NONE -> null;
        };

        if (reader == null) {
            return null;
        }

        BlockPos main = new BlockPos(entry.getX(), entry.getY(), entry.getZ());
        ContentSlot[] mainSlots = reader.apply(main);
        ContentSlot[] result = mainSlots;

        if (entry.hasSecondPosition()) {
            // Both halves are always asked, so both answers arrive together
            ContentSlot[] secondSlots = reader.apply(new BlockPos(entry.getSecondX(), entry.getSecondY(), entry.getSecondZ()));
            result = mainSlots != null && secondSlots != null ? mergeDouble(level.getBlockState(main), mainSlots, secondSlots) : null;
        }

        if (result == null && source == Source.SERVUX) {
            servuxWaiting = true;
        }

        return result;
    }

    /** Vanilla lists the right half of a double chest first (ChestBlock's FIRST block), then the left one. */
    private static ContentSlot[] mergeDouble(BlockState mainState, ContentSlot[] main, ContentSlot[] second) {
        boolean mainIsLeft = mainState.hasProperty(ChestBlock.TYPE) && mainState.getValue(ChestBlock.TYPE) == ChestType.LEFT;
        ContentSlot[] first = mainIsLeft ? second : main;
        ContentSlot[] last = mainIsLeft ? main : second;
        ContentSlot[] merged = Arrays.copyOf(first, first.length + last.length);
        System.arraycopy(last, 0, merged, first.length, last.length);
        return merged;
    }

    /** Turns block entity data (Servux or op query) into slots. Null for loot chests that were never opened. */
    @Nullable
    private static ContentSlot[] parse(Level level, BlockPos pos, @Nullable CompoundTag tag) {
        if (tag == null || tag.contains("LootTable")) {
            return null;
        }

        int size = level.getBlockEntity(pos) instanceof Container container ? container.getContainerSize() : 27;
        ContentSlot[] slots = new ContentSlot[size];
        Arrays.fill(slots, ContentSlot.EMPTY);

        // An empty container has no "Items" at all
        Container inventory = tag.contains("Items")
                ? InventoryUtils.getDataInventory(DataConverterNbt.fromVanillaCompound(tag), size, level.registryAccess())
                : null;

        if (inventory != null) {
            for (int i = 0; i < Math.min(size, inventory.getContainerSize()); i++) {
                slots[i] = ContentSlot.fromStack(inventory.getItem(i));
            }
        }

        return slots;
    }

    private static boolean canUseOpQuery() {
        Minecraft mc = Minecraft.getInstance();
        return opNoAnswer == false && mc.player != null && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Nullable
    private static ContentSlot[] readOpQuery(BlockPos pos) {
        BlockPos key = pos.immutable();
        Long answered = OP_RESULT_TIME.get(key);

        if (OP_IN_FLIGHT.contains(key) == false && (answered == null || System.currentTimeMillis() - answered > OP_REQUERY_MS)) {
            OP_QUEUE.add(key);
        }

        return OP_RESULTS.get(key);
    }

    private static void onOpAnswer(BlockPos pos, @Nullable CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        OP_IN_FLIGHT.remove(pos);
        opAnswers++;

        if (mc.level == null) {
            return;
        }

        ContentSlot[] slots = parse(mc.level, pos, tag);
        OP_RESULT_TIME.put(pos, System.currentTimeMillis());

        if (slots == null) {
            OP_RESULTS.remove(pos);
            return;
        }

        if (Arrays.equals(slots, OP_RESULTS.put(pos, slots)) == false) {
            fresh = true;
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        ContentsSource setting = getSetting();

        if (mc.level != lastLevel || setting != lastSetting) {
            reset();
            lastLevel = mc.level;
            lastSetting = setting;
        }

        ClientPacketListener connection = mc.getConnection();

        if (mc.level == null || connection == null) {
            return;
        }

        SingleplayerContentReader.onClientTick();

        if (SingleplayerContentReader.consumeFresh()) {
            fresh = true;
        }

        // Op query: a few questions per tick
        Iterator<BlockPos> it = OP_QUEUE.iterator();

        for (int i = 0; i < OP_QUERIES_PER_TICK && it.hasNext(); i++) {
            BlockPos pos = it.next();
            it.remove();
            OP_IN_FLIGHT.add(pos);
            connection.getDebugQueryHandler().queryBlockEntityTag(pos, tag -> onOpAnswer(pos, tag));
        }

        if (OP_IN_FLIGHT.isEmpty() || opAnswers > 0) {
            opWaitingTicks = 0;
        }
        else if (++opWaitingTicks > OP_NO_ANSWER_TICKS) {
            // The server ignores us (not op after all, or a plugin blocks the query): stop asking until rejoin
            opNoAnswer = true;
            OP_QUEUE.clear();
            OP_IN_FLIGHT.clear();
        }

        // Servux answers land in MiniHUD's cache without telling us, so look again every second while waiting
        if (++ticks >= 20) {
            ticks = 0;

            if (servuxWaiting) {
                servuxWaiting = false;
                fresh = true;
            }
        }
    }

    /** True once after new contents arrived (Servux or op query), so the rescanner reads them right away. */
    public static boolean consumeFreshResults() {
        boolean result = fresh;
        fresh = false;
        return result;
    }

    public static void reset() {
        OP_QUEUE.clear();
        OP_IN_FLIGHT.clear();
        OP_RESULTS.clear();
        OP_RESULT_TIME.clear();
        opAnswers = 0;
        opWaitingTicks = 0;
        opNoAnswer = false;
        servuxWaiting = false;
        fresh = false;
        MiniHudCompat.reset();
        SingleplayerContentReader.reset();
    }

    private static ContentsSource getSetting() {
        return (ContentsSource) Configs.Generic.STORAGE_CONTENTS_SOURCE.getOptionListValue();
    }
}

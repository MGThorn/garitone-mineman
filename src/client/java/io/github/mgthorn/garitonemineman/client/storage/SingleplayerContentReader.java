package io.github.mgthorn.garitonemineman.client.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import io.github.mgthorn.garitonemineman.client.storage.StorageBlockEntry.ContentSlot;

/**
 * Reads containers straight off the integrated server (singleplayer / LAN host), without opening them.
 *
 * The server's level only hands out block entities on its own thread ({@code Level#getBlockEntity}
 * returns null anywhere else), so reads are queued and run on the server thread; the answers land here a
 * tick later. {@link #read} returns the last answer and asks again.
 */
final class SingleplayerContentReader {
    /** Answer for a position; {@code slots} is null when there's no container there. */
    private record Answer(@Nullable ContentSlot[] slots) {}

    private static final LinkedHashSet<BlockPos> QUEUE = new LinkedHashSet<>();
    private static final Map<BlockPos, Answer> ANSWERS = new ConcurrentHashMap<>();
    private static volatile boolean fresh;

    private SingleplayerContentReader() {}

    /** The last contents read for {@code pos} (null until the server answered), and queues a new read. */
    @Nullable
    static ContentSlot[] read(BlockPos pos) {
        BlockPos key = pos.immutable();
        QUEUE.add(key);
        Answer answer = ANSWERS.get(key);
        return answer != null ? answer.slots() : null;
    }

    /** Hands the queued reads to the server thread. Called every client tick. */
    static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();

        if (QUEUE.isEmpty() || server == null || mc.level == null) {
            QUEUE.clear();
            return;
        }

        List<BlockPos> batch = new ArrayList<>(QUEUE);
        QUEUE.clear();
        var dimension = mc.level.dimension();

        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);

            if (level == null) {
                return;
            }

            for (BlockPos pos : batch) {
                BlockEntity blockEntity = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
                ContentSlot[] slots = null;

                if (blockEntity instanceof Container container) {
                    slots = new ContentSlot[container.getContainerSize()];

                    for (int i = 0; i < slots.length; i++) {
                        slots[i] = ContentSlot.fromStack(container.getItem(i));
                    }
                }

                Answer previous = ANSWERS.put(pos, new Answer(slots));

                if (previous == null || java.util.Arrays.equals(previous.slots(), slots) == false) {
                    fresh = true;
                }
            }
        });
    }

    /** True once after an answer differed from the one before. */
    static boolean consumeFresh() {
        boolean result = fresh;
        fresh = false;
        return result;
    }

    static void reset() {
        QUEUE.clear();
        ANSWERS.clear();
        fresh = false;
    }
}

package io.github.mgthorn.garitonemineman.client.structures;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.garitonemineman.GaritoneMineman;
import io.github.mgthorn.garitonemineman.Reference;
import io.github.mgthorn.garitonemineman.client.compat.JourneyMapCompat;
import io.github.mgthorn.garitonemineman.client.compat.MiniHudCompat;
import io.github.mgthorn.garitonemineman.client.compat.journeymap.JourneyMapWaypoints;

/**
 * The structure waypoints: the structures found so far and which of them have a waypoint, stored per
 * world seed (so a world with the same seed on another server shares them). The waypoints themselves live
 * in JourneyMap and are brought in line with this state after every change and after joining a world.
 * <p>
 * The seed comes from MiniHUD, from the integrated server in singleplayer, or from a seed typed into the
 * structure screen (remembered per world or server).
 */
public final class StructureWaypointManager {
    private static final StructureWaypointManager INSTANCE = new StructureWaypointManager();
    private static final int SYNC_INTERVAL_TICKS = 40;
    /** JourneyMap saves its whole waypoint file per change, so big batches are spread over many ticks. */
    private static final int SYNC_CHANGES_PER_TICK = 2;

    private final Map<Long, SeedData> dataBySeed = new HashMap<>();
    @Nullable private Map<String, Long> manualSeeds;
    @Nullable private ClientLevel lastLevel;
    private boolean syncNeeded;
    private int tickCounter;
    private boolean searching;
    private String status = "";
    private int revision;

    public static StructureWaypointManager getInstance() {
        return INSTANCE;
    }

    private StructureWaypointManager() {}

    /** Changes whenever the found structures, the waypoints or the seed change, so screens can refresh. */
    public int getRevision() {
        return this.revision;
    }

    // ---- Seed ----

    public OptionalLong getSeed() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;

        if (level == null) {
            return OptionalLong.empty();
        }

        OptionalLong seed = MiniHudCompat.getWorldSeed(level);

        if (seed.isPresent()) {
            return seed;
        }

        MinecraftServer server = mc.getSingleplayerServer();

        if (server != null) {
            return OptionalLong.of(server.overworld().getSeed());
        }

        Long manual = this.getManualSeeds().get(getWorldId());
        return manual != null ? OptionalLong.of(manual) : OptionalLong.empty();
    }

    /** Where the seed comes from, for the screen. */
    public String getSeedSource() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level != null && MiniHudCompat.getWorldSeed(mc.level).isPresent()) {
            return "MiniHUD";
        }

        if (mc.getSingleplayerServer() != null) {
            return "singleplayer";
        }

        return this.getManualSeeds().containsKey(getWorldId()) ? "entered" : "unknown";
    }

    /** Remembers a typed-in seed for the current world or server; null forgets it. */
    public void setManualSeed(@Nullable Long seed) {
        Map<String, Long> seeds = this.getManualSeeds();

        if (seed == null) {
            seeds.remove(getWorldId());
        }
        else {
            seeds.put(getWorldId(), seed);
        }

        JsonObject root = new JsonObject();
        seeds.forEach(root::addProperty);
        FileUtils.createDirectoriesIfMissing(dataDir());
        JsonUtils.writeJsonToFile(root, dataDir().resolve("manual_seeds.json"));
        this.syncNeeded = true;
        this.revision++;
    }

    private Map<String, Long> getManualSeeds() {
        if (this.manualSeeds == null) {
            this.manualSeeds = new HashMap<>();
            JsonElement element = JsonUtils.parseJsonFile(dataDir().resolve("manual_seeds.json"));

            if (element != null && element.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                    try {
                        this.manualSeeds.put(entry.getKey(), entry.getValue().getAsLong());
                    }
                    catch (RuntimeException ignored) {
                    }
                }
            }
        }

        return this.manualSeeds;
    }

    private static String getWorldId() {
        String id = StringUtils.getWorldOrServerName();
        return id != null ? id : "unknown";
    }

    // ---- Searching ----

    public boolean isSearching() {
        return this.searching;
    }

    public String getStatus() {
        return this.status;
    }

    /**
     * Searches all three dimensions around the player: around the player's position in the current
     * dimension, and around the matching position in the others (Nether coordinates are divided by 8).
     */
    public void searchAroundPlayer(int radius) {
        LocalPlayer player = Minecraft.getInstance().player;
        OptionalLong seed = this.getSeed();

        if (player == null || seed.isEmpty() || this.searching) {
            return;
        }

        StructureDimension current = StructureDimension.of(player.level().dimension());

        if (current == null) {
            this.status = "Unknown dimension, can't search here";
            return;
        }

        Map<StructureDimension, int[]> centers = new EnumMap<>(StructureDimension.class);

        for (StructureDimension dimension : StructureDimension.values()) {
            centers.put(dimension, new int[] {
                    (int) Math.floor(dimension.convertFrom(current, player.getX())),
                    (int) Math.floor(dimension.convertFrom(current, player.getZ())) });
        }

        long seedValue = seed.getAsLong();
        this.searching = true;
        this.status = "Searching within " + radius + " blocks...";
        this.revision++;

        StructureLocator.search(seedValue, centers, radius).whenComplete((found, error) ->
                Minecraft.getInstance().execute(() -> {
                    this.searching = false;
                    this.revision++;

                    if (error != null) {
                        GaritoneMineman.LOGGER.error("Structure search failed", error);
                        this.status = "Search failed: " + error.getCause();
                        return;
                    }

                    int added = this.getData(seedValue).addAll(found, centers);
                    this.save(seedValue);
                    this.status = "Found " + found.size() + " structures (" + added + " new)";
                }));
    }

    // ---- Found structures and waypoints ----

    /** The found structures of a group, in the order they were found (their waypoint numbers). */
    public List<FoundStructure> getFound(StructureDimension dimension, String groupKey) {
        OptionalLong seed = this.getSeed();
        return seed.isPresent() ? this.getData(seed.getAsLong()).get(dimension, groupKey) : List.of();
    }

    /** The waypoint number of a structure within its group, starting at 1. */
    public int getNumber(FoundStructure structure) {
        return this.getFound(structure.dimension(), structure.groupKey()).indexOf(structure) + 1;
    }

    public boolean isEnabled(FoundStructure structure) {
        OptionalLong seed = this.getSeed();
        return seed.isPresent() && this.getData(seed.getAsLong()).enabled.contains(structure.key());
    }

    public void setEnabled(Collection<FoundStructure> structures, boolean enabled) {
        OptionalLong seed = this.getSeed();

        if (seed.isEmpty() || structures.isEmpty()) {
            return;
        }

        SeedData data = this.getData(seed.getAsLong());

        for (FoundStructure structure : structures) {
            if (enabled) {
                data.enabled.add(structure.key());
            }
            else {
                data.enabled.remove(structure.key());
            }
        }

        this.save(seed.getAsLong());
        this.syncNeeded = true;
        this.tickCounter = SYNC_INTERVAL_TICKS;
        this.revision++;
    }

    /** Enables the waypoints of the {@code count} structures of a group nearest to the player. */
    public void enableNearest(StructureDimension dimension, String groupKey, int count) {
        double[] pos = getPlayerPosIn(dimension);

        if (pos == null) {
            return;
        }

        List<FoundStructure> nearest = new ArrayList<>(this.getFound(dimension, groupKey));
        nearest.sort(Comparator.comparingDouble(structure -> structure.distanceSqTo(pos[0], pos[1])));
        this.setEnabled(nearest.subList(0, Math.min(count, nearest.size())), true);
    }

    /** The player's x and z converted into the given dimension, or null without a player. */
    @Nullable
    public static double[] getPlayerPosIn(StructureDimension dimension) {
        LocalPlayer player = Minecraft.getInstance().player;
        StructureDimension current = player != null ? StructureDimension.of(player.level().dimension()) : null;

        if (current == null) {
            return null;
        }

        return new double[] { dimension.convertFrom(current, player.getX()), dimension.convertFrom(current, player.getZ()) };
    }

    // ---- JourneyMap sync ----

    public void onClientTick() {
        ClientLevel level = Minecraft.getInstance().level;

        if (level != this.lastLevel) {
            // Joined a world or changed dimension: JourneyMap may need our waypoints for this world
            this.lastLevel = level;
            this.syncNeeded = level != null;
            this.tickCounter = 0;
        }

        if (!this.syncNeeded || level == null || !JourneyMapCompat.isLoaded() || ++this.tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }

        this.tickCounter = 0;
        OptionalLong seed = this.getSeed();

        // Without a seed there is nothing to sync yet; it's retried once a seed is known
        if (seed.isEmpty()) {
            return;
        }

        if (JourneyMapWaypoints.sync(this.createSpecs(seed.getAsLong()), SYNC_CHANGES_PER_TICK)) {
            this.syncNeeded = false;
        }
        else if (JourneyMapWaypoints.isReady()) {
            // More waypoints left: continue next tick, a few at a time
            this.tickCounter = SYNC_INTERVAL_TICKS - 1;
        }
    }

    private List<StructureWaypointSpec> createSpecs(long seed) {
        SeedData data = this.getData(seed);
        List<StructureWaypointSpec> specs = new ArrayList<>();

        for (Map.Entry<String, List<FoundStructure>> entry : data.byGroup.entrySet()) {
            List<FoundStructure> structures = entry.getValue();

            for (int i = 0; i < structures.size(); i++) {
                FoundStructure structure = structures.get(i);
                String variant = StructureGroups.variantOf(structure.structureId());
                String name = StructureGroups.displayName(structure.groupKey()) + " #" + (i + 1)
                        + (variant.isEmpty() ? "" : " (" + variant + ")");

                specs.add(new StructureWaypointSpec(structure.key(), name, structure.dimension().id(),
                        structure.dimension().groupName, structure.x(), structure.y(), structure.z(),
                        StructureGroups.color(structure.groupKey()), data.enabled.contains(structure.key())));
            }
        }

        return specs;
    }

    // ---- Storage ----

    private static Path dataDir() {
        return FileUtils.getConfigDirectory().resolve(Reference.MOD_ID).resolve("structure_waypoints");
    }

    private SeedData getData(long seed) {
        return this.dataBySeed.computeIfAbsent(seed, StructureWaypointManager::load);
    }

    private static SeedData load(long seed) {
        SeedData data = new SeedData();
        JsonElement element = JsonUtils.parseJsonFile(dataDir().resolve(seed + ".json"));

        if (element == null || !element.isJsonObject()) {
            return data;
        }

        JsonObject root = element.getAsJsonObject();

        if (root.has("structures") && root.get("structures").isJsonArray()) {
            List<FoundStructure> found = new ArrayList<>();

            for (JsonElement el : root.getAsJsonArray("structures")) {
                try {
                    JsonObject obj = el.getAsJsonObject();
                    StructureDimension dimension = StructureDimension.byId(obj.get("dimension").getAsString());

                    if (dimension != null) {
                        found.add(new FoundStructure(dimension, obj.get("structure").getAsString(), obj.get("biome").getAsString(),
                                obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()));
                    }
                }
                catch (RuntimeException ignored) {
                }
            }

            data.addAll(found, Map.of());
        }

        if (root.has("enabled") && root.get("enabled").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("enabled")) {
                data.enabled.add(el.getAsString());
            }
        }

        return data;
    }

    private void save(long seed) {
        SeedData data = this.getData(seed);
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("seed", seed);

        JsonArray structures = new JsonArray();

        for (List<FoundStructure> group : data.byGroup.values()) {
            for (FoundStructure structure : group) {
                JsonObject obj = new JsonObject();
                obj.addProperty("dimension", structure.dimension().id());
                obj.addProperty("structure", structure.structureId());
                obj.addProperty("biome", structure.biomeId());
                obj.addProperty("x", structure.x());
                obj.addProperty("y", structure.y());
                obj.addProperty("z", structure.z());
                structures.add(obj);
            }
        }

        root.add("structures", structures);

        JsonArray enabled = new JsonArray();
        data.enabled.forEach(enabled::add);
        root.add("enabled", enabled);

        FileUtils.createDirectoriesIfMissing(dataDir());
        JsonUtils.writeJsonToFile(root, dataDir().resolve(seed + ".json"));
    }

    private static final class SeedData {
        /** Per dimension and group, in the order found; the order gives the waypoint numbers. */
        private final Map<String, List<FoundStructure>> byGroup = new LinkedHashMap<>();
        private final Set<String> keys = new HashSet<>();
        private final Set<String> enabled = new HashSet<>();

        List<FoundStructure> get(StructureDimension dimension, String groupKey) {
            return this.byGroup.getOrDefault(dimension.id() + "|" + groupKey, List.of());
        }

        /**
         * Adds the structures not found before. New ones are added nearest to the search center first,
         * so lower waypoint numbers are closer.
         * @return how many were new
         */
        int addAll(List<FoundStructure> found, Map<StructureDimension, int[]> centers) {
            List<FoundStructure> sorted = new ArrayList<>(found);
            sorted.sort(Comparator.comparingDouble(structure -> {
                int[] center = centers.get(structure.dimension());
                return center != null ? structure.distanceSqTo(center[0], center[1]) : 0;
            }));

            int added = 0;

            for (FoundStructure structure : sorted) {
                if (this.keys.add(structure.key())) {
                    this.byGroup.computeIfAbsent(structure.dimension().id() + "|" + structure.groupKey(), key -> new ArrayList<>()).add(structure);
                    added++;
                }
            }

            return added;
        }
    }
}

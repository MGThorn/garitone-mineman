package io.github.mgthorn.garitonemineman.client.structures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import io.github.mgthorn.garitonemineman.GaritoneMineman;

/**
 * Finds structures from the world seed with Minecraft's own worldgen code and the vanilla data pack
 * (loaded from the game jar), the same way {@code /locate} picks candidate chunks: the placement rules of each structure
 * set (spacing, frequency, exclusion zones, stronghold rings), the weighted pick between the structures
 * of a set, and the biome check at the structure.
 * <p>
 * For most structures vanilla's own generation check runs (biome, terrain height, End City and Nether
 * fossil terrain checks), so the result is exact. Jigsaw structures (villages, outposts, bastions, ancient
 * cities, trail ruins, trial chambers, ...) and ruined portals need the structure templates to find their
 * position, so for them the biome is sampled at the chunk center at the height they start at; that is
 * right in nearly all cases. Worlds with data packs or modded worldgen give wrong results.
 * <p>
 * Everything runs on one background thread.
 */
public final class StructureLocator {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Mineman structure locator");
        // Daemon, so it never keeps the game from closing
        thread.setDaemon(true);
        return thread;
    });

    @Nullable private static HolderLookup.Provider lookup;
    @Nullable private static RegistryAccess.Frozen worldgenAccess;
    @Nullable private static CompletableFuture<Map<StructureDimension, List<String>>> catalog;

    private StructureLocator() {}

    /** The structure groups that can generate in each dimension, sorted by name. Computed once. */
    public static synchronized CompletableFuture<Map<StructureDimension, List<String>>> catalog() {
        if (catalog == null) {
            catalog = CompletableFuture.supplyAsync(StructureLocator::createCatalog, EXECUTOR);
        }

        return catalog;
    }

    /**
     * Searches every dimension around the given center (in that dimension's coordinates).
     * @param centers the search center per dimension, as {x, z}
     * @param radius the search radius in blocks
     */
    public static CompletableFuture<List<FoundStructure>> search(long seed, Map<StructureDimension, int[]> centers, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            List<FoundStructure> found = new ArrayList<>();

            for (Map.Entry<StructureDimension, int[]> entry : centers.entrySet()) {
                long start = System.currentTimeMillis();
                int[] center = entry.getValue();
                List<FoundStructure> inDimension = new DimensionSearch(getLookup(), entry.getKey(), seed).search(center[0], center[1], radius);
                found.addAll(inDimension);
                GaritoneMineman.LOGGER.info("Structure search in {}: {} structures within {} blocks of {} {} ({} ms)",
                        entry.getKey().id(), inDimension.size(), radius, center[0], center[1], System.currentTimeMillis() - start);
            }

            return found;
        }, EXECUTOR);
    }

    /**
     * Loads the worldgen registries (biomes, noise, structures, structure sets, ...) with their tags from
     * the vanilla data pack inside the game jar, the same way the integrated server does when it loads a world.
     */
    private static HolderLookup.Provider getLookup() {
        if (lookup == null) {
            try (CloseableResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                    List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
                LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
                List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
                List<HolderLookup.RegistryLookup<?>> staticLookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLD), staticTags);
                RegistryAccess.Frozen worldgen = RegistryDataLoader.load(resources, staticLookups, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
                lookup = HolderLookup.Provider.create(Stream.concat(staticLookups.stream(), worldgen.listRegistries()));
                worldgenAccess = worldgen;
            }
        }

        return lookup;
    }

    private static Map<StructureDimension, List<String>> createCatalog() {
        HolderLookup.Provider registries = getLookup();
        Map<StructureDimension, List<String>> result = new EnumMap<>(StructureDimension.class);

        for (StructureDimension dimension : StructureDimension.values()) {
            BiomeSource biomeSource = createBiomeSource(registries, dimension);
            TreeSet<String> groups = new TreeSet<>(Comparator.comparing(StructureGroups::displayName));

            registries.lookupOrThrow(Registries.STRUCTURE_SET).listElements().forEach(set -> {
                for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                    if (canGenerateIn(entry.structure().value(), biomeSource)) {
                        groups.add(StructureGroups.groupOf(structureId(entry.structure())));
                    }
                }
            });

            result.put(dimension, List.copyOf(groups));
        }

        return result;
    }

    private static boolean canGenerateIn(Structure structure, BiomeSource biomeSource) {
        for (Holder<Biome> biome : biomeSource.possibleBiomes()) {
            if (structure.biomes().contains(biome)) {
                return true;
            }
        }

        return false;
    }

    private static BiomeSource createBiomeSource(HolderLookup.Provider registries, StructureDimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            case NETHER -> MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER));
            case END -> TheEndBiomeSource.create(registries.lookupOrThrow(Registries.BIOME));
        };
    }

    private static String structureId(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** The worldgen of one dimension for one seed. */
    private static final class DimensionSearch {
        private final StructureDimension dimension;
        private final long seed;
        private final NoiseBasedChunkGenerator generator;
        private final BiomeSource biomeSource;
        private final Climate.Sampler climateSampler;
        private final RandomState randomState;
        private final ChunkGeneratorStructureState structureState;
        private final BiomeResolver biomes;
        private final LevelHeightAccessor heightAccessor;

        DimensionSearch(HolderLookup.Provider registries, StructureDimension dimension, long seed) {
            this.dimension = dimension;
            this.seed = seed;

            Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(switch (dimension) {
                case OVERWORLD -> NoiseGeneratorSettings.OVERWORLD;
                case NETHER -> NoiseGeneratorSettings.NETHER;
                case END -> NoiseGeneratorSettings.END;
            });

            this.biomeSource = createBiomeSource(registries, dimension);
            this.generator = new NoiseBasedChunkGenerator(this.biomeSource, settings);
            this.randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
            this.climateSampler = this.randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
            this.structureState = this.generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, seed);
            this.biomes = this.biomeSource.createUncachedResolver(this.randomState);
            this.heightAccessor = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
        }

        List<FoundStructure> search(int centerX, int centerZ, int radius) {
            List<FoundStructure> found = new ArrayList<>();
            long radiusSq = (long) radius * radius;

            for (Holder<StructureSet> set : this.structureState.possibleStructureSets()) {
                StructurePlacement placement = set.value().placement();

                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    int spacing = spread.spacing();
                    int minRegionX = Math.floorDiv(Math.floorDiv(centerX - radius, 16), spacing);
                    int maxRegionX = Math.floorDiv(Math.floorDiv(centerX + radius, 16), spacing);
                    int minRegionZ = Math.floorDiv(Math.floorDiv(centerZ - radius, 16), spacing);
                    int maxRegionZ = Math.floorDiv(Math.floorDiv(centerZ + radius, 16), spacing);

                    for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                        for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                            // Takes any chunk of the region
                            ChunkPos chunk = spread.getPotentialStructureChunk(this.seed, regionX * spacing, regionZ * spacing);

                            if (isInRange(chunk, centerX, centerZ, radiusSq) && spread.isStructureChunk(this.structureState, chunk.x(), chunk.z())) {
                                this.addIfValid(found, set.value(), chunk);
                            }
                        }
                    }
                }
                else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                    for (ChunkPos chunk : this.structureState.getRingPositionsFor(rings)) {
                        if (isInRange(chunk, centerX, centerZ, radiusSq)) {
                            this.addIfValid(found, set.value(), chunk);
                        }
                    }
                }
            }

            return found;
        }

        private static boolean isInRange(ChunkPos chunk, int centerX, int centerZ, long radiusSq) {
            long dx = chunk.getMiddleBlockX() - centerX;
            long dz = chunk.getMiddleBlockZ() - centerZ;
            return dx * dx + dz * dz <= radiusSq;
        }

        /** Picks the structure of the set like vanilla does: weighted, retrying without the ones that fail. */
        private void addIfValid(List<FoundStructure> found, StructureSet set, ChunkPos chunk) {
            List<StructureSet.StructureSelectionEntry> entries = set.structures();

            if (entries.size() == 1) {
                FoundStructure structure = this.tryStructure(entries.get(0).structure(), chunk);

                if (structure != null) {
                    found.add(structure);
                }

                return;
            }

            List<StructureSet.StructureSelectionEntry> candidates = new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(this.seed, chunk.x(), chunk.z());
            int totalWeight = 0;

            for (StructureSet.StructureSelectionEntry entry : candidates) {
                totalWeight += entry.weight();
            }

            while (!candidates.isEmpty()) {
                int pick = random.nextInt(totalWeight);
                int index = 0;

                for (StructureSet.StructureSelectionEntry entry : candidates) {
                    pick -= entry.weight();

                    if (pick < 0) {
                        break;
                    }

                    index++;
                }

                StructureSet.StructureSelectionEntry entry = candidates.get(index);
                FoundStructure structure = this.tryStructure(entry.structure(), chunk);

                if (structure != null) {
                    found.add(structure);
                    return;
                }

                candidates.remove(index);
                totalWeight -= entry.weight();
            }
        }

        @Nullable
        private FoundStructure tryStructure(Holder<Structure> holder, ChunkPos chunk) {
            Structure structure = holder.value();
            String path = path(structureId(holder));

            // Vanilla's own check is exact, but jigsaw structures and ruined portals need the structure
            // templates (only the server has them loaded) to find their position, so those are approximated
            if (structure.type() != StructureType.JIGSAW && !path.startsWith("ruined_portal")) {
                try {
                    Structure.GenerationContext context = new Structure.GenerationContext(worldgenAccess, this.generator, this.biomeSource,
                            this.climateSampler, this.randomState, null, this.seed, chunk, this.heightAccessor, structure.biomes()::contains);
                    Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(context);

                    if (stub.isEmpty()) {
                        return null;
                    }

                    BlockPos pos = stub.get().position();
                    Holder<Biome> biome = this.biomes.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
                    return new FoundStructure(this.dimension, structureId(holder), structureId(biome), pos.getX(), pos.getY(), pos.getZ());
                }
                catch (RuntimeException e) {
                    GaritoneMineman.LOGGER.debug("Structure check of {} failed, approximating it", path, e);
                }
            }

            return this.approximate(holder, path, chunk);
        }

        /** Samples the biome at the chunk center, at the height the structure starts at. */
        @Nullable
        private FoundStructure approximate(Holder<Structure> holder, String path, ChunkPos chunk) {
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();
            int y = this.getStartY(path, x, z);
            Holder<Biome> biome = this.biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));

            if (!holder.value().biomes().contains(biome)) {
                return null;
            }

            return new FoundStructure(this.dimension, structureId(holder), structureId(biome), x, y, z);
        }

        /** The height a jigsaw structure or ruined portal starts at, where its biome is checked. */
        private int getStartY(String path, int x, int z) {
            if (this.dimension == StructureDimension.NETHER) {
                return path.equals("bastion_remnant") ? 33 : 64;
            }

            if (path.equals("ancient_city")) {
                return -27;
            }

            if (path.equals("trial_chambers")) {
                return -30;
            }

            int surface = this.generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.heightAccessor, this.randomState);
            // Trail ruins start buried 15 blocks below the surface
            return path.equals("trail_ruins") ? surface - 15 : surface;
        }
    }
}

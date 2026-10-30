package io.github.mgthorn.garitonemineman.client.structures;

/**
 * A structure found by {@link StructureLocator}.
 * @param structureId the structure, e.g. {@code minecraft:village_taiga}
 * @param biomeId the biome at the structure, e.g. {@code minecraft:snowy_taiga}
 * @param y surface height, or the depth the structure generates at for underground structures
 */
public record FoundStructure(StructureDimension dimension, String structureId, String biomeId, int x, int y, int z) {
    /** Stable ID, also stored on the JourneyMap waypoint. */
    public String key() {
        return this.dimension.id() + "|" + this.structureId + "|" + this.x + "," + this.z;
    }

    public String groupKey() {
        return StructureGroups.groupOf(this.structureId);
    }

    public double distanceSqTo(double px, double pz) {
        double dx = this.x - px;
        double dz = this.z - pz;
        return dx * dx + dz * dz;
    }
}

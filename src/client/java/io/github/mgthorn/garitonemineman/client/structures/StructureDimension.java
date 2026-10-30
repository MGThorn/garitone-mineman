package io.github.mgthorn.garitonemineman.client.structures;

import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The three columns of the structure screen, left to right. */
public enum StructureDimension {
    NETHER(Level.NETHER, "Nether", "netherStructs", 8.0),
    OVERWORLD(Level.OVERWORLD, "Overworld", "overworldStructs", 1.0),
    END(Level.END, "The End", "endStructs", 1.0);

    public final ResourceKey<Level> levelKey;
    public final String displayName;
    /** The JourneyMap waypoint group (folder) of this dimension. */
    public final String groupName;
    /** How many overworld blocks one block of this dimension is (8 for the Nether). */
    public final double coordinateScale;

    StructureDimension(ResourceKey<Level> levelKey, String displayName, String groupName, double coordinateScale) {
        this.levelKey = levelKey;
        this.displayName = displayName;
        this.groupName = groupName;
        this.coordinateScale = coordinateScale;
    }

    public String id() {
        return this.levelKey.identifier().toString();
    }

    /** Converts a coordinate from {@code from} into this dimension (only the Nether is scaled). */
    public double convertFrom(StructureDimension from, double coordinate) {
        return coordinate * from.coordinateScale / this.coordinateScale;
    }

    @Nullable
    public static StructureDimension of(ResourceKey<Level> levelKey) {
        for (StructureDimension dimension : values()) {
            if (dimension.levelKey.equals(levelKey)) {
                return dimension;
            }
        }

        return null;
    }

    @Nullable
    public static StructureDimension byId(String id) {
        for (StructureDimension dimension : values()) {
            if (dimension.id().equals(id)) {
                return dimension;
            }
        }

        return null;
    }
}

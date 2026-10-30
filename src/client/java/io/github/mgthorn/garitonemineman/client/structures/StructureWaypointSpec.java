package io.github.mgthorn.garitonemineman.client.structures;

/**
 * One structure waypoint as it should exist in the map mod.
 * @param key stable ID of the structure, used to find the waypoint again
 * @param dimension the dimension ID, e.g. {@code minecraft:the_nether}
 * @param groupName the waypoint folder, e.g. {@code overworldStructs}
 * @param color RGB color
 */
public record StructureWaypointSpec(String key, String name, String dimension, String groupName,
                                    int x, int y, int z, int color, boolean enabled) {
}

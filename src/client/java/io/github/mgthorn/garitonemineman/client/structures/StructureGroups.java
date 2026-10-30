package io.github.mgthorn.garitonemineman.client.structures;

import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Groups structure variants into one row of the structure screen ({@code village_plains} and
 * {@code village_taiga} are both "Village"), with a display name and a waypoint color per group.
 */
public final class StructureGroups {
    /** Path prefixes whose variants are shown as one group. */
    private static final String[] VARIANT_PREFIXES = { "village", "ocean_ruin", "shipwreck", "ruined_portal", "mineshaft", "abandoned_camp" };

    private static final Map<String, String> NAMES = Map.of(
            "fortress", "Nether Fortress",
            "monument", "Ocean Monument",
            "mansion", "Woodland Mansion",
            "jungle_pyramid", "Jungle Temple",
            "ocean_ruin", "Ocean Ruins",
            "igloo", "Igloo");

    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("village", 0x55FF55),
            Map.entry("pillager_outpost", 0x7F7F7F),
            Map.entry("desert_pyramid", 0xF2D16B),
            Map.entry("jungle_pyramid", 0x2E8B57),
            Map.entry("swamp_hut", 0x6B8E23),
            Map.entry("igloo", 0xE0FFFF),
            Map.entry("mansion", 0x8B4513),
            Map.entry("monument", 0x40E0D0),
            Map.entry("ocean_ruin", 0x4682B4),
            Map.entry("shipwreck", 0xA0522D),
            Map.entry("buried_treasure", 0xFFD700),
            Map.entry("ruined_portal", 0x9400D3),
            Map.entry("mineshaft", 0xCD853F),
            Map.entry("stronghold", 0x00BFFF),
            Map.entry("ancient_city", 0x1E3A5F),
            Map.entry("trail_ruins", 0xD2691E),
            Map.entry("trial_chambers", 0xFF8C00),
            Map.entry("fortress", 0xB22222),
            Map.entry("bastion_remnant", 0x333333),
            Map.entry("nether_fossil", 0xF5F5DC),
            Map.entry("end_city", 0xDA70D6),
            Map.entry("abandoned_camp", 0x8FBC8F));

    private StructureGroups() {}

    /** The group of a structure ID, e.g. {@code minecraft:village} for {@code minecraft:village_taiga}. */
    public static String groupOf(String structureId) {
        int colon = structureId.indexOf(':');
        String namespace = colon >= 0 ? structureId.substring(0, colon + 1) : "";
        String path = structureId.substring(colon + 1);

        for (String prefix : VARIANT_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "_")) {
                return namespace + prefix;
            }
        }

        return structureId;
    }

    /** The variant part of a structure ID, e.g. {@code taiga} for {@code minecraft:village_taiga}, or "". */
    public static String variantOf(String structureId) {
        String group = groupOf(structureId);

        if (group.length() >= structureId.length()) {
            return "";
        }

        return structureId.substring(group.length() + 1).replace('_', ' ');
    }

    public static String displayName(String groupKey) {
        String path = path(groupKey);
        String name = NAMES.get(path);

        if (name != null) {
            return name;
        }

        StringBuilder sb = new StringBuilder();

        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }

            if (!sb.isEmpty()) {
                sb.append(' ');
            }

            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }

        return sb.toString();
    }

    /** RGB waypoint color of a group; unknown (modded) groups get a color from their ID. */
    public static int color(String groupKey) {
        Integer color = COLORS.get(path(groupKey));

        if (color != null) {
            return color;
        }

        float hue = (groupKey.hashCode() & 0xFFFF) / 65535.0F;
        return Mth.hsvToRgb(hue, 0.7F, 1.0F) & 0xFFFFFF;
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}

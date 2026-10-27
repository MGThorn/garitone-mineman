package io.github.mgthorn.garitonemineman.client.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.data.Color4f;

/**
 * Per-ID ESP colors, stored in a {@link ConfigStringList} as {@code "<id>=#AARRGGBB"} entries.
 * IDs without an entry use the default color, so only changed colors are stored.
 */
public final class EspColors {
    private EspColors() {}

    /** The yellow Block ESP always used, at 40 % opacity. */
    public static final int DEFAULT_BLOCK_COLOR = new Color4f(1f, 0.85f, 0f, 0.4f).intValue;

    public static Map<String, Integer> parse(List<String> entries) {
        Map<String, Integer> colors = new HashMap<>();

        for (String entry : entries) {
            int sep = entry.lastIndexOf('=');

            if (sep > 0) {
                String value = entry.substring(sep + 1).trim();
                colors.put(entry.substring(0, sep).trim(), Color4f.getColorFromString(value, 0xFFFFFFFF));
            }
        }

        return colors;
    }

    public static int get(ConfigStringList config, String id, int defaultColor) {
        return parse(config.getStrings()).getOrDefault(id, defaultColor);
    }

    /** Sets the color of one ID; setting it back to the default removes the entry. */
    public static void set(ConfigStringList config, String id, int color, int defaultColor) {
        List<String> entries = new ArrayList<>();

        for (String entry : config.getStrings()) {
            int sep = entry.lastIndexOf('=');

            if (sep <= 0 || !entry.substring(0, sep).trim().equals(id)) {
                entries.add(entry);
            }
        }

        if (color != defaultColor) {
            entries.add(id + "=" + Color4f.getHexColorString(color));
        }

        config.setStrings(entries);
    }
}

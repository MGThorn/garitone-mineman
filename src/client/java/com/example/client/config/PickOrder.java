package com.example.client.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum PickOrder implements IConfigOptionListEntry {
    MOST_SATURATION("most_saturation", "Most Saturation"),
    MOST_HUNGER    ("most_hunger",     "Most Hunger"),
    FIRST_FOUND    ("first_found",     "First Found"),
    MOST_ITEMS     ("most_items",      "Most Items");

    private final String stringValue;
    private final String displayName;

    PickOrder(String stringValue, String displayName) {
        this.stringValue = stringValue;
        this.displayName = displayName;
    }

    @Override public String getStringValue() { return stringValue; }
    @Override public String getDisplayName()  { return displayName; }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        PickOrder[] values = PickOrder.values();
        int next = (ordinal() + (forward ? 1 : values.length - 1)) % values.length;
        return values[next];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (PickOrder v : PickOrder.values()) {
            if (v.stringValue.equalsIgnoreCase(value)) return v;
        }
        return MOST_SATURATION;
    }
}

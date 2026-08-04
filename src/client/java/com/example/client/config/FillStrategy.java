package com.example.client.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

public enum FillStrategy implements IConfigOptionListEntry {
    FIRST_AVAILABLE_SLOT("first_available_slot", "First Available Slot"),
    STACK_MERGE_FIRST   ("stack_merge_first",    "Stack Merge First");

    private final String stringValue;
    private final String displayName;

    FillStrategy(String stringValue, String displayName) {
        this.stringValue = stringValue;
        this.displayName = displayName;
    }

    @Override public String getStringValue() { return stringValue; }
    @Override public String getDisplayName()  { return displayName; }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        FillStrategy[] values = FillStrategy.values();
        int next = (ordinal() + (forward ? 1 : values.length - 1)) % values.length;
        return values[next];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (FillStrategy v : FillStrategy.values()) {
            if (v.stringValue.equalsIgnoreCase(value)) return v;
        }
        return FIRST_AVAILABLE_SLOT;
    }
}

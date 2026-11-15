package io.github.mgthorn.garitonemineman.client.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/** Where storage point contents come from on a server, without opening the chests (setting storageContentsSource). */
public enum ContentsSource implements IConfigOptionListEntry {
    AUTO        ("auto",         "Auto"),
    SERVUX      ("servux",       "Servux (MiniHUD)"),
    OP_QUERY    ("op_query",     "Op query"),
    OPENING_ONLY("opening_only", "Only when opened");

    private final String stringValue;
    private final String displayName;

    ContentsSource(String stringValue, String displayName) {
        this.stringValue = stringValue;
        this.displayName = displayName;
    }

    @Override public String getStringValue() { return stringValue; }
    @Override public String getDisplayName()  { return displayName; }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        ContentsSource[] values = ContentsSource.values();
        int next = (ordinal() + (forward ? 1 : values.length - 1)) % values.length;
        return values[next];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (ContentsSource v : ContentsSource.values()) {
            if (v.stringValue.equalsIgnoreCase(value)) return v;
        }
        return AUTO;
    }
}

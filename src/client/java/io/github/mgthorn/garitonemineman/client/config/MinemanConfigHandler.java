package io.github.mgthorn.garitonemineman.client.config;

import java.nio.file.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import io.github.mgthorn.garitonemineman.Reference;

public class MinemanConfigHandler implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    @Override
    public void load() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
        JsonElement element = JsonUtils.parseJsonFile(configFile);
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            ConfigUtils.readConfigBase(root, "Generic", Configs.Generic.CONFIG_LIST);
            ConfigUtils.readHotkeys(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
            ConfigUtils.readConfigBase(root, "EspEditor", Configs.EspEditor.CONFIG_LIST);
        }
    }

    @Override
    public void save() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Configs.Generic.CONFIG_LIST);
        ConfigUtils.writeHotkeys(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
        ConfigUtils.writeConfigBase(root, "EspEditor", Configs.EspEditor.CONFIG_LIST);
        JsonUtils.writeJsonToFile(root, configFile);
    }
}

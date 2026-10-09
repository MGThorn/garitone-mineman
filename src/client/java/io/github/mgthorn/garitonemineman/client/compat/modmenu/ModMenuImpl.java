package io.github.mgthorn.garitonemineman.client.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.mgthorn.garitonemineman.client.gui.GuiMinemanMain;

public class ModMenuImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (screen) -> {
            GuiMinemanMain gui = new GuiMinemanMain(screen);
            return gui;
        };
    }
}

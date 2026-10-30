package io.github.mgthorn.garitonemineman.client.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import io.github.mgthorn.garitonemineman.Reference;

/**
 * JourneyMap plugin, registered through the {@code journeymap} entrypoint in fabric.mod.json.
 * JourneyMap only accepts waypoints from mods that registered a plugin, so this hands its API to
 * {@link JourneyMapWaypoints}.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class MinemanJourneyMapPlugin implements IClientPlugin {
    @Override
    public void initialize(IClientAPI api) {
        JourneyMapWaypoints.setApi(api);
    }

    @Override
    public String getModId() {
        return Reference.MOD_ID;
    }
}

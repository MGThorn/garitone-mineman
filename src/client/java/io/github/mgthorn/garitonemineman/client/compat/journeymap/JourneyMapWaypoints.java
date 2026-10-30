package io.github.mgthorn.garitonemineman.client.compat.journeymap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import io.github.mgthorn.garitonemineman.Reference;
import io.github.mgthorn.garitonemineman.client.structures.StructureWaypointSpec;

/**
 * Creates and updates our structure waypoints in JourneyMap. Only call this when JourneyMap is loaded
 * (see {@code JourneyMapCompat}).
 * <p>
 * Each waypoint carries its {@link StructureWaypointSpec#key()} as custom data, so it can be found again
 * after a restart. Waypoints are persistent in JourneyMap; turning one off only disables it.
 */
public final class JourneyMapWaypoints {
    private static final String KEY_DATA = Reference.MOD_ID + "_key";

    @Nullable private static IClientAPI api;

    private JourneyMapWaypoints() {}

    static void setApi(IClientAPI clientApi) {
        api = clientApi;
    }

    /** Whether JourneyMap has initialized our plugin. */
    public static boolean isReady() {
        return api != null;
    }

    /**
     * Brings JourneyMap in line with the given waypoints: enabled ones are created if missing,
     * and existing ones are enabled or disabled. Disabled waypoints that don't exist yet are not created.
     * <p>
     * JourneyMap writes its whole waypoint file on every change, on the render thread, so at most
     * {@code maxChanges} waypoints are changed per call. Hundreds at once (e.g. "All" Nether fortresses)
     * froze and killed the game.
     * @return true when everything is in line; false if there is more to do (call again next tick)
     *         or JourneyMap hasn't initialized our plugin yet
     */
    public static boolean sync(List<StructureWaypointSpec> specs, int maxChanges) {
        IClientAPI clientApi = api;

        if (clientApi == null) {
            return false;
        }

        int changes = 0;

        Map<String, Waypoint> existing = new HashMap<>();

        for (Waypoint waypoint : clientApi.getWaypoints(Reference.MOD_ID)) {
            String key = waypoint.getCustomData(KEY_DATA);

            if (key != null) {
                existing.put(key, waypoint);
            }
        }

        Map<String, WaypointGroup> groups = new HashMap<>();

        for (StructureWaypointSpec spec : specs) {
            Waypoint waypoint = existing.get(spec.key());
            boolean needsCreate = waypoint == null && spec.enabled();
            boolean needsToggle = waypoint != null && waypoint.isEnabled() != spec.enabled();

            if (!needsCreate && !needsToggle) {
                continue;
            }

            if (changes >= maxChanges) {
                return false;
            }

            if (needsCreate) {
                create(clientApi, spec, groups.computeIfAbsent(spec.groupName(), name -> getOrCreateGroup(clientApi, name)));
            }
            else {
                waypoint.setEnabled(spec.enabled());
                // Adding an existing waypoint again saves the change
                clientApi.addWaypoint(Reference.MOD_ID, waypoint);
            }

            changes++;
        }

        return true;
    }

    private static void create(IClientAPI clientApi, StructureWaypointSpec spec, WaypointGroup group) {
        Waypoint waypoint = WaypointFactory.createWaypoint(Reference.MOD_ID,
                new BlockPos(spec.x(), spec.y(), spec.z()), spec.name(), spec.dimension(), true);
        waypoint.setColor(spec.color() & 0xFFFFFF);
        waypoint.setCustomData(KEY_DATA, spec.key());
        waypoint.setEnabled(true);
        clientApi.addWaypoint(Reference.MOD_ID, waypoint);
        group.addWaypoint(waypoint);
    }

    private static WaypointGroup getOrCreateGroup(IClientAPI clientApi, String name) {
        WaypointGroup group = clientApi.getWaypointGroupByName(Reference.MOD_ID, name);

        if (group == null) {
            group = WaypointFactory.createWaypointGroup(Reference.MOD_ID, name);
            group.setPersistent(true);
            clientApi.addWaypointGroup(group);
        }

        return group;
    }
}

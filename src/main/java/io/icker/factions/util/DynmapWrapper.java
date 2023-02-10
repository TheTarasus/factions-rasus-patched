package io.icker.factions.util;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.ClaimEvents;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.api.persistents.Claim;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.Home;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.*;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class DynmapWrapper {
    private DynmapCommonAPI api;
    private MarkerAPI markerApi;
    private MarkerSet markerSet;
    @Nullable
    private MinecraftServer server;
    private boolean loadWhenReady = false;

    public DynmapWrapper() {
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI dCAPI) {
                api = dCAPI;
                markerApi = api.getMarkerAPI();
                markerSet = markerApi.getMarkerSet("dynmap-factions");
                if (markerSet == null) {
                    markerSet = markerApi.createMarkerSet("dynmap-factions", "The Dynmap Factions integration", null, true);
                }
                markerSet.getMarkers().forEach(GenericMarker::deleteMarker);
                generateMarkers();
            }
        });

        ClaimEvents.ADD.register(this::addClaim);
        ClaimEvents.REMOVE.register(this::removeClaim);

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            this.server = server;
            if (loadWhenReady) {
                loadWhenReady = false;

                generateMarkers();
            }
        });

        FactionEvents.SET_HOME.register(this::setHome);
        FactionEvents.MODIFY.register(faction -> updateFaction(faction));
        FactionEvents.MEMBER_JOIN.register((faction, user) -> updateFaction(faction));
        FactionEvents.MEMBER_LEAVE.register((faction, user) -> updateFaction(faction));
        FactionEvents.POWER_CHANGE.register((faction, oldPower) -> updateFaction(faction));
    }

    private void generateMarkers() {
        if (server == null) {
            loadWhenReady = true;
            FactionsMod.LOGGER.info("Server hasn't loaded, postponing dynmap marker loading");
            return;
        }

        for (Faction faction : Faction.all()) {
            Home home = faction.getHome();
            if (home != null) {
                setHome(faction, home);
            }

            String info = getInfo(faction);
            for (Claim claim : faction.getClaims()) {
                addClaim(claim, info);
            }
        }
    }

    private void addClaim(Claim claim, String factionInfo) {
        Faction faction = claim.getFaction(); 
        ChunkPos pos = new ChunkPos(claim.x, claim.z);

        AreaMarker marker = markerSet.createAreaMarker(
            claim.getKey(), factionInfo, 
            true, dimensionTagToID(claim.level), 
            new double[]{pos.getStartX(), pos.getEndX() + 1}, 
            new double[]{pos.getStartZ(), pos.getEndZ() + 1},
            true
        );
        if (marker != null) {
            marker.setFillStyle(marker.getFillOpacity(), faction.getColor().getColorValue());
            marker.setLineStyle(marker.getLineWeight(), marker.getLineOpacity(), faction.getColor().getColorValue());
        }
    }

    private void addClaim(Claim claim) {
        addClaim(claim, getInfo(claim.getFaction()));
    }

    private void removeClaim(int x, int z, String level, Faction faction) {
        String areaMarkerId = String.format("%s-%d-%d", level, x, z);
        markerSet.findAreaMarker(areaMarkerId).deleteMarker();
    }

    private void updateFaction(Faction faction) {
        String info = getInfo(faction);

        for (Claim claim : faction.getClaims()) {
            AreaMarker marker = markerSet.findAreaMarker(claim.getKey());

            marker.setFillStyle(marker.getFillOpacity(), faction.getColor().getColorValue());
            marker.setLineStyle(marker.getLineWeight(), marker.getLineOpacity(), faction.getColor().getColorValue());
            marker.setDescription(info);
        }
    }

    private void setHome(Faction faction, Home home) {
        FactionsMod.LOGGER.info("Set home");
        Marker marker = markerSet.findMarker(faction.getID().toString() + "-home");
        if (marker == null) {
            markerSet.createMarker(faction.getID().toString() + "-home", faction.getName() + "'s Home", dimensionTagToID(home.level), home.x, home.y, home.z, null, true);
        } else {
            marker.setLocation(dimensionTagToID(home.level), home.x, home.y, home.z);
        }
    }

    public String getWorldName(World w) { // Taken from the Dynmap mod (Credit to them)
        RegistryKey<World> rk = w.getRegistryKey();
        if (rk == World.OVERWORLD) {
            return w.getServer().getSaveProperties().getLevelName();
        } else if (rk == World.END) {
            return "DIM1";
        } else if (rk == World.NETHER) {
            return "DIM-1";
        } else {
            return rk.getValue().getNamespace() + "_" + rk.getValue().getPath();
        }
    }

    public String dimensionTagToID(String dimension_id) {
        if (server == null) {
            FactionsMod.LOGGER.warn("Server object has not been initialized please run the dynmap reload command");
            return dimension_id;
        }

        Optional<RegistryKey<World>> worldKey = server.getWorldRegistryKeys().stream().filter(key -> Objects.equals(key.getValue(), new Identifier(dimension_id))).findAny();

        if (worldKey.isEmpty()) {
            FactionsMod.LOGGER.error("Unable to find world");
            return dimension_id;
        }

        ServerWorld world = server.getWorld(worldKey.get());

        return getWorldName(world);
    }

    private String getInfo(Faction faction) {
        return "Name: " + faction.getName() + "<br>"
            + "Description: " + faction.getDescription() + "<br>"
            + "Power: " + faction.getPower() + "<br>"
            + "Number of members: " + faction.getUsers().size();// + "<br>"
            //+ "Allies: " + Ally.getAllies(faction.getName).stream().map(ally -> ally.target).collect(Collectors.joining(", "));
    }

    public void reloadAll() {
        markerSet.deleteMarkerSet();
        markerSet = markerApi.createMarkerSet("dynmap-factions", "The Dynmap Factions integration", null, true);
        generateMarkers();
    }
}

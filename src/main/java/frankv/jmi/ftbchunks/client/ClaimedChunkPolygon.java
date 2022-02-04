package frankv.jmi.ftbchunks.client;

import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.net.SendChunkPacket;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.event.ClientTeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.event.TeamEvent;
import frankv.jmi.JMI;
import frankv.jmi.JMIOverlayHelper;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.PolygonOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

import static frankv.jmi.JMIOverlayHelper.*;
import static frankv.jmi.JMIOverlayHelper.createPolygon;

public class ClaimedChunkPolygon {
    private IClientAPI jmAPI;
    private static final Minecraft mc = Minecraft.getInstance();

    public static HashMap<ChunkDimPos, PolygonOverlay> chunkOverlays = new HashMap<>();
    public static HashMap<ChunkDimPos, FTBClaimedChunkData> chunkData = new HashMap<>();
    public static HashMap<ChunkDimPos, PolygonOverlay> forceLoadedOverlays = new HashMap<>();
    public static List<FTBClaimedChunkData> queue = new LinkedList<>();


    public ClaimedChunkPolygon(IClientAPI jmAPI) {
        this.jmAPI = jmAPI;
        TeamEvent.CLIENT_PROPERTIES_CHANGED.register(this::onTeamPropsChanged);
    }

    public static String getPolygonTitleByPlayerPos() {
        if (mc.player == null) return "";

        ChunkDimPos pos = new ChunkDimPos(mc.player.level.dimension(), mc.player.xChunk, mc.player.zChunk);
        if (!chunkOverlays.containsKey(pos)) return "Wilderness";
        return chunkOverlays.get(pos).getTitle();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!JMI.CLIENT_CONFIG.getFtbChunks()) return;
        if (mc.level == null) return;

        for (int i = 0; i<200; i++) {
            if (queue == null || queue.isEmpty()) return;

            RegistryKey<World> playerDim = mc.level.dimension();
            FTBClaimedChunkData data = queue.get(0);

            if (data.team == null) removeChunk(data, playerDim);
            else if (shouldReplace(data)) replaceChunk(data, playerDim);
            else addChunk(data, playerDim);
            queue.remove(0);

        }
    }

    public void createPolygonsOnMappingStarted() {
        ClientWorld level = mc.level;

        if (level == null) return;

        for (FTBClaimedChunkData data : chunkData.values()) {
            if (!data.chunkDimPos.dimension.equals(level.dimension())) continue;
            if (createPolygon(data.overlay)) chunkOverlays.put(data.chunkDimPos, data.overlay);
        }
    }

    private void addChunk(FTBClaimedChunkData data, RegistryKey<World> dim) {
        final ChunkDimPos pos = data.chunkDimPos;

        if (chunkOverlays.containsKey(pos)) return;

        chunkData.put(pos, data);
        if (!pos.dimension.equals(dim)) return;

        if (createPolygon(data.overlay)) chunkOverlays.put(data.chunkDimPos, data.overlay);

    }

    private void removeChunk(FTBClaimedChunkData data, RegistryKey<World> dim) {
        final ChunkDimPos pos = data.chunkDimPos;

        if (!chunkOverlays.containsKey(pos)) return;

        chunkData.remove(pos);

        if (!pos.dimension.equals(dim)) return;

        try {
            jmAPI.remove(chunkOverlays.get(pos));
            chunkOverlays.remove(pos);
            chunkData.remove(pos);
        } catch (Throwable t) {
            JMI.LOGGER.error(t.getMessage(), t);
        }
    }

    private void replaceChunk(FTBClaimedChunkData data, RegistryKey<World> dim) {
        removeChunk(data, dim);
        addChunk(data, dim);
        if (ClaimingMode.activated) {
            showForceLoaded(data.chunkDimPos, false);
            showForceLoaded(data.chunkDimPos, true);
        }
    }

    public void showForceLoadedByArea(boolean show) {
        World level = mc.player.level;
        if (level == null) return;

        if (!show) {
            for (ChunkDimPos pos : forceLoadedOverlays.keySet()) {
                chunkOverlays.get(pos).setTitle(chunkData.get(pos).team.getDisplayName());
            }

            removePolygons(forceLoadedOverlays.values());
            forceLoadedOverlays.clear();
            return;
        }

        for (ChunkPos p : ClaimingMode.area) {
            ChunkDimPos chunkDimPos = new ChunkDimPos(level.dimension(), p.x, p.z);
            showForceLoaded(chunkDimPos, true);
        }
    }

    private void showForceLoaded(ChunkDimPos chunkDimPos, boolean show) {
        if (!chunkData.containsKey(chunkDimPos)) return;
        FTBClaimedChunkData data = chunkData.get(chunkDimPos);
        String teamName = data.team.getDisplayName();

        if (show && data.forceLoaded && !forceLoadedOverlays.containsKey(chunkDimPos)) {
            PolygonOverlay claimedOverlay = ClaimingMode.forceLoadedPolygon(chunkDimPos);
            if (createPolygon(claimedOverlay)) forceLoadedOverlays.put(chunkDimPos, claimedOverlay);

            chunkOverlays.get(chunkDimPos).setTitle(teamName + "\nForce Loaded");

        } else if (!show && forceLoadedOverlays.containsKey(chunkDimPos)) {
            jmAPI.remove(forceLoadedOverlays.get(chunkDimPos));
            forceLoadedOverlays.remove(chunkDimPos);

            chunkOverlays.get(chunkDimPos).setTitle(teamName);
        }
    }

    public void onTeamPropsChanged(ClientTeamPropertiesChangedEvent event) {
        UUID teamId = event.getTeam().getId();
        RegistryKey<World> dim = mc.level.dimension();

        for (FTBClaimedChunkData data : new HashSet<>(chunkData.values())) {
            if (!data.teamId.equals(teamId)) continue;

            data.updateOverlayProps();
            replaceChunk(data, dim);
        }
    }

    public static void addToQueue(MapDimension dim, SendChunkPacket.SingleChunk chunk, UUID teamId) {
        if (!JMI.ftbchunks) return;
        queue.add(new FTBClaimedChunkData(dim, chunk, teamId));
    }

    private static boolean shouldReplace(FTBClaimedChunkData data) {
        if (data.team == null) return false;

        FTBClaimedChunkData that = chunkData.get(data.chunkDimPos);
        if (that == null) return false;
        return !data.equals(that);
    }
}
package me.frankv.jmi.compat.ftbchunks;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import lombok.Getter;
import me.frankv.jmi.Constants;
import me.frankv.jmi.api.jmoverlay.IClientConfig;
import me.frankv.jmi.api.jmoverlay.ToggleableOverlay;
import me.frankv.jmi.util.OverlayHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

public enum ClaimingMode implements ToggleableOverlay {
    INSTANCE;

    private IClientAPI jmAPI;
    private final Minecraft mc = Minecraft.getInstance();

    @Getter
    private ClaimingModeHandler handler;

    @Getter
    private boolean activated = false;
    @Getter
    private PolygonOverlay claimAreaPolygon = null;
    @Getter
    private Set<ChunkPos> area = new HashSet<>();

    @Getter
    private String buttonLabel = "FTBChunks Claiming Mode";
    @Getter
    private final int order = 0;


    public void init(IClientAPI jmAPI, IClientConfig config) {
        this.jmAPI = jmAPI;
        handler = new ClaimingModeHandler(this);
    }

    private void removeOverlays() {
        if (claimAreaPolygon == null) return;
        jmAPI.remove(claimAreaPolygon);

        ClaimedChunkPolygon.INSTANCE.showForceLoadedByArea(false);
        claimAreaPolygon = null;
        area.clear();
    }

    PolygonOverlay dragPolygon(XZ xz) {
        final var player = Minecraft.getInstance().player;
//        final var displayId = "drag_polygon_" + xz.x() + "_" + xz.z();

        final var shapeProps = new ShapeProperties()
                .setStrokeWidth(0)
                .setFillColor(0xffffff).setFillOpacity(.3f);

        final var polygon = PolygonHelper.createChunkPolygon(xz.x(), 10, xz.z());

        return new PolygonOverlay(Constants.MOD_ID, player.clientLevel.dimension(), shapeProps, polygon);
    }

    PolygonOverlay forceLoadedPolygon(ChunkDimPos pos) {
        final var player = Minecraft.getInstance().player;
//        final var displayId = "ftb_force_loaded_" + pos.x() + "_" + pos.z();

        final var shapeProps = new ShapeProperties()
                .setStrokeWidth(2).setStrokeColor(0xff0000)
                .setFillOpacity(0f);

        final var polygon = PolygonHelper.createChunkPolygon(pos.x(), 10, pos.z());

        return new PolygonOverlay(Constants.MOD_ID, player.clientLevel.dimension(), shapeProps, polygon);
    }

    private void createClaimingAreaOverlays() {
        final var player = Minecraft.getInstance().player;
        final var startPoint = new ChunkPos(player.chunkPosition().x - 7, player.chunkPosition().z - 7);

//        final var displayId = "claim_mode";
        final var shapeProps = new ShapeProperties()
                .setStrokeWidth(3)
                .setStrokeColor(0xffffff).setStrokeOpacity(1.0f)
                .setFillOpacity(0f);

        for (var x = 0; x < 15; x++) {
            for (var z = 0; z < 15; z++) {
                area.add(new ChunkPos(startPoint.x + x, startPoint.z + z));
            }
        }

        ClaimedChunkPolygon.INSTANCE.showForceLoadedByArea(true);

        final var polygons = PolygonHelper.createChunksPolygon(area, 100);

        final var overlay = new PolygonOverlay(Constants.MOD_ID, player.level().dimension(), shapeProps, polygons.get(0));
        OverlayHelper.showOverlay(overlay);
        claimAreaPolygon = overlay;
    }

    public void onScreenClose(Screen screen) {
        if (!activated) return;
        if (!(screen instanceof IFullscreen)) return;
        activated = false;
        removeOverlays();
    }

    @Override
    public void onToggle(IThemeButton button) {
        if (mc.player == null) return;
        if (!activated) {
            createClaimingAreaOverlays();
        } else {
            removeOverlays();
        }
        ClaimedChunkPolygon.INSTANCE.onClaiming(activated);
        activated = !activated;
        button.setToggled(activated);
    }


    @Override
    public ResourceLocation getButtonIconName() {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "grid");
    }
}

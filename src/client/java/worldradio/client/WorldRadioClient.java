package worldradio.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import worldradio.WorldRadio;
import worldradio.block.AmplifierBlockEntity;
import worldradio.block.RadioBlockEntity;
import worldradio.block.SignalBlock;
import worldradio.client.audio.RadioSoundInstance;
import worldradio.client.audio.SourceTracker;
import worldradio.client.audio.StationStream;
import worldradio.client.audio.StreamPool;
import worldradio.client.screen.AmplifierScreen;
import worldradio.client.screen.RadioScreen;
import worldradio.net.Packets;


public class WorldRadioClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        String version = FabricLoader.getInstance().getModContainer(WorldRadio.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        StationStream.userAgent = "WorldRadio/" + version + " (BaconCakeFactory)";
        StationStream.log = WorldRadio.LOGGER::info;
        SignalBlock.screenOpener = (level, pos) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (level.getBlockEntity(pos) instanceof RadioBlockEntity) minecraft.gui.setScreen(new RadioScreen(pos));
            else if (level.getBlockEntity(pos) instanceof AmplifierBlockEntity) minecraft.gui.setScreen(new AmplifierScreen(pos));
        };
        FavouritesCache.load();
        ClientPlayNetworking.registerGlobalReceiver(Packets.FavouritesSync.TYPE,
                (payload, context) -> FavouritesCache.importLegacy(payload.favourites()));
        ClientPlayNetworking.registerGlobalReceiver(Packets.Sources.TYPE, (payload, context) -> SourceTracker.setSources(payload));
        ClientTickEvents.END_CLIENT_TICK.register(SourceTracker::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
            SourceTracker.stopAll();
            SourceTracker.setSources(null);
            StreamPool.closeAll();
        });
        WorldRadio.LOGGER.info("World Radio client: sound category {}, home country for the search '{}'",
                RadioSoundInstance.category().getName(), worldradio.client.api.RadioBrowser.homeCountry());
        DevClientHooks.init();
    }
}

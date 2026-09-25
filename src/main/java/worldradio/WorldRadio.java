package worldradio;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worldradio.block.AmplifierBlock;
import worldradio.block.AmplifierBlockEntity;
import worldradio.block.RadioBlock;
import worldradio.block.RadioBlockEntity;
import worldradio.net.Packets;
import worldradio.server.RadioCommand;
import worldradio.server.RadioNetwork;

import java.util.function.Function;

public class WorldRadio implements ModInitializer {
    public static final String MOD_ID = "worldradio";
    public static final Logger LOGGER = LoggerFactory.getLogger("WorldRadio");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final Block RADIO = registerBlock("radio", props -> new RadioBlock(props
            .strength(1.5f).sound(SoundType.WOOD)));
    public static final Block AMPLIFIER = registerBlock("amplifier", props -> new AmplifierBlock(props
            .strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final BlockEntityType<RadioBlockEntity> RADIO_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("radio"),
            FabricBlockEntityTypeBuilder.create(RadioBlockEntity::new, RADIO).build());
    public static final BlockEntityType<AmplifierBlockEntity> AMPLIFIER_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("amplifier"),
            FabricBlockEntityTypeBuilder.create(AmplifierBlockEntity::new, AMPLIFIER).build());

    public static final Item RADIO_ITEM = registerItem("radio", props -> new BlockItem(RADIO, props.useBlockDescriptionPrefix()));
    public static final Item AMPLIFIER_ITEM = registerItem("amplifier", props -> new BlockItem(AMPLIFIER, props.useBlockDescriptionPrefix()));

    /** The mod's own creative tab; the items are not listed anywhere else. */
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"),
            FabricCreativeModeTab.builder()
                    .icon(() -> new ItemStack(RADIO_ITEM))
                    .title(Component.translatable("itemGroup.worldradio"))
                    .displayItems((params, output) -> {
                        output.accept(RADIO_ITEM);
                        output.accept(AMPLIFIER_ITEM);
                    })
                    .build());

    @Override
    public void onInitialize() {
        Config.load();
        Packets.register();
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
            if (be instanceof RadioBlockEntity || be instanceof AmplifierBlockEntity) RadioNetwork.get(level).add(be);
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
            if (be instanceof RadioBlockEntity || be instanceof AmplifierBlockEntity) RadioNetwork.get(level).unloaded(be);
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> RadioNetwork.get(level).tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RadioNetwork.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Packets.sendFavourites(handler.getPlayer());
            RadioNetwork.get((net.minecraft.server.level.ServerLevel) handler.getPlayer().level()).sendTo(handler.getPlayer());
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> RadioNetwork.get(to).sendTo(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                RadioNetwork.get((net.minecraft.server.level.ServerLevel) newPlayer.level()).sendTo(newPlayer));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, selection) -> RadioCommand.register(dispatcher));
        DevHooks.initServer();
        LOGGER.info("World Radio ready (range {} + {} per antenna block, up to {} blocks)", Config.get().baseRange(),
                Config.get().antennaStep(), Config.get().maxAntenna());
    }

    private static <T extends Block> T registerBlock(String name, Function<BlockBehaviour.Properties, T> factory) {
        Identifier id = id(name);
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id));
        return Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(props));
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
        Identifier id = id(name);
        Item.Properties props = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
        return Registry.register(BuiltInRegistries.ITEM, id, factory.apply(props));
    }
}

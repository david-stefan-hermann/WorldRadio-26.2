package worldradio;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Development helper, only active with -Dworldradio.dev.view=x,y,z,yaw,pitch (set by `-PdevScreenshot`): one second
 * after a player joins, the server teleports them to that view point so the client can take its screenshots. With
 * -Dworldradio.dev.hold=item_id the player also gets that item into the main hand (right-click tests with an item).
 */
public final class DevHooks {
    public static final String VIEW_PROPERTY = "worldradio.dev.view";

    private DevHooks() {
    }

    public static void initServer() {
        double[] view = parseView();
        if (view == null) return;
        WorldRadio.LOGGER.info("Dev view point active: {}", System.getProperty(VIEW_PROPERTY));
        String hold = System.getProperty("worldradio.dev.hold");
        java.util.Map<ServerPlayer, Integer> seen = new java.util.WeakHashMap<>();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int age = seen.merge(player, 1, Integer::sum);
                if (age != 20) continue;
                player.teleportTo(server.overworld(), view[0], view[1], view[2], Set.<Relative>of(),
                        (float) view[3], (float) view[4], false);
                WorldRadio.LOGGER.info("Dev view: teleported {}", player.getName().getString());
                if (hold != null) {
                    player.setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(hold))));
                    WorldRadio.LOGGER.info("Dev view: {} holds {}", player.getName().getString(),
                            player.getMainHandItem());
                }
            }
        });
    }

    public static double[] parseView() {
        String raw = System.getProperty(VIEW_PROPERTY);
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length != 5) return null;
        double[] view = new double[5];
        for (int i = 0; i < 5; i++) view[i] = Double.parseDouble(parts[i].trim());
        return view;
    }
}

package worldradio;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import worldradio.block.ReceiverBlockEntity;
import worldradio.item.Tuning;

import java.util.List;
import java.util.Set;

/**
 * Development helper, only active with -Dworldradio.dev.view=x,y,z,yaw,pitch (set by `-PdevScreenshot`): one second
 * after a player joins, the server teleports them to that view point so the client can take its screenshots. With
 * -Dworldradio.dev.hold=item_id the player also gets that item into the main hand (right-click tests with an item),
 * -Dworldradio.dev.offhand=item_id one into the offhand (the sneak switch must work with an occupied offhand).
 */
public final class DevHooks {
    public static final String VIEW_PROPERTY = "worldradio.dev.view";

    private DevHooks() {
    }

    public static void initServer() {
        if (System.getProperty("worldradio.dev.portable") != null) {
            int[] ticks = {0};
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (++ticks[0] == 40) portableTest(server.overworld());
            });
        }
        double[] view = parseView();
        if (view == null) return;
        WorldRadio.LOGGER.info("Dev view point active: {}", System.getProperty(VIEW_PROPERTY));
        String hold = System.getProperty("worldradio.dev.hold");
        String offhand = System.getProperty("worldradio.dev.offhand");
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
                if (offhand != null) {
                    player.setItemInHand(InteractionHand.OFF_HAND,
                            new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(offhand))));
                    WorldRadio.LOGGER.info("Dev view: {} holds {} in the offhand", player.getName().getString(),
                            player.getOffhandItem());
                }
            }
        });
    }

    /**
     * -Dworldradio.dev.portable=1 (`-PdevPortable`, tools/dev-test-portable.sh): a fake player puts a tuned portable
     * radio down and the block is broken again; logs PORTABLE PASS/FAIL per check and "PORTABLE TEST done".
     */
    private static void portableTest(ServerLevel level) {
        BlockPos ground = new BlockPos(20, -61, 20), at = ground.above();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        level.removeBlock(at, false);
        FakePlayer player = FakePlayer.get(level);
        player.setPos(20.5, -60, 23.5);
        Tuning tuning = new Tuning("http://example.org/test.mp3", "Test FM", false, 0.4f);
        ItemStack stack = new ItemStack(WorldRadio.PORTABLE_RADIO_ITEM);
        stack.set(WorldRadio.TUNING, tuning);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);

        InteractionResult plain = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        portableCheck("a plain right-click on a block does not put it down",
                plain == InteractionResult.PASS && level.getBlockState(at).isAir());
        player.setShiftKeyDown(true);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        portableCheck("sneak + right-click puts it down", level.getBlockState(at).is(WorldRadio.PORTABLE_RADIO));
        portableCheck("the item is used up", stack.isEmpty());
        portableCheck("the block took the item's setting", level.getBlockEntity(at) instanceof ReceiverBlockEntity radio
                && radio.url().equals(tuning.url()) && radio.name().equals(tuning.name()) && !radio.enabled()
                && radio.volume() == tuning.volume());
        portableCheck("it is heard for the portable radio's own range, " + Config.get().portableHearing() + " blocks",
                level.getBlockEntity(at) instanceof ReceiverBlockEntity radio && radio.range() == Config.get().portableHearing());
        portableCheck("a bare hand breaks it at once", level.getBlockState(at).getDestroySpeed(level, at) == 0);
        List<ItemStack> drops = Block.getDrops(level.getBlockState(at), level, at, level.getBlockEntity(at));
        portableCheck("broken, it drops one portable radio with that setting", drops.size() == 1
                && drops.get(0).is(WorldRadio.PORTABLE_RADIO_ITEM) && tuning.equals(drops.get(0).get(WorldRadio.TUNING)));
        level.removeBlock(at, false);
        WorldRadio.LOGGER.info("PORTABLE TEST done");
    }

    private static void portableCheck(String name, boolean passed) {
        WorldRadio.LOGGER.info("PORTABLE {} {}", passed ? "PASS" : "FAIL", name);
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

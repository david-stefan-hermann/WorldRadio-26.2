package worldradio.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.block.state.BlockState;
import worldradio.block.AmplifierBlockEntity;
import worldradio.block.RadioBlockEntity;
import worldradio.block.SignalBlock;
import worldradio.net.Packets;
import worldradio.signal.Signal;

/**
 * {@code /worldradio tune <pos> <url> [name]}, {@code /worldradio enable <pos> <true|false>},
 * {@code /worldradio volume <pos> <0-100>} and {@code /worldradio status <pos>} for admins and the test scripts;
 * players normally use the station screen.
 */
public final class RadioCommand {
    private RadioCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldradio")
                .requires(source -> source.permissions().hasPermission(
                        new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("tune")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> tune(ctx, ""))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> tune(ctx, StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(RadioCommand::enable))))
                .then(Commands.literal("status")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(RadioCommand::status)))
                .then(Commands.literal("volume")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(RadioCommand::volume)))));
    }

    private static int volume(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        int percent = IntegerArgumentType.getInteger(ctx, "percent");
        if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            ctx.getSource().sendFailure(Component.literal("No radio at " + pos.toShortString()));
            return 0;
        }
        radio.setVolume(percent / 100.0f);
        RadioNetwork.get(level).markDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Radio at " + pos.toShortString() + " volume " + percent + " %"), true);
        return 1;
    }

    private static int tune(CommandContext<CommandSourceStack> ctx, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        String url = StringArgumentType.getString(ctx, "url");
        if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            ctx.getSource().sendFailure(Component.literal("No radio at " + pos.toShortString()));
            return 0;
        }
        if (!url.isEmpty() && !Packets.isStreamUrl(url)) {
            ctx.getSource().sendFailure(Component.literal("Not an http(s) address: " + url));
            return 0;
        }
        radio.setStation(url, name, "");
        RadioNetwork.get(level).markDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Radio at " + pos.toShortString() + " tuned to " + url), true);
        return 1;
    }

    private static int enable(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        boolean on = BoolArgumentType.getBool(ctx, "on");
        if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) {
            ctx.getSource().sendFailure(Component.literal("No radio at " + pos.toShortString()));
            return 0;
        }
        radio.setEnabled(on);
        RadioNetwork.get(level).markDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("Radio at " + pos.toShortString() + (on ? " on" : " off")), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        StringBuilder out = new StringBuilder();
        if (level.getBlockEntity(pos) instanceof RadioBlockEntity radio) {
            out.append("radio range=").append(radio.range()).append(" antenna=").append(radio.antenna())
                    .append(" level=").append(level(radio.getBlockState())).append(" enabled=").append(radio.enabled())
                    .append(" volume=").append(Math.round(radio.volume() * 100))
                    .append(" url=").append(radio.url())
                    .append(" name=").append(radio.name());
        } else if (level.getBlockEntity(pos) instanceof AmplifierBlockEntity amp) {
            out.append("amplifier range=").append(amp.range()).append(" antenna=").append(amp.antenna())
                    .append(" level=").append(level(amp.getBlockState())).append(" signals=").append(amp.signals().size());
            for (Signal s : amp.signals()) {
                out.append(" | ").append(s.url()).append(" from ").append(s.radioX()).append(',').append(s.radioY())
                        .append(',').append(s.radioZ()).append(" hops=").append(s.hops())
                        .append(String.format(java.util.Locale.ROOT, " dist=%.1f factor=%.2f", s.distance(), s.factor()));
            }
        } else {
            out.append("nothing at ").append(pos.toShortString());
        }
        RadioNetwork network = RadioNetwork.get(level);
        out.append(" (network: ").append(network.radioCount()).append(" radios, ").append(network.amplifierCount())
                .append(" amplifiers)");
        String text = out.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int level(BlockState state) {
        return state.hasProperty(SignalBlock.LEVEL) ? state.getValue(SignalBlock.LEVEL) : -1;
    }
}

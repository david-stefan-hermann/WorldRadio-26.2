package worldradio.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import worldradio.WorldRadio;
import worldradio.client.audio.SourceTracker;
import worldradio.client.audio.StationStream;
import worldradio.client.audio.StreamPool;
import worldradio.client.screen.AmplifierScreen;
import worldradio.client.screen.RadioScreen;

import java.util.Locale;
import java.util.Map;

/**
 * With -Dworldradio.dev.screenshot=NAME the client waits after joining, optionally opens a screen
 * (-Dworldradio.dev.screen=radio|browse|url|search|amplifier,x,y,z, creative or sounds), saves screenshots/NAME.png and NAME-b.png, logs what it
 * hears every second and quits after -Dworldradio.dev.ticks (default 200). -Dworldradio.dev.walk=dx,dz moves the
 * player by that much every second after the first screenshot (distance test for the volume curve).
 */
public final class DevClientHooks {
    private static int ticks;
    private static boolean done;

    private DevClientHooks() {
    }

    public static void init() {
        String name = System.getProperty("worldradio.dev.screenshot");
        if (name == null) return;
        int end = Integer.getInteger("worldradio.dev.ticks", 200);
        WorldRadio.LOGGER.info("Dev screenshot active: {} ({} ticks)", name, end);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft, name, end));
    }

    private static void tick(Minecraft minecraft, String name, int end) {
        if (done || minecraft.level == null || minecraft.player == null) return;
        ticks++;
        if (ticks == 60) openScreen(minecraft);
        if (ticks == 80) typeKeys(minecraft);
        useBlock(minecraft);
        if (ticks == 80 && System.getProperty("worldradio.dev.star") != null
                && minecraft.gui.screen() instanceof RadioScreen radio) {
            radio.devPressStar();
        }
        if (ticks == 80 && System.getProperty("worldradio.dev.power") != null
                && minecraft.gui.screen() instanceof RadioScreen radio) {
            radio.devPressPower();
            WorldRadio.LOGGER.info("Dev: pressed the on/off button");
        }
        if (ticks == 100 && System.getProperty("worldradio.dev.power") != null
                && minecraft.level.getBlockEntity(powerPos()) instanceof worldradio.block.RadioBlockEntity r) {
            WorldRadio.LOGGER.info("Dev: after the on/off button: enabled={} url={} {}", r.enabled(), r.url(),
                    minecraft.level.getBlockState(powerPos()));
        }
        if (ticks >= 100 && ticks <= 141 && System.getProperty("worldradio.dev.hover") != null) hoverRangeLine(minecraft, ticks <= 140);
        if (minecraft.gui.screen() instanceof RadioScreen radio) radioScreenTests(radio);
        if (ticks == 130 || ticks == end - 20) minecraft.gui.toastManager().clear(); // join/tutorial toasts
        if (ticks == 140) grab(minecraft, name + ".png");
        if (ticks > 100 && ticks % 20 == 0) {
            if (ticks > 140) walk(minecraft);
            if (ticks > 140) orbit(minecraft);
            report(minecraft);
        }
        if (ticks == end - 10) grab(minecraft, name + "-b.png");
        if (ticks >= end) {
            done = true;
            minecraft.stop();
        }
    }

    /**
     * Station screen tests: -Dworldradio.dev.click=N clicks list row N at tick 80 and logs the marked rows at 100 and
     * 120; -Dworldradio.dev.rename=N opens the rename box of favorite N at tick 75 (TYPE then types into it at 80 and
     * -Dworldradio.dev.enter presses Enter at 85); -Dworldradio.dev.volume=0.4 moves the volume slider at tick 80.
     */
    private static void radioScreenTests(RadioScreen radio) {
        String click = System.getProperty("worldradio.dev.click");
        if (click != null && ticks == 70) WorldRadio.LOGGER.info("Dev: marked rows before the click: {}", radio.devMarkedRows());
        if (click != null && ticks == 80) radio.devClickRow(Integer.parseInt(click));
        if (click != null && (ticks == 100 || ticks == 120)) WorldRadio.LOGGER.info("Dev: marked rows after the click: {}", radio.devMarkedRows());
        String rename = System.getProperty("worldradio.dev.rename");
        if (rename != null && ticks == 75) radio.devStartRename(Integer.parseInt(rename));
        if (System.getProperty("worldradio.dev.enter") != null && ticks == 85) {
            int enter = com.mojang.blaze3d.platform.InputConstants.KEY_RETURN;
            radio.keyPressed(new net.minecraft.client.input.KeyEvent(enter, enter, 0));
            WorldRadio.LOGGER.info("Dev: pressed Enter, favorites now {}", FavouritesCache.list());
        }
        String volume = System.getProperty("worldradio.dev.volume");
        if (volume != null && ticks == 80) {
            radio.devSetVolume(Double.parseDouble(volume));
            WorldRadio.LOGGER.info("Dev: moved the volume slider to {}", volume);
        }
    }

    /** The block of -Dworldradio.dev.screen=...,x,y,z. */
    private static BlockPos powerPos() {
        String[] p = System.getProperty("worldradio.dev.screen", "radio,0,0,0").split(",");
        return new BlockPos(Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
    }

    private static void openScreen(Minecraft minecraft) {
        String raw = System.getProperty("worldradio.dev.screen");
        if (raw == null) return;
        String[] p = raw.split(",");
        if (p[0].trim().equals("creative")) {
            var screen = new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(minecraft.player,
                    minecraft.player.connection.enabledFeatures(), minecraft.options.operatorItemsTab().get());
            minecraft.gui.setScreen(screen);
            boolean ok = ((net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen) screen).setSelectedTab(WorldRadio.TAB);
            WorldRadio.LOGGER.info("Dev: opened the creative inventory on the World Radio tab: {}", ok);
            return;
        }
        if (p[0].trim().equals("sounds")) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.SoundOptionsScreen(null, minecraft.options));
            return;
        }
        BlockPos pos = new BlockPos(Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()));
        switch (p[0].trim()) {
            case "amplifier" -> minecraft.gui.setScreen(new AmplifierScreen(pos));
            case "browse" -> {
                RadioScreen.devCountry = System.getProperty("worldradio.dev.country");
                minecraft.gui.setScreen(new RadioScreen(pos, RadioScreen.Tab.BROWSE));
            }
            case "url" -> minecraft.gui.setScreen(new RadioScreen(pos, RadioScreen.Tab.URL));
            case "search" -> {
                RadioScreen.devSearch = System.getProperty("worldradio.dev.search");
                minecraft.gui.setScreen(new RadioScreen(pos, RadioScreen.Tab.SEARCH));
            }
            default -> minecraft.gui.setScreen(new RadioScreen(pos, RadioScreen.Tab.FAVOURITES));
        }
        WorldRadio.LOGGER.info("Dev: opened {} screen at {}", p[0], pos.toShortString());
    }

    /**
     * -Dworldradio.dev.type=TEXT clears the focused text box and types TEXT into it; -Dworldradio.dev.key=e presses
     * that letter key. Both go through keyPressed + charTyped like real input, and the screen left open is logged.
     */
    private static void typeKeys(Minecraft minecraft) {
        String text = System.getProperty("worldradio.dev.type");
        String key = System.getProperty("worldradio.dev.key");
        if (text == null && key == null) return;
        net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
        if (screen == null) return;
        if (text != null && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box) box.setValue("");
        String keys = text != null ? text : key;
        for (char c : keys.toCharArray()) {
            if (minecraft.gui.screen() != screen) break;
            // 26.3 key codes are scancodes, so look them up by their key.keyboard.* name like options.txt does.
            String name = Character.isLetterOrDigit(c) ? String.valueOf(Character.toLowerCase(c)) : c == '/' ? "slash" : c == '.' ? "period" : null;
            int code = name == null ? -1 : com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard." + name).getValue();
            if (!screen.keyPressed(new net.minecraft.client.input.KeyEvent(code, code, 0)) && minecraft.gui.screen() == screen) {
                screen.charTyped(new net.minecraft.client.input.CharacterEvent(c));
            }
        }
        net.minecraft.client.gui.screens.Screen after = minecraft.gui.screen();
        WorldRadio.LOGGER.info("Dev: typed '{}', screen now {}{}", keys, after == null ? "closed" : after.getClass().getSimpleName(),
                after != null && after.getFocused() instanceof net.minecraft.client.gui.components.EditBox box ? ", text box '" + box.getValue() + "'" : "");
    }

    /**
     * -Dworldradio.dev.use=x,y,z,plain|sneak,count[,face] right-clicks the block x,y,z count times (ticks 80, 90, ...)
     * on the given face (default south) with whatever the player holds, sneaking or not like a player would, and logs
     * the block state, the block above and the open screen after each click.
     */
    private static void useBlock(Minecraft minecraft) {
        String raw = System.getProperty("worldradio.dev.use");
        if (raw == null || minecraft.gameMode == null) return;
        String[] p = raw.split(",");
        BlockPos pos = new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        boolean sneak = p.length > 3 && p[3].trim().equals("sneak");
        int count = p.length > 4 ? Integer.parseInt(p[4].trim()) : 1;
        net.minecraft.core.Direction face = p.length > 5 ? net.minecraft.core.Direction.byName(p[5].trim()) : net.minecraft.core.Direction.SOUTH;
        if (ticks == 70 && sneak) minecraft.options.keyShift.setDown(true);
        int last = 80 + 10 * (count - 1);
        if (ticks >= 80 && ticks <= last && (ticks - 80) % 10 == 0) {
            if (minecraft.gui.screen() != null) minecraft.gui.setScreen(null);
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
            var result = minecraft.gameMode.useItemOn(minecraft.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            WorldRadio.LOGGER.info("Dev: right-click{} on {} face {} holding {} -> {}", sneak ? " (sneaking)" : "", pos.toShortString(),
                    face, minecraft.player.getMainHandItem(), result);
        }
        if (ticks >= 85 && ticks <= last + 5 && (ticks - 85) % 10 == 0) {
            var screen = minecraft.gui.screen();
            String radio = minecraft.level.getBlockEntity(pos) instanceof worldradio.block.RadioBlockEntity r
                    ? " enabled=" + r.enabled() + " url=" + r.url() : "";
            WorldRadio.LOGGER.info("Dev: after the click: {}{} above={} screen={}", minecraft.level.getBlockState(pos), radio,
                    minecraft.level.getBlockState(pos.above()), screen == null ? "none" : screen.getClass().getSimpleName());
        }
        if (ticks == last + 6 && sneak) minecraft.options.keyShift.setDown(false);
    }

    /**
     * -Dworldradio.dev.hover=1 puts the mouse on the range line of the open screen (ticks 100-140) so its tooltip shows
     * in the first screenshot, then into the top left corner for the second. The mouse handler's position is set
     * directly; the real cursor does not move.
     */
    private static void hoverRangeLine(Minecraft minecraft, boolean on) {
        if (worldradio.client.screen.RadioUi.lastRangeX < 0) return;
        double scale = on ? minecraft.getWindow().getGuiScale() : 0;
        try {
            var x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(minecraft.mouseHandler, (worldradio.client.screen.RadioUi.lastRangeX + 20) * scale);
            y.setDouble(minecraft.mouseHandler, (worldradio.client.screen.RadioUi.lastRangeY + 4) * scale);
        } catch (ReflectiveOperationException e) {
            WorldRadio.LOGGER.warn("Dev: cannot move the mouse: {}", e.toString());
        }
    }

    private static int orbitStep;

    /**
     * -Dworldradio.dev.orbit=cx,cz,r puts the player on a circle of radius r around the block cx,cz, a quarter turn per
     * second, always looking north: the radio is left, ahead, right and behind in turn (pan test).
     */
    private static void orbit(Minecraft minecraft) {
        String raw = System.getProperty("worldradio.dev.orbit");
        if (raw == null) return;
        String[] p = raw.split(",");
        double angle = Math.toRadians(90.0 * orbitStep++);
        double r = Double.parseDouble(p[2]);
        double px = Double.parseDouble(p[0]) + 0.5 + r * Math.cos(angle);
        double pz = Double.parseDouble(p[1]) + 0.5 + r * Math.sin(angle);
        minecraft.player.setPos(px, minecraft.player.getY(), pz);
        minecraft.player.setYRot(180.0f);
        minecraft.player.setXRot(10.0f);
    }

    private static void walk(Minecraft minecraft) {
        String raw = System.getProperty("worldradio.dev.walk");
        if (raw == null) return;
        String[] p = raw.split(",");
        Vec3 at = minecraft.player.position();
        minecraft.player.setPos(at.x + Double.parseDouble(p[0]), at.y, at.z + Double.parseDouble(p[1]));
    }

    private static void report(Minecraft minecraft) {
        Vec3 ear = minecraft.gameRenderer.mainCamera().position();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Vec3> e : SourceTracker.playing().entrySet()) {
            StationStream stream = StreamPool.find(e.getKey());
            out.append(String.format(Locale.ROOT, " [%s from %.0f,%.0f,%.0f dist=%.1f vol=%.3f state=%s decoded=%d %s title=%s]",
                    e.getKey(), e.getValue().x, e.getValue().y, e.getValue().z, e.getValue().distanceTo(ear),
                    SourceTracker.volumeOf(e.getKey()), stream == null ? "-" : stream.state(),
                    stream == null ? 0 : stream.samplesOut(), SourceTracker.debug(e.getKey()),
                    stream == null ? "" : stream.title()));
        }
        for (String url : SourceTracker.playing().keySet()) {
            WorldRadio.LOGGER.info("Radio: {} yaw={} ({})", SourceTracker.panOf(url),
                    String.format(Locale.ROOT, "%.0f", minecraft.gameRenderer.mainCamera().yRot()), url);
        }
        WorldRadio.LOGGER.info("Dev hear t={} at {}:{}", ticks, String.format(Locale.ROOT, "%.1f,%.1f,%.1f",
                ear.x, ear.y, ear.z), out.isEmpty() ? " nothing" : out);
    }

    private static void grab(Minecraft minecraft, String file) {
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.gameRenderer.mainRenderTarget(), 1, message ->
                WorldRadio.LOGGER.info("Screenshot: {}", message.getString()));
    }
}

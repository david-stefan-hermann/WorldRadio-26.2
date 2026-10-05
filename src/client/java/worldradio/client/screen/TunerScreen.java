package worldradio.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import worldradio.Config;
import worldradio.WorldRadio;
import worldradio.block.ReceiverBlockEntity;
import worldradio.client.audio.SourceTracker;
import worldradio.item.PortableRadioItem;
import worldradio.item.Tuning;
import worldradio.net.Packets;
import worldradio.signal.Reception;
import worldradio.signal.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tuner of a radio block or of the portable radio in a hand: the station it is set to with its stream state, the
 * stations whose signal arrives (at the block, or where the player stands) to pick from, a Turn off/Turn on key and
 * the volume; the block also shows its hearing range, which comes from the speakers connected to it.
 */
public class TunerScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 190;
    /** The volume slider starts right of the on/off key. */
    private static final int FOOTER_X = 82;
    /** Ticks between two packets while the slider is dragged. */
    private static final int SEND_EVERY = 4;

    /** The radio block, or null for the portable radio in {@link #hand}. */
    private final BlockPos pos;
    private final InteractionHand hand;
    private final StationList list = new StationList();
    private int left;
    private int top;
    private KeyButton power;
    private Slider volume;
    /** The slider's value while this screen is open (-1: not touched yet, take the radio's). */
    private double volumeValue = -1;
    private float volumeToSend = -1;
    private int cooldown;
    /** Whether the station this radio is set to arrives right now. */
    private boolean received;

    public TunerScreen(BlockPos pos) {
        super(Component.translatable("block.worldradio.receiver"));
        this.pos = pos;
        this.hand = null;
    }

    public TunerScreen(InteractionHand hand) {
        super(Component.translatable("item.worldradio.portable_radio"));
        this.pos = null;
        this.hand = hand;
    }

    private ReceiverBlockEntity block() {
        return pos != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof ReceiverBlockEntity r ? r : null;
    }

    /** What the radio is set to, or null when it is gone (block broken, item out of the hand). */
    private Tuning current() {
        if (pos != null) {
            ReceiverBlockEntity r = block();
            return r == null ? null : new Tuning(r.url(), r.name(), r.enabled(), r.volume());
        }
        if (minecraft.player == null) return null;
        ItemStack stack = minecraft.player.getItemInHand(hand);
        return stack.getItem() instanceof PortableRadioItem ? PortableRadioItem.tuning(stack) : null;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        list.setBounds(left + 8, top + 40, WIDTH - 16, HEIGHT - 72);
        list.setEmptyText(Component.translatable("worldradio.tuner.none").getString());
        int y = top + HEIGHT - 24;
        power = addRenderableWidget(new KeyButton(left + 8, y, 70, 18, Component.translatable("worldradio.station.off"), b -> togglePower()));
        Tuning now = current();
        if (volumeValue < 0) volumeValue = now == null ? 1.0 : now.volume();
        volume = addRenderableWidget(new Slider(left + FOOTER_X, y, WIDTH - FOOTER_X - 8, 18, volumeValue));
        volume.setTooltip(Tooltip.create(Component.translatable(pos == null ? "worldradio.volume.tooltip.portable" : "worldradio.volume.tooltip")));
        refreshList();
    }

    @Override
    public void tick() {
        Tuning now = current();
        if (now == null) {
            onClose();
            return;
        }
        power.setMessage(Component.translatable(now.enabled() ? "worldradio.station.off" : "worldradio.station.on"));
        refreshList();
        if (cooldown > 0) cooldown--;
        if (cooldown == 0 && volumeToSend >= 0) {
            flush();
            cooldown = SEND_EVERY;
        }
    }

    private void refreshList() {
        Tuning now = current();
        String tuned = now == null ? "" : now.url();
        List<StationList.Row> rows = new ArrayList<>();
        received = false;
        if (pos != null) {
            ReceiverBlockEntity radio = block();
            for (Signal s : radio == null ? List.<Signal>of() : radio.signals()) {
                String right = Component.translatable("worldradio.tuner.row", String.format(Locale.ROOT, "%.0f", s.distance()), s.hops()).getString();
                rows.add(new StationList.Row(label(s.name(), s.url()), right, s.url().equals(tuned), () -> tune(s.url(), s.name())));
                received |= s.url().equals(tuned);
            }
        } else {
            for (Reception.Heard h : SourceTracker.receivable(minecraft)) {
                String right = Math.round(h.strength() * 100) + " %";
                rows.add(new StationList.Row(label(h.name(), h.url()), right, h.url().equals(tuned), () -> tune(h.url(), h.name())));
                received |= h.url().equals(tuned);
            }
        }
        list.setRows(rows);
    }

    private static String label(String name, String url) {
        return name.isEmpty() ? RadioScreen.hostOf(url) : name;
    }

    // ---- actions

    private void tune(String url, String name) {
        if (pos != null) ClientPlayNetworking.send(new Packets.SetStation(pos, url, name, ""));
        else sendPortable(url, name, null);
    }

    /** The key names the action: "Turn off" while on, "Turn on" while off. */
    private void togglePower() {
        Tuning now = current();
        if (now == null) return;
        if (pos != null) ClientPlayNetworking.send(new Packets.SetEnabled(pos, !now.enabled()));
        else sendPortable(now.url(), now.name(), !now.enabled());
    }

    /** The portable radio's whole setting with a new station and/or switch; a slider value not sent yet goes along. */
    private void sendPortable(String url, String name, Boolean enabled) {
        Tuning now = current();
        if (now == null) return;
        float v = volumeToSend >= 0 ? volumeToSend : now.volume();
        volumeToSend = -1;
        ClientPlayNetworking.send(new Packets.TunePortable(hand, new Tuning(url, name, enabled == null ? now.enabled() : enabled, v)));
    }

    private void flush() {
        if (volumeToSend < 0) return;
        if (pos != null) {
            ClientPlayNetworking.send(new Packets.SetVolume(pos, volumeToSend));
            volumeToSend = -1;
        } else {
            Tuning now = current();
            if (now != null) sendPortable(now.url(), now.name(), null);
        }
    }

    /** Dev tests only: clicks row {@code index} of the list like the player would. */
    public void devClickRow(int index) {
        list.click(list.x() + 10, list.rowTop(index) + 4);
    }

    /** Dev tests only. */
    public void devPressPower() {
        togglePower();
    }

    /** Dev tests only: moves the volume slider like a drag would. */
    public void devSetVolume(double value) {
        volume.set(value);
    }

    /** Dev tests only: the rows on offer and the marked ones. */
    public String devRows() {
        return list.size() + " rows, marked " + list.markedRows();
    }

    // ---- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        RadioUi.panel(g, RadioUi.TUNER_PANEL, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Tuning now = current();
        if (now != null) {
            String count = Component.translatable("worldradio.tuner.count", list.size()).getString();
            int countWidth = font.width(count);
            g.text(font, count, left + WIDTH - 8 - countWidth, top + 8, RadioUi.TEXT_SOFT, false);
            String name = now.url().isEmpty() ? Component.translatable("worldradio.station.none").getString() : label(now.name(), now.url());
            g.text(font, RadioUi.ellipsize(font, name, WIDTH - 24 - countWidth), left + 8, top + 8, RadioUi.TEXT, false);
            RadioUi.StatusLine status = !now.url().isEmpty() && now.enabled() && !received
                    ? new RadioUi.StatusLine(Component.translatable("worldradio.status.nosignal").getString(), RadioUi.WARN)
                    : RadioUi.status(now.url(), now.enabled(), "worldradio.status.idle");
            // the block: how far it is heard, at the right end of the status line, with a hint about speakers
            int hearingWidth = 0;
            ReceiverBlockEntity radio = block();
            if (radio != null) {
                String hearing = (radio.antenna() == 1 ? Component.translatable("worldradio.hearing.one", radio.range())
                        : Component.translatable("worldradio.hearing", radio.range(), radio.antenna())).getString() + "  (?)";
                hearingWidth = font.width(hearing) + 8;
                int x = left + WIDTH - 8 - font.width(hearing);
                g.text(font, hearing, x, top + 20, RadioUi.TEXT_SOFT, false);
                if (mouseX >= x && mouseX < left + WIDTH - 8 && mouseY >= top + 18 && mouseY < top + 30) {
                    Component hint = Component.translatable(radio.getBlockState().is(WorldRadio.PORTABLE_RADIO)
                            ? "worldradio.hearing.hint.portable" : "worldradio.hearing.hint", Config.get().speakerStep());
                    g.setTooltipForNextFrame(font, font.split(hint, 220), mouseX, mouseY + 16);
                }
            }
            g.text(font, RadioUi.ellipsize(font, status.text(), WIDTH - 16 - hearingWidth), left + 8, top + 20, status.colour(), false);
        }
        list.extract(g, font, mouseX, mouseY);
        // widgets (key, slider) on top of the list
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    // ---- input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || list.click(event.x(), event.y());
    }

    /** The inventory key closes the screen like a container. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        // a drag that ended just before closing still counts
        flush();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The radio's volume, 0 to 100 %. */
    private final class Slider extends KeySlider {
        Slider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, value);
            updateMessage();
        }

        void set(double v) {
            setValue(v);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("worldradio.volume", Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            volumeValue = value;
            volumeToSend = (float) value;
        }
    }
}

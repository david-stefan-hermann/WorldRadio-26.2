package worldradio.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import worldradio.block.AmplifierBlockEntity;
import worldradio.signal.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only list of the signals reaching an amplifier (station, where its radio stands, distance and hops) under the
 * amplifier's range.
 */
public class AmplifierScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 190;

    private final BlockPos pos;
    private final StationList list = new StationList();
    private List<Signal> shown;
    private int left;
    private int top;

    public AmplifierScreen(BlockPos pos) {
        super(Component.translatable("block.worldradio.amplifier"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        list.setBounds(left + 8, top + 40, WIDTH - 16, HEIGHT - 48);
        shown = null;
    }

    @Override
    public void tick() {
        if (!(minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AmplifierBlockEntity amp)) {
            onClose();
            return;
        }
        if (amp.signals().equals(shown)) return;
        shown = amp.signals();
        List<StationList.Row> rows = new ArrayList<>();
        for (Signal s : shown) {
            String name = s.name().isEmpty() ? s.url() : s.name();
            String right = Component.translatable("worldradio.amplifier.row", s.radioX(), s.radioY(), s.radioZ(),
                    String.format(Locale.ROOT, "%.0f", s.distance()), s.hops(), Math.round(s.factor() * 100)).getString();
            rows.add(new StationList.Row(name, right, false, null));
        }
        list.setRows(rows);
        list.setEmptyText(Component.translatable("worldradio.amplifier.none").getString());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        RadioUi.panel(g, RadioUi.AMPLIFIER_PANEL, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int range = 0;
        int antenna = 0;
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AmplifierBlockEntity amp) {
            range = amp.range();
            antenna = amp.antenna();
        }
        g.text(font, title, left + 8, top + 8, RadioUi.TEXT, false);
        RadioUi.rangeLine(g, font, range, antenna, left + 8, top + 20, mouseX, mouseY);
        String count = Component.translatable("worldradio.amplifier.count", list.size()).getString();
        g.text(font, count, left + WIDTH - 8 - font.width(count), top + 8, RadioUi.TEXT_SOFT, false);
        list.extract(g, font, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return list.click(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
    }

    /** The inventory key closes the screen like a container, unless the player is typing into a text box. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!(getFocused() instanceof EditBox) && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

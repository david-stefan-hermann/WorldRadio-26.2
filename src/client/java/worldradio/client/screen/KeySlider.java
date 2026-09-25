package worldradio.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider in the piano-key look: a pressed key as the slot and a small key as the handle. */
public abstract class KeySlider extends AbstractSliderButton {
    protected KeySlider(int x, int y, int width, int height, double value) {
        super(x, y, width, height, Component.empty(), value);
    }

    public double value() {
        return value;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        RadioUi.key(g, getX(), getY(), width, height, false, false);
        int handle = getX() + (int) (value * (width - HANDLE_WIDTH));
        RadioUi.key(g, handle, getY(), HANDLE_WIDTH, height, true, isHoveredOrFocused());
        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        g.text(font, message, getX() + (width - font.width(message)) / 2, getY() + (height - 7) / 2, RadioUi.KEY_TEXT, false);
        handleCursor(g);
    }
}

package worldradio.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** A button drawn as a white piano key with dark lettering; an inactive key (the open tab) stays pressed. */
public class KeyButton extends Button {
    public KeyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public KeyButton tooltip(Component text) {
        setTooltip(Tooltip.create(text));
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        RadioUi.key(g, getX(), getY(), width, height, active, isHoveredOrFocused());
        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int x = getX() + (width - font.width(message)) / 2;
        int y = getY() + (height - 7) / 2;
        g.text(font, message, x, y, active ? RadioUi.KEY_TEXT : RadioUi.KEY_TEXT_OFF, false);
    }
}

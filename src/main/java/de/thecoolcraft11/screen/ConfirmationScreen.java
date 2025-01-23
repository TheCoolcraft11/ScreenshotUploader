package de.thecoolcraft11.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class ConfirmationScreen extends Screen {
    private final Consumer<Boolean> callback;
    private final Text message;
    private final Text title;

    public ConfirmationScreen(Consumer<Boolean> callback, Text title, Text message) {
        super(Text.literal("Confirmation"));
        this.callback = callback;
        this.message = message;
        this.title = title;
    }

    @Override
    protected void init() {
        int midX = this.width / 2;
        int midY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("YES"), button -> callback.accept(true)).dimensions(midX - 100, midY, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("NO"), button -> callback.accept(false)).dimensions(midX - 100, midY + 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, this.message, this.width / 2, 50, 0xFFFFFF);
    }
}


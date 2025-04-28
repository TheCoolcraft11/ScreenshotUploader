package de.thecoolcraft11.screen;


import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ConfirmScreenshotsScreen extends Screen {
    private final Screen parent;
    private final String jsonData;
    private NativeImage image;
    private Identifier textureId;
    private final String filename;

    private final ScreenCloseCallback onClose;


    public ConfirmScreenshotsScreen(Screen parent, NativeImage nativeImage, String jsonData, String filename, ScreenCloseCallback onClose) {
        super(Text.translatable("gui.screenshot_uploader.confirm_screenshot.title"));
        this.parent = parent;
        this.jsonData = jsonData;
        this.onClose = onClose;
        this.image = nativeImage;
        this.filename = filename;
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int padding = 10;

        int imageWidth = Math.min(width / 2, 640);
        int imageHeight = Math.min(height / 2, 360);
        int imageX = (width - imageWidth) / 2;
        int imageY = (height - imageHeight) / 3;

        int buttonsY = imageY + imageHeight + padding * 2;
        int firstButtonX = imageX + (imageWidth - (buttonWidth * 3 + padding * 2)) / 2;

        ButtonWidget saveButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.confirm_screenshot.save"), button -> {
                    if (onClose != null) {
                        onClose.accept(true, false, image, jsonData);
                    }
                    if (client != null) {
                        client.setScreen(parent);
                    }
                })
                .dimensions(firstButtonX, buttonsY, buttonWidth, buttonHeight)
                .build();

        ButtonWidget uploadButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.confirm_screenshot.upload"), button -> {
                    if (onClose != null) {
                        onClose.accept(true, true, image, jsonData);
                    }
                    if (client != null) {
                        client.setScreen(parent);
                    }
                })
                .dimensions(firstButtonX + buttonWidth + padding, buttonsY, buttonWidth, buttonHeight)
                .build();

        ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.confirm_screenshot.cancel"), button -> {
                    if (onClose != null) {
                        onClose.accept(false, false, image, jsonData);
                    }
                    if (client != null) {
                        client.setScreen(parent);
                    }
                })
                .dimensions(firstButtonX + (buttonWidth * 2) + (padding * 2), buttonsY, buttonWidth, buttonHeight)
                .build();

        addDrawableChild(saveButton);
        addDrawableChild(uploadButton);
        addDrawableChild(cancelButton);

        allocateImage(image);
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);


        int imageWidth = Math.min(width / 2, 640);
        int imageHeight = Math.min(height / 2, 360);
        int imageX = (width - imageWidth) / 2;
        int imageY = (height - imageHeight) / 3;

        Text title = Text.translatable("gui.screenshot_uploader.confirm_screenshot.header", filename);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, imageY - 20, 0xFFFFFF);

        if (textureId != null) {
            context.fill(imageX - 2, imageY - 2, imageX + imageWidth + 2, imageY + imageHeight + 2, 0xFF555555);

            RenderSystem.setShaderTexture(0, textureId);
            context.drawTexture(textureId, imageX, imageY, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        } else {
            if (image != null && client != null) {
                textureId = client.getTextureManager().registerDynamicTexture("edit_image", new NativeImageBackedTexture(image));
            }
        }
    }

    private void allocateImage(NativeImage newImage) {
        if (newImage != null) {
            NativeImage imageCopy = new NativeImage(newImage.getWidth(), newImage.getHeight(), false);
            for (int y = 0; y < newImage.getHeight(); y++) {
                for (int x = 0; x < newImage.getWidth(); x++) {
                    imageCopy.setColor(x, y, newImage.getColor(x, y));
                }
            }
            image = imageCopy;
        }
    }


    @Override
    public void close() {
        if (onClose != null) {
            onClose.accept(true, true, image, jsonData);
        }
        if (image != null) image.close();
        if (client != null) client.setScreen(parent);

    }
}


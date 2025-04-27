package de.thecoolcraft11.screen;


import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfirmScreenshotsScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(ConfirmScreenshotsScreen.class);
    private final Screen parent;
    private final String jsonData;
    private NativeImage image;
    private Identifier textureId;

    private final ScreenCloseCallback onClose;


    public ConfirmScreenshotsScreen(Screen parent, NativeImage nativeImage, String jsonData, ScreenCloseCallback onClose) {
        super(Text.translatable("gui.screenshot_uploader.editor.title"));
        this.parent = parent;
        this.jsonData = jsonData;
        this.onClose = onClose;

        allocateImage(nativeImage);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (textureId != null) {
            RenderSystem.setShaderTexture(0, textureId);
            context.drawTexture(textureId, width / 4, height / 4, 0, 0, width / 2, height / 2, width / 2, height / 2);
        } else {
            if (image != null) {
                if (client != null) {
                    textureId = client.getTextureManager().registerDynamicTexture("edit_image", new NativeImageBackedTexture(image));
                }
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


package de.thecoolcraft11.screen;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

public class CustomSignEditScreen extends Screen {

    private static final Logger logger = LoggerFactory.getLogger(CustomSignEditScreen.class);
    private final SignBlockEntity sign;
    private static Identifier screenshotIdentifier;
    TextFieldWidget urlField;
    public static int customEditsLeft = 0;

    public CustomSignEditScreen(SignBlockEntity sign) {
        super(Text.of("Sign"));
        this.sign = sign;
    }

    @Override
    protected void init() {
        screenshotIdentifier = null;

        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height / 2 + this.height / 4;
        int textFieldWidth = this.width / 3;
        int textFieldX = (this.width - textFieldWidth) / 2;
        int textFieldY = this.height / 2 + this.height / 8;
        urlField = new TextFieldWidget(textRenderer, textFieldX, textFieldY, textFieldWidth, 20, Text.literal(""));
        urlField.setMaxLength(15 * 8);
        addDrawableChild(urlField);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_sign.set_text"), buttonWidget -> pasteTextToSign()).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).tooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.screenshot_sign.tooltip"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_sign.close"), buttonWidget -> {
            if (client != null) {
                client.setScreen(null);
            }
        }).dimensions(buttonX, buttonY + buttonHeight + 5, buttonWidth, buttonHeight).build());

        super.init();
    }

    private void pasteTextToSign() {
        customEditsLeft = 2;
        String[] splitStrings = splitIntoChunks(urlField.getText(), 15, 8);
        String[] strings = new String[8];

        for (int i = 0; i < 8 && i < splitStrings.length; i++) {
            strings[i] = splitStrings[i];
        }

        ClientPlayNetworkHandler clientPlayNetworkHandler;
        if (this.client != null) {
            clientPlayNetworkHandler = this.client.getNetworkHandler();

            if (clientPlayNetworkHandler != null) {
                clientPlayNetworkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3d.ofCenter(sign.getPos()),
                                Direction.UP,
                                sign.getPos(),
                                false
                        ),
                        0
                ));
                clientPlayNetworkHandler.sendPacket(
                        new UpdateSignC2SPacket(sign.getPos(), true, strings[0], strings[1], strings[2], strings[3])
                );
                client.setScreen(null);
                clientPlayNetworkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3d.ofCenter(sign.getPos()),
                                Direction.UP,
                                sign.getPos(),
                                false
                        ),
                        0
                ));
                clientPlayNetworkHandler.sendPacket(
                        new UpdateSignC2SPacket(sign.getPos(), false, strings[4], strings[5], strings[6], strings[7])
                );
            }
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                client.setScreen(null);
            });
        }
    }

    public static String[] splitIntoChunks(String input, int chunkSize, int maxChunks) {
        String[] result = new String[maxChunks];
        int length = input.length();

        for (int i = 0; i < maxChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, length);
            if (start < length) {
                result[i] = input.substring(start, end);
            } else {
                result[i] = "";
            }
        }
        return result;
    }

    private static void loadWebImage(String imageUrl) {
        String cacheFileName = "screenshots_cache/" + imageUrl.hashCode() + ".png";
        File cachedImage = new File(cacheFileName);

        if (cachedImage.exists()) {
            try {
                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                    Identifier textureId = Identifier.of("webimage", "temp/" + imageUrl.hashCode());
                    if (MinecraftClient.getInstance() != null) {
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, loadedImage));
                        screenshotIdentifier = textureId;
                    }
                }
            } catch (IOException ignored) {
            }
        } else {
            try {
                URI uri = new URI(imageUrl);
                if (!uri.isAbsolute()) {
                    screenshotIdentifier = null;
                    return;
                }
                URL url = uri.toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        BufferedImage bufferedImage = ImageIO.read(inputStream);

                        if (bufferedImage != null) {
                            File cacheFolder = new File("screenshots_cache");
                            if (!cacheFolder.exists()) {
                                if (cacheFolder.mkdirs()) {
                                    logger.info("Created web screenshots cache folder");
                                }
                            }

                            ImageIO.write(bufferedImage, "PNG", cachedImage);

                            try (NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false)) {
                                for (int y = 0; y < bufferedImage.getHeight(); y++) {
                                    for (int x = 0; x < bufferedImage.getWidth(); x++) {
                                        int rgb = bufferedImage.getRGB(x, y);
                                        int alpha = (rgb >> 24) & 0xFF;
                                        int red = (rgb >> 16) & 0xFF;
                                        int green = (rgb >> 8) & 0xFF;
                                        int blue = rgb & 0xFF;

                                        int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                                        nativeImage.setColorArgb(x, y, argb);
                                    }
                                }

                                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                                    Identifier textureId = Identifier.of("webimage", "temp/" + imageUrl.hashCode());
                                    if (MinecraftClient.getInstance() != null) {
                                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, loadedImage));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (URISyntaxException | IOException ignored) {
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        loadWebImage(urlField.getText());
        if (screenshotIdentifier != null) {
            renderEnlargedImage(context);
        }

    }

    private void renderEnlargedImage(DrawContext context) {

        Identifier clickedImageId = screenshotIdentifier;

        int imageWidth = width / 4;
        int imageHeight = height / 4;
        if (client != null) {
            imageWidth = (int) ((double) client.getWindow().getWidth() / 8);
            imageHeight = (int) ((double) client.getWindow().getHeight() / 8);
        }


        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 3;

        int borderWidth = 5;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, 0xFFFFFFFF);
        //RenderSystem.setShaderTexture(0, clickedImageId);
        //RenderSystem.enableBlend();
        context.drawTexture(RenderLayer::getGuiTextured, clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        //RenderSystem.disableBlend();


    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.urlField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (this.urlField.charTyped(chr, keyCode)) {
            return true;
        }

        return super.charTyped(chr, keyCode);
    }


}
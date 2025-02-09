package de.thecoolcraft11.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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


public class ScreenshotScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotScreen.class);
    private static Identifier screenshotIdentifier;
    private double zoomLevel = 1.0;
    private double imageOffsetX = 0.0;
    private double imageOffsetY = 0.0;

    public ScreenshotScreen(String screenshotUrl) {
        super(Text.of("Screenshot"));
        loadWebImage(screenshotUrl);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (screenshotIdentifier != null) {
            renderEnlargedImage(context);
        }

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
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(loadedImage));
                        screenshotIdentifier = textureId;
                    }
                }
            } catch (IOException ignored) {
            }
        } else {
            try {
                URI uri = new URI(imageUrl);
                if (!uri.isAbsolute()) return;
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
                                        nativeImage.setColor(x, y, argb);
                                    }
                                }

                                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                                    Identifier textureId = Identifier.of("webimage", "temp/" + imageUrl.hashCode());
                                    if (MinecraftClient.getInstance() != null) {
                                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(loadedImage));
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

    private void renderEnlargedImage(DrawContext context) {

        Identifier clickedImageId = screenshotIdentifier;

        int imageWidth = (int) (1920 * zoomLevel);
        int imageHeight = (int) (1080 * zoomLevel);
        if (client != null) {
            imageWidth = (int) ((double) client.getWindow().getWidth() / 4 * zoomLevel);
            imageHeight = (int) ((double) client.getWindow().getHeight() / 4 * zoomLevel);
        }

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        context.fill(0, 0, width, height, 0x80000000);

        int borderWidth = 5;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, 0xFFFFFFFF);
        RenderSystem.setShaderTexture(0, clickedImageId);
        RenderSystem.enableBlend();
        context.drawTexture(clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        RenderSystem.disableBlend();


    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoomAndRepositionImage(mouseX, mouseY, verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void zoomAndRepositionImage(double mouseX, double mouseY, double verticalAmount) {
        double mouseXRelative = mouseX - (width / 2.0);
        double mouseYRelative = mouseY - (height / 2.0);

        int IMAGE_WIDTH = 192;
        double imageWidth = IMAGE_WIDTH * zoomLevel;
        int IMAGE_HEIGHT = 108;
        double imageHeight = IMAGE_HEIGHT * zoomLevel;

        double mouseXInImage = -(mouseXRelative - imageOffsetX);
        double mouseYInImage = -(mouseYRelative - imageOffsetY);

        zoomLevel = Math.min(Math.max(zoomLevel + (verticalAmount > 0 ? 0.1 : -0.1), 0.05), 512.0);

        double newImageWidth = IMAGE_WIDTH * zoomLevel;
        double newImageHeight = IMAGE_HEIGHT * zoomLevel;

        imageOffsetX += (mouseXInImage * (newImageWidth / imageWidth - 1));
        imageOffsetY += (mouseYInImage * (newImageHeight / imageHeight - 1));
    }
}

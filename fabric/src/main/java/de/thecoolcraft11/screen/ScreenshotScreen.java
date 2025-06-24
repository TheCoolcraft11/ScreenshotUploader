package de.thecoolcraft11.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
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
    private final String screenshotUrl;

    public ScreenshotScreen(String screenshotUrl) {
        super(Text.translatable("gui.screenshot_uploader.screenshot_screen.title"));
        this.screenshotUrl = screenshotUrl;
        screenshotIdentifier = null;

    }

    boolean loaded = false;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (screenshotIdentifier != null) {
            renderEnlargedImage(context);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.screenshot_uploader.screenshot_screen.no_image"), this.width / 2, this.height / 2, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(screenshotUrl), this.width / 2, this.height / 2 + 10, 0xFFFFFF);
            if (!loaded) {
                loadWebImage(screenshotUrl);
                loaded = true;
            }
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
                                        nativeImage.setColorArgb(x, y, argb);
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

        int x = (width - imageWidth) / 2 + (int) imageOffsetX;
        int y = (height - imageHeight) / 2 + (int) imageOffsetY;

        context.fill(0, 0, width, height, 0x80000000);

        int borderWidth = 5;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, 0xFFFFFFFF);
        context.drawTexture(RenderLayer::getGuiTextured, clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);


    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        zoomAndRepositionImage(mouseX, mouseY, verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private double scrollSpeedMultiplier = 1.0;
    private long lastScrollTime = 0;

    private void zoomAndRepositionImage(double mouseX, double mouseY, double verticalAmount) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastScroll = currentTime - lastScrollTime;
        lastScrollTime = currentTime;

        if (timeSinceLastScroll < 200) {
            scrollSpeedMultiplier = Math.min(scrollSpeedMultiplier * 1.25, 5.0);
        } else {
            scrollSpeedMultiplier = 1.0;
        }

        double scrollStep = 0.1 * scrollSpeedMultiplier;
        double zoomChange = (verticalAmount > 0 ? scrollStep : -scrollStep);

        double mouseXRelative = mouseX - (width / 2.0);
        double mouseYRelative = mouseY - (height / 2.0);

        double imageWidth = 192 * zoomLevel;
        double imageHeight = 108 * zoomLevel;

        double mouseXInImage = -(mouseXRelative - imageOffsetX);
        double mouseYInImage = -(mouseYRelative - imageOffsetY);

        zoomLevel = Math.min(Math.max(zoomLevel + zoomChange, 0.05), 512.0);

        double newImageWidth = 192 * zoomLevel;
        double newImageHeight = 108 * zoomLevel;

        imageOffsetX += (mouseXInImage * (newImageWidth / imageWidth - 1));
        imageOffsetY += (mouseYInImage * (newImageHeight / imageHeight - 1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int imageWidth = 0;
            int imageHeight = 0;
            if (client != null) {
                imageWidth = (int) (((double) client.getWindow().getWidth() / 4) * zoomLevel);
                imageHeight = (int) (((double) client.getWindow().getHeight() / 4) * zoomLevel);
            }

            int x = (width - imageWidth) / 2 + (int) imageOffsetX;
            int y = (height - imageHeight) / 2 + (int) imageOffsetY;

            boolean insideImage = mouseX >= x && mouseX <= x + imageWidth && mouseY >= y && mouseY <= y + imageHeight;

            if (!insideImage) {
                if (client != null) {
                    client.setScreen(null);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

}

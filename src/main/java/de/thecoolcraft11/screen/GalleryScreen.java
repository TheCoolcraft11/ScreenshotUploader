package de.thecoolcraft11.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.util.ReceivePackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class GalleryScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(GalleryScreen.class);

    private static final List<Identifier> imageIds = new ArrayList<>();
    private static final List<Path> imagePaths = new ArrayList<>();

    private static final int IMAGES_PER_ROW = ConfigManager.getClientConfig().imagesPerRow;
    private static int IMAGE_WIDTH = 192;
    private static int IMAGE_HEIGHT = 108;
    private static int GAP = ConfigManager.getClientConfig().imageGap;
    private static int TOP_PADDING = ConfigManager.getClientConfig().imageTopPadding;

    private boolean isImageClicked = false;
    private int clickedImageIndex = -1;
    private int scrollOffset = 0;

    private double zoomLevel = 2.0;
    private double imageOffsetX = 0.0;
    private double imageOffsetY = 0.0;


    private ButtonWidget saveButton;
    private ButtonWidget deleteButton;
    private ButtonWidget openInAppButton;

    private ButtonWidget configButton;

    private final List<ButtonWidget> navigatorButtons = new ArrayList<>();

    private final List<ButtonWidget> buttonsToHideOnOverlap = new ArrayList<>();

    public GalleryScreen() {
        super(Text.translatable("gui.screenshot_uploader.screenshot_gallery.title"));
    }

    @Override
    protected void init() {
        super.init();
        imageIds.clear();
        imagePaths.clear();
        navigatorButtons.clear();

        int scaledHeight = height / 6;
        int scaledWidth = (scaledHeight * 16) / 9;
        int scaledGap = scaledHeight / 10;

        TOP_PADDING = height / 20;
        IMAGE_WIDTH = scaledWidth;
        IMAGE_HEIGHT = scaledHeight;
        GAP = scaledGap;

        loadAllImagesAsync();

        int buttonWidth = width / 8;
        int buttonHeight = height / 25;
        int buttonSpacing = buttonWidth / 5;

        int xPosition = (width - (buttonWidth * (navigatorButtons.size() + 1) + buttonSpacing * (navigatorButtons.size() - 1))) / 5;
        int buttonY = (int) (height * 0.9);

        int navigatorY = (int) (height * 0.01);

        navigatorButtons.add(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.current"), button -> {
        }).dimensions(xPosition, navigatorY, buttonWidth, buttonHeight).build());

        xPosition += buttonWidth + buttonSpacing;

        if (ReceivePackets.gallerySiteAddress != null) {
            navigatorButtons.add(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.server_gallery"), button -> {
                String webserverUrl = ReceivePackets.gallerySiteAddress;
                if (client != null) {
                    client.setScreen(new WebGalleryScreen(this, webserverUrl, null));
                } else {
                    logger.error("Failed to get client trying to open Server Gallery with URL {}", webserverUrl);
                }
            }).dimensions(xPosition, navigatorY, buttonWidth, buttonHeight).build());

            xPosition += buttonWidth + buttonSpacing;
        }

        for (Map.Entry<String, Map<String, String>> entry : ConfigManager.getClientConfig().upload_urls.entrySet()) {
            String webserverUrl = entry.getValue().get("gallery");
            String buttonLabel = entry.getKey();

            navigatorButtons.add(ButtonWidget.builder(
                            Text.literal(buttonLabel),
                            button -> {
                                if (client != null) {
                                    client.setScreen(new WebGalleryScreen(this, webserverUrl, null));
                                } else {
                                    logger.error("Failed to get client trying to open Gallery for {}", webserverUrl);
                                }
                            })
                    .dimensions(xPosition, navigatorY, buttonWidth, buttonHeight)
                    .build());

            xPosition += buttonWidth + buttonSpacing;
        }

        navigatorButtons.forEach(this::addDrawableChild);

        navigatorButtons.stream().filter(buttonWidget -> buttonWidget.getMessage().equals(Text.translatable("gui.screenshot_uploader.screenshot_gallery.current"))).forEach(buttonWidget -> buttonWidget.active = false);

        saveButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.save"),
                button -> saveImage()
        ).dimensions(5, buttonY, buttonWidth, buttonHeight).build();

        deleteButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.delete"),
                button -> deleteImage()
        ).dimensions(buttonWidth + 10, buttonY, buttonWidth, buttonHeight).build();

        openInAppButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.open_in_app"),
                button -> openImageInApp()
        ).dimensions((2 * buttonWidth) + 15, buttonY, buttonWidth, buttonHeight).build();

        configButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.config"),
                button -> {
                    if (client != null) {
                        client.setScreen(new ConfigScreen());
                    }
                }
        ).dimensions(5, buttonY, buttonWidth, buttonHeight).build();


        addDrawableChild(saveButton);
        addDrawableChild(deleteButton);
        addDrawableChild(openInAppButton);
        addDrawableChild(configButton);

        saveButton.visible = false;
        deleteButton.visible = false;
        openInAppButton.visible = false;
        configButton.visible = true;

        buttonsToHideOnOverlap.add(saveButton);
        buttonsToHideOnOverlap.add(deleteButton);
        buttonsToHideOnOverlap.add(openInAppButton);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isImageClicked) {
                int imageWidth = (int) (1920 * zoomLevel);
                int imageHeight = (int) (1080 * zoomLevel);
                if (client != null) {
                    imageWidth = (int) ((double) client.getWindow().getWidth() / 4 * zoomLevel);
                    imageHeight = (int) ((double) client.getWindow().getHeight() / 4 * zoomLevel);
                }

                int x = (width - imageWidth) / 2 + (int) imageOffsetX;
                int y = (height - imageHeight) / 2 + (int) imageOffsetY;

                if (mouseX >= x && mouseX <= x + imageWidth && mouseY >= y && mouseY <= y + imageHeight) {
                    return false;
                }

                for (Element buttonWidget : this.children()) {
                    if (buttonWidget != null && buttonWidget.isMouseOver(mouseX, mouseY)) {
                        return super.mouseClicked(mouseX, mouseY, button);
                    }
                }

                isImageClicked = false;
                clickedImageIndex = -1;
                return true;
            } else {
                int totalImages = imageIds.size();
                for (int i = 0; i < totalImages; i++) {
                    int row = i / IMAGES_PER_ROW;
                    int col = i % IMAGES_PER_ROW;
                    int x = (width - (IMAGES_PER_ROW * IMAGE_WIDTH + (IMAGES_PER_ROW - 1) * GAP)) / 2 + col * (IMAGE_WIDTH + GAP);
                    int y = TOP_PADDING + 20 + row * (IMAGE_HEIGHT + GAP) - scrollOffset;

                    if (mouseX > x && mouseX < x + IMAGE_WIDTH && mouseY > y && mouseY < y + IMAGE_HEIGHT) {
                        isImageClicked = true;
                        clickedImageIndex = i;

                        zoomLevel = 1.0;
                        imageOffsetX = 0.0;
                        imageOffsetY = 0.0;

                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isImageClicked && clickedImageIndex >= 0) {
            double mouseXRelative = mouseX - (width / 2.0);
            double mouseYRelative = mouseY - (height / 2.0);

            double imageWidth = IMAGE_WIDTH * zoomLevel;
            double imageHeight = IMAGE_HEIGHT * zoomLevel;

            double mouseXInImage = -(mouseXRelative - imageOffsetX);
            double mouseYInImage = -(mouseYRelative - imageOffsetY);

            zoomLevel = Math.min(Math.max(zoomLevel + (verticalAmount > 0 ? 0.1 : -0.1), 0.5), 10.0);

            double newImageWidth = IMAGE_WIDTH * zoomLevel;
            double newImageHeight = IMAGE_HEIGHT * zoomLevel;

            imageOffsetX += (mouseXInImage * (newImageWidth / imageWidth - 1));
            imageOffsetY += (mouseYInImage * (newImageHeight / imageHeight - 1));

            return true;
        } else {
            if (verticalAmount < 0) {
                scrollOffset += GAP + IMAGE_HEIGHT;
            } else if (verticalAmount > 0) {
                scrollOffset -= GAP + IMAGE_HEIGHT;
            }

            scrollOffset = Math.max(0, Math.min(scrollOffset, (imageIds.size() / IMAGES_PER_ROW + 2) * (IMAGE_HEIGHT + GAP) - height + TOP_PADDING));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        if (isImageClicked && clickedImageIndex >= 0) {
            renderEnlargedImage(context);

            saveButton.visible = true;
            deleteButton.visible = true;
            openInAppButton.visible = true;
            configButton.visible = false;
            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = false);
        } else {
            renderGallery(context, mouseX, mouseY);

            saveButton.visible = false;
            deleteButton.visible = false;
            openInAppButton.visible = false;
            configButton.visible = true;

            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = true);
        }
        boolean isImageOverlappingButtons = clickedImageIndex >= 0 && isImageOverlappingButtons();

        for (Element button : this.children()) {
            if (button instanceof ButtonWidget) {
                if (buttonsToHideOnOverlap.contains(button)) {
                    if (isImageClicked) {
                        ((ButtonWidget) button).visible = !isImageOverlappingButtons;
                    } else {
                        ((ButtonWidget) button).visible = false;
                    }
                }
            }
        }
    }

    private void renderGallery(DrawContext context, int mouseX, int mouseY) {
        int startX = (width - (IMAGES_PER_ROW * IMAGE_WIDTH + (IMAGES_PER_ROW - 1) * GAP)) / 2;
        int startY = TOP_PADDING + 20;

        for (int i = 0; i < imageIds.size(); i++) {
            int row = i / IMAGES_PER_ROW;
            int col = i % IMAGES_PER_ROW;
            int x = startX + col * (IMAGE_WIDTH + GAP);
            int y = startY + row * (IMAGE_HEIGHT + GAP) - scrollOffset;

            context.fill(x - 2, y - 2, x + IMAGE_WIDTH + 2, y + IMAGE_HEIGHT + 2, 0xFF888888);
            Identifier imageId = imageIds.get(i);
            RenderSystem.setShaderTexture(0, imageId);
            context.drawTexture(imageId, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

            if (mouseX > x && mouseX < x + IMAGE_WIDTH && mouseY > y && mouseY < y + IMAGE_HEIGHT) {
                context.fill(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, 0x80FFFFFF);
            }
        }
    }

    private void renderEnlargedImage(DrawContext context) {
        if (clickedImageIndex < 0 || clickedImageIndex >= imageIds.size()) {
            return;
        }

        Identifier clickedImageId = imageIds.get(clickedImageIndex);

        int imageWidth = (int) (1920 * zoomLevel);
        int imageHeight = (int) (1080 * zoomLevel);
        if (client != null) {
            imageWidth = (int) ((double) client.getWindow().getWidth() / 4 * zoomLevel);
            imageHeight = (int) ((double) client.getWindow().getHeight() / 4 * zoomLevel);
        } else {
            logger.info("Failed to get client while trying to get screen resolution while rendering large image, proceeding with 1920x1080");
        }

        int x = (width - imageWidth) / 2 + (int) imageOffsetX;
        int y = (height - imageHeight) / 2 + (int) imageOffsetY;

        context.fill(0, 0, width, height, 0x80000000);

        int borderWidth = 5;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, 0xFFFFFFFF);

        RenderSystem.setShaderTexture(0, clickedImageId);
        RenderSystem.enableBlend();

        context.drawTexture(clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        RenderSystem.disableBlend();
    }

    private void loadAllImagesAsync() {
        Path screenshotsDir = Paths.get(System.getProperty("user.dir"), "screenshots");

        CompletableFuture.runAsync(() -> {
            try (Stream<Path> paths = Files.list(screenshotsDir)) {
                paths.filter(path -> path.toString().endsWith(".png"))
                        .forEach(imagePaths::add);
                loadImagesAsync();
            } catch (IOException e) {
                logger.error("Failed to load images: {}", e.getMessage());
            }
        });
    }

    private void loadImagesAsync() {
        CompletableFuture.runAsync(() -> {
            for (Path path : imagePaths) {
                try {
                    NativeImage image = NativeImage.read(Files.newInputStream(path));
                    Identifier textureId = Identifier.of("gallery", "textures/" + path.getFileName().toString());
                    MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
                    imageIds.add(textureId);
                } catch (IOException e) {
                    logger.error("Failed to load image '{}': {}", path, e.getMessage());
                }
            }
        });
    }

    private void saveImage() {
        if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
            Path imagePath = imagePaths.get(clickedImageIndex);
            Path savePath = Paths.get(System.getProperty("user.home"), "Desktop", "Saved_" + imagePath.getFileName().toString());

            try {
                Files.copy(imagePath, savePath);
                logger.info("Image saved to: {}", savePath);
            } catch (IOException e) {
                logger.error("Failed to save image: {}", e.getMessage());
            }
        }
    }

    private void deleteImage() {
        if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
            Path imagePath = imagePaths.get(clickedImageIndex);

            try {
                Files.delete(imagePath);
                imagePaths.remove(clickedImageIndex);
                imageIds.remove(clickedImageIndex);
                logger.info("Image deleted: {}", imagePath);
                clickedImageIndex = -1;
                isImageClicked = false;
            } catch (IOException e) {
                logger.error("Failed to delete image: {}", e.getMessage());
            }
        }
    }

    private void openImageInApp() {
        if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
            Path imagePath = imagePaths.get(clickedImageIndex);

            try {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder processBuilder;

                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder("explorer", imagePath.toAbsolutePath().toString());
                } else if (os.contains("mac")) {
                    processBuilder = new ProcessBuilder("open", imagePath.toAbsolutePath().toString());
                } else if (os.contains("nix") || os.contains("nux")) {
                    processBuilder = new ProcessBuilder("xdg-open", imagePath.toAbsolutePath().toString());
                } else {
                    logger.error("Unsupported operating system for opening the image.");
                    return;
                }
                processBuilder.start();

            } catch (IOException e) {
                logger.error("Failed to open image with external application: {}", e.getMessage());
            }
        }
    }


    private boolean isImageOverlappingButtons() {
        if (!isImageClicked || clickedImageIndex < 0) {
            return false;
        }

        int imageWidth = (int) (1920 * zoomLevel);
        int imageHeight = (int) (1080 * zoomLevel);
        if (client != null) {
            imageWidth = (int) ((double) client.getWindow().getWidth() / 4 * zoomLevel);
            imageHeight = (int) ((double) client.getWindow().getHeight() / 4 * zoomLevel);
        }

        int x = (width - imageWidth) / 2 + (int) imageOffsetX;
        int y = (height - imageHeight) / 2 + (int) imageOffsetY;

        for (Element button : this.children()) {
            if (isButtonCoveredByImage((ButtonWidget) button, x, y, imageWidth, imageHeight)) {
                return true;
            }
        }
        return false;
    }

    private boolean isButtonCoveredByImage(ButtonWidget button, int imageX, int imageY, int imageWidth, int imageHeight) {
        int buttonX = button.getX();
        int buttonY = button.getY();
        int buttonWidth = button.getWidth();
        int buttonHeight = button.getHeight();

        return !(buttonX + buttonWidth < imageX || buttonX > imageX + imageWidth ||
                buttonY + buttonHeight < imageY || buttonY > imageY + imageHeight);
    }


    @Override
    public void resize(MinecraftClient client, int width, int height) {
        client.setScreen(null);
        super.resize(client, width, height);
    }

    @Override
    public void close() {
        if (isImageClicked) {
            isImageClicked = false;
            clickedImageIndex = -1;
        } else {
            super.close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

package de.thecoolcraft11.screen;

import com.google.gson.*;
import de.thecoolcraft11.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class LikedScreenshotsScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(LikedScreenshotsScreen.class);

    private final Screen parent;
    private static final List<Identifier> imageIds = new ArrayList<>();
    private static final List<Path> imagePaths = new ArrayList<>();
    private static final List<JsonObject> metaDatas = new ArrayList<>();
    private static final List<Boolean> isServerScreenshot = new ArrayList<>();

    private static final int IMAGES_PER_ROW = ConfigManager.getClientConfig().imagesPerRow;
    private static int IMAGE_WIDTH = 192;
    private static int IMAGE_HEIGHT = 108;
    private static int GAP = ConfigManager.getClientConfig().imageGap;
    private static int TOP_PADDING = ConfigManager.getClientConfig().imageTopPadding;

    private static boolean isImageClicked = false;
    private static int clickedImageIndex = -1;
    private int scrollOffset;

    private double zoomLevel = 1.0;
    private double imageOffsetX = 0.0;
    private double imageOffsetY = 0.0;

    private ButtonWidget openInAppButton;
    private ButtonWidget filterAllButton;
    private ButtonWidget filterLocalButton;
    private ButtonWidget filterServerButton;

    private enum FilterMode {
        ALL,
        LOCAL,
        SERVER
    }

    private FilterMode currentFilter = FilterMode.ALL;

    private static final String LOCAL_FILE_PATH = "./config/screenshotUploader/data/local.json";
    private static final String SERVER_DIR_PATH = "./screenshots_cache/";

    public LikedScreenshotsScreen(Screen parent) {
        super(Text.translatable("gui.screenshot_uploader.liked_screenshots.title"));
        this.parent = parent;
        initializeScreen();
    }

    private void initializeScreen() {
        imageIds.clear();
        imagePaths.clear();
        metaDatas.clear();
        isServerScreenshot.clear();
        clickedImageIndex = -1;
        isImageClicked = false;
        scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();

        int scaledHeight = height / 6;
        int scaledWidth = (scaledHeight * 16) / 9;
        int scaledGap = scaledHeight / 10;

        TOP_PADDING = height / 20;
        IMAGE_WIDTH = scaledWidth;
        IMAGE_HEIGHT = scaledHeight;
        GAP = scaledGap;

        int buttonWidth = width / 8;
        int buttonHeight = height / 25;
        int buttonY = height - buttonHeight - 5;

        ButtonWidget backButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.liked_screenshots.back"),
                button -> {
                    if (isImageClicked) {
                        isImageClicked = false;
                        clickedImageIndex = -1;
                    } else if (client != null) {
                        client.setScreen(parent);
                    }
                }
        ).dimensions(5, 5, buttonWidth, buttonHeight).build();

        openInAppButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.open_in_app"),
                button -> openImageInApp()
        ).dimensions((2 * buttonWidth) + 15, buttonY, buttonWidth, buttonHeight).build();

        filterAllButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.liked_screenshots.all"),
                button -> {
                    currentFilter = FilterMode.ALL;
                    updateFilterButtons();
                    loadLikedScreenshots();
                }
        ).dimensions(width - (buttonWidth * 3) - 15, 5, buttonWidth, buttonHeight).build();

        filterLocalButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.liked_screenshots.local"),
                button -> {
                    currentFilter = FilterMode.LOCAL;
                    updateFilterButtons();
                    loadLikedScreenshots();
                }
        ).dimensions(width - (buttonWidth * 2) - 10, 5, buttonWidth, buttonHeight).build();

        filterServerButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.liked_screenshots.server"),
                button -> {
                    currentFilter = FilterMode.SERVER;
                    updateFilterButtons();
                    loadLikedScreenshots();
                }
        ).dimensions(width - buttonWidth - 5, 5, buttonWidth, buttonHeight).build();

        addDrawableChild(backButton);
        addDrawableChild(openInAppButton);
        addDrawableChild(filterAllButton);
        addDrawableChild(filterLocalButton);
        addDrawableChild(filterServerButton);

        openInAppButton.visible = false;
        updateFilterButtons();

        if (MinecraftClient.getInstance() != null) {
            MinecraftClient.getInstance().execute(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                loadLikedScreenshots();
            });
        }
    }

    private void updateFilterButtons() {
        filterAllButton.active = currentFilter != FilterMode.ALL;
        filterLocalButton.active = currentFilter != FilterMode.LOCAL;
        filterServerButton.active = currentFilter != FilterMode.SERVER;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isImageClicked) {
                int imageWidth = (int) (IMAGE_WIDTH * zoomLevel);
                int imageHeight = (int) (IMAGE_HEIGHT * zoomLevel);

                int x = (width - imageWidth) / 2 + (int) imageOffsetX;
                int y = (height - imageHeight) / 2 + (int) imageOffsetY;

                if (mouseX >= x && mouseX <= x + imageWidth && mouseY >= y && mouseY <= y + imageHeight) {
                    return false;
                }

                for (Element buttonWidget : children()) {
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
        super.render(context, mouseX, mouseY, delta);

        if (isImageClicked && clickedImageIndex >= 0) {
            renderEnlargedImage(context);
            openInAppButton.visible = true;
        } else {
            renderGallery(context, mouseX, mouseY);
            openInAppButton.visible = false;

            if (client != null) {
                String titleText = "Liked Screenshots";
                switch (currentFilter) {
                    case LOCAL -> titleText = "Local Liked Screenshots";
                    case SERVER -> titleText = "Server Liked Screenshots";
                    default -> {
                    }
                }
                context.drawCenteredTextWithShadow(client.textRenderer, titleText,
                        width / 2, TOP_PADDING / 2, 0xFFFFFF);
            }

            if (imageIds.isEmpty()) {
                if (client != null) {
                    context.drawCenteredTextWithShadow(client.textRenderer, "No liked screenshots found",
                            width / 2, height / 2, 0xAAAAAA);
                }
            }
        }

    }

    private void renderGallery(DrawContext context, int mouseX, int mouseY) {
        int startX = (width - (IMAGES_PER_ROW * IMAGE_WIDTH + (IMAGES_PER_ROW - 1) * GAP)) / 2;
        int startY = TOP_PADDING + 20;

        if (imageIds.isEmpty()) return;

        for (int i = 0; i < imageIds.size(); i++) {
            int row = i / IMAGES_PER_ROW;
            int col = i % IMAGES_PER_ROW;
            int x = startX + col * (IMAGE_WIDTH + GAP);
            int y = startY + row * (IMAGE_HEIGHT + GAP) - scrollOffset;

            int borderColor = isServerScreenshot.get(i) ? 0xFF8888FF : 0xFF88FF88;
            context.fill(x - 2, y - 2, x + IMAGE_WIDTH + 2, y + IMAGE_HEIGHT + 2, borderColor);

            Identifier imageId = imageIds.get(i);
            context.drawTexture(RenderLayer::getGuiTextured, imageId, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

            if (mouseX > x && mouseX < x + IMAGE_WIDTH && mouseY > y && mouseY < y + IMAGE_HEIGHT) {
                context.fill(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, 0x80FFFFFF);
            }

            if (client != null && i < metaDatas.size()) {
                context.drawText(client.textRenderer, "❤", x + 5, y + IMAGE_HEIGHT - 15, 0xFF0000, true);

                String sourceIcon = isServerScreenshot.get(i) ? "S" : "L";
                int iconColor = isServerScreenshot.get(i) ? 0x8888FF : 0x88FF88;
                context.drawText(client.textRenderer, sourceIcon, x + IMAGE_WIDTH - 10, y + 5, iconColor, true);
            }
        }
    }

    private void renderEnlargedImage(DrawContext context) {
        if (clickedImageIndex < 0 || clickedImageIndex >= imageIds.size()) {
            return;
        }

        Identifier clickedImageId = imageIds.get(clickedImageIndex);

        int imageWidth = (int) (IMAGE_WIDTH * zoomLevel);
        int imageHeight = (int) (IMAGE_HEIGHT * zoomLevel);

        int x = (width - imageWidth) / 2 + (int) imageOffsetX;
        int y = (height - imageHeight) / 2 + (int) imageOffsetY;

        context.fill(0, 0, width, height, 0x80000000);

        final int borderWidth = 5;
        int borderColor = isServerScreenshot.get(clickedImageIndex) ? 0xFF8888FF : 0xFF88FF88;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, borderColor);

        context.drawTexture(RenderLayer::getGuiTextured, clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);

        if (clickedImageIndex < metaDatas.size() && client != null) {
            JsonObject metadata = metaDatas.get(clickedImageIndex);
            int textY = y + imageHeight + 10;

            String source = isServerScreenshot.get(clickedImageIndex) ? "Server Screenshot" : "Local Screenshot";
            context.drawText(client.textRenderer, source, x, textY, 0xFFFFFF, false);

            if (metadata.has("screenshotUrl")) {
                String path = metadata.get("screenshotUrl").getAsString();
                String fileName = new File(path).getName();
                context.drawText(client.textRenderer, fileName, x, textY + 15, 0xCCCCCC, false);
            }

            if (metadata.has("screenshotId")) {
                String id = metadata.get("screenshotId").getAsString();
                context.drawText(client.textRenderer, "ID: " + id, x, textY + 30, 0xAAAAAA, false);
            }
        }
    }

    private void loadLikedScreenshots() {
        initializeScreen();

        CompletableFuture.runAsync(() -> {
            Map<String, Path> serverScreenshotsMap = new HashMap<>();
            List<JsonObject> localLikedScreenshots = new ArrayList<>();

            File serverDir = new File(SERVER_DIR_PATH);
            if (serverDir.exists() && serverDir.isDirectory()) {
                for (File file : Objects.requireNonNull(serverDir.listFiles())) {
                    if (file.isFile() && file.getName().endsWith(".png")) {
                        String hash = file.getName().replace(".png", "");
                        serverScreenshotsMap.put("webimage:temp/" + hash, file.toPath());
                    }
                }
            }

            File localFile = new File(LOCAL_FILE_PATH);
            if (localFile.exists()) {
                try (FileReader reader = new FileReader(localFile, StandardCharsets.UTF_8)) {
                    JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
                    for (JsonElement element : jsonArray) {
                        if (element.isJsonObject()) {
                            JsonObject obj = element.getAsJsonObject();
                            localLikedScreenshots.add(obj);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to load liked screenshots: {}", e.getMessage());
                }
            }

            try {
                File[] jsonFiles = new File("./config/screenshotUploader/data/").listFiles(
                        (dir, name) -> name.endsWith(".json") && !name.equals("local.json")
                );

                if (jsonFiles != null) {
                    for (File jsonFile : jsonFiles) {
                        try (FileReader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
                            JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
                            for (JsonElement element : jsonArray) {
                                if (element.isJsonObject()) {
                                    JsonObject obj = element.getAsJsonObject();
                                    if (obj.has("screenshotId")) {
                                        String screenshotId = obj.get("screenshotId").getAsString();
                                        if (serverScreenshotsMap.containsKey(screenshotId)) {
                                            localLikedScreenshots.add(obj);
                                        }
                                    }
                                }
                            }
                        } catch (IOException | JsonSyntaxException e) {
                            logger.error("Failed to load server liked screenshots from {}: {}",
                                    jsonFile.getName(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing server JSON files: {}", e.getMessage());
            }

            for (JsonObject entry : localLikedScreenshots) {
                if (!entry.has("screenshotId") || !entry.has("screenshotUrl")) {
                    continue;
                }

                String screenshotId = entry.get("screenshotId").getAsString();
                String screenshotUrl = entry.get("screenshotUrl").getAsString();
                boolean isServer = screenshotId.startsWith("webimage:temp/");

                if ((currentFilter == FilterMode.LOCAL && isServer) ||
                        (currentFilter == FilterMode.SERVER && !isServer)) {
                    continue;
                }

                Path imagePath;
                if (isServer) {
                    imagePath = serverScreenshotsMap.get(screenshotId);
                    if (imagePath == null) {
                        String hash = screenshotId.replace("webimage:temp/", "");
                        imagePath = Paths.get(SERVER_DIR_PATH, hash + ".png");
                        if (!Files.exists(imagePath)) {
                            continue;
                        }
                    }
                } else {
                    imagePath = Paths.get(screenshotUrl);
                    if (!Files.exists(imagePath)) {
                        continue;
                    }
                }

                try {
                    Path finalImagePath = imagePath;

                    CompletableFuture.runAsync(() -> {
                        try {
                            NativeImage image;
                            String fileName = finalImagePath.getFileName().toString().toLowerCase();

                            if (fileName.endsWith(".png")) {
                                image = NativeImage.read(Files.newInputStream(finalImagePath));
                            } else {
                                image = loadNonPngImage(finalImagePath.toFile());
                                if (image == null) {
                                    logger.error("Failed to load non-PNG image: {}", finalImagePath);
                                    return;
                                }
                            }

                            Identifier textureId = Identifier.of("gallery", "textures/" + finalImagePath.getFileName().toString());

                            if (client != null) {
                                client.execute(() -> {
                                    client.getTextureManager().registerTexture(textureId,
                                            new NativeImageBackedTexture(image));

                                    imageIds.add(textureId);
                                    imagePaths.add(finalImagePath);
                                    metaDatas.add(entry);
                                    isServerScreenshot.add(isServer);
                                });
                            }
                        } catch (IOException e) {
                            logger.error("Failed to load image {}: {}", finalImagePath, e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error processing screenshot {}: {}", imagePath, e.getMessage());
                }
            }
        });
    }


    private NativeImage loadNonPngImage(File file) {
        try {
            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(file);
            if (bufferedImage == null) {
                return null;
            }

            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();

            NativeImage nativeImage = new NativeImage(width, height, true);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int rgb = bufferedImage.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    int red = (rgb >> 16) & 0xFF;
                    int green = (rgb >> 8) & 0xFF;
                    int blue = rgb & 0xFF;

                    nativeImage.setColorArgb(x, y, alpha << 24 | red << 16 | green << 8 | blue);
                }
            }

            return nativeImage;
        } catch (IOException e) {
            logger.error("Failed to load non-PNG image {}: {}", file.getName(), e.getMessage());
            return null;
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


    @Override
    public void close() {
        if (isImageClicked) {
            isImageClicked = false;
            clickedImageIndex = -1;
        } else if (client != null) {
            client.setScreen(parent);
        }
        imageIds.clear();
        imagePaths.clear();
        metaDatas.clear();
        isServerScreenshot.clear();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

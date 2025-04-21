package de.thecoolcraft11.screen;

import com.google.gson.*;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class GalleryScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(GalleryScreen.class);

    private static final List<Identifier> imageIds = new ArrayList<>();
    private static final List<Path> imagePaths = new ArrayList<>();
    private static final List<JsonObject> metaDatas = new ArrayList<>();

    private static final int IMAGES_PER_ROW = ConfigManager.getClientConfig().imagesPerRow;
    private static int IMAGE_WIDTH = 192;
    private static int IMAGE_HEIGHT = 108;
    private static int GAP = ConfigManager.getClientConfig().imageGap;
    private static int TOP_PADDING = ConfigManager.getClientConfig().imageTopPadding;

    private static boolean isImageClicked = false;
    private static int clickedImageIndex = -1;
    private int scrollOffset = 0;

    private double zoomLevel = 2.0;
    private double imageOffsetX = 0.0;
    private double imageOffsetY = 0.0;


    private ButtonWidget saveButton;
    private ButtonWidget deleteButton;
    private ButtonWidget openInAppButton;
    private ButtonWidget editButton;
    private static ButtonWidget likeButton;
    private ButtonWidget sortByButton;
    private ButtonWidget sortOrderButton;

    private ButtonWidget configButton;

    private static final String FILE_PATH = "./config/screenshotUploader/data/local.json";
    private static final LinkedHashMap<String, Boolean> likedScreenshots = new LinkedHashMap<>();

    private final List<ButtonWidget> navigatorButtons = new ArrayList<>();

    private final List<ButtonWidget> buttonsToHideOnOverlap = new ArrayList<>();

    private SortBy sortBy = SortBy.DEFAULT;
    private SortOrder sortOrder = SortOrder.ASCENDING;

    public GalleryScreen() {
        super(Text.translatable("gui.screenshot_uploader.screenshot_gallery.title"));
    }

    @Override
    protected void init() {
        super.init();
        imageIds.clear();
        imagePaths.clear();
        navigatorButtons.clear();
        metaDatas.clear();

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

        editButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.edit"),
                button -> {
                    if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
                        Path imagePath = imagePaths.get(clickedImageIndex - 1);
                        if (client != null) client.setScreen(new EditScreen(this, imagePath, null, (image) -> {
                        }));
                    }
                }
        ).dimensions((3 * buttonWidth) + 20, buttonY, buttonWidth, buttonHeight).build();

        likeButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.like_screenshot").withColor(0x2a2a2a),
                button -> likeScreenshot()
        ).dimensions((buttonWidth * 4) + 25, buttonY, 20, 20).build();

        configButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.config"),
                button -> {
                    if (client != null) {
                        client.setScreen(new ConfigScreen());
                    }
                }
        ).dimensions(5, 5, buttonWidth / 2, buttonHeight).build();

        sortByButton = ButtonWidget.builder(
                Text.literal(sortBy.toString()),
                button -> {
                    sortBy = SortBy.values()[(sortBy.ordinal() + 1) % SortBy.values().length];
                    sortByButton.setMessage(Text.of(sortBy.toString()));
                    loadImageSorted(sortOrder, sortBy);
                }
        ).dimensions(5, height - buttonHeight - 5, buttonWidth, buttonHeight).build();

        sortOrderButton = ButtonWidget.builder(
                Text.literal(sortOrder.toString()),
                button -> {
                    sortOrder = SortOrder.values()[(sortOrder.ordinal() + 1) % SortOrder.values().length];
                    sortOrderButton.setMessage(Text.of(sortOrder.toString()));
                    loadImageSorted(sortOrder, sortBy);
                }
        ).dimensions(5 + buttonWidth + 5, height - buttonHeight - 5, buttonWidth, buttonHeight).build();


        addDrawableChild(saveButton);
        addDrawableChild(deleteButton);
        addDrawableChild(openInAppButton);
        addDrawableChild(configButton);
        addDrawableChild(editButton);
        addDrawableChild(likeButton);
        addDrawableChild(sortByButton);
        addDrawableChild(sortOrderButton);

        saveButton.visible = false;
        deleteButton.visible = false;
        openInAppButton.visible = false;
        editButton.visible = false;
        configButton.visible = true;
        likeButton.visible = true;

        buttonsToHideOnOverlap.add(saveButton);
        buttonsToHideOnOverlap.add(deleteButton);
        buttonsToHideOnOverlap.add(openInAppButton);
        buttonsToHideOnOverlap.add(editButton);
        buttonsToHideOnOverlap.add(likeButton);
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
            editButton.visible = true;
            configButton.visible = false;
            likeButton.visible = false;
            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = false);
        } else {
            renderGallery(context, mouseX, mouseY);

            saveButton.visible = false;
            deleteButton.visible = false;
            openInAppButton.visible = false;
            editButton.visible = false;
            configButton.visible = true;
            likeButton.visible = true;

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

        if (imageIds.isEmpty()) return;
        for (int i = 0; i < imageIds.size(); i++) {
            if (imagePaths.size() <= i) {
                break;
            }
            int row = i / IMAGES_PER_ROW;
            int col = i % IMAGES_PER_ROW;
            int x = startX + col * (IMAGE_WIDTH + GAP);
            int y = startY + row * (IMAGE_HEIGHT + GAP) - scrollOffset;

            context.fill(x - 2, y - 2, x + IMAGE_WIDTH + 2, y + IMAGE_HEIGHT + 2, 0xFF888888);
            Identifier imageId = null;
            if (imageIds.size() > i) {
                imageId = imageIds.get(i);
            }
            if (imageId == null) {
                logger.error("Image ID is null for index {}", i);
                continue;
            }
            RenderSystem.setShaderTexture(0, imageId);
            context.drawTexture(imageId, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

            if (mouseX > x && mouseX < x + IMAGE_WIDTH && mouseY > y && mouseY < y + IMAGE_HEIGHT) {
                context.fill(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, 0x80FFFFFF);
            }
            if (client != null) {
                if (likedScreenshots.containsKey(imagePaths.get(i).toString()) && likedScreenshots.get(imagePaths.get(i).toString())) {
                    context.drawText(client.textRenderer, "❤", x + 5, y + IMAGE_HEIGHT - 10, 0xFFFFFF, false);
                }
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

        int sidebarWidth = 300;
        int sidebarHeight = imageHeight;
        int sidebarXLeft = x - sidebarWidth;

        context.fill(sidebarXLeft, y, sidebarXLeft + sidebarWidth, y + sidebarHeight, 0xCC000000);


        if (clickedImageIndex >= 0 && clickedImageIndex < metaDatas.size()) {
            Map<Text, Text> drawableInfo = getStringStringMap();

            int textXLeft = sidebarXLeft + 10;
            int textYLeft = y + 20;
            for (Text info : drawableInfo.keySet()) {
                context.drawText(client.textRenderer, info.copy().append(drawableInfo.get(info)), textXLeft, textYLeft, 0xFFFFFF, false);
                textYLeft += 10;
            }
        }

        likeButton.setMessage(Text.translatable("gui.screenshot_uploader.screenshot_gallery.like_screenshot").withColor(likedScreenshots.containsKey(imagePaths.get(clickedImageIndex).toString()) && likedScreenshots.get(imagePaths.get(clickedImageIndex).toString()) ? 0xFFFFFF : 0x2a2a2a));

    }

    private void loadAllImagesAsync() {
        Path screenshotsDir = Paths.get(System.getProperty("user.dir"), "screenshots");

        CompletableFuture.runAsync(() -> {
            try (Stream<Path> paths = Files.list(screenshotsDir)) {
                Set<String> likedScreenshotsSet = loadLikedScreenshots();

                List<Path> sortedPaths = paths
                        .filter(path -> path.toString().endsWith(".png"))
                        .sorted(Comparator
                                .comparing((Path path) -> likedScreenshotsSet.contains(path.toString()) ? 0 : 1)
                                .thenComparing((Path path) -> path.toFile().lastModified(), Comparator.reverseOrder())
                        )
                        .toList();


                imagePaths.clear();
                imagePaths.addAll(sortedPaths);

                loadImagesAsync();
            } catch (IOException e) {
                logger.error("Failed to load images: {}", e.getMessage());
            }
        });
    }

    private CompletableFuture<?> asyncSortFuture;
    private final AtomicInteger sortTaskId = new AtomicInteger();


    private void loadImagesAsync() {
        int currentTaskId = sortTaskId.incrementAndGet();
        asyncSortFuture = CompletableFuture.runAsync(() -> {
            Set<String> likedScreenshotsSet = loadLikedScreenshots();
            if (sortTaskId.get() != currentTaskId) return;
            for (Path path : imagePaths) {
                if (sortTaskId.get() != currentTaskId) return;
                try {
                    NativeImage image = NativeImage.read(Files.newInputStream(path));
                    Identifier textureId = Identifier.of("gallery", "textures/" + path.getFileName().toString());
                    MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
                    imageIds.add(textureId);

                    String imagePathString = path.toString();
                    if (likedScreenshotsSet.contains(imagePathString)) {
                        likedScreenshots.put(imagePathString, true);
                    }
                    JsonObject metaData = new JsonObject();
                    File jsonData = new File(path.getParent().toString(), path.getFileName().toString().replace(".png", ".json"));
                    if (jsonData.exists()) {
                        try (FileReader reader = new FileReader(jsonData, StandardCharsets.UTF_8)) {
                            metaData = JsonParser.parseReader(reader).getAsJsonObject();
                        } catch (JsonSyntaxException e) {
                            logger.error("Corrupt JSON file detected. Resetting it.", e);
                        } catch (IOException e) {
                            logger.error("Error reading the JSON file.", e);
                        }
                    }
                    metaData.addProperty("screenshotUrl", imagePathString);
                    metaData.addProperty("liked", likedScreenshotsSet.contains(imagePathString));
                    metaData.addProperty("screenshotDate", new Date(jsonData.lastModified()).toString());

                    metaDatas.add(metaData);

                } catch (IOException e) {
                    logger.error("Failed to load image '{}': {}", path, e.getMessage());
                }
            }
        });
    }

    private static Set<String> loadLikedScreenshots() {
        Set<String> likedScreenshots = new HashSet<>();
        File file = new File(FILE_PATH);

        if (file.exists() && file.length() > 0) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    JsonObject obj = element.getAsJsonObject();
                    if (obj.has("screenshotUrl")) {
                        likedScreenshots.add(obj.get("screenshotUrl").getAsString());
                    }
                }
            } catch (JsonSyntaxException e) {
                logger.error("Corrupt JSON file detected. Resetting it.", e);
            } catch (IOException e) {
                logger.error("Error reading the like file.", e);
            }
        }
        return likedScreenshots;
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

    private static void likeScreenshot() {

        if (imageIds.isEmpty() || clickedImageIndex < 0 || clickedImageIndex >= imageIds.size()) {
            System.err.println("Invalid image index or list is empty.");
            return;
        }

        String screenshotId = String.valueOf(imageIds.get(clickedImageIndex));
        String screenshotUrl = String.valueOf(imagePaths.get(clickedImageIndex));

        File file = new File(FILE_PATH);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs();
            if (dirsCreated) {
                logger.info("Created missing directories: {}", parentDir.getAbsolutePath());
            }
        }

        JsonArray jsonArray = new JsonArray();

        if (file.exists() && file.length() > 0) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
            } catch (JsonSyntaxException e) {
                logger.error("Corrupt JSON file detected. Resetting it.", e);
                jsonArray = new JsonArray();
            } catch (IOException e) {
                logger.error("Error reading the like file.", e);
                return;
            }
        }

        boolean screenshotAlreadyLiked = false;
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject existingLike = jsonArray.get(i).getAsJsonObject();
            if (existingLike.has("screenshotId") && existingLike.get("screenshotId").getAsString().equals(screenshotId)) {
                jsonArray.remove(i);
                screenshotAlreadyLiked = true;
                break;
            }
        }

        if (!screenshotAlreadyLiked) {
            JsonObject newLike = new JsonObject();
            newLike.addProperty("screenshotId", screenshotId);
            newLike.addProperty("screenshotUrl", screenshotUrl);
            jsonArray.add(newLike);
        }

        try (FileWriter writer = new FileWriter(FILE_PATH, StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(jsonArray));
        } catch (IOException e) {
            logger.error("Error while saving likes.", e);
        }
        isImageClicked = false;
        clickedImageIndex = -1;
    }


    private void loadImageSorted(SortOrder sortOrder, SortBy sortBy) {
        int currentTaskId = sortTaskId.incrementAndGet();
        sortByButton.active = false;
        sortOrderButton.active = false;

        if (asyncSortFuture != null && !asyncSortFuture.isDone()) {
            asyncSortFuture.cancel(true);
        }

        Path screenshotsDir = Paths.get(System.getProperty("user.dir"), "screenshots");

        asyncSortFuture = CompletableFuture.runAsync(() -> {
            try {
                Set<String> likedScreenshotsSet = loadLikedScreenshots();

                List<Path> sortedPaths;
                try (Stream<Path> paths = Files.list(screenshotsDir)) {
                    sortedPaths = paths
                            .filter(path -> path.toString().endsWith(".png"))
                            .sorted((path1, path2) -> {
                                int result;
                                if (sortBy == SortBy.DEFAULT) {
                                    if (likedScreenshotsSet.contains(path1.toString()) && !likedScreenshotsSet.contains(path2.toString())) {
                                        return -1;
                                    } else if (!likedScreenshotsSet.contains(path1.toString()) && likedScreenshotsSet.contains(path2.toString())) {
                                        return 1;
                                    }

                                    return Long.compare(path2.toFile().lastModified(), path1.toFile().lastModified());

                                } else {
                                    result = switch (sortBy) {
                                        case NAME ->
                                                path1.getFileName().toString().compareTo(path2.getFileName().toString());
                                        case DATE ->
                                                Long.compare(path2.toFile().lastModified(), path1.toFile().lastModified());
                                        case SIZE -> Long.compare(path1.toFile().length(), path2.toFile().length());
                                        default -> 0;
                                    };
                                }
                                return sortOrder == SortOrder.ASCENDING ? result : -result;
                            })
                            .toList();
                }

                if (sortTaskId.get() != currentTaskId) return;

                MinecraftClient.getInstance().execute(() -> {
                    imageIds.clear();
                    imagePaths.clear();
                    metaDatas.clear();

                    imagePaths.addAll(sortedPaths);

                    sortByButton.active = true;
                    sortOrderButton.active = true;
                });
                for (Path path : sortedPaths) {
                    if (sortTaskId.get() != currentTaskId) return;
                    JsonObject metaData = new JsonObject();
                    File jsonData = new File(path.getParent().toString(), path.getFileName().toString().replace(".png", ".json"));
                    if (jsonData.exists()) {
                        try (FileReader reader = new FileReader(jsonData, StandardCharsets.UTF_8)) {
                            metaData = JsonParser.parseReader(reader).getAsJsonObject();
                        } catch (JsonSyntaxException e) {
                            logger.error("Corrupt JSON file detected. Resetting it.", e);
                        } catch (IOException e) {
                            logger.error("Error reading the JSON file.", e);
                        }
                    }

                    metaData.addProperty("screenshotUrl", path.toString());
                    metaData.addProperty("liked", likedScreenshotsSet.contains(path.toString()));
                    metaData.addProperty("screenshotDate", new Date(jsonData.lastModified()).toString());

                    Identifier textureId = null;
                    try {
                        NativeImage image = NativeImage.read(Files.newInputStream(path));
                        textureId = Identifier.of("gallery", "textures/" + path.getFileName().toString());
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(image));
                    } catch (IOException e) {
                        logger.error("Failed to load image during sort '{}': {}", path, e.getMessage());
                    }

                    JsonObject finalMetaData = metaData;
                    Identifier finalTextureId = textureId;
                    MinecraftClient.getInstance().execute(() -> {
                        metaDatas.add(finalMetaData);
                        imageIds.add(finalTextureId);
                    });
                }
            } catch (IOException e) {
                logger.error("Failed to sort images: {}", e.getMessage());
            }
        });
    }


    private @NotNull LinkedHashMap<Text, Text> getStringStringMap() {
        JsonObject metaData = metaDatas.get(clickedImageIndex);
        LinkedHashMap<Text, Text> drawableInfo = new LinkedHashMap<>();

        if (metaData.has("username"))
            drawableInfo.put(Text.literal("Username: "), metaData.has("username") && metaData.get("username").isJsonPrimitive() ? Text.literal(metaData.get("username").getAsString()) : metaData.has("fileUsername") && metaData.get("fileUsername").isJsonPrimitive() ? Text.literal(metaData.get("fileUsername").getAsString()) : Text.literal("N/A"));
        if (metaData.has("server_address"))
            drawableInfo.put(Text.literal("Server: "), metaData.has("server_address") && metaData.get("server_address").isJsonPrimitive() ? Text.literal(metaData.get("server_address").getAsString()) : Text.literal("N/A"));
        if (metaData.has("world_name"))
            drawableInfo.put(Text.literal("World: "), metaData.has("world_name") && metaData.get("world_name").isJsonPrimitive() ? Text.literal(metaData.get("world_name").getAsString()) : Text.literal("N/A"));
        if (metaData.has("coordinates"))
            drawableInfo.put(Text.literal("Location: "), metaData.has("coordinates") && metaData.get("coordinates").isJsonPrimitive() ? Text.literal(metaData.get("coordinates").getAsString()) : Text.literal("N/A"));
        if (metaData.has("facing_direction"))
            drawableInfo.put(Text.literal("Facing: "), metaData.has("facing_direction") && metaData.get("facing_direction").isJsonPrimitive() ? Text.literal(metaData.get("facing_direction").getAsString()) : Text.literal("N/A"));
        if (metaData.has("player_state"))
            drawableInfo.put(Text.literal("Player: "), metaData.has("player_state") && metaData.get("player_state").isJsonPrimitive() ? Text.literal(metaData.get("player_state").getAsString()) : Text.literal("N/A"));
        if (metaData.has("biome"))
            drawableInfo.put(Text.literal("Biome: "), metaData.has("biome") && metaData.get("biome").isJsonPrimitive() ? Text.literal(metaData.get("biome").getAsString()) : Text.literal("N/A"));
        if (metaData.has("world_info"))
            drawableInfo.put(Text.literal("World Info: "), metaData.has("world_info") && metaData.get("world_info").isJsonPrimitive() ? Text.literal(metaData.get("world_info").getAsString()) : Text.literal("N/A"));
        if (metaData.has("world_seed"))
            drawableInfo.put(Text.literal("Seed: "), metaData.has("world_seed") && metaData.get("world_seed").isJsonPrimitive() ? Text.literal(metaData.get("world_seed").getAsString()) : Text.literal("N/A"));
        drawableInfo.put(Text.literal(" "), Text.literal(" "));
        if (metaData.has("current_time"))
            drawableInfo.put(metaData.has("current_time") ? getTimestamp(metaData.get("current_time").getAsLong()) : metaData.has("date") ? getTimestamp(metaData.get("date").getAsLong()) : Text.literal("N/A"), Text.literal(""));
        if (metaData.has("current_time"))
            drawableInfo.put(metaData.has("current_time") ? getTimeAgo(metaData.get("current_time").getAsLong()) : metaData.has("date") ? getTimeAgo(metaData.get("date").getAsLong()) : Text.literal("N/A"), Text.literal(""));

        return drawableInfo;
    }


    public static Text getTimestamp(long millis) {

        Instant timestampInstant = Instant.ofEpochMilli(millis);

        LocalDateTime timestampDateTime = LocalDateTime.ofInstant(timestampInstant, ZoneId.systemDefault());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        return Text.literal(timestampDateTime.format(formatter)).styled(style -> style.withUnderline(true));
    }

    private static Text getTimeAgo(long millis) {
        Instant timestampInstant = Instant.ofEpochMilli(millis);
        Instant nowInstant = Instant.now();
        Duration duration = Duration.between(timestampInstant, nowInstant);
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return Text.literal("(").append(Text.translatable("message.screenshot_uploader.seconds_ago", seconds).append(")"));
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return Text.literal("(").append(Text.translatable("message.screenshot_uploader.minutes_ago", minutes).append(")"));
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return Text.literal("(").append(Text.translatable("message.screenshot_uploader.hours_ago", hours, minutes).append(")"));
        } else {
            long days = seconds / 86400;
            return Text.literal("(").append(Text.translatable("message.screenshot_uploader.days_ago", days).append(")"));
        }
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

    private enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    private enum SortBy {
        NAME,
        DATE,
        SIZE,
        DEFAULT
    }
}

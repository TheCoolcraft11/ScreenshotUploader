package de.thecoolcraft11.screen;

import com.google.gson.*;
import de.thecoolcraft11.config.AlbumManager;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.config.data.Album;
import de.thecoolcraft11.util.ReceivePackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
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
import java.time.*;
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
    private int scrollOffset;

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

    private ButtonWidget addToAlbumButton;
    private ButtonWidget viewTagsButton;

    private ButtonWidget configButton;
    private TextFieldWidget searchField;
    private ButtonWidget albumConfigButton;
    private ButtonWidget screenshotStatisticsButton;
    private ButtonWidget uploadToServerButton;
    private ButtonWidget likedScreenshotsButton;

    private final List<Path> originalImagePaths = new ArrayList<>();
    private String lastSearchQuery = "";
    private Runnable searchDebounceTask = null;

    private static final String FILE_PATH = "./config/screenshotUploader/data/local.json";
    private static final LinkedHashMap<String, Boolean> likedScreenshots = new LinkedHashMap<>();

    private final List<ButtonWidget> navigatorButtons = new ArrayList<>();

    private final List<ButtonWidget> buttonsToHideOnOverlap = new ArrayList<>();

    private SortBy sortBy = SortBy.DEFAULT;
    private SortOrder sortOrder = SortOrder.ASCENDING;

    private static UUID albumUUID;

    private static final List<Path> newScreenshots = new ArrayList<>();

    private int starY = 0;
    private boolean goingUp = true;


    public GalleryScreen() {
        super(Text.translatable("gui.screenshot_uploader.screenshot_gallery.title"));
        albumUUID = null;
        initializeScreen();
    }

    public GalleryScreen(UUID passedAlbumUUID) {
        super(Text.translatable("gui.screenshot_uploader.screenshot_gallery.title"));
        albumUUID = passedAlbumUUID;
        initializeScreen();
    }

    public static void addNewScreenshot(Path newScreenshot) {
        newScreenshots.add(newScreenshot);
    }

    public static void removeNewScreenshot(Path path) {
        newScreenshots.remove(path);
    }

    public static List<Path> getNewScreenshots() {
        return newScreenshots;
    }


    private void initializeScreen() {
        imageIds.clear();
        imagePaths.clear();
        navigatorButtons.clear();
        metaDatas.clear();
        originalImagePaths.clear();
        clickedImageIndex = -1;
        isImageClicked = false;
        scrollOffset = 0;
        likedScreenshots.clear();
        cancelAllAsyncTasks();
        sortTaskId.incrementAndGet();
        if (asyncSortFuture != null && !asyncSortFuture.isDone()) {
            asyncSortFuture.cancel(true);
        }

        if (searchDebounceTask != null) {
            MinecraftClient.getInstance().send(() -> searchDebounceTask = null);
        }

        sortTaskId.incrementAndGet();
    }

    @Override
    protected void init() {
        super.init();

        GAP = ConfigManager.getClientConfig().imageGap;
        TOP_PADDING = ConfigManager.getClientConfig().imageTopPadding;

        int scaledHeight = height / 6;
        int scaledWidth = (scaledHeight * 16) / 9;
        int scaledGap = scaledHeight / 10;

        TOP_PADDING = (height / 20) + TOP_PADDING;
        IMAGE_WIDTH = scaledWidth;
        IMAGE_HEIGHT = scaledHeight;
        GAP = scaledGap;


        if (MinecraftClient.getInstance() != null) {
            MinecraftClient.getInstance().execute(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                initializeScreen();
            });
        }
        if (MinecraftClient.getInstance() != null) {
            MinecraftClient.getInstance().execute(() -> {
                try {
                    Thread.sleep(0);
                } catch (InterruptedException ignored) {
                }
                loadAllImagesAsync();
            });
        }


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


        albumConfigButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.album_config"),
                button -> {
                    if (client != null) {
                        client.setScreen(new AlbumScreen(this));
                    }
                }
        ).dimensions(5 + 5 + buttonWidth / 2, 5, buttonWidth / 2, buttonHeight).build();

        screenshotStatisticsButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.local_statistics"),
                button -> {
                    if (client != null) {
                        client.setScreen(new ScreenshotStatisticsScreen(this));
                    }
                }
        ).dimensions(5, 5 + 5 + buttonHeight, (int) (buttonWidth / 1.5f), buttonHeight).build();

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

        searchField = new TextFieldWidget(textRenderer, 5 + (2 * buttonWidth) + 10, height - buttonHeight - 5, buttonWidth * 2, buttonHeight, Text.translatable("gui.screenshot_uploader.screenshot_gallery.search"));
        searchField.setChangedListener(query -> {
            if (searchDebounceTask != null) {
                MinecraftClient.getInstance().send(searchDebounceTask);
            }

            searchDebounceTask = () -> performSearch(query);
            MinecraftClient.getInstance().send(searchDebounceTask);
        });

        addToAlbumButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.add_to_album"),
                button -> {
                    if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
                        Path imagePath = imagePaths.get(clickedImageIndex);
                        if (client != null) {
                            client.setScreen(new SelectAlbumScreen(this, imagePath.toString()));
                        }
                    }
                }
        ).dimensions((buttonWidth * 4) + 50, buttonY, buttonWidth, buttonHeight).build();

        viewTagsButton = ButtonWidget.builder(
                        Text.translatable("gui.screenshot_uploader.screenshot_gallery.view_tags"),
                        button -> {
                            String screenshotName = null;
                            if (imagePaths.get(clickedImageIndex) != null) {
                                screenshotName = imagePaths.get(clickedImageIndex).toString();

                            }

                            if (screenshotName != null) {
                                if (client != null) {
                                    client.setScreen(new ScreenshotTaggingScreen(this, screenshotName));
                                }
                            }
                        })
                .dimensions((buttonWidth * 5) + 55, buttonY, buttonWidth, buttonHeight).build();


        uploadToServerButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.upload_to_server"),
                button -> {
                    if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
                        Path imagePath = imagePaths.get(clickedImageIndex);
                        if (client != null) {
                            client.setScreen(new UploadToServerScreen(this, imagePath.toAbsolutePath()));
                        }
                    }
                }
        ).dimensions((buttonWidth * 6) + 60, buttonY, buttonWidth, buttonHeight).build();

        likedScreenshotsButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.liked_screenshots"),
                button -> {
                    if (client != null) {
                        client.setScreen(new LikedScreenshotsScreen(this));
                    }
                }
        ).dimensions(5, 5 + 5 + 5 + buttonHeight * 2, buttonWidth, buttonHeight).build();

        addDrawableChild(addToAlbumButton);

        addDrawableChild(saveButton);

        addDrawableChild(deleteButton);

        addDrawableChild(openInAppButton);

        addDrawableChild(configButton);

        addDrawableChild(editButton);

        addDrawableChild(likeButton);

        addDrawableChild(sortByButton);

        addDrawableChild(sortOrderButton);

        addDrawableChild(searchField);

        addDrawableChild(viewTagsButton);

        addDrawableChild(albumConfigButton);

        addDrawableChild(screenshotStatisticsButton);

        addDrawableChild(uploadToServerButton);

        addDrawableChild(likedScreenshotsButton);

        saveButton.visible = false;
        deleteButton.visible = false;
        openInAppButton.visible = false;
        editButton.visible = false;
        configButton.visible = true;
        likeButton.visible = true;
        sortByButton.visible = true;
        sortOrderButton.visible = true;
        searchField.visible = true;
        searchField.setMaxLength(128);
        searchField.setPlaceholder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.search_placeholder"));
        addToAlbumButton.visible = false;
        albumConfigButton.visible = true;
        screenshotStatisticsButton.visible = true;
        uploadToServerButton.visible = false;
        likedScreenshotsButton.visible = true;

        buttonsToHideOnOverlap.add(saveButton);
        buttonsToHideOnOverlap.add(deleteButton);
        buttonsToHideOnOverlap.add(openInAppButton);
        buttonsToHideOnOverlap.add(editButton);
        buttonsToHideOnOverlap.add(likeButton);
        buttonsToHideOnOverlap.add(addToAlbumButton);
        buttonsToHideOnOverlap.add(viewTagsButton);
        buttonsToHideOnOverlap.add(uploadToServerButton);
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
//         renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        if (isImageClicked && clickedImageIndex >= 0) {
            renderEnlargedImage(context);

            saveButton.visible = true;
            deleteButton.visible = true;
            openInAppButton.visible = true;
            editButton.visible = true;
            configButton.visible = false;
            likeButton.visible = false;
            sortByButton.visible = false;
            sortOrderButton.visible = false;
            searchField.visible = false;
            viewTagsButton.visible = true;
            addToAlbumButton.visible = true;
            albumConfigButton.visible = false;
            screenshotStatisticsButton.visible = false;
            uploadToServerButton.visible = true;
            likedScreenshotsButton.visible = false;
            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = false);
        } else {
            renderGallery(context, mouseX, mouseY);

            saveButton.visible = false;
            deleteButton.visible = false;
            openInAppButton.visible = false;
            editButton.visible = false;
            configButton.visible = true;
            likeButton.visible = true;
            sortByButton.visible = true;
            sortOrderButton.visible = true;
            searchField.visible = true;
            viewTagsButton.visible = false;
            addToAlbumButton.visible = false;
            albumConfigButton.visible = true;
            screenshotStatisticsButton.visible = true;
            uploadToServerButton.visible = false;
            likedScreenshotsButton.visible = true;

            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = true);
        }
        boolean isImageOverlappingButtons = clickedImageIndex >= 0 && isImageOverlappingButtons();

        for (Element button : children()) {
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

            int fillColor = 0xFF888888;

            if (i < metaDatas.size()) {
                JsonObject metadata = metaDatas.get(i);
                if (metadata != null && metadata.has("album") && client != null) {
                    String albumUUID = metadata.get("album").getAsString();
                    Album album = AlbumManager.getAlbum(UUID.fromString(albumUUID));
                    if (album != null) {
                        String colorHex = album.getColor();
                        try {
                            fillColor = Integer.parseInt(colorHex.replace("#", ""), 16) | 0xFF000000;
                        } catch (NumberFormatException e) {
                            logger.error("Failed to parse color for album {}: {}; {}", album.getTitle(), colorHex, e);
                        }
                    }
                }
            }

            context.fill(x - 2, y - 2, x + IMAGE_WIDTH + 2, y + IMAGE_HEIGHT + 2, fillColor);


            Identifier imageId = null;
            if (imageIds.size() > i) {
                imageId = imageIds.get(i);
            }
            if (imageId == null) {
                logger.error("Image ID is null for index {}", i);
                continue;
            }
            context.drawTexture(RenderPipelines.GUI_TEXTURED, imageId, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

            if (mouseX > x && mouseX < x + IMAGE_WIDTH && mouseY > y && mouseY < y + IMAGE_HEIGHT) {
                context.fill(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, 0x80FFFFFF);
            }

            if (client != null) {
                if (likedScreenshots.containsKey(imagePaths.get(i).toString()) && likedScreenshots.get(imagePaths.get(i).toString())) {
                    context.drawText(client.textRenderer, "❤", x + 5, y + IMAGE_HEIGHT - 10, 0xFFFFFF, false);
                }
            }

            if (imagePaths.size() > i) {
                if (newScreenshots.contains(imagePaths.get(i))) {
                    if (client != null) {
                        context.drawText(client.textRenderer, "★", x + 20, y + IMAGE_HEIGHT - 10 - starY, 0xFFFF00, false);
                    }
                }
            }

            if (i < metaDatas.size() && metaDatas.get(i) != null) {
                JsonObject metadata = metaDatas.get(i);
                if (metadata.has("tags") && metadata.get("tags").isJsonArray()) {
                    JsonArray tags = metadata.getAsJsonArray("tags");
                    if (client != null) {
                        if (!tags.isEmpty()) {
                            String firstTag = tags.get(0).getAsString();
                            int tagWidth = client.textRenderer.getWidth(firstTag);
                            int tagX = x + IMAGE_WIDTH - tagWidth - 5;
                            int tagY = y + IMAGE_HEIGHT - 12;

                            final int padding = 2;
                            context.fill(tagX - padding, tagY - padding,
                                    tagX + tagWidth + padding, tagY + client.textRenderer.fontHeight + padding,
                                    0xA0502000);

                            context.fill(tagX - padding - 1, tagY - padding - 1,
                                    tagX + tagWidth + padding + 1, tagY - padding,
                                    0xFFDDDDDD);
                            context.fill(tagX - padding - 1, tagY + client.textRenderer.fontHeight + padding,
                                    tagX + tagWidth + padding + 1, tagY + client.textRenderer.fontHeight + padding + 1,
                                    0xFFDDDDDD);
                            context.fill(tagX - padding - 1, tagY - padding,
                                    tagX - padding, tagY + client.textRenderer.fontHeight + padding,
                                    0xFFDDDDDD);
                            context.fill(tagX + tagWidth + padding, tagY - padding,
                                    tagX + tagWidth + padding + 1, tagY + client.textRenderer.fontHeight + padding,
                                    0xFFDDDDDD);

                            context.drawText(client.textRenderer, firstTag, tagX + 1, tagY + 1, 0x000000, false);
                            context.drawText(client.textRenderer, firstTag, tagX, tagY, 0xFFFFFF, false);
                        }
                    }
                }
            }
        }
    }

    private void renderEnlargedImage(DrawContext context) {
        if (clickedImageIndex < 0 || clickedImageIndex >= imageIds.size()) {
            return;
        }

        Identifier clickedImageId = imageIds.get(clickedImageIndex);

        removeNewScreenshot(imagePaths.get(clickedImageIndex));

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

        final int borderWidth = 5;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, 0xFFFFFFFF);

        context.drawTexture(RenderPipelines.GUI_TEXTURED, clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);


        final int sidebarWidth = 300;
        int sidebarXLeft = x - sidebarWidth;


        if (clickedImageIndex >= 0 && clickedImageIndex < metaDatas.size()) {
            Map<Text, Text> drawableInfo = getStringStringMap();

            int textXLeft = sidebarXLeft + 10;
            int textYLeft = y + 20;
            for (Text info : drawableInfo.keySet()) {
                context.drawText(client.textRenderer, info.copy().append(drawableInfo.get(info)), textXLeft, textYLeft, 0xFFFFFF, false);
                textYLeft += 10;
            }
        }

        if (imagePaths.size() > clickedImageIndex && imagePaths.get(clickedImageIndex) != null) {
            likeButton.setMessage(Text.translatable("gui.screenshot_uploader.screenshot_gallery.like_screenshot").withColor(likedScreenshots.containsKey(imagePaths.get(clickedImageIndex).toString()) && likedScreenshots.get(imagePaths.get(clickedImageIndex).toString()) ? 0xFFFFFF : 0x2a2a2a));
        } else {
            likeButton.setMessage(Text.translatable("gui.screenshot_uploader.screenshot_gallery.like_screenshot").withColor(0x2a2a2a));
        }

    }

    private void loadAllImagesAsync() {
        Path screenshotsDir = Paths.get(System.getProperty("user.dir"), "screenshots");

        CompletableFuture.runAsync(() -> {
            try (Stream<Path> paths = Files.list(screenshotsDir)) {
                Set<String> likedScreenshotsSet = loadLikedScreenshots();

                Stream<Path> filteredPaths = getFilteredPaths(paths);

                List<Path> sortedPaths = filteredPaths
                        .sorted(Comparator
                                .comparing((Path path) -> likedScreenshotsSet.contains(path.toString()) ? 0 : 1)
                                .thenComparing((Path path) -> path.toFile().lastModified(), Comparator.reverseOrder())
                        )
                        .toList();

                originalImagePaths.clear();
                originalImagePaths.addAll(sortedPaths);

                imagePaths.clear();
                imagePaths.addAll(sortedPaths);

                loadImagesAsync();
            } catch (IOException e) {
                logger.error("Failed to load images: {}", e.getMessage());
            }
        });
    }

    private static @NotNull Stream<Path> getFilteredPaths(Stream<Path> paths) {
        Stream<Path> filteredPaths = paths.filter(path -> path.toString().endsWith(".png"));

        if (albumUUID != null) {
            filteredPaths = filteredPaths.filter(path -> {
                File jsonData = new File(path.getParent().toString(),
                        path.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));

                if (jsonData.exists()) {
                    try (FileReader reader = new FileReader(jsonData, StandardCharsets.UTF_8)) {
                        JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
                        return metadata.has("album") &&
                                metadata.get("album").getAsString().equals(albumUUID.toString());
                    } catch (Exception e) {
                        logger.error("Error reading metadata for album filtering: {}", e.getMessage());
                    }
                }
                return false;
            });
        }
        return filteredPaths;
    }

    private void performSearch(String query) {
        if (query.equals(lastSearchQuery)) {
            return;
        }

        lastSearchQuery = query;

        if (query.isEmpty()) {
            imagePaths.clear();
            imagePaths.addAll(originalImagePaths);
            imageIds.clear();
            metaDatas.clear();
            loadImagesAsync();
            return;
        }

        List<SearchTerm> searchTerms = parseSearchTerms(query);

        CompletableFuture.runAsync(() -> {
            List<Path> matchingPaths = new ArrayList<>();
            Map<Path, JsonObject> pathToMetadata = new HashMap<>();

            for (Path path : originalImagePaths) {
                try {
                    JsonObject metaData = new JsonObject();
                    File jsonData = new File(path.getParent().toString(), path.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));
                    if (jsonData.exists()) {
                        try (FileReader reader = new FileReader(jsonData, StandardCharsets.UTF_8)) {
                            metaData = JsonParser.parseReader(reader).getAsJsonObject();
                        } catch (JsonSyntaxException e) {
                            logger.error("Corrupt JSON file detected while searching.", e);
                        }
                    }

                    metaData.addProperty("filename", path.getFileName().toString().toLowerCase());
                    pathToMetadata.put(path, metaData);

                } catch (IOException e) {
                    logger.error("Failed to load metadata for search: {}", e.getMessage());
                }
            }

            for (Map.Entry<Path, JsonObject> entry : pathToMetadata.entrySet()) {
                Path path = entry.getKey();
                JsonObject metaData = entry.getValue();

                boolean matchesAllTerms = true;

                for (SearchTerm term : searchTerms) {
                    if (!matchesTerm(metaData, term)) {
                        matchesAllTerms = false;
                        break;
                    }
                }

                if (matchesAllTerms) {
                    matchingPaths.add(path);
                }
            }

            MinecraftClient.getInstance().execute(() -> {
                imagePaths.clear();
                imagePaths.addAll(matchingPaths);
                imageIds.clear();
                metaDatas.clear();
                loadImagesAsync();
            });
        });
    }

    private List<SearchTerm> parseSearchTerms(String query) {
        List<SearchTerm> searchTerms = new ArrayList<>();
        Map<String, String> fieldMappings = getSearchFieldTerms();

        String[] terms = query.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String term : terms) {
            term = term.trim();
            if (term.isEmpty()) continue;

            String fieldName = null;
            String fieldValue = term.toLowerCase();

            for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
                if (term.toLowerCase().startsWith(entry.getKey())) {
                    fieldName = entry.getValue();
                    fieldValue = term.substring(entry.getKey().length()).trim().toLowerCase();
                    break;
                }
            }

            searchTerms.add(new SearchTerm(fieldName, fieldValue));
        }

        return searchTerms;
    }

    private boolean matchesTerm(JsonObject metaData, SearchTerm term) {
        String searchFieldName = term.fieldName;
        String searchFieldValue = term.fieldValue;

        String operator = extractComparisonOperator(searchFieldValue);
        String actualValue = searchFieldValue;
        if (!operator.isEmpty()) {
            actualValue = searchFieldValue.substring(operator.length()).trim();
        }

        if (searchFieldName == null) {
            for (Map.Entry<String, JsonElement> field : metaData.entrySet()) {
                if (field.getValue().isJsonPrimitive() &&
                        field.getValue().getAsString().toLowerCase().contains(actualValue)) {
                    return true;
                }
            }
            return false;
        }

        switch (searchFieldName) {
            case "tags" -> {
                if (metaData.has("tags") && metaData.get("tags").isJsonArray()) {
                    JsonArray tags = metaData.getAsJsonArray("tags");
                    for (JsonElement tag : tags) {
                        if (tag.isJsonPrimitive()) {
                            String tagValue = tag.getAsString().toLowerCase();
                            if (operator.isEmpty()) {
                                if (tagValue.equals(actualValue) || tagValue.contains(actualValue)) {
                                    return true;
                                }
                            } else {
                                return compareValues(tagValue, operator, actualValue);
                            }
                        }
                    }
                }
                return false;
            }
            case "album" -> {
                if (metaData.has("album") && metaData.get("album").isJsonPrimitive()) {
                    String albumUUID = metaData.get("album").getAsString();
                    try {
                        Album album = AlbumManager.getAlbum(UUID.fromString(albumUUID));
                        if (album != null) {
                            String albumTitle = album.getTitle().toLowerCase();
                            if (operator.isEmpty()) {
                                return albumTitle.equals(actualValue) || albumTitle.contains(actualValue);
                            } else {
                                return compareValues(albumTitle, operator, actualValue);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing album search: {}", e.getMessage());
                    }
                }
                return false;
            }
            case "date" -> {
                if (metaData.has("current_time") && metaData.get("current_time").isJsonPrimitive()) {
                    return compareDate(metaData.get("current_time").getAsLong(), operator, actualValue);
                } else if (metaData.has("date") && metaData.get("date").isJsonPrimitive()) {
                    return compareDate(metaData.get("date").getAsLong(), operator, actualValue);
                }
                return false;
            }
        }


        if (metaData.has(searchFieldName) && metaData.get(searchFieldName).isJsonPrimitive()) {
            String fieldValue = metaData.get(searchFieldName).getAsString().toLowerCase();
            if (operator.isEmpty()) {
                return fieldValue.contains(actualValue);
            } else {
                return compareValues(fieldValue, operator, actualValue);
            }
        }

        switch (searchFieldName) {
            case "health", "food", "air", "speed" -> {
                if (metaData.has("player_state") && metaData.get("player_state").isJsonPrimitive()) {
                    String playerState = metaData.get("player_state").getAsString().toLowerCase();
                    return matchesNestedField(playerState, searchFieldName, operator, actualValue);
                }
            }
            case "time", "weather", "difficulty" -> {
                if (metaData.has("world_info") && metaData.get("world_info").isJsonPrimitive()) {
                    String worldInfo = metaData.get("world_info").getAsString().toLowerCase();
                    return matchesNestedField(worldInfo, searchFieldName, operator, actualValue);
                }
            }
            case "x", "y", "z" -> {
                if (metaData.has("coordinates") && metaData.get("coordinates").isJsonPrimitive()) {
                    String coordinates = metaData.get("coordinates").getAsString().toLowerCase();
                    return matchesNestedField(coordinates, searchFieldName, operator, actualValue);
                }
            }
        }

        return false;
    }

    private String extractComparisonOperator(String searchValue) {
        searchValue = searchValue.trim();
        if (searchValue.startsWith(">=") || searchValue.startsWith("<=")) {
            return searchValue.substring(0, 2);
        } else if (searchValue.startsWith(">") || searchValue.startsWith("<") || searchValue.startsWith("=")) {
            return searchValue.substring(0, 1);
        }
        return "";
    }

    private boolean matchesNestedField(String fieldString, String subFieldName, String operator, String searchValue) {
        String pattern = subFieldName + ": ";
        int pos = fieldString.indexOf(pattern);
        if (pos == -1) {
            pattern = subFieldName + ":";
            pos = fieldString.indexOf(pattern);
        }

        if (pos != -1) {
            int valueStart = pos + pattern.length();
            int valueEnd = fieldString.indexOf(',', valueStart);
            if (valueEnd == -1) {
                valueEnd = fieldString.length();
            }

            String extractedValue = fieldString.substring(valueStart, valueEnd).trim();

            if (operator.isEmpty()) {
                return extractedValue.contains(searchValue);
            } else {
                return compareValues(extractedValue, operator, searchValue);
            }
        }

        return false;
    }

    private boolean compareDate(long timestamp, String operator, String searchValue) {
        LocalDate screenshotDate = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        try {
            if (searchValue.equals("today")) {
                LocalDate today = LocalDate.now();
                return compareDateWithOperator(screenshotDate, operator, today);
            } else if (searchValue.equals("yesterday")) {
                LocalDate yesterday = LocalDate.now().minusDays(1);
                return compareDateWithOperator(screenshotDate, operator, yesterday);
            } else if (searchValue.contains("week")) {
                LocalDate aWeekAgo = LocalDate.now().minusWeeks(1);
                if (operator.isEmpty()) {
                    return !screenshotDate.isBefore(aWeekAgo);
                }
                return compareDateWithOperator(screenshotDate, operator, aWeekAgo);
            } else if (searchValue.contains("month")) {
                LocalDate aMonthAgo = LocalDate.now().minusMonths(1);
                if (operator.isEmpty()) {
                    return !screenshotDate.isBefore(aMonthAgo);
                }
                return compareDateWithOperator(screenshotDate, operator, aMonthAgo);
            }

            LocalDate searchDate = null;

            if (searchValue.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                searchDate = LocalDate.parse(searchValue, formatter);
            } else if (searchValue.matches("\\d{1,2}\\.\\d{4}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM.yyyy");
                YearMonth queryYearMonth = YearMonth.parse(searchValue, formatter);
                searchDate = queryYearMonth.atDay(1);
            } else if (searchValue.matches("\\d{4}")) {
                int year = Integer.parseInt(searchValue);
                searchDate = LocalDate.of(year, 1, 1);
            }

            if (searchDate != null) {
                return compareDateWithOperator(screenshotDate, operator, searchDate);
            }
        } catch (Exception ignored) {
        }
        String formattedDate = screenshotDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        return formattedDate.contains(searchValue);
    }

    private boolean compareDateWithOperator(LocalDate screenshotDate, String operator, LocalDate referenceDate) {
        return switch (operator) {
            case ">" -> screenshotDate.isAfter(referenceDate);
            case "<" -> screenshotDate.isBefore(referenceDate);
            case ">=" -> screenshotDate.isAfter(referenceDate) || screenshotDate.isEqual(referenceDate);
            case "<=" -> screenshotDate.isBefore(referenceDate) || screenshotDate.isEqual(referenceDate);
            default -> screenshotDate.isEqual(referenceDate);
        };
    }

    private boolean compareValues(String fieldValue, String operator, String searchValue) {
        try {
            double fieldNumeric = Double.parseDouble(fieldValue.replaceAll("[^0-9.-]", ""));
            double searchNumeric = Double.parseDouble(searchValue.replaceAll("[^0-9.-]", ""));

            return switch (operator) {
                case ">" -> fieldNumeric > searchNumeric;
                case "<" -> fieldNumeric < searchNumeric;
                case ">=" -> fieldNumeric >= searchNumeric;
                case "<=" -> fieldNumeric <= searchNumeric;
                case "=" -> fieldNumeric == searchNumeric;
                default -> fieldValue.contains(searchValue);
            };
        } catch (NumberFormatException e) {
            return fieldValue.contains(searchValue);
        }
    }


    private static @NotNull Map<String, String> getSearchFieldTerms() {
        Map<String, String> fieldMappings = new HashMap<>();

        fieldMappings.put("seed:", "world_seed");
        fieldMappings.put("biome:", "biome");
        fieldMappings.put("world:", "world_name");
        fieldMappings.put("server:", "server_address");
        fieldMappings.put("username:", "username");
        fieldMappings.put("coordinates:", "coordinates");
        fieldMappings.put("location:", "coordinates");
        fieldMappings.put("facing:", "facing_direction");
        fieldMappings.put("player:", "player_state");

        fieldMappings.put("date:", "date");
        fieldMappings.put("day:", "date");
        fieldMappings.put("time:", "date");

        fieldMappings.put("health:", "health");
        fieldMappings.put("food:", "food");
        fieldMappings.put("air:", "air");
        fieldMappings.put("speed:", "speed");

        fieldMappings.put("worldtime:", "time");
        fieldMappings.put("weather:", "weather");
        fieldMappings.put("difficulty:", "difficulty");

        fieldMappings.put("x:", "x");
        fieldMappings.put("y:", "y");
        fieldMappings.put("z:", "z");

        fieldMappings.put("tag:", "tags");
        fieldMappings.put("album:", "album");

        return fieldMappings;
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
                    if (client != null) {
                        client.execute(() -> client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, image)));
                    }
                    imageIds.add(textureId);

                    String imagePathString = path.toString();
                    if (likedScreenshotsSet.contains(imagePathString)) {
                        likedScreenshots.put(imagePathString, true);
                    }
                    JsonObject metaData = new JsonObject();
                    File jsonData = new File(path.getParent().toString(), path.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));
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


        for (var var : children()) {
            if ((var instanceof ButtonWidget button)) {
                if (isButtonCoveredByImage(button, x, y, imageWidth, imageHeight)) {
                    return true;
                }
            } else if (var instanceof TextFieldWidget textFieldWidget) {
                if (isTextFieldCoveredByImage(textFieldWidget, x, y, imageWidth, imageHeight)) {
                    return true;
                }
            } else if (var instanceof Element element) {
                if (element.isMouseOver(x, y)) {
                    return true;
                }
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

    private boolean isTextFieldCoveredByImage(TextFieldWidget textFieldWidget, int imageX, int imageY, int imageWidth, int imageHeight) {
        int buttonX = textFieldWidget.getX();
        int buttonY = textFieldWidget.getY();
        int buttonWidth = textFieldWidget.getWidth();
        int buttonHeight = textFieldWidget.getHeight();

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
                    List<PathWithMetadata> pathsWithMetadata = paths
                            .filter(path -> path.toString().endsWith(".png"))
                            .map(path -> {
                                JsonObject metadata = new JsonObject();
                                File jsonData = new File(path.getParent().toString(),
                                        path.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));
                                if (jsonData.exists()) {
                                    try (FileReader reader = new FileReader(jsonData, StandardCharsets.UTF_8)) {
                                        metadata = JsonParser.parseReader(reader).getAsJsonObject();
                                    } catch (Exception e) {
                                        logger.error("Error reading metadata for sorting: {}", e.getMessage());
                                    }
                                }
                                return new PathWithMetadata(path, metadata);
                            }).sorted((pm1, pm2) -> {
                                Path path1 = pm1.path();
                                Path path2 = pm2.path();
                                JsonObject metadata1 = pm1.metadata();
                                JsonObject metadata2 = pm2.metadata();

                                int result;
                                if (sortBy == SortBy.DEFAULT) {
                                    if (likedScreenshotsSet.contains(path1.toString()) && !likedScreenshotsSet.contains(path2.toString())) {
                                        return -1;
                                    } else if (!likedScreenshotsSet.contains(path1.toString()) && likedScreenshotsSet.contains(path2.toString())) {
                                        return 1;
                                    }

                                    return Long.compare(path2.toFile().lastModified(), path1.toFile().lastModified());
                                } else if (sortBy == SortBy.TAG) {
                                    String tag1 = getFirstTag(metadata1);
                                    String tag2 = getFirstTag(metadata2);

                                    if (tag1 == null && tag2 == null) {
                                        return path1.getFileName().toString().compareTo(path2.getFileName().toString());
                                    } else if (tag1 == null) {
                                        return 1;
                                    } else if (tag2 == null) {
                                        return -1;
                                    } else {
                                        return tag1.compareToIgnoreCase(tag2);
                                    }
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
                            }).toList();

                    sortedPaths = pathsWithMetadata.stream()
                            .map(PathWithMetadata::path)
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
                    File jsonData = new File(path.getParent().toString(), path.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));
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
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, image));
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

    private String getFirstTag(JsonObject metadata) {
        if (metadata != null && metadata.has("tags") && metadata.get("tags").isJsonArray()) {
            JsonArray tagsArray = metadata.getAsJsonArray("tags");
            if (!tagsArray.isEmpty() && tagsArray.get(0).isJsonPrimitive()) {
                return tagsArray.get(0).getAsString();
            }
        }
        return null;
    }

    private record PathWithMetadata(Path path, JsonObject metadata) {
    }

    private @NotNull LinkedHashMap<Text, Text> getStringStringMap() {
        JsonObject metaData = metaDatas.get(clickedImageIndex);
        LinkedHashMap<Text, Text> drawableInfo = new LinkedHashMap<>();

        if (metaData.has("username"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.username"), metaData.has("username") && metaData.get("username").isJsonPrimitive() ? Text.literal(metaData.get("username").getAsString()) : metaData.has("fileUsername") && metaData.get("fileUsername").isJsonPrimitive() ? Text.literal(metaData.get("fileUsername").getAsString()) : Text.literal("N/A"));
        if (metaData.has("server_address"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.serverAddress"), metaData.has("server_address") && metaData.get("server_address").isJsonPrimitive() ? Text.literal(metaData.get("server_address").getAsString()) : Text.literal("N/A"));
        if (metaData.has("world_name"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.world"), metaData.has("world_name") && metaData.get("world_name").isJsonPrimitive() ? Text.literal(metaData.get("world_name").getAsString()) : Text.literal("N/A"));
        if (metaData.has("coordinates"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.location"), metaData.has("coordinates") && metaData.get("coordinates").isJsonPrimitive() ? Text.literal(metaData.get("coordinates").getAsString()) : Text.literal("N/A"));
        if (metaData.has("facing_direction"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.facing"), metaData.has("facing_direction") && metaData.get("facing_direction").isJsonPrimitive() ? Text.literal(metaData.get("facing_direction").getAsString()) : Text.literal("N/A"));
        if (metaData.has("player_state"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.playerState"), metaData.has("player_state") && metaData.get("player_state").isJsonPrimitive() ? Text.literal(metaData.get("player_state").getAsString()) : Text.literal("N/A"));
        if (metaData.has("biome"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.biome"), metaData.has("biome") && metaData.get("biome").isJsonPrimitive() ? Text.literal(metaData.get("biome").getAsString()) : Text.literal("N/A"));
        if (metaData.has("world_info"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.worldInfo"), metaData.has("world_info") && metaData.get("world_info").isJsonPrimitive() ? Text.literal(metaData.get("world_info").getAsString()) : Text.literal("N/A"));
        if (metaData.has("world_seed"))
            drawableInfo.put(Text.translatable("gui.screenshot_uploader.metadata.seed"), metaData.has("world_seed") && metaData.get("world_seed").isJsonPrimitive() ? Text.literal(metaData.get("world_seed").getAsString()) : Text.literal("N/A"));
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


    public void cancelAllAsyncTasks() {
        imageIds.clear();
        imagePaths.clear();
        navigatorButtons.clear();
        metaDatas.clear();
        clickedImageIndex = -1;
        isImageClicked = false;

        if (asyncSortFuture != null && !asyncSortFuture.isDone()) {
            asyncSortFuture.cancel(true);
        }

        if (searchDebounceTask != null) {
            MinecraftClient.getInstance().send(() -> searchDebounceTask = null);
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
        }
        imageIds.clear();
        imagePaths.clear();
        metaDatas.clear();
        lastSearchQuery = "";
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchField.isFocused() && searchField.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField.isFocused() && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchField.setFocused(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (goingUp) {
            starY += 4;
            if (starY >= 12) {
                goingUp = false;
            }
        } else {
            starY -= 4;
            if (starY <= 0) {
                goingUp = true;
            }
        }
    }

    private enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    private enum SortBy {
        NAME,
        DATE,
        SIZE,
        TAG,
        DEFAULT
    }

    private record SearchTerm(String fieldName, String fieldValue) {
    }


}

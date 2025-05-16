package de.thecoolcraft11.screen;

import com.google.gson.*;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.systems.RenderSystem;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.packet.CommentPayload;
import de.thecoolcraft11.packet.DeletionPacket;
import de.thecoolcraft11.util.ReceivePackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WebGalleryScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(WebGalleryScreen.class);

    private static final List<Identifier> imageIds = new ArrayList<>();
    private static final List<String> imagePaths = new ArrayList<>();
    private static final List<JsonObject> metaDatas = new ArrayList<>();

    private static final int IMAGES_PER_ROW = 5;
    private static int IMAGE_WIDTH = 192;
    private static int IMAGE_HEIGHT = 108;
    private static int GAP = 10;
    private static int TOP_PADDING = 35;

    private static boolean isImageClicked = false;
    private static int clickedImageIndex = -1;
    private int scrollOffset = 0;

    private double zoomLevel = 1.0;
    private double imageOffsetX = 0.0;
    private double imageOffsetY = 0.0;

    private final Screen parent;
    private final String webserverUrl;
    private final String initialImageName;

    private ButtonWidget openInBrowserButton;

    private final List<ButtonWidget> navigatorButtons = new ArrayList<>();

    private final List<ButtonWidget> buttonsToHideOnOverlap = new ArrayList<>();

    private ButtonWidget saveButton;

    private ButtonWidget openInAppButton;

    private ButtonWidget shareButton;

    private static ButtonWidget likeButton;

    private ButtonWidget sendCommentButton;
    private ButtonWidget sortByButton;
    private ButtonWidget sortOrderButton;
    private ButtonWidget deleteButton;

    private TextFieldWidget commentWidget;

    private TextFieldWidget searchField;

    private String lastSearchQuery = "";
    private Runnable searchDebounceTask = null;


    private SortBy sortBy = SortBy.DEFAULT;
    private SortOrder sortOrder = SortOrder.ASCENDING;

    private static String FILE_PATH;

    public WebGalleryScreen(Screen parent, String webserverUrl, String initialImageName) {
        super(Text.translatable("gui.screenshot_uploader.screenshot_gallery.web_title", webserverUrl));
        this.parent = Objects.requireNonNullElseGet(parent, GalleryScreen::new);
        this.webserverUrl = webserverUrl;

        this.initialImageName = initialImageName;
        FILE_PATH = "./config/screenshotUploader/data/" + webserverUrl.hashCode() + ".json";
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

        loadScreenshotsFromServer(webserverUrl);

        int buttonWidth = width / 8;
        int buttonHeight = height / 25;
        int buttonSpacing = buttonWidth / 5;

        int xPosition = (width - (buttonWidth * (navigatorButtons.size() + 1) + buttonSpacing * (navigatorButtons.size() - 1))) / 5;
        int buttonY = (int) (height * 0.9);

        int navigatorY = (int) (height * 0.01);

        navigatorButtons.add(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.my_gallery"), button -> {
            if (client != null) {
                client.setScreen(parent);
            } else {
                logger.error("Failed to get client");
            }
        }).dimensions(xPosition, navigatorY, buttonWidth, buttonHeight).build());
        xPosition += buttonWidth + buttonSpacing;

        if (ReceivePackets.gallerySiteAddress != null) {
            if (!ReceivePackets.gallerySiteAddress.equals(webserverUrl)) {
                navigatorButtons.add(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.server_gallery"), button -> {
                    String webserverUrl = ReceivePackets.gallerySiteAddress;
                    if (client != null) {
                        client.setScreen(new WebGalleryScreen(this, webserverUrl, null));
                    } else {
                        logger.error("Failed to get client trying to open Server Gallery with URL {}", webserverUrl);
                    }
                }).dimensions(xPosition, navigatorY, buttonWidth, buttonHeight).build());
            } else {
                navigatorButtons.add(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.current"), button -> {
                }).dimensions(xPosition, navigatorY, buttonWidth, buttonHeight).build());
                openInBrowserButton = ButtonWidget.builder(
                        Text.translatable("gui.screenshot_uploader.screenshot_gallery.open_in_browser"),
                        button -> openBrowser(ReceivePackets.homeSiteAddress)
                ).dimensions(width - buttonWidth, buttonY, buttonWidth, buttonHeight).build();
            }
            xPosition += buttonWidth + buttonSpacing;
        }
        for (Map.Entry<String, Map<String, String>> entry : ConfigManager.getClientConfig().upload_urls.entrySet()) {
            String webserverUrl = entry.getValue().get("gallery");
            String webserverUrlHome = entry.getValue().get("home");
            String buttonLabel = entry.getKey();

            if (webserverUrl == null || webserverUrl.isEmpty() || webserverUrlHome == null || webserverUrlHome.isEmpty()) {
                continue;
            }

            if (!this.webserverUrl.equals(webserverUrl)) {
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
            } else {
                navigatorButtons.add(ButtonWidget.builder(
                                Text.translatable("gui.screenshot_uploader.screenshot_gallery.current"),
                                button -> {
                                })
                        .dimensions(xPosition, navigatorY, buttonWidth, buttonHeight).build());

            }

            if (openInBrowserButton == null) {
                openInBrowserButton = ButtonWidget.builder(
                        Text.translatable("gui.screenshot_uploader.screenshot_gallery.open_in_browser"),
                        button -> openBrowser(webserverUrlHome)
                ).dimensions(width - buttonWidth, buttonY, buttonWidth, buttonHeight).build();
            }


            xPosition += buttonWidth + buttonSpacing;
        }

        navigatorButtons.forEach(this::addDrawableChild);
        addDrawableChild(openInBrowserButton);


        navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = true);
        navigatorButtons.stream().filter(buttonWidget -> buttonWidget.getMessage().equals(Text.translatable("gui.screenshot_uploader.screenshot_gallery.current"))).forEach(buttonWidget -> buttonWidget.active = false);


        saveButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.save"),
                button -> downloadImage()
        ).dimensions(5, buttonY, buttonWidth, buttonHeight).build();

        openInAppButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.open_sc_in_browser"),
                button -> openInBrowser()
        ).dimensions(buttonWidth + 10, buttonY, buttonWidth, buttonHeight).build();

        shareButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.share_screenshot"),
                button -> shareScreenshot()
        ).dimensions(buttonWidth * 2 + 15, buttonY, buttonWidth, buttonHeight).build();
        likeButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.like_screenshot").withColor(0x2a2a2a),
                button -> likeScreenshot()
        ).dimensions(buttonWidth * 3 + 20, buttonY, 20, 20).build();
        deleteButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.screenshot_gallery.delete_screenshot"),
                button -> {
                    if (clickedImageIndex >= 0 && clickedImageIndex < imageIds.size()) {
                        String screenshotId = String.valueOf(imagePaths.get(clickedImageIndex));
                        ClientPlayNetworking.send(new DeletionPacket(screenshotId));
                        if (client != null) {
                            client.setScreen(null);
                        }
                    }
                }
        ).dimensions(buttonWidth * 4 - (buttonWidth - 20) + 25, buttonY, buttonWidth, buttonHeight).build();


        commentWidget = new TextFieldWidget(textRenderer, 0, 0, 100, 20, Text.of(""));
        addSelectableChild(commentWidget);

        sendCommentButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.send_comment"),
                button -> sendComment()
        ).dimensions(buttonWidth * 3 + 20, buttonY, buttonWidth, buttonHeight).build();
        sortByButton = ButtonWidget.builder(
                Text.literal(sortBy.toString()),
                button -> {
                    sortBy = SortBy.values()[(sortBy.ordinal() + 1) % SortBy.values().length];
                    sortByButton.setMessage(Text.of(sortBy.toString()));
                    loadScreenshotsSorted(sortOrder, sortBy);
                }
        ).dimensions(5, height - buttonHeight - 5, buttonWidth, buttonHeight).build();

        sortOrderButton = ButtonWidget.builder(
                Text.literal(sortOrder.toString()),
                button -> {
                    sortOrder = SortOrder.values()[(sortOrder.ordinal() + 1) % SortOrder.values().length];
                    sortOrderButton.setMessage(Text.of(sortOrder.toString()));
                    loadScreenshotsSorted(sortOrder, sortBy);
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

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.screenshot_uploader.screenshot_gallery.view_tags"),
                        button -> {
                            String screenshotName = null;
                            if (imagePaths.get(clickedImageIndex) != null) {
                                screenshotName = imagePaths.get(clickedImageIndex);

                            }

                            JsonArray tags = new JsonArray();
                            if (metaDatas.size() > clickedImageIndex) {
                                JsonObject metaData = metaDatas.get(clickedImageIndex);
                                if (metaData.has("tags")) {
                                    tags = metaData.getAsJsonArray("tags");
                                }
                            }


                            if (screenshotName != null) {
                                if (client != null) {
                                    client.setScreen(new ScreenshotWebTaggingScreen(this, screenshotName, tags));
                                }
                            }
                        })
                .dimensions(this.width - 150, this.height - 40, 100, 20)
                .build());


        addDrawableChild(sortByButton);
        addDrawableChild(sortOrderButton);
        addDrawableChild(saveButton);
        addDrawableChild(openInAppButton);
        addDrawableChild(shareButton);
        addDrawableChild(sendCommentButton);
        addDrawableChild(likeButton);
        addDrawableChild(searchField);
        if (ReceivePackets.allowDelete || ReceivePackets.allowDeleteOwn) addDrawableChild(deleteButton);

        saveButton.visible = false;
        openInAppButton.visible = false;
        shareButton.visible = false;
        commentWidget.setMaxLength(1024);
        commentWidget.visible = false;
        sendCommentButton.visible = false;
        likeButton.visible = false;
        searchField.visible = true;
        searchField.setMaxLength(100);
        deleteButton.visible = false;

        buttonsToHideOnOverlap.add(saveButton);
        buttonsToHideOnOverlap.add(openInAppButton);
        buttonsToHideOnOverlap.add(shareButton);
        buttonsToHideOnOverlap.add(likeButton);
        buttonsToHideOnOverlap.add(deleteButton);

        int initialImageIndex = imagePaths.indexOf(initialImageName);
        if (initialImageIndex >= 0) {
            isImageClicked = true;
            clickedImageIndex = initialImageIndex;
            zoomLevel = 1.0;
            imageOffsetX = 0.0;
            imageOffsetY = 0.0;
        }


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


    private void shareScreenshot() {
        StringBuilder template = new StringBuilder(ConfigManager.getClientConfig().shareText);
        int placeholderIndex = template.indexOf("{sharedLink}");
        if (placeholderIndex != -1) {
            int endIndex = placeholderIndex + "{sharedLink}".length();
            template.replace(placeholderIndex, endIndex, imagePaths.get(clickedImageIndex));
        }
        String message = template.toString();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatMessage(message);
            client.setScreen(null);
        }
    }

    private void openBrowser(String url) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            ProcessBuilder processBuilder;
            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                processBuilder = new ProcessBuilder("open", url);
            } else if (os.contains("nix") || os.contains("nux")) {
                processBuilder = new ProcessBuilder("xdg-open", url);
            } else {
                logger.error("Unsupported operating system for opening the home URL.");
                return;
            }
            processBuilder.start();
        } catch (IOException e) {
            logger.error("Failed to open the home URL {}: {}", url, e.getMessage());
        }
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
            zoomAndRepositionImage(mouseX, mouseY, verticalAmount);
            return true;
        } else {
            scrollOffset += verticalAmount > 0 ? -(IMAGE_HEIGHT + GAP) : (IMAGE_HEIGHT + GAP);
            scrollOffset = Math.max(0, Math.min(scrollOffset, calculateMaxScrollOffset()));
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void zoomAndRepositionImage(double mouseX, double mouseY, double verticalAmount) {
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
    }

    private int calculateMaxScrollOffset() {
        return (imageIds.size() / IMAGES_PER_ROW + 2) * (IMAGE_HEIGHT + GAP) - height + TOP_PADDING;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        commentWidget.render(context, mouseX, mouseY, delta);

        if (isImageClicked && clickedImageIndex >= 0) {
            saveButton.visible = true;
            openInAppButton.visible = true;
            shareButton.visible = true;
            likeButton.visible = true;
            renderEnlargedImage(context);
            openInBrowserButton.visible = false;
            commentWidget.visible = true;
            sendCommentButton.visible = true;
            searchField.visible = false;
            boolean isAllowDelete = false;
            if (client != null) {
                if (client.player != null) {
                    isAllowDelete = metaDatas.get(clickedImageIndex).has("uuid") && metaDatas.get(clickedImageIndex).get("uuid").getAsString().equals(client.player.getUuid().toString());
                }
            }
            deleteButton.visible = ReceivePackets.allowDelete || (ReceivePackets.allowDeleteOwn && isAllowDelete);
            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = false);
        } else {
            saveButton.visible = false;
            openInAppButton.visible = false;
            shareButton.visible = false;
            renderGallery(context, mouseX, mouseY);
            openInBrowserButton.visible = true;
            commentWidget.visible = false;
            sendCommentButton.visible = false;
            likeButton.visible = false;
            searchField.visible = true;
            deleteButton.visible = false;
            navigatorButtons.forEach(buttonWidget -> buttonWidget.visible = true);
        }
        boolean isImageOverlappingButtons = clickedImageIndex >= 0 && isImageOverlappingButtons();

        for (Element button : this.children()) {
            if (button instanceof ButtonWidget) {
                if (buttonsToHideOnOverlap.contains(button)) {
                    if (button == deleteButton) {
                        boolean isAllowDelete = false;
                        if (metaDatas.size() > clickedImageIndex && clickedImageIndex >= 0) {
                            if (client != null) {
                                if (client.player != null) {
                                    isAllowDelete = metaDatas.get(clickedImageIndex).has("uuid") && metaDatas.get(clickedImageIndex).get("uuid").getAsString().equals(client.player.getUuid().toString());
                                }
                            }
                        }
                        deleteButton.visible = ReceivePackets.allowDelete || (ReceivePackets.allowDeleteOwn && isAllowDelete);
                        return;
                    }
                    if (isImageClicked) {
                        ((ButtonWidget) button).visible = !isImageOverlappingButtons;
                    } else {
                        ((ButtonWidget) button).visible = false;
                    }
                }
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
            if (button instanceof ButtonWidget) {
                if (isButtonCoveredByImage((ButtonWidget) button, x, y, imageWidth, imageHeight)) {
                    return true;
                }
            }
            if (button instanceof TextFieldWidget) {
                if (isTextFieldCoveredByImage((TextFieldWidget) button, x, y, imageWidth, imageHeight)) {
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

    private boolean isTextFieldCoveredByImage(TextFieldWidget button, int imageX, int imageY, int imageWidth, int imageHeight) {
        int buttonX = button.getX();
        int buttonY = button.getY();
        int buttonWidth = button.getWidth();
        int buttonHeight = button.getHeight();

        return !(buttonX + buttonWidth < imageX || buttonX > imageX + imageWidth ||
                buttonY + buttonHeight < imageY || buttonY > imageY + imageHeight);
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

            String username = getString(i);
            if (client != null) {
                int textX = x + 5;
                int textY = y + IMAGE_HEIGHT - 10;

                int usernameWidth = client.textRenderer.getWidth(username);
                int padding = 2;

                context.fill(textX - padding, textY - padding,
                        textX + usernameWidth + padding, textY + client.textRenderer.fontHeight + padding,
                        0xA0000050);

                context.fill(textX - padding - 1, textY - padding - 1,
                        textX + usernameWidth + padding + 1, textY - padding,
                        0xFFAAAAAA);
                context.fill(textX - padding - 1, textY + client.textRenderer.fontHeight + padding,
                        textX + usernameWidth + padding + 1, textY + client.textRenderer.fontHeight + padding + 1,
                        0xFFAAAAAA);
                context.fill(textX - padding - 1, textY - padding,
                        textX - padding, textY + client.textRenderer.fontHeight + padding,
                        0xFFAAAAAA);
                context.fill(textX + usernameWidth + padding, textY - padding,
                        textX + usernameWidth + padding + 1, textY + client.textRenderer.fontHeight + padding,
                        0xFFAAAAAA);

                context.drawText(client.textRenderer, username, textX, textY, 0xFFFFFF, false);

                if (metaDatas.get(i).has("liked") && metaDatas.get(i).get("liked").getAsBoolean()) {
                    context.drawText(client.textRenderer, "❤", textX + usernameWidth + 10, textY, 0xFFFFFF, false);
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

                            int padding = 2;
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

            if (metaDatas.get(i) != null && metaDatas.get(i).has("uuid")) {
                String playerHead = getPlayerHeadTexture(UUID.fromString(metaDatas.get(i).get("uuid").getAsString()));
                Identifier playerHeadId = null;

                if (playerHead != null && !playerHead.isEmpty()) {
                    playerHeadId = loadHeadImage(playerHead);
                }

                if (playerHeadId != null) {
                    int headSize = 20;
                    int headX = x + 5;
                    int headY = y + 5;

                    RenderSystem.setShaderTexture(0, playerHeadId);
                    context.drawTexture(playerHeadId, headX, headY, 0, 0, headSize, headSize, headSize, headSize);
                }
            }
        }
    }


    private static String getString(int i) {
        String username = "Unknown";
        if (metaDatas.size() > i) {
            if (metaDatas.get(i) != null) {
                JsonObject metaData = metaDatas.get(i);
                if (metaData.has("username") && !metaData.get("username").isJsonNull()) {
                    username = metaData.get("username").getAsString();
                } else if (metaData.has("fileUsername") && !metaData.get("fileUsername").isJsonNull()) {
                    username = metaData.get("fileUsername").getAsString();
                }
            }
        }
        return username;
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

        int sidebarXRight = x + imageWidth;

        context.fill(sidebarXRight, y, sidebarXRight + sidebarWidth, y + sidebarHeight, 0x80000000);

        Map<String, UUID> comments = getComments(metaDatas.get(clickedImageIndex));
        int commentListY = y + 20;
        for (String comment : comments.keySet()) {
            String[] commentParts = comment.split(": ", 2);
            if (commentParts.length > 1) {
                String playerName = commentParts[0];
                String playerComment = commentParts[1];

                String playerHead = getPlayerHeadTexture(comments.get(comment));
                Identifier playerHeadId = null;
                if (playerHead != null && !playerHead.isEmpty()) {

                    playerHeadId = loadHeadImage(playerHead);
                }

                int headSize = 20;
                int headX = sidebarXRight + 10;
                int headY = commentListY - 10;

                if (playerHeadId != null) {

                    RenderSystem.setShaderTexture(0, playerHeadId);
                    context.drawTexture(playerHeadId, (int) (headX + ((headSize - headSize * 0.25) / 2)), (int) (headY + ((headSize - headSize * 0.25) / 2)), 0, 0, (int) (headSize - headSize * 0.25), (int) (headSize - headSize * 0.25), (int) (headSize - headSize * 0.25), (int) (headSize - headSize * 0.25));
                }
                context.drawText(client.textRenderer, Text.literal(playerName + ": " + playerComment), headX + headSize + 5, commentListY, 0xFFFFFF, false);
            }

            commentListY += 20;
        }
        int textFieldX = sidebarXRight + 10;
        int textFieldY = Math.min(commentListY + 10, y + imageHeight - 20 - 10);

        commentWidget.setX(textFieldX);
        commentWidget.setY(textFieldY);
        commentWidget.setMaxLength(200);
        commentWidget.setVisible(true);
        commentWidget.setEditable(true);

        sendCommentButton.setX(textFieldX + commentWidget.getWidth() + 5);
        sendCommentButton.setY(textFieldY);

        likeButton.setMessage(Text.translatable("gui.screenshot_uploader.screenshot_gallery.like_screenshot").withColor(metaDatas.get(clickedImageIndex).has("liked") && metaDatas.get(clickedImageIndex).get("liked").getAsBoolean() ? 0xFFFFFF : 0x2a2a2a));

    }

    private String getPlayerHeadTexture(UUID playerUUID) {
        try {
            ProfileResult result = MinecraftClient.getInstance().getSessionService().fetchProfile(playerUUID, false);

            if (result != null && result.profile() != null) {

                if (result.profile().getProperties().containsKey("textures")) {
                    Iterator<Property> textures = result.profile().getProperties().get("textures").iterator();

                    if (textures.hasNext()) {
                        String textureValue = textures.next().value();

                        String decodedJson = new String(Base64.getDecoder().decode(textureValue));


                        return decodedJson.split("\"url\" : \"")[1].split("\"")[0];
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error while downloading player head image: {}", e.getMessage());
        }
        return "";
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


    private Map<String, UUID> getComments(JsonObject metaData) {
        Map<String, UUID> commentList = new LinkedHashMap<>();
        if (!metaData.has("comments")) return commentList;
        JsonArray comments = metaData.get("comments").getAsJsonArray();

        comments.forEach(comment -> {
            JsonObject commentObject = comment.getAsJsonObject();
            commentList.put(commentObject.get("author").getAsString() + ": " + commentObject.get("comment").getAsString(), commentObject.get("authorUUID") != null ? UUID.fromString(commentObject.get("authorUUID").getAsString()) : UUID.randomUUID());
        });

        return commentList;
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
                        imageIds.add(textureId);
                    } else {
                        logger.error("Failed to get client while loading web image!");
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load cached image: {}", e.getMessage());
            }
        } else {
            try {
                URI uri = new URI(imageUrl);
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
                                    logger.info("Created web screenshots cache folder for Screenshots");
                                }
                            }

                            ImageIO.write(bufferedImage, "PNG", cachedImage);
                            logger.info("Image saved to cache: {}", cachedImage.getAbsolutePath());

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
                                        imageIds.add(textureId);
                                    } else {
                                        logger.error("Failed to get client while saving the web image!");
                                    }
                                }
                            }
                        }
                    }
                } else {
                    logger.error("Failed to load image from URL: {}", imageUrl);
                }
            } catch (URISyntaxException | IOException e) {
                logger.error("Failed to download web screenshots: {}", e.getMessage());
            }
        }
    }

    public static Identifier loadHeadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        String cacheFileName = "screenshots_heads_cache/" + imageUrl.hashCode() + ".png";
        File cachedImage = new File(cacheFileName);

        if (cachedImage.exists()) {
            try {
                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                    Identifier textureId = Identifier.of("webimage", "head/" + imageUrl.hashCode());
                    if (MinecraftClient.getInstance() != null) {
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(loadedImage));
                    } else {
                        System.err.println("Failed to get client while loading web image!");
                    }
                    return textureId;
                }
            } catch (IOException e) {
                System.err.println("Failed to load cached head image: " + e.getMessage());
            }
        } else {
            try {
                URI uri = new URI(imageUrl);
                URL url = uri.toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        BufferedImage bufferedImage = ImageIO.read(inputStream);

                        if (bufferedImage != null) {
                            int headSize = 8;
                            BufferedImage headImage = bufferedImage.getSubimage(8, 8, headSize, headSize);

                            File cacheFolder = new File("screenshots_heads_cache");
                            if (!cacheFolder.exists()) {
                                if (cacheFolder.mkdirs()) {
                                    logger.info("Created web screenshots cache folder for Heads");
                                }
                            }

                            ImageIO.write(headImage, "PNG", cachedImage);
                            logger.info("Head image saved to cache: {}", cachedImage.getAbsolutePath());

                            try (NativeImage nativeImage = new NativeImage(headImage.getWidth(), headImage.getHeight(), false)) {
                                for (int y = 0; y < headImage.getHeight(); y++) {
                                    for (int x = 0; x < headImage.getWidth(); x++) {
                                        int rgb = headImage.getRGB(x, y);
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
                                    Identifier textureId = Identifier.of("webimage", "head/" + imageUrl.hashCode());
                                    if (MinecraftClient.getInstance() != null) {
                                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(loadedImage));
                                    } else {
                                        System.err.println("Failed to get client while saving the web image!");
                                    }
                                    return textureId;
                                }
                            }
                        }
                    }
                } else {
                    System.err.println("Failed to load image from URL: " + imageUrl);
                }
            } catch (Exception e) {
                System.err.println("Failed to download web screenshots: " + e.getMessage());
            }
        }
        return null;
    }


    private static void loadScreenshotsFromServer(String server) {
        metaDatas.clear();
        imagePaths.clear();

        Set<String> likedScreenshots = loadLikedScreenshots();

        List<AbstractMap.SimpleEntry<String, JsonObject>> screenshotData = requestScreenshotListFromServer(server);

        screenshotData.sort(Comparator.comparing(entry -> likedScreenshots.contains(entry.getKey()) ? 0 : 1));

        screenshotData.forEach(entry -> {
            String url = entry.getKey();
            JsonObject metaData = entry.getValue();

            metaData.addProperty("liked", likedScreenshots.contains(url));

            imagePaths.add(url);
            metaDatas.add(metaData);
            loadWebImage(url);
        });
    }

    private void loadScreenshotsSorted(SortOrder sortOrder, SortBy sortBy) {
        List<AbstractMap.SimpleEntry<String, JsonObject>> entries = requestScreenshotListFromServer(webserverUrl);

        Set<String> likedScreenshotsSet = loadLikedScreenshots();

        entries.sort((entry1, entry2) -> {
            int result;
            if (sortBy == SortBy.DEFAULT) {
                boolean liked1 = likedScreenshotsSet.contains(entry1.getKey());
                boolean liked2 = likedScreenshotsSet.contains(entry2.getKey());
                result = Boolean.compare(!liked1, !liked2);
            } else {
                result = switch (sortBy) {
                    case NAME -> entry1.getKey().compareTo(entry2.getKey());
                    case DATE -> {
                        long date1 = entry1.getValue().get("date").getAsLong();
                        long date2 = entry2.getValue().get("date").getAsLong();
                        yield Long.compare(date2, date1);
                    }
                    case PLAYER -> {
                        String player1 = (entry1.getValue().get("username") != null) ? entry1.getValue().get("username").getAsString() : entry1.getValue().get("fileUsername").getAsString();
                        String player2 = (entry2.getValue().get("username") != null) ? entry2.getValue().get("username").getAsString() : entry2.getValue().get("fileUsername").getAsString();
                        yield player1.compareTo(player2);
                    }
                    case DIMENSION -> {
                        String dimension1 = (entry1.getValue().get("world_name") != null) ? entry1.getValue().get("world_name").getAsString() : "N/A";
                        String dimension2 = (entry2.getValue().get("world_name") != null) ? entry2.getValue().get("world_name").getAsString() : "N/A";
                        yield dimension1.compareTo(dimension2);
                    }
                    case BIOME -> {
                        String biome1 = (entry1.getValue().get("biome") != null) ? entry1.getValue().get("biome").getAsString() : "N/A";
                        String biome2 = (entry2.getValue().get("biome") != null) ? entry2.getValue().get("biome").getAsString() : "N/A";
                        yield biome1.compareTo(biome2);
                    }
                    case POSITION -> {
                        if (entry1.getValue().get("coordinates") == null || entry2.getValue().get("coordinates") == null) {
                            yield 0;
                        }
                        String[] pos1 = entry1.getValue().get("coordinates").getAsString().split(", ");
                        String[] pos2 = entry2.getValue().get("coordinates").getAsString().split(", ");
                        int x1 = Integer.parseInt(pos1[0].split(": ")[1]);
                        int y1 = Integer.parseInt(pos1[1].split(": ")[1]);
                        int z1 = Integer.parseInt(pos1[2].split(": ")[1]);
                        int x2 = Integer.parseInt(pos2[0].split(": ")[1]);
                        int y2 = Integer.parseInt(pos2[1].split(": ")[1]);
                        int z2 = Integer.parseInt(pos2[2].split(": ")[1]);
                        BlockPos pos1Block = new BlockPos(x1, y1, z1);
                        BlockPos pos2Block = new BlockPos(x2, y2, z2);
                        yield pos1Block.compareTo(pos2Block);
                    }
                    default -> 0;
                };
            }

            return sortOrder == SortOrder.ASCENDING ? result : -result;
        });

        imageIds.clear();
        imagePaths.clear();
        metaDatas.clear();

        for (AbstractMap.SimpleEntry<String, JsonObject> entry : entries) {
            imagePaths.add(entry.getKey());
            metaDatas.add(entry.getValue());
            loadWebImage(entry.getKey());
        }
    }


    private static List<AbstractMap.SimpleEntry<String, JsonObject>> requestScreenshotListFromServer(String server) {
        List<AbstractMap.SimpleEntry<String, JsonObject>> screenshotData = new ArrayList<>();
        try {
            URI uri = new URI(server);
            URL url = uri.toURL();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Minecraft Screenshot Uploader");

            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonArray jsonArray = JsonParser.parseString(response.toString()).getAsJsonArray();

                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject screenshot = jsonArray.get(i).getAsJsonObject();
                    String urlStr = screenshot.get("url").getAsString();
                    JsonObject metaData = screenshot.get("metaData").isJsonObject() ? screenshot.get("metaData").getAsJsonObject() : new JsonObject();
                    metaData.add("fileUsername", screenshot.get("username"));
                    metaData.add("date", screenshot.get("date"));


                    screenshotData.add(new AbstractMap.SimpleEntry<>(urlStr, metaData));
                }
            } else {
                logger.error("Failed to fetch data. HTTP Status: {}", status);
            }
        } catch (Exception e) {
            logger.error("Failed to request screenshot list: {}", e.getMessage());
        }
        return screenshotData;
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


    private void downloadImage() {
        if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
            try {
                URI imageUri = new URI(imagePaths.get(clickedImageIndex));
                URL imageUrl = imageUri.toURL();

                Path savePath = Paths.get(
                        System.getProperty("user.home"),
                        "Desktop",
                        "Downloaded_" + Paths.get(imageUri.getPath()).getFileName()
                );
                try (InputStream inputStream = imageUrl.openStream()) {
                    Files.copy(inputStream, savePath);
                    logger.info("Image downloaded to: {}", savePath);
                }
            } catch (Exception e) {
                logger.error("Failed to download image: {}", e.getMessage());
            }
        } else {
            logger.warn("Invalid image while downloading index: {}", clickedImageIndex);
        }
    }

    private void openInBrowser() {
        if (clickedImageIndex >= 0 && clickedImageIndex < imagePaths.size()) {
            String os = System.getProperty("os.name").toLowerCase();
            String url = imagePaths.get(clickedImageIndex);
            try {
                ProcessBuilder processBuilder;
                if (os.contains("win")) {
                    processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                } else if (os.contains("mac")) {
                    processBuilder = new ProcessBuilder("open", url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    processBuilder = new ProcessBuilder("xdg-open", url);
                } else {
                    logger.error("Unsupported operating system for opening the image URL.");
                    return;
                }
                processBuilder.start();
            } catch (IOException e) {
                logger.error("Failed to open the image URL {}: {}", url, e.getMessage());
            }

        } else {
            logger.warn("Invalid image index while opening: {}", clickedImageIndex);
        }
    }

    private void sendComment() {
        String comment = commentWidget.getText();
        String screenshot = imagePaths.get(clickedImageIndex);
        int lastSlash = screenshot.lastIndexOf("/");
        screenshot = (lastSlash != -1) ? screenshot.substring(lastSlash + 1) : null;
        if (comment != null && !comment.isEmpty() && screenshot != null && !screenshot.isEmpty()) {
            ClientPlayNetworking.send(new CommentPayload(comment, screenshot));
        }

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


    private void performSearch(String query) {
        if (query.equals(lastSearchQuery)) {
            return;
        }

        lastSearchQuery = query;

        if (query.isEmpty()) {
            imageIds.clear();
            metaDatas.clear();
            imagePaths.clear();
            loadScreenshotsFromServer(webserverUrl);
            return;
        }

        List<SearchTerm> searchTerms = parseSearchTerms(query);

        CompletableFuture.runAsync(() -> {
            List<String> matchingPaths = new ArrayList<>();
            List<JsonObject> matchingMetadata = new ArrayList<>();
            List<Integer> matchingIndices = new ArrayList<>();

            for (int i = 0; i < metaDatas.size(); i++) {
                JsonObject metaData = metaDatas.get(i);
                String path = imagePaths.get(i);

                boolean matchesAllTerms = true;
                for (SearchTerm term : searchTerms) {
                    if (!matchesTerm(metaData, term)) {
                        matchesAllTerms = false;
                        break;
                    }
                }

                if (matchesAllTerms) {
                    matchingPaths.add(path);
                    matchingMetadata.add(metaData);
                    matchingIndices.add(i);
                }
            }

            MinecraftClient.getInstance().execute(() -> {
                List<Identifier> newImageIds = new ArrayList<>();
                for (Integer index : matchingIndices) {
                    if (index < imageIds.size()) {
                        newImageIds.add(imageIds.get(index));
                    }
                }

                imageIds.clear();
                imageIds.addAll(newImageIds);

                imagePaths.clear();
                imagePaths.addAll(matchingPaths);

                metaDatas.clear();
                metaDatas.addAll(matchingMetadata);

                scrollOffset = 0;
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

        if (searchFieldName.equals("date")) {
            if (metaData.has("current_time") && metaData.get("current_time").isJsonPrimitive()) {
                return compareDate(metaData.get("current_time").getAsLong(), operator, actualValue);
            } else if (metaData.has("date") && metaData.get("date").isJsonPrimitive()) {
                return compareDate(metaData.get("date").getAsLong(), operator, actualValue);
            }
            return false;
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

        return fieldMappings;
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
            if (client != null) {
                client.setScreen(parent);
            }
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
        PLAYER,
        DEFAULT,
        DIMENSION,
        BIOME,
        POSITION
    }

    private record SearchTerm(String fieldName, String fieldValue) {
    }
}
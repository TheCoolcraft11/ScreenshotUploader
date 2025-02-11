package de.thecoolcraft11.screen;

import com.google.gson.*;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.systems.RenderSystem;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.packet.CommentPayload;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    private TextFieldWidget commentWidget;

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


        commentWidget = new TextFieldWidget(textRenderer, 0, 0, 100, 20, Text.of(""));
        addSelectableChild(commentWidget);

        sendCommentButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_gallery.send_comment"),
                button -> sendComment()
        ).dimensions(buttonWidth * 3 + 20, buttonY, buttonWidth, buttonHeight).build();


        addDrawableChild(saveButton);
        addDrawableChild(openInAppButton);
        addDrawableChild(shareButton);
        addDrawableChild(sendCommentButton);
        addDrawableChild(likeButton);

        saveButton.visible = false;
        openInAppButton.visible = false;
        shareButton.visible = false;
        commentWidget.setMaxLength(1024);
        commentWidget.visible = false;
        sendCommentButton.visible = false;
        likeButton.visible = false;

        buttonsToHideOnOverlap.add(saveButton);
        buttonsToHideOnOverlap.add(openInAppButton);
        buttonsToHideOnOverlap.add(shareButton);
        buttonsToHideOnOverlap.add(likeButton);

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
            int textX = x + 5;
            if (client != null) {
                int textY = y + IMAGE_HEIGHT - 10;
                context.drawText(client.textRenderer, username, textX, textY, 0xFFFFFF, false);

                if (metaDatas.get(i).has("liked") && metaDatas.get(i).get("liked").getAsBoolean()) {
                    context.drawText(client.textRenderer, "❤", textX + username.length() * 5 + 10, textY, 0xFFFFFF, false);
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

        drawableInfo.put(Text.literal("Username: "), metaData.has("username") && metaData.get("username").isJsonPrimitive() ? Text.literal(metaData.get("username").getAsString()) : metaData.has("fileUsername") && metaData.get("fileUsername").isJsonPrimitive() ? Text.literal(metaData.get("fileUsername").getAsString()) : Text.literal("N/A"));
        drawableInfo.put(Text.literal("Server: "), metaData.has("server_address") && metaData.get("server_address").isJsonPrimitive() ? Text.literal(metaData.get("server_address").getAsString()) : Text.literal("N/A"));
        drawableInfo.put(Text.literal("World: "), metaData.has("world_name") && metaData.get("world_name").isJsonPrimitive() ? Text.literal(metaData.get("world_name").getAsString()) : Text.literal("N/A"));
        drawableInfo.put(Text.literal("Location: "), metaData.has("coordinates") && metaData.get("coordinates").isJsonPrimitive() ? Text.literal(metaData.get("coordinates").getAsString()) : Text.literal("N/A"));
        drawableInfo.put(Text.literal("Biome: "), metaData.has("biome") && metaData.get("biome").isJsonPrimitive() ? Text.literal(metaData.get("biome").getAsString()) : Text.literal("N/A"));
        drawableInfo.put(Text.literal(" "), Text.literal(" "));
        drawableInfo.put(metaData.has("current_time") ? getTimestamp(metaData.get("current_time").getAsLong()) : metaData.has("date") ? getTimestamp(metaData.get("date").getAsLong()) : Text.literal("N/A"), Text.literal(""));
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


    private static List<AbstractMap.SimpleEntry<String, JsonObject>> requestScreenshotListFromServer(String server) {
        List<AbstractMap.SimpleEntry<String, JsonObject>> screenshotData = new ArrayList<>();
        try {
            URI uri = new URI(server);
            URL url = uri.toURL();

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

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
}
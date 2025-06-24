package de.thecoolcraft11.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.config.AlbumManager;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.config.data.Album;
import de.thecoolcraft11.util.ReceivePackets;
import de.thecoolcraft11.util.ScreenshotUploadHelper;
import net.minecraft.client.gui.DrawContext;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UploadToServerScreen extends Screen {

    private static final Logger logger = LoggerFactory.getLogger(UploadToServerScreen.class);

    private static Screen parent;
    private static Path screenshotPath;
    private static NativeImage screenshotImage;
    private Identifier textureId;
    private JsonObject metadata;

    private int imageWidth = 0;
    private int imageHeight = 0;
    private static final int BORDER_WIDTH = 5;
    private static final int METADATA_PANEL_WIDTH = 300;
    private int scrollOffset = 0;

    private final Map<String, String> serverOptions = new HashMap<>();
    private String selectedServer = null;
    private boolean isServerPanelOpen = false;
    private int serverPanelScroll = 0;
    private static final int SERVER_PANEL_WIDTH = 200;
    private static final int SERVER_PANEL_HEIGHT = 150;

    private static final int SERVER_BUTTON_WIDTH = 120;
    private static final int SERVER_BUTTON_HEIGHT = 20;
    private static final int SERVER_BUTTON_SPACING = 10;
    private final List<ButtonWidget> serverButtons = new ArrayList<>();

    public UploadToServerScreen(Screen passedParent, Path passedScreenshotPath) {
        super(Text.translatable("gui.screenshot_uploader.uploading.title"));
        parent = passedParent;
        screenshotPath = passedScreenshotPath;
        loadMetadata();
    }

    @Override
    protected void init() {
        super.init();

        serverButtons.clear();

        loadScreenshotImage();

        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonSpacing = 10;
        int buttonsY = height - buttonHeight - 20;

        ButtonWidget cancelButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.cancel"),
                button -> {
                    if (client != null) {
                        client.setScreen(parent);
                    }
                }
        ).dimensions((width / 2) - buttonWidth - (buttonSpacing / 2), buttonsY, buttonWidth, buttonHeight).build();

        ButtonWidget uploadButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.upload"),
                button -> uploadScreenshot()
        ).dimensions((width / 2) + (buttonSpacing / 2), buttonsY, buttonWidth, buttonHeight).build();

        Map<String, Map<String, String>> uploadUrls = ConfigManager.getClientConfig().upload_urls;
        if (uploadUrls != null && !uploadUrls.isEmpty()) {
            for (Map.Entry<String, Map<String, String>> entry : uploadUrls.entrySet()) {
                if (entry.getValue().containsKey("upload")) {
                    serverOptions.put(entry.getKey(), entry.getValue().get("upload"));
                }
            }
        }

        addDrawableChild(cancelButton);
        addDrawableChild(uploadButton);

        ButtonWidget selectServerButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.select_server"),
                button -> toggleServerPanel()
        ).dimensions(10, 10, 150, 20).build();

        addDrawableChild(selectServerButton);

        initializeServerButtons();
    }

    private void initializeServerButtons() {
        int serverY = height - SERVER_BUTTON_HEIGHT - 60;
        int startX = (width - (SERVER_BUTTON_WIDTH * 3 + SERVER_BUTTON_SPACING * 2)) / 2;

        if (ReceivePackets.serverSiteAddress != null) {
            ButtonWidget currentServerButton = ButtonWidget.builder(
                    Text.literal("Current Server"),
                    button -> {
                        selectedServer = "Current Server";
                        updateServerButtonStates();
                    }
            ).dimensions(startX, serverY, SERVER_BUTTON_WIDTH, SERVER_BUTTON_HEIGHT).build();
            serverButtons.add(currentServerButton);
            addDrawableChild(currentServerButton);
            startX += SERVER_BUTTON_WIDTH + SERVER_BUTTON_SPACING;
        }

        int buttonsPerRow = 3;
        int currentColumn = ReceivePackets.serverSiteAddress != null ? 1 : 0;

        for (String serverName : serverOptions.keySet()) {
            ButtonWidget serverButton = ButtonWidget.builder(
                    Text.literal(serverName),
                    button -> {
                        selectedServer = serverName;
                        updateServerButtonStates();
                    }
            ).dimensions(startX, serverY, SERVER_BUTTON_WIDTH, SERVER_BUTTON_HEIGHT).build();

            serverButtons.add(serverButton);
            addDrawableChild(serverButton);

            currentColumn++;
            if (currentColumn >= buttonsPerRow) {
                currentColumn = 0;
                serverY += SERVER_BUTTON_HEIGHT + 5;
                startX = (width - (SERVER_BUTTON_WIDTH * 3 + SERVER_BUTTON_SPACING * 2)) / 2;
            } else {
                startX += SERVER_BUTTON_WIDTH + SERVER_BUTTON_SPACING;
            }
        }

        if (!serverButtons.isEmpty() && selectedServer == null) {
            if (ReceivePackets.serverSiteAddress != null) {
                selectedServer = "Current Server";
            } else if (!serverOptions.isEmpty()) {
                selectedServer = serverOptions.keySet().iterator().next();
            }
            updateServerButtonStates();
        }
    }

    private void toggleServerPanel() {
        isServerPanelOpen = !isServerPanelOpen;
    }

    private void loadScreenshotImage() {
        if (screenshotPath == null) {
            logger.error("Screenshot path is null");
            return;
        }

        try {
            screenshotImage = NativeImage.read(Files.newInputStream(screenshotPath));

            textureId = Identifier.of("screenshot_uploader", "textures/screenshots/" + screenshotPath.getFileName().toString());

            if (client != null) {
                client.getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(screenshotImage));

                float aspectRatio = (float) screenshotImage.getWidth() / screenshotImage.getHeight();
                imageHeight = Math.min(height - 100, 400);
                imageWidth = (int) (imageHeight * aspectRatio);

                if (imageWidth > width - METADATA_PANEL_WIDTH - 40) {
                    imageWidth = width - METADATA_PANEL_WIDTH - 40;
                    imageHeight = (int) (imageWidth / aspectRatio);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load screenshot image: {}", e.getMessage());
        }
    }

    private void loadMetadata() {
        metadata = new JsonObject();

        if (screenshotPath != null) {
            File jsonData = new File(screenshotPath.getParent().toString(),
                    screenshotPath.getFileName().toString().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));

            if (jsonData.exists()) {
                try (FileReader reader = new FileReader(jsonData, StandardCharsets.UTF_8)) {
                    metadata = JsonParser.parseReader(reader).getAsJsonObject();
                } catch (Exception e) {
                    logger.error("Error reading metadata: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX > width - METADATA_PANEL_WIDTH && hasScrollableContent()) {
            int maxScroll = getScrollableContentHeight() - imageHeight + 50;

            if (verticalAmount < 0) {
                scrollOffset = Math.min(scrollOffset + 15, maxScroll);
            } else {
                scrollOffset = Math.max(scrollOffset - 15, 0);
            }
            return true;
        }

        if (isServerPanelOpen && mouseX < SERVER_PANEL_WIDTH && mouseY < SERVER_PANEL_HEIGHT) {
            if (verticalAmount < 0) {
                serverPanelScroll = Math.min(serverPanelScroll + 15, Math.max(0, serverOptions.size() * 20 - SERVER_PANEL_HEIGHT));
            } else {
                serverPanelScroll = Math.max(serverPanelScroll - 15, 0);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean hasScrollableContent() {
        return getMetadataEntries().size() * 25 + 40 > imageHeight - 50;
    }

    private int getScrollableContentHeight() {
        return getMetadataEntries().size() * 25 + 40;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, this.title, width / 2, 20, 0xFFFFFF);

        if (textureId != null && screenshotImage != null) {
            int imageX = (width - imageWidth - METADATA_PANEL_WIDTH) / 2;
            int imageY = 50;

            context.fill(imageX - BORDER_WIDTH, imageY - BORDER_WIDTH,
                    imageX + imageWidth + BORDER_WIDTH, imageY + imageHeight + BORDER_WIDTH, 0xFFFFFFFF);

            context.drawTexture(RenderLayer::getGuiTextured, textureId, imageX, imageY, 0, 0,
                    imageWidth, imageHeight, imageWidth, imageHeight);

            renderMetadataPanel(context, imageX + imageWidth + 20, imageY);

            if (hasScrollableContent()) {
                int indicatorY = imageY + 5;
                int indicatorHeight = 10;
                context.fill(width - 10, indicatorY, width - 5, indicatorY + indicatorHeight, 0xFFAAAAAA);
            }
        } else {
            String errorMessage = "Could not load screenshot image.";
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, height / 2, 0xFF0000);
        }

        if (isServerPanelOpen) {
            renderServerSelectionPanel(context);
        }
    }

    private void renderServerSelectionPanel(DrawContext context) {
        int panelX = 10;
        int panelY = 40;

        context.fill(panelX, panelY, panelX + SERVER_PANEL_WIDTH, panelY + SERVER_PANEL_HEIGHT, 0x80000000);

        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_selection"),
                panelX + 10, panelY + 10, 0xFFFFFF);

        context.enableScissor(panelX, panelY + 30, panelX + SERVER_PANEL_WIDTH - 10, panelY + SERVER_PANEL_HEIGHT - 10);

        int serverY = panelY + 30 - serverPanelScroll;
        final int serverLineHeight = 20;

        for (Map.Entry<String, String> serverEntry : serverOptions.entrySet()) {
            if (serverY + serverLineHeight < panelY + 30) {
                serverY += serverLineHeight;
                continue;
            }

            if (serverY > panelY + SERVER_PANEL_HEIGHT - 10) {
                break;
            }

            int buttonColor = serverEntry.getKey().equals(selectedServer) ? 0xFFAAAAAA : 0xFFFFFFFF;

            context.fill(panelX + 5, serverY, panelX + SERVER_PANEL_WIDTH - 5, serverY + serverLineHeight, buttonColor);

            context.drawTextWithShadow(textRenderer, Text.literal(serverEntry.getKey()), panelX + 10, serverY + 5, 0x000000);

            serverY += serverLineHeight;
        }

        context.disableScissor();
    }

    private void renderMetadataPanel(DrawContext context, int x, int y) {
        context.fill(x, y, x + METADATA_PANEL_WIDTH, y + imageHeight, 0x80000000);

        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.metadata.title"),
                x + 10, y + 10, 0xFFFFFF);

        context.enableScissor(x, y + 30, x + METADATA_PANEL_WIDTH - 10, y + imageHeight - 40);

        LinkedHashMap<Text, Text> metadataEntries = getMetadataEntries();

        int entryY = y + 30 - scrollOffset;
        final int lineHeight = 12;

        for (Map.Entry<Text, Text> entry : metadataEntries.entrySet()) {
            if (entryY + lineHeight * 2 < y + 30) {
                entryY += lineHeight * 2 + 5;
                continue;
            }

            if (entryY > y + imageHeight - 40) {
                break;
            }

            context.drawTextWithShadow(textRenderer, entry.getKey(), x + 10, entryY, 0xAAAAAA);

            List<Text> wrappedValue = wrapText(entry.getValue().getString());
            int valueY = entryY + lineHeight;
            for (Text line : wrappedValue) {
                context.drawTextWithShadow(textRenderer, line, x + 10, valueY, 0xFFFFFF);
                valueY += lineHeight;
            }

            entryY = valueY + 5;
        }

        context.disableScissor();

        String uploadInfoText = "Selected server: " + (selectedServer != null ? selectedServer : "None");
        context.drawTextWithShadow(textRenderer, uploadInfoText, x + 10, y + imageHeight - 30, 0xFFFFFF);
    }

    private List<Text> wrapText(String text) {
        List<Text> lines = new ArrayList<>();
        if (text.length() <= 30 || textRenderer == null) {
            lines.add(Text.literal(text));
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        String[] words = text.split(" ");

        for (String word : words) {
            if (textRenderer.getWidth(currentLine + " " + word) > 270 && !currentLine.isEmpty()) {
                lines.add(Text.literal(currentLine.toString()));
                currentLine = new StringBuilder(word);
            } else {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(Text.literal(currentLine.toString()));
        }

        return lines;
    }

    private LinkedHashMap<Text, Text> getMetadataEntries() {
        LinkedHashMap<Text, Text> entries = new LinkedHashMap<>();

        entries.put(Text.translatable("gui.screenshot_uploader.metadata.filename"),
                Text.literal(screenshotPath.getFileName().toString()));

        if (metadata.has("username")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.username"),
                    Text.literal(metadata.get("username").getAsString()));
        }

        if (metadata.has("uuid")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.uuid"),
                    Text.literal(metadata.get("uuid").getAsString()));
        }

        if (metadata.has("accountType")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.accountType"),
                    Text.literal(metadata.get("accountType").getAsString()));
        }

        if (metadata.has("dimension")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.dimension"),
                    Text.literal(metadata.get("dimension").getAsString()));
        }

        if (metadata.has("world_name")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.world"),
                    Text.literal(metadata.get("world_name").getAsString()));
        }

        if (metadata.has("world_seed")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.seed"),
                    Text.literal(metadata.get("world_seed").getAsString()));
        }

        if (metadata.has("biome")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.biome"),
                    Text.literal(metadata.get("biome").getAsString()));
        }

        if (metadata.has("coordinates")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.location"),
                    Text.literal(metadata.get("coordinates").getAsString()));
        }

        if (metadata.has("facing_direction")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.facing"),
                    Text.literal(metadata.get("facing_direction").getAsString()));
        }

        if (metadata.has("world_info")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.worldInfo"),
                    Text.literal(metadata.get("world_info").getAsString()));
        }

        if (metadata.has("player_state")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.playerState"),
                    Text.literal(metadata.get("player_state").getAsString()));
        }

        if (metadata.has("chunk_info")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.chunkInfo"),
                    Text.literal(metadata.get("chunk_info").getAsString()));
        }

        if (metadata.has("entities_info")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.entitiesInfo"),
                    Text.literal(metadata.get("entities_info").getAsString()));
        }

        if (metadata.has("server_address")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.serverAddress"),
                    Text.literal(metadata.get("server_address").getAsString()));
        }

        if (metadata.has("client_settings")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.clientSettings"),
                    Text.literal(metadata.get("client_settings").getAsString()));
        }

        if (metadata.has("system_info")) {
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.systemInfo"),
                    Text.literal(metadata.get("system_info").getAsString()));
        }

        if (metadata.has("album") && metadata.get("album").isJsonPrimitive()) {
            String albumUUID = metadata.get("album").getAsString();
            try {
                Album album = AlbumManager.getAlbum(UUID.fromString(albumUUID));
                if (album != null) {
                    entries.put(Text.translatable("gui.screenshot_uploader.metadata.album"),
                            Text.literal(album.getTitle()));
                }
            } catch (Exception e) {
                logger.error("Error processing album: {}", e.getMessage());
            }
        }

        if (metadata.has("current_time")) {
            long timestamp = metadata.get("current_time").getAsLong();
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.time"),
                    Text.literal(formatTimestamp(timestamp)));
        } else if (metadata.has("date")) {
            long timestamp = metadata.get("date").getAsLong();
            entries.put(Text.translatable("gui.screenshot_uploader.metadata.time"),
                    Text.literal(formatTimestamp(timestamp)));
        }

        return entries;
    }

    private String formatTimestamp(long millis) {
        Instant timestampInstant = Instant.ofEpochMilli(millis);
        LocalDateTime timestampDateTime = LocalDateTime.ofInstant(timestampInstant, ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        return timestampDateTime.format(formatter);
    }

    private void uploadScreenshot() {
        logger.info("Uploading screenshot: {}", screenshotPath);

        String metadataString;
        String uploadServer = serverOptions.get(selectedServer);
        if (selectedServer.equals("Current Server")) {
            uploadServer = ReceivePackets.serverSiteAddress;
        }
        if (metadata.isJsonObject()) {
            metadataString = new com.google.gson.Gson().toJson(metadata);

        } else {
            metadataString = metadata.getAsString();
        }

        ScreenshotUploadHelper.uploadScreenshot(screenshotImage, metadataString, List.of(uploadServer));

        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void updateServerButtonStates() {
        for (ButtonWidget button : serverButtons) {
            button.active = !button.getMessage().getString().equals(selectedServer);
        }
    }

    @Override
    public void close() {
        if (screenshotImage != null) {
            screenshotImage.close();
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isServerPanelOpen && button == 0) {
            int panelX = 10;
            int panelY = 40;

            if (mouseX >= panelX + 5 && mouseX <= panelX + SERVER_PANEL_WIDTH - 5 &&
                    mouseY >= panelY + 30 && mouseY <= panelY + SERVER_PANEL_HEIGHT - 10) {

                int clickedY = (int) mouseY - (panelY + 30) + serverPanelScroll;
                int serverIndex = clickedY / 20;

                if (serverIndex >= 0 && serverIndex < serverOptions.size()) {
                    int i = 0;
                    for (String serverName : serverOptions.keySet()) {
                        if (i == serverIndex) {
                            selectedServer = serverName;
                            isServerPanelOpen = false;
                            return true;
                        }
                        i++;
                    }
                }
            }
            if (mouseX >= panelX && mouseX <= panelX + SERVER_PANEL_WIDTH &&
                    mouseY >= panelY && mouseY <= panelY + SERVER_PANEL_HEIGHT) {
                return true;
            }
            isServerPanelOpen = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}


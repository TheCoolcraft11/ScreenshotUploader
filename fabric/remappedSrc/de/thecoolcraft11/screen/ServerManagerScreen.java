package de.thecoolcraft11.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class ServerManagerScreen extends Screen {
    public static final String GUI_SCREENSHOT_UPLOADER_SERVER_MANAGER_UNSAVED = "gui.screenshot_uploader.server_manager.unsaved";
    private final Screen parent;
    private final JsonObject serversConfig;
    private final Map<String, JsonObject> servers = new LinkedHashMap<>();
    private final List<String> serverNames = new ArrayList<>();
    private String selectedServer = null;
    private boolean isChanged = false;

    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 30;
    private static final int SERVER_LIST_WIDTH = 200;

    private TextFieldWidget nameField;
    private TextFieldWidget uploadField;
    private TextFieldWidget galleryField;
    private TextFieldWidget homeField;
    private ButtonWidget addServerButton;
    private ButtonWidget updateServerButton;
    private ButtonWidget deleteServerButton;
    private ButtonWidget saveButton;

    public ServerManagerScreen(Screen parent, JsonObject serversConfig) {
        super(Text.translatable("gui.screenshot_uploader.server_manager.title"));
        this.parent = parent;
        this.serversConfig = serversConfig;
        loadServers();
    }

    private void loadServers() {
        servers.clear();
        serverNames.clear();
        for (Map.Entry<String, JsonElement> entry : serversConfig.entrySet()) {
            String serverName = entry.getKey();
            JsonObject serverData = entry.getValue().getAsJsonObject();
            servers.put(serverName, serverData);
            serverNames.add(serverName);
        }
        Collections.sort(serverNames);
    }

    @Override
    protected void init() {
        clearChildren();

        int centerX = width / 2;
        int leftPanelX = centerX - 210;
        int rightPanelX = centerX + 10;
        int topY = 60;

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.server_manager.add_new"),
                        button -> selectNewServer())
                .dimensions(leftPanelX, 60, SERVER_LIST_WIDTH, 20).build());

        int serverListY = topY;
        for (String serverName : serverNames) {
            int yPos = serverListY + scrollOffset + 40;

            if (yPos >= topY && yPos < height - 60) {
                ButtonWidget serverButton = ButtonWidget.builder(Text.of(serverName),
                                button -> selectServer(serverName))
                        .dimensions(leftPanelX, yPos, SERVER_LIST_WIDTH, 20).build();

                if (serverName.equals(selectedServer)) {
                    serverButton.setMessage(Text.literal("> " + serverName + " <").formatted(Formatting.YELLOW));
                }

                addDrawableChild(serverButton);
            }

            serverListY += ENTRY_HEIGHT;
        }

        int fieldWidth = 300;
        int fieldSpacing = ENTRY_HEIGHT + 10;

        nameField = addDrawableChild(new TextFieldWidget(textRenderer, rightPanelX, topY, fieldWidth, 20, Text.of("Server Name")));
        nameField.setMaxLength(64);

        uploadField = addDrawableChild(new TextFieldWidget(textRenderer, rightPanelX, topY + fieldSpacing, fieldWidth, 20, Text.of("Upload URL")));
        uploadField.setMaxLength(256);
        uploadField.setTooltip(Tooltip.of(Text.of("URL for uploading screenshots")));

        galleryField = addDrawableChild(new TextFieldWidget(textRenderer, rightPanelX, topY + fieldSpacing * 2, fieldWidth, 20, Text.of("Gallery URL")));
        galleryField.setMaxLength(256);
        galleryField.setTooltip(Tooltip.of(Text.of("URL to view screenshot gallery")));

        homeField = addDrawableChild(new TextFieldWidget(textRenderer, rightPanelX, topY + fieldSpacing * 3, fieldWidth, 20, Text.of("Home URL")));
        homeField.setMaxLength(256);
        homeField.setTooltip(Tooltip.of(Text.of("Base URL for the server")));

        nameField.setChangedListener(s -> checkFields());
        uploadField.setChangedListener(s -> checkFields());
        galleryField.setChangedListener(s -> checkFields());
        homeField.setChangedListener(s -> checkFields());

        int buttonY = topY + fieldSpacing * 4 + 10;

        addServerButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.server_manager.add"),
                        button -> addServer())
                .dimensions(rightPanelX, buttonY, 140, 20).build());

        updateServerButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.server_manager.update"),
                        button -> updateServer())
                .dimensions(rightPanelX + 160, buttonY, 140, 20).build());

        deleteServerButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.server_manager.delete"),
                        button -> deleteServer())
                .dimensions(rightPanelX, buttonY + 30, 300, 20).build());

        saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.server_manager.save"),
                        button -> saveChanges())
                .dimensions(centerX - 155, height - 30, 150, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.server_manager.back"),
                        button -> close())
                .dimensions(centerX + 5, height - 30, 150, 20).build());

        updateButtonStates();

        if (selectedServer != null) {
            loadServerData(selectedServer);
        } else {
            resetFields();
        }
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedServer != null;
        boolean isValidInput = !nameField.getText().trim().isEmpty() &&
                !uploadField.getText().trim().isEmpty() &&
                !galleryField.getText().trim().isEmpty() &&
                !homeField.getText().trim().isEmpty();

        addServerButton.active = isValidInput && selectedServer == null;
        updateServerButton.active = isValidInput && hasSelection;
        deleteServerButton.active = hasSelection;
        saveButton.active = isChanged;
    }

    private void checkFields() {
        updateButtonStates();
    }

    private void selectServer(String serverName) {
        selectedServer = serverName;
        loadServerData(serverName);
        init();
    }

    private void selectNewServer() {
        selectedServer = null;
        resetFields();
        init();
    }

    private void loadServerData(String serverName) {
        JsonObject serverData = servers.get(serverName);
        nameField.setText(serverName);
        uploadField.setText(serverData.has("upload") ? serverData.get("upload").getAsString() : "");
        galleryField.setText(serverData.has("gallery") ? serverData.get("gallery").getAsString() : "");
        homeField.setText(serverData.has("home") ? serverData.get("home").getAsString() : "");
    }

    private void resetFields() {
        nameField.setText("");
        uploadField.setText("");
        galleryField.setText("");
        homeField.setText("");
    }

    private void addServer() {
        String name = nameField.getText().trim();
        if (name.isEmpty() || servers.containsKey(name)) {
            return;
        }

        JsonObject serverData = new JsonObject();
        serverData.addProperty("upload", uploadField.getText().trim());
        serverData.addProperty("gallery", galleryField.getText().trim());
        serverData.addProperty("home", homeField.getText().trim());

        servers.put(name, serverData);
        serverNames.add(name);
        Collections.sort(serverNames);
        selectedServer = name;
        isChanged = true;

        init();
    }

    private void updateServer() {
        if (selectedServer == null) return;

        String newName = nameField.getText().trim();
        if (newName.isEmpty()) return;

        JsonObject serverData = new JsonObject();
        serverData.addProperty("upload", uploadField.getText().trim());
        serverData.addProperty("gallery", galleryField.getText().trim());
        serverData.addProperty("home", homeField.getText().trim());

        if (!newName.equals(selectedServer)) {
            servers.remove(selectedServer);
            serverNames.remove(selectedServer);
            servers.put(newName, serverData);
            serverNames.add(newName);
            Collections.sort(serverNames);
            selectedServer = newName;
        } else {
            servers.put(selectedServer, serverData);
        }

        isChanged = true;
        init();
    }

    private void deleteServer() {
        if (selectedServer == null) return;

        servers.remove(selectedServer);
        serverNames.remove(selectedServer);
        selectedServer = null;
        isChanged = true;

        resetFields();
        init();
    }

    private void saveChanges() {
        JsonObject updatedServersConfig = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : servers.entrySet()) {
            updatedServersConfig.add(entry.getKey(), entry.getValue());
        }

        if (parent instanceof ConfigScreen configScreen) {
            configScreen.changeConfig(updatedServersConfig);
            isChanged = false;
            updateButtonStates();
        }
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        context.fill(centerX - 5, 40, centerX + 5, height - 40, 0x66FFFFFF);

        context.drawCenteredTextWithShadow(textRenderer, title, centerX, 10, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_manager.server_list"), centerX - 210, 35, 0xCCCCCC);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_manager.server_details"), centerX + 10, 35, 0xCCCCCC);

        int rightPanelX = centerX + 10;
        int topY = 60;
        int fieldSpacing = ENTRY_HEIGHT + 10;

        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_manager.name"), rightPanelX, topY - 15, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_manager.upload_url"), rightPanelX, topY + fieldSpacing - 15, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_manager.gallery_url"), rightPanelX, topY + fieldSpacing * 2 - 15, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.server_manager.home_url"), rightPanelX, topY + fieldSpacing * 3 - 15, 0xAAAAAA);

    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset += (int) (verticalAmount * 10);

        int maxScroll = Math.max(0, serverNames.size() * ENTRY_HEIGHT - (height - 150));
        scrollOffset = Math.max(-maxScroll, Math.min(0, scrollOffset));

        init();
        return true;
    }

    @Override
    public void close() {
        if (!isChanged) {
            if (client != null) {
                client.setScreen(parent);
            }
            return;
        }
        if (client != null) {
            client.setScreen(new ConfirmationScreen(
                    confirmed -> {
                        if (confirmed) {
                            saveChanges();
                            client.setScreen(parent);
                        } else {
                            client.setScreen(this);
                        }
                    },
                    Text.translatable(GUI_SCREENSHOT_UPLOADER_SERVER_MANAGER_UNSAVED),
                    Text.translatable("gui.screenshot_uploader.server_manager.unsaved_detail")
            ));
        }
    }
}
package de.thecoolcraft11.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.thecoolcraft11.packet.TagPayload;
import de.thecoolcraft11.util.ReceivePackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ScreenshotWebTaggingScreen extends Screen {
    private final Screen parent;
    private final String screenshotId;
    private final JsonArray existingJson;
    private TextFieldWidget tagInputField;
    private final List<String> currentTags = new ArrayList<>();
    private int scrollOffset = 0;
    private final int maxVisible = 10;
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotWebTaggingScreen.class);

    public ScreenshotWebTaggingScreen(Screen parent, String screenshotId, JsonArray existingJson) {
        super(Text.translatable("gui.screenshot_uploader.tagging_screen.title"));
        this.parent = parent;
        this.screenshotId = screenshotId;
        this.existingJson = existingJson;

        loadExistingTags();
    }

    @Override
    protected void init() {
        super.init();

        this.tagInputField = new TextFieldWidget(this.textRenderer,
                this.width / 2 - 100, 50, 200, 20,
                Text.translatable("gui.screenshot_uploader.tagging_screen.input"));
        this.tagInputField.setMaxLength(12);
        this.addSelectableChild(this.tagInputField);
        this.setInitialFocus(this.tagInputField);

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.screenshot_uploader.tagging_screen.add_tag"),
                        button -> addTag())
                .dimensions(this.width / 2 - 100, 75, 200, 20)
                .build());

        ButtonWidget saveButton = ButtonWidget.builder(
                        Text.translatable("gui.screenshot_uploader.tagging_screen.save"),
                        button -> {
                            saveTags();
                            MinecraftClient.getInstance().setScreen(this.parent);
                        })
                .dimensions(this.width / 2 - 100, this.height - 40, 200, 20)
                .build();

        this.addDrawableChild(saveButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
//         this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        this.tagInputField.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("gui.screenshot_uploader.tagging_screen.current_tags"),
                this.width / 2, 110, 0xFFFFFF);

        int yPos = 130;
        int tagsAreaHeight = this.height - 170;
        int totalEntries = currentTags.size();

        if (totalEntries > 0) {
            for (int i = scrollOffset; i < Math.min(currentTags.size(), scrollOffset + maxVisible); i++) {
                String tag = currentTags.get(i);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(tag), this.width / 2, yPos, 0xAAAAAA);

                if (mouseX >= this.width / 2 + 110 && mouseX <= this.width / 2 + 120 &&
                        mouseY >= yPos - 5 && mouseY <= yPos + 5) {
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("✕"), this.width / 2 + 115, yPos, 0xFF0000);
                } else {
                    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("✕"), this.width / 2 + 115, yPos, 0x999999);
                }

                yPos += 20;
            }

            if (totalEntries > maxVisible) {
                int scrollBarHeight = Math.max(20, tagsAreaHeight * maxVisible / totalEntries);
                int scrollBarY = 130 + (tagsAreaHeight - scrollBarHeight) * scrollOffset / (totalEntries - maxVisible);

                context.fill(this.width / 2 + 130, 130, this.width / 2 + 135, this.height - 40, 0x33FFFFFF);

                context.fill(this.width / 2 + 130, scrollBarY, this.width / 2 + 135, scrollBarY + scrollBarHeight, 0x99FFFFFF);
            }
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("gui.screenshot_uploader.tagging_screen.no_tags"),
                    this.width / 2, yPos + 30, 0x888888);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTags.size() > maxVisible) {
            if (verticalAmount > 0) {
                if (scrollOffset > 0) {
                    scrollOffset--;
                }
            } else if (verticalAmount < 0) {
                if (scrollOffset < currentTags.size() - maxVisible) {
                    scrollOffset++;
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int yPos = 130;

        for (int i = scrollOffset; i < Math.min(currentTags.size(), scrollOffset + maxVisible); i++) {
            if (mouseX >= (double) this.width / 2 + 110 && mouseX <= (double) this.width / 2 + 120 &&
                    mouseY >= yPos - 5 && mouseY <= yPos + 5) {
                currentTags.remove(i);
                return true;
            }
            yPos += 20;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addTag() {
        String tag = tagInputField.getText().trim();
        if (!tag.isEmpty() && !currentTags.contains(tag)) {
            currentTags.add(tag);
            tagInputField.setText("");
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tagInputField.isFocused() && keyCode == 257) {
            addTag();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void loadExistingTags() {
        if (existingJson != null) {
            for (JsonElement element : existingJson) {
                String tag = element.getAsString();
                if (!currentTags.contains(tag)) {
                    currentTags.add(tag);
                }
            }
        } else {
            logger.error("No tags found in existing JSON.");
        }
    }

    private void saveTags() {
        StringBuilder tagsJson = new StringBuilder("{\"tags\":[");
        for (int i = 0; i < currentTags.size(); i++) {
            tagsJson.append("\"").append(currentTags.get(i)).append("\"");
            if (i < currentTags.size() - 1) {
                tagsJson.append(",");
            }
        }
        tagsJson.append("]}");
        String jsonString = tagsJson.toString();
        logger.info("Saving tags: {}", jsonString);
        if (ReceivePackets.tagSiteAddress == null || ReceivePackets.tagSiteAddress.isEmpty()) {
            ClientPlayNetworking.send(new TagPayload(jsonString, screenshotId));
        } else {
            try {
                int responseCode = getTagResponse(screenshotId, jsonString);
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    logger.info("Tags saved successfully: {}", jsonString);
                    MinecraftClient.getInstance().setScreen(this.parent);
                } else {
                    logger.error("Failed to delete screenshot. Response code: {}", responseCode);
                }
            } catch (Exception e) {
                logger.error("Error while deleting screenshot: {}", e.getMessage());
            }
        }
    }

    private static int getTagResponse(String screenshotId, String jsonString) throws URISyntaxException, IOException {
        URI uri = new URI(ReceivePackets.deletionSiteAddress);
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        JsonObject deletionJson = new JsonObject();
        deletionJson.addProperty("screenshotId", screenshotId);
        deletionJson.addProperty("author", MinecraftClient.getInstance().getSession().getUsername());
        deletionJson.addProperty("authorUUID", MinecraftClient.getInstance().getSession().getUuidOrNull().toString());
        deletionJson.addProperty("tags", jsonString);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = deletionJson.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        return connection.getResponseCode();
    }
}
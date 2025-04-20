package de.thecoolcraft11.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigScreen extends Screen {

    private static final Logger logger = LoggerFactory.getLogger(ConfigScreen.class);
    private final Path configPath = Paths.get("config/screenshotUploader/config.json");
    private JsonObject config;

    private final Map<String, TextFieldWidget> inputFields = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private final int entryHeight = 30;
    private final Map<String, Element> scrollableButtons = new LinkedHashMap<>();
    private boolean isConfigSaved = true;
    private ButtonWidget saveButton;

    public ConfigScreen() {
        super(Text.translatable("gui.screenshot_uploader.config.title"));
        loadConfig();
    }

    @Override
    protected void init() {
        inputFields.clear();
        this.clearChildren();

        int yOffsetStart = 40;
        int inputXOffset = this.width / 2;

        int currentYOffset = yOffsetStart;

        for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (key.startsWith("_comment")) continue;

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                TextFieldWidget textField = new TextFieldWidget(textRenderer, inputXOffset, currentYOffset, 200, 20, Text.literal(key));
                textField.setMaxLength(1024);
                textField.setText(value.getAsString());
                textField.setTooltip(Tooltip.of(Text.of(key + ":\n").copy().styled(style -> style.withBold(true).withUnderline(true)).append(Text.translatable((config.has("_comment_" + key) ? config.get("_comment_" + key).getAsString().replaceAll("^\"|\"$", "'") : "")).styled(style -> style.withUnderline(false).withBold(false).withColor(Formatting.AQUA)))));
                textField.setChangedListener(s -> isConfigSaved = false);
                inputFields.put(key, textField);
                scrollableButtons.put(key, textField);
                addSelectableChild(textField);
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                TextFieldWidget textField = new TextFieldWidget(textRenderer, inputXOffset, currentYOffset, 200, 20, Text.literal(key));
                textField.setMaxLength(1024);
                textField.setText(value.getAsString());
                textField.setTooltip(Tooltip.of(Text.of(key + ":\n").copy().styled(style -> style.withBold(true).withUnderline(true)).append(Text.translatable((config.has("_comment_" + key) ? config.get("_comment_" + key).getAsString().replaceAll("^\"|\"$", "'") : "")).styled(style -> style.withUnderline(false).withBold(false).withColor(Formatting.AQUA)))));
                textField.setChangedListener(s -> isConfigSaved = false);
                inputFields.put(key, textField);
                scrollableButtons.put(key, textField);
                addSelectableChild(textField);
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {

                boolean currentValue = value.getAsBoolean();
                ButtonWidget widget = ButtonWidget.builder(Text.translatable(currentValue ? "gui.screenshot_uploader.config.true" : "gui.screenshot_uploader.config.false"), button -> {
                    isConfigSaved = false;
                    boolean newValue = !config.get(key).getAsBoolean();
                    config.addProperty(key, newValue);
                    button.setMessage(Text.translatable(newValue ? "gui.screenshot_uploader.config.true" : "gui.screenshot_uploader.config.false"));
                }).dimensions(inputXOffset, currentYOffset, 200, 20).tooltip(Tooltip.of(Text.of(key))).build();
                widget.setTooltip(Tooltip.of(Text.of(key + ":\n").copy().styled(style -> style.withBold(true).withUnderline(true)).append(Text.translatable((config.has("_comment_" + key) ? config.get("_comment_" + key).getAsString().replaceAll("^\"|\"$", "'") : "")).styled(style -> style.withUnderline(false).withBold(false).withColor(Formatting.AQUA)))));
                addDrawableChild(widget);
                scrollableButtons.put(key, widget);
            } else if (value.isJsonNull() || value.isJsonArray() || value.isJsonObject()) {
                ButtonWidget widget = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.config.not_edit"), button -> {
                }).dimensions(inputXOffset, currentYOffset, 200, 20).tooltip(Tooltip.of(Text.of(key + "\nHead to /config/screenshotUploader/config.json to edit servers"))).build();
                widget.setTooltip(Tooltip.of(Text.of(key + ":\n").copy().styled(style -> style.withBold(true).withUnderline(true)).append(Text.translatable((config.has("_comment_" + key) ? config.get("_comment_" + key).getAsString().replaceAll("^\"|\"$", "'") : "")).styled(style -> style.withUnderline(false).withBold(false).withColor(Formatting.AQUA)))));
                widget.active = false;
                addDrawableChild(widget);
                scrollableButtons.put(key, widget);
            }

            currentYOffset += entryHeight;
        }

        int buttonYOffset = Math.max(currentYOffset + 10, this.height - 60);
        saveButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.config.save"), button -> saveConfig())
                .dimensions(this.width / 2, buttonYOffset, 200, 20).build();
        saveButton.active = !isConfigSaved;
        saveButton.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.config.nothing_changed")));
        addDrawableChild(saveButton);
        ButtonWidget backButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.config.close"), button -> close())
                .dimensions(this.width / 2, buttonYOffset + 30, 200, 20).build();
        addDrawableChild(saveButton);
        addDrawableChild(backButton);
        scrollableButtons.put("Save", saveButton);
        scrollableButtons.put("Close", backButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, this.width / 2, 10, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);

        for (TextFieldWidget field : inputFields.values()) {
            field.render(context, mouseX, mouseY, delta);
        }

        int currentYOffset = 40 + scrollOffset;
        int labelXOffset = this.width / 4;


        for (Map.Entry<String, JsonElement> entry : config.entrySet()) {
            String key = entry.getKey();
            int labelY = currentYOffset;

            if (key.startsWith("_comment")) continue;

            if (labelY >= 20 && labelY <= this.height - 40) {
                context.drawTextWithShadow(
                        textRenderer,
                        key,
                        labelXOffset,
                        labelY + 6,
                        0xCCCCCC
                );
            }

            currentYOffset += entryHeight;
        }
        saveButton.active = !isConfigSaved;
        saveButton.setTooltip(!isConfigSaved ? Tooltip.of(Text.of("In order for the changed config values to take effect, please restart the game")) : Tooltip.of(Text.translatable("gui.screenshot_uploader.config.nothing_changed")));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount, double horizontalAmount) {
        scrollOffset += (int) (horizontalAmount * 10);

        int maxScroll = Math.max(((scrollableButtons.size() + 2) * entryHeight) - 2 * (this.height - 60), 0);
        scrollOffset = Math.max(scrollOffset, -((scrollableButtons.size() + 2) * entryHeight - (this.height - 60)));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        updateWidgetPositions();
        return true;
    }

    private void updateWidgetPositions() {
        int yOffsetStart = 40 + scrollOffset;
        int inputXOffset = this.width / 2;
        int currentYOffset = yOffsetStart;

        for (Map.Entry<String, Element> entry : scrollableButtons.entrySet()) {
            if (entry.getValue() instanceof ButtonWidget field) {
                field.setX(inputXOffset);
                field.setY(currentYOffset);
                currentYOffset += entryHeight;
            } else if (entry.getValue() instanceof TextFieldWidget field) {
                field.setX(inputXOffset);
                field.setY(currentYOffset);
                currentYOffset += entryHeight;
            }
        }

    }


    private void loadConfig() {
        try {
            if (!Files.exists(configPath)) {
                config = new JsonObject();
                config.addProperty("exampleString", "DefaultValue");
                config.addProperty("exampleNumber", 0);
                config.addProperty("exampleBoolean", true);
                saveConfigFile();
            } else {
                String jsonString = Files.readString(configPath);
                config = JsonParser.parseString(jsonString).getAsJsonObject();
            }
        } catch (IOException e) {
            logger.error("Error loading config file {}: {}", configPath, e.getMessage());
            config = new JsonObject();
        }
    }

    private void saveConfig() {
        for (Map.Entry<String, TextFieldWidget> entry : inputFields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().getText();

            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                config.addProperty(key, Boolean.parseBoolean(value));
            } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                config.addProperty(key, Double.parseDouble(value));
            } else {
                config.addProperty(key, value);
            }
        }

        saveConfigFile();
        ConfigManager.reloadConfig(new File("config/screenshotUploader/"), true);
        isConfigSaved = true;
    }

    private void saveConfigFile() {
        try {
            Files.writeString(configPath, config.toString());
        } catch (IOException e) {
            logger.error("Error saving config file {}: {}", configPath, e.getMessage());
        }
    }

    @Override
    public void close() {
        if (isConfigSaved) {
            super.close();
        } else {
            assert this.client != null;

            this.client.setScreen(new ConfirmationScreen(
                    confirmed -> {
                        if (confirmed) {
                            isConfigSaved = true;
                            super.close();
                        } else {
                            this.client.setScreen(this);
                        }
                    },
                    Text.translatable("gui.screenshot_uploader.config.unsaved"),
                    Text.translatable("gui.screenshot_uploader.config.unsaved_detail")
            ));
        }
    }
}
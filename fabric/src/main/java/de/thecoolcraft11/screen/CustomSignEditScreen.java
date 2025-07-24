package de.thecoolcraft11.screen;

import de.thecoolcraft11.ScreenshotUploader;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomSignEditScreen extends Screen {

    private static final Logger logger = LoggerFactory.getLogger(CustomSignEditScreen.class);
    private final SignBlockEntity sign;
    private static Identifier screenshotIdentifier;
    TextFieldWidget urlField;
    private TextFieldWidget xOffsetField;
    private TextFieldWidget yOffsetField;
    private TextFieldWidget zOffsetField;
    private TextFieldWidget yawField;
    private TextFieldWidget pitchField;
    private TextFieldWidget sizeField;
    private TextFieldWidget lightField;
    private TextFieldWidget itemField;
    private boolean showTransformSettings = false;
    private ButtonWidget applyButton;

    public static int customEditsLeft = 0;
    private static int lastUrlHash = 0;
    private long lastTypingTime = 0;
    private static final int TYPING_TIMEOUT_MS = 800;
    private String scheduledImageUrl = null;
    private float xOffset = 0f;
    private float yOffset = 0f;
    private float zOffset = 0f;
    private float yaw = 0f;
    private float pitch = 0f;
    private float size = 1f;
    private int light = 255;
    private String item = "$painting";

    public CustomSignEditScreen(SignBlockEntity sign) {
        super(Text.of("Sign"));
        this.sign = sign;

        String signText = extractSignText(sign);
        if (!signText.isEmpty()) {
            parseTransformationFromUrl(signText);
        }
    }

    private String extractSignText(SignBlockEntity sign) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            text.append(sign.getFrontText().getMessage(i, false).getString());
        }
        for (int i = 0; i < 4; i++) {
            text.append(sign.getBackText().getMessage(i, false).getString());
        }
        return text.toString();
    }

    private void parseTransformationFromUrl(String url) {
        String urlPattern = "(https?://[\\w.-]+(?::\\d+)?(?:/[\\w.-]*)*)(\\[-?\\d+(?:[.,]\\d+)?(?:[;,:_+-]-?[a-zA-Z0-9$]+(?:[.:,_+-]\\d+)?)*?])?";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(url);

        if (matcher.find() && matcher.group(2) != null && !matcher.group(2).isEmpty()) {
            String transformStr = matcher.group(2);
            transformStr = transformStr.substring(1, transformStr.length() - 1);

            String[] parts = transformStr.split("[;,]");

            try {
                if (parts.length >= 1) {
                    xOffset = Float.parseFloat(parts[0]);
                }
                if (parts.length >= 2) {
                    yOffset = Float.parseFloat(parts[1]);
                }
                if (parts.length >= 3) {
                    zOffset = Float.parseFloat(parts[2]);
                }
                if (parts.length >= 4) {
                    yaw = Float.parseFloat(parts[3]);
                }
                if (parts.length >= 5) {
                    pitch = Float.parseFloat(parts[4]);
                }
                if (parts.length >= 6) {
                    size = Float.parseFloat(parts[5]);
                }
                if (parts.length >= 7) {
                    light = Integer.parseInt(parts[6]);
                }
                if (parts.length >= 8) {
                    item = parts[7];
                }
            } catch (NumberFormatException e) {
                logger.error("Error parsing transformation values", e);
            }
        }
    }

    @Override
    protected void init() {
        screenshotIdentifier = null;
        lastUrlHash = 0;

        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height / 2 + this.height / 4;
        int textFieldWidth = this.width / 3;
        int textFieldX = (this.width - textFieldWidth) / 2;
        int textFieldY = this.height / 2 + this.height / 8;

        urlField = new TextFieldWidget(textRenderer, textFieldX, textFieldY, textFieldWidth, 20, Text.literal(""));
        urlField.setMaxLength(15 * 8);
        addDrawableChild(urlField);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_sign.set_text"), buttonWidget -> pasteTextToSign())
                .dimensions(buttonX, buttonY, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.screenshot_sign.tooltip")))
                .build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.screenshot_sign.close"), buttonWidget -> {
            if (client != null) {
                client.setScreen(null);
            }
        }).dimensions(buttonX, buttonY + buttonHeight + 5, buttonWidth, buttonHeight).build());

        ButtonWidget transformButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.sign_edit.trans_settings"), button -> toggleTransformSettings())
                .dimensions(buttonX, buttonY - buttonHeight - 5, buttonWidth, buttonHeight)
                .tooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.trans_settings.tooltip")))
                .build();
        addDrawableChild(transformButton);

        initTransformFields();
        updateTransformFieldsVisibility();

        super.init();
    }

    private void initTransformFields() {
        int smallFieldWidth = 50;
        int mediumFieldWidth = 120;
        int fieldHeight = 20;
        int spacing = 5;

        int startX = (int) (width * 0.85);
        int startY = height / 4;

        int buttonY = this.height / 2 + this.height / 4;

        xOffsetField = createNumberField(startX, startY, smallFieldWidth, String.valueOf(xOffset));
        xOffsetField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.x_offset")));

        yOffsetField = createNumberField(startX, startY + fieldHeight + spacing, smallFieldWidth, String.valueOf(yOffset));
        yOffsetField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.y_offset")));

        zOffsetField = createNumberField(startX, startY + 2 * (fieldHeight + spacing), smallFieldWidth, String.valueOf(zOffset));
        zOffsetField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.z_offset")));

        yawField = createNumberField(startX, startY + 3 * (fieldHeight + spacing), smallFieldWidth, String.valueOf(yaw));
        yawField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.yaw_rotation")));

        pitchField = createNumberField(startX, startY + 4 * (fieldHeight + spacing), smallFieldWidth, String.valueOf(pitch));
        pitchField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.pitch_rotation")));

        sizeField = createNumberField(startX, startY + 5 * (fieldHeight + spacing), smallFieldWidth, String.valueOf(size));
        sizeField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.size")));

        lightField = createNumberField(startX, startY + 6 * (fieldHeight + spacing), smallFieldWidth, String.valueOf(light));
        lightField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.light")));

        itemField = new TextFieldWidget(textRenderer, startX, startY + 7 * (fieldHeight + spacing),
                mediumFieldWidth, fieldHeight, Text.literal(""));
        itemField.setText(item);
        itemField.setTooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.item")));

        int buttonHeight = 20;
        applyButton = ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.sign_edit.apply_trans"), button -> applyTransformSettings())
                .dimensions(startX - 25, buttonY + buttonHeight + 5, 100, buttonHeight)
                .tooltip(Tooltip.of(Text.translatable("gui.screenshot_uploader.sign_edit.apply_trans.tooltip")))
                .build();


        addDrawableChild(xOffsetField);
        addDrawableChild(yOffsetField);
        addDrawableChild(zOffsetField);
        addDrawableChild(yawField);
        addDrawableChild(pitchField);
        addDrawableChild(sizeField);
        addDrawableChild(lightField);
        addDrawableChild(itemField);
        addDrawableChild(applyButton);
    }

    private TextFieldWidget createNumberField(int x, int y, int width, String initialValue) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.literal(""));
        field.setText(initialValue);
        field.setTextPredicate(text -> text.isEmpty() || text.matches("-?\\d*\\.?\\d*"));
        return field;
    }

    private void updateTransformFieldsVisibility() {
        xOffsetField.setVisible(showTransformSettings);
        yOffsetField.setVisible(showTransformSettings);
        zOffsetField.setVisible(showTransformSettings);
        yawField.setVisible(showTransformSettings);
        pitchField.setVisible(showTransformSettings);
        sizeField.setVisible(showTransformSettings);
        lightField.setVisible(showTransformSettings);
        itemField.setVisible(showTransformSettings);
        applyButton.visible = showTransformSettings;
    }

    private void toggleTransformSettings() {
        showTransformSettings = !showTransformSettings;
        updateTransformFieldsVisibility();
    }

    private void pasteTextToSign() {
        customEditsLeft = 2;
        String[] splitStrings = splitIntoChunks(urlField.getText(), 15, 8);
        String[] strings = new String[8];

        for (int i = 0; i < 8 && i < splitStrings.length; i++) {
            strings[i] = splitStrings[i];
        }

        ClientPlayNetworkHandler clientPlayNetworkHandler;
        if (this.client != null) {
            clientPlayNetworkHandler = this.client.getNetworkHandler();

            if (clientPlayNetworkHandler != null) {
                clientPlayNetworkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3d.ofCenter(sign.getPos()),
                                Direction.UP,
                                sign.getPos(),
                                false
                        ),
                        0
                ));
                clientPlayNetworkHandler.sendPacket(
                        new UpdateSignC2SPacket(sign.getPos(), true, strings[0], strings[1], strings[2], strings[3])
                );
                client.setScreen(null);
                clientPlayNetworkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3d.ofCenter(sign.getPos()),
                                Direction.UP,
                                sign.getPos(),
                                false
                        ),
                        0
                ));
                clientPlayNetworkHandler.sendPacket(
                        new UpdateSignC2SPacket(sign.getPos(), false, strings[4], strings[5], strings[6], strings[7])
                );
            }
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                client.setScreen(null);
            });
        }
    }

    public static String[] splitIntoChunks(String input, int chunkSize, int maxChunks) {
        String[] result = new String[maxChunks];
        int length = input.length();

        for (int i = 0; i < maxChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, length);
            if (start < length) {
                result[i] = input.substring(start, end);
            } else {
                result[i] = "";
            }
        }
        return result;
    }

    private static void loadWebImage(String imageUrl) {
        String urlPattern = "(https?://[\\w.-]+(?::\\d+)?(?:/[\\w.-]*)*)(\\[-?\\d+(?:[.,]\\d+)?(?:[;,:_+-]-?[a-zA-Z0-9$]+(?:[.:,_+-]\\d+)?)*?])?";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(imageUrl);
        if (!matcher.matches()) {
            screenshotIdentifier = null;
            return;
        }

        imageUrl = matcher.group(1);

        try {
            URL urlObj = new URI(imageUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestProperty("User-Agent", ScreenshotUploader.MOD_USER_AGENT);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    logger.info("Following redirect: {} -> {}", imageUrl, redirectUrl);
                    imageUrl = redirectUrl;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to resolve URL: {}", e.getMessage());
        }

        lastUrlHash = imageUrl.hashCode();
        String cacheFileName = "screenshots_cache/" + imageUrl.hashCode() + ".png";
        File cachedImage = new File(cacheFileName);

        boolean imageLoaded = false;

        if (cachedImage.exists()) {
            try {
                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                    Identifier textureId = Identifier.of("webimage", "temp/" + imageUrl.hashCode());
                    if (MinecraftClient.getInstance() != null) {
                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, loadedImage));
                        screenshotIdentifier = textureId;
                        imageLoaded = true;
                    }
                }
            } catch (IOException ignored) {
                screenshotIdentifier = null;
            }
        }

        if (!imageLoaded) {
            try {
                URI uri = new URI(imageUrl);
                if (!uri.isAbsolute()) {
                    screenshotIdentifier = null;
                    return;
                }
                URL url = uri.toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", ScreenshotUploader.MOD_USER_AGENT);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        BufferedImage bufferedImage = ImageIO.read(inputStream);

                        if (bufferedImage != null) {
                            File cacheFolder = new File("screenshots_cache");
                            if (!cacheFolder.exists()) {
                                if (cacheFolder.mkdirs()) {
                                    logger.info("Created web screenshots cache folder");
                                }
                            }

                            ImageIO.write(bufferedImage, "PNG", cachedImage);

                            try (NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false)) {
                                for (int y = 0; y < bufferedImage.getHeight(); y++) {
                                    for (int x = 0; x < bufferedImage.getWidth(); x++) {
                                        int rgb = bufferedImage.getRGB(x, y);
                                        int alpha = (rgb >> 24) & 0xFF;
                                        int red = (rgb >> 16) & 0xFF;
                                        int green = (rgb >> 8) & 0xFF;
                                        int blue = rgb & 0xFF;

                                        int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                                        nativeImage.setColorArgb(x, y, argb);
                                    }
                                }

                                try (InputStream fileInputStream = Files.newInputStream(cachedImage.toPath());
                                     NativeImage loadedImage = NativeImage.read(fileInputStream)) {
                                    Identifier textureId = Identifier.of("webimage", "temp/" + imageUrl.hashCode());
                                    if (MinecraftClient.getInstance() != null) {
                                        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, new NativeImageBackedTexture(String::new, loadedImage));
                                        screenshotIdentifier = textureId;
                                        imageLoaded = true;
                                    }
                                }
                            }
                        } else {
                            screenshotIdentifier = null;
                        }
                    }
                }
            } catch (URISyntaxException | IOException ignored) {
                screenshotIdentifier = null;
            }
        }

        if (!imageLoaded) {
            screenshotIdentifier = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (showTransformSettings) {
            int labelColor = 0xFFFFFFFF;
            int startX = (int) (width * 0.85) - 20;
            int startY = height / 4;
            int fieldHeight = 20;
            int spacing = 5;

            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_x"), startX - 15, startY + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_y"), startX - 15, startY + (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_z"), startX - 15, startY + 2 * (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_yaw"), startX - 15, startY + 3 * (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_pitch"), startX - 15, startY + 4 * (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_size"), startX - 15, startY + 5 * (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_light"), startX - 15, startY + 6 * (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_item"), startX - 15, startY + 7 * (fieldHeight + spacing) + 5, labelColor, false);
            context.drawText(textRenderer, Text.translatable("gui.screenshot_uploader.sign_edit.trans_settings"), (int) (width * 0.85) - 60, startY - 20, 0xFFFFFF00, false);
        }

        if (scheduledImageUrl != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTypingTime > TYPING_TIMEOUT_MS) {
                if (scheduledImageUrl.trim().length() < 5) {
                    screenshotIdentifier = null;
                    lastUrlHash = 0;
                } else if (lastUrlHash != scheduledImageUrl.hashCode()) {
                    loadWebImage(scheduledImageUrl);
                }
                scheduledImageUrl = null;
            }
        }

        if (screenshotIdentifier != null) {
            renderEnlargedImage(context);
        }
    }

    private void renderEnlargedImage(DrawContext context) {

        Identifier clickedImageId = screenshotIdentifier;

        int imageWidth = width / 4;
        int imageHeight = height / 4;
        if (client != null) {
            imageWidth = (int) ((double) client.getWindow().getWidth() / 8);
            imageHeight = (int) ((double) client.getWindow().getHeight() / 8);
        }


        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 3;

        int borderWidth = 5;
        context.fill(x - borderWidth, y - borderWidth, x + imageWidth + borderWidth, y + imageHeight + borderWidth, 0xFFFFFFFF);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, clickedImageId, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);


    }

    private void applyTransformSettings() {
        try {
            xOffset = parseFloatOrDefault(xOffsetField.getText(), 0f);
            yOffset = parseFloatOrDefault(yOffsetField.getText(), 0f);
            zOffset = parseFloatOrDefault(zOffsetField.getText(), 0f);
            yaw = parseFloatOrDefault(yawField.getText(), 0f);
            pitch = parseFloatOrDefault(pitchField.getText(), 0f);
            size = parseFloatOrDefault(sizeField.getText(), 0.75f);
            light = (int) parseFloatOrDefault(lightField.getText(), 15728880);
            item = itemField.getText().isEmpty() ? "$painting" : itemField.getText();

            String transformString = "[" + formatNumber(xOffset) + ";" +
                    formatNumber(yOffset) + ";" +
                    formatNumber(zOffset) + ";" +
                    formatNumber(yaw) + ";" +
                    formatNumber(pitch) + ";" +
                    formatNumber(size) + ";" +
                    light + ";" +
                    item + "]";

            String baseUrl = extractBaseUrl(urlField.getText());

            String newUrl = baseUrl + transformString;
            urlField.setText(newUrl);

            scheduleImageLoad(newUrl);

            showTransformSettings = false;
            updateTransformFieldsVisibility();
        } catch (Exception e) {
            logger.error("Failed to apply transformation settings", e);
        }
    }

    private String formatNumber(float number) {
        if (number == (int) number) {
            return Integer.toString((int) number);
        } else {
            return Float.toString(number);
        }
    }

    private String extractBaseUrl(String fullUrl) {
        String urlPattern = "(https?://[\\w.-]+(?::\\d+)?(?:/[\\w.-]*)*)(?:\\[.*])?";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(fullUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return fullUrl;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showTransformSettings) {
            if (this.xOffsetField.isFocused() && this.xOffsetField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.yOffsetField.isFocused() && this.yOffsetField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.zOffsetField.isFocused() && this.zOffsetField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.yawField.isFocused() && this.yawField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.pitchField.isFocused() && this.pitchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.sizeField.isFocused() && this.sizeField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.lightField.isFocused() && this.lightField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            } else if (this.itemField.isFocused() && this.itemField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (this.urlField.keyPressed(keyCode, scanCode, modifiers)) {
            scheduleImageLoad(urlField.getText());
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int keyCode) {
        if (showTransformSettings) {
            if (this.xOffsetField.isFocused() && this.xOffsetField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.yOffsetField.isFocused() && this.yOffsetField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.zOffsetField.isFocused() && this.zOffsetField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.yawField.isFocused() && this.yawField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.pitchField.isFocused() && this.pitchField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.sizeField.isFocused() && this.sizeField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.lightField.isFocused() && this.lightField.charTyped(chr, keyCode)) {
                return true;
            } else if (this.itemField.isFocused() && this.itemField.charTyped(chr, keyCode)) {
                return true;
            }
        }

        if (this.urlField.charTyped(chr, keyCode)) {
            scheduleImageLoad(urlField.getText());
            return true;
        }

        return super.charTyped(chr, keyCode);
    }

    private void scheduleImageLoad(String url) {
        lastTypingTime = System.currentTimeMillis();
        scheduledImageUrl = url;
    }

    private float parseFloatOrDefault(String value, float defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}

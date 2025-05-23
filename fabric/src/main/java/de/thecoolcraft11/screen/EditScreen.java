package de.thecoolcraft11.screen;


import com.mojang.blaze3d.systems.RenderSystem;
import de.thecoolcraft11.config.ConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;
import java.util.function.Consumer;

@SuppressWarnings({"ReassignedVariable", "SuspiciousNameCombination"})
public class EditScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(EditScreen.class);
    private final Screen parent;
    private final Path imagePath;
    private NativeImage image;
    private final Stack<NativeImage> editHistory = new Stack<>();

    private int cropStartX = -1, cropStartY = -1;
    private int cropEndX = -1, cropEndY = -1;
    private boolean isSelecting = false;

    private TextFieldWidget textInputField;
    private TextFieldWidget fontSizeWidget;
    private int textX = 0, textY = 0;
    private Identifier textureId;


    int previewX;
    int previewY;
    int previewSize;
    TextFieldWidget colorField;
    Consumer<NativeImage> onClose;


    public EditScreen(Screen parent, Path imagePath, NativeImage nativeImage, Consumer<NativeImage> onClose) {
        super(Text.translatable("gui.screenshot_uploader.editor.title"));
        this.parent = parent;
        if (imagePath == null) {
            allocateImage(nativeImage);
        }
        this.imagePath = imagePath;
        this.onClose = onClose;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 120;
        int buttonHeight = 20;
        int padding = 10;

        int imageX = width / 4;
        int imageY = height / 4;
        int imageWidth = width / 2;
        int imageHeight = height / 2;


        int startX = imageX;
        int startY = imageY - (buttonHeight + padding);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.crop"), button -> cropImage())
                .dimensions(startX, startY, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.rotate"), button -> rotateImage())
                .dimensions(startX + buttonWidth + padding, startY, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.sepia"), button -> applySepia())
                .dimensions(startX + 2 * (buttonWidth + padding), startY, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.back"), button -> {
                    if (client != null) client.setScreen(parent);
                    if (onClose != null) {
                        onClose.accept(image);
                    }
                }).dimensions(startX + 3 * (buttonWidth + padding), startY, buttonWidth, buttonHeight)
                .build());

        startX = imageX + imageWidth + padding;
        startY = imageY;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.undo"), button -> undo())
                .dimensions(startX, startY, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.brightness"), button -> adjustBrightnessContrast())
                .dimensions(startX, startY + buttonHeight + padding, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.grayscale"), button -> applyGrayscale())
                .dimensions(startX, startY + 2 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.hue_shift"), button -> applyHueShift())
                .dimensions(startX, startY + 3 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.pixel_averaging"), button -> applyPixelAveraging())
                .dimensions(startX, startY + 4 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.invert_colors"), button -> applyInvert())
                .dimensions(startX, startY + 5 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.posterize"), button -> applyPosterize())
                .dimensions(startX, startY + 6 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.vignette"), button -> applyVignette())
                .dimensions(startX, startY + 7 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.emboss"), button -> applyEmboss())
                .dimensions(startX, startY + 8 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.solarize"), button -> applySolarize())
                .dimensions(startX, startY + 9 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.noise"), button -> applyNoise())
                .dimensions(startX, startY + 10 * (buttonHeight + padding), buttonWidth, buttonHeight)
                .build());

        startX = imageX;
        startY = imageY + imageHeight + padding;
        textInputField = new TextFieldWidget(textRenderer, startX, startY, 200, buttonHeight, Text.translatable("gui.screenshot_uploader.editor.enter_text"));
        textInputField.setMaxLength(1024);
        fontSizeWidget = new TextFieldWidget(textRenderer, startX + 210, startY, 60, buttonHeight, Text.translatable("gui.screenshot_uploader.editor.font_size"));
        fontSizeWidget.setMaxLength(3);
        TextFieldWidget colorField = new TextFieldWidget(textRenderer, startX + 280, startY, 100, buttonHeight, Text.literal("#FFFFFF"));
        fontSizeWidget.setText("128");
        colorField.setText("#FFFFFF");
        colorField.setMaxLength(7);
        addDrawableChild(textInputField);
        addDrawableChild(fontSizeWidget);
        addDrawableChild(colorField);

        int previewX = startX + 390;
        int previewY = startY;


        startY += buttonHeight + padding;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.apply_text"), button -> {
                    String hexColor = colorField.getText();
                    if (!hexColor.contains("#")) {
                        hexColor = "#" + hexColor;
                    }
                    applyText(hexColor);
                })
                .dimensions(startX, startY, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.set_text_pos"), button -> setTextPosition())
                .dimensions(startX + buttonWidth + padding, startY, buttonWidth, buttonHeight)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.fill_image"), button -> {
                    String hexColor = colorField.getText();
                    if (!hexColor.contains("#")) {
                        hexColor = "#" + hexColor;
                    }
                    fillImage(hexColor);
                }).dimensions(startX + 2 * (buttonWidth + padding), startY, buttonWidth, buttonHeight)
                .build());

        startY += buttonHeight + padding;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.editor.blur"), button -> applyBlur())
                .dimensions(startX, startY, buttonWidth, buttonHeight)
                .build());

        loadImage();

        this.colorField = colorField;
        this.previewX = previewX;
        this.previewY = previewY;
        this.previewSize = buttonHeight;
    }


    private void applyText(String hexColor) {
        if (image == null || textInputField == null) return;

        saveStateForUndo();

        String text = textInputField.getText();
        if (text == null || text.isEmpty()) return;

        int fillColor = new Color(255, 255, 255).getRGB();
        if (hexColor != null && hexColor.matches("#[0-9A-Fa-f]{6}")) {
            int b = Integer.parseInt(hexColor.substring(1, 3), 16);
            int g = Integer.parseInt(hexColor.substring(3, 5), 16);
            int r = Integer.parseInt(hexColor.substring(5, 7), 16);
            fillColor = new Color(r, g, b).getRGB();
        }


        try {
            BufferedImage textImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = textImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2d.setFont(new Font("Minecraft", Font.PLAIN, Integer.parseInt(fontSizeWidget.getText())));
            g2d.setColor(new Color(fillColor));

            g2d.drawString(text, textX, textY);

            g2d.dispose();

            for (int y = 0; y < textImage.getHeight(); y++) {
                for (int x = 0; x < textImage.getWidth(); x++) {
                    int color = textImage.getRGB(x, y);

                    if ((color >> 24) != 0x00) {
                        image.setColor(x, y, color);
                    }
                }
            }

            saveImage();
            logger.info("Text '{}' applied to the image at position ({}, {})", text, textX, textY);
        } catch (Exception e) {
            logger.error("Failed to apply text to the image: {}", e.getMessage());
        }
    }


    private void setTextPosition() {
        if (cropStartX < 0 || cropStartY < 0 || cropEndX < 0 || cropEndY < 0 || image == null) return;

        int x1 = Math.max(0, Math.min(Math.min(cropStartX, cropEndX), image.getWidth() - 1));
        int y1 = Math.max(0, Math.min(Math.min(cropStartY, cropEndY), image.getHeight() - 1));

        textX = x1;
        textY = y1;

        logger.info("Text position set to: X={}, Y={}", textX, textY);
    }


    private void loadImage() {
        if (imagePath != null) {
            try {
                image = NativeImage.read(Files.newInputStream(imagePath));
            } catch (IOException e) {
                logger.error("Failed to load image for editing: {}", e.getMessage());
                if (client != null) client.setScreen(parent);
            }
        }
    }


    private void cropImage() {
        if (cropStartX < 0 || cropStartY < 0 || cropEndX < 0 || cropEndY < 0 || image == null) return;

        saveStateForUndo();

        int x1 = Math.max(0, Math.min(Math.min(cropStartX, cropEndX), image.getWidth() - 1));
        int y1 = Math.max(0, Math.min(Math.min(cropStartY, cropEndY), image.getHeight() - 1));
        int x2 = Math.max(0, Math.min(Math.max(cropStartX, cropEndX), image.getWidth()));
        int y2 = Math.max(0, Math.min(Math.max(cropStartY, cropEndY), image.getHeight()));

        int width = x2 - x1;
        int height = y2 - y1;

        NativeImage croppedImage = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                croppedImage.setColor(x, y, image.getColor(x1 + x, y1 + y));
            }
        }


        image.close();
        image = croppedImage;

        cropStartX = cropStartY = cropEndX = cropEndY = -1;

        saveImage();
    }

    private void fillImage(String hexColor) {
        if (cropStartX < 0 || cropStartY < 0 || cropEndX < 0 || cropEndY < 0 || image == null) return;

        saveStateForUndo();

        int fillColor = new Color(255, 255, 255).getRGB();
        if (hexColor != null && hexColor.matches("#[0-9A-Fa-f]{6}")) {
            int b = Integer.parseInt(hexColor.substring(1, 3), 16);
            int g = Integer.parseInt(hexColor.substring(3, 5), 16);
            int r = Integer.parseInt(hexColor.substring(5, 7), 16);
            fillColor = new Color(r, g, b).getRGB();
        }

        int x1 = Math.max(0, Math.min(Math.min(cropStartX, cropEndX), image.getWidth() - 1));
        int y1 = Math.max(0, Math.min(Math.min(cropStartY, cropEndY), image.getHeight() - 1));
        int x2 = Math.max(0, Math.min(Math.max(cropStartX, cropEndX), image.getWidth()));
        int y2 = Math.max(0, Math.min(Math.max(cropStartY, cropEndY), image.getHeight()));

        int width = x2 - x1;
        int height = y2 - y1;

        if (width <= 0 || height <= 0) {
            logger.warn("Invalid fill area: width={}, height={}", width, height);
            return;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setColor(x1 + x, y1 + y, fillColor);
            }
        }

        cropStartX = cropStartY = cropEndX = cropEndY = -1;

        saveImage();
    }


    private void rotateImage() {
        if (image == null) return;

        saveStateForUndo();

        NativeImage rotatedImage = new NativeImage(image.getHeight(), image.getWidth(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                rotatedImage.setColor(image.getHeight() - y - 1, x, image.getColor(x, y));
            }
        }

        image.close();
        image = rotatedImage;
        saveImage();
    }

    private void applySepia() {
        if (image == null) return;

        saveStateForUndo();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int newColor = getNewColor(color);
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private static int getNewColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int tr = (int) (0.393 * r + 0.769 * g + 0.189 * b);
        int tg = (int) (0.349 * r + 0.686 * g + 0.168 * b);
        int tb = (int) (0.272 * r + 0.534 * g + 0.131 * b);

        r = Math.min(tr, 255);
        g = Math.min(tg, 255);
        b = Math.min(tb, 255);

        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private void saveImage() {
        if (imagePath != null) {
            try {
                image.writeTo(imagePath);
                logger.info("Image saved: {}", imagePath);
                textureId = null;
                if (client != null) {
                    client.getTextureManager().destroyTexture(textureId);
                }
            } catch (IOException e) {
                logger.error("Failed to save edited image: {}", e.getMessage());
            }
        } else {
            textureId = null;
            if (client != null) {
                client.getTextureManager().destroyTexture(textureId);
            }
        }
    }

    private void saveStateForUndo() {
        if (image != null) {
            NativeImage imageCopy = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    imageCopy.setColor(x, y, image.getColor(x, y));
                }
            }
            editHistory.push(imageCopy);
        }
    }

    private void allocateImage(NativeImage newImage) {
        if (newImage != null) {
            NativeImage imageCopy = new NativeImage(newImage.getWidth(), newImage.getHeight(), false);
            for (int y = 0; y < newImage.getHeight(); y++) {
                for (int x = 0; x < newImage.getWidth(); x++) {
                    imageCopy.setColor(x, y, newImage.getColor(x, y));
                }
            }
            image = imageCopy;
        }
    }


    private void undo() {
        if (!editHistory.isEmpty()) {
            if (image != null) {
                image.close();
            }
            image = editHistory.pop();

            saveImage();
        }
    }

    private void adjustBrightnessContrast() {
        if (image == null) return;

        saveStateForUndo();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                r = (int) (((r - 128) * ConfigManager.getClientConfig().contrastMultiplier) + 128);
                g = (int) (((g - 128) * ConfigManager.getClientConfig().contrastMultiplier) + 128);
                b = (int) (((b - 128) * ConfigManager.getClientConfig().contrastMultiplier) + 128);

                r = (int) (r + (float) ConfigManager.getClientConfig().brightnessAdjustment);
                g = (int) (g + (float) ConfigManager.getClientConfig().brightnessAdjustment);
                b = (int) (b + (float) ConfigManager.getClientConfig().brightnessAdjustment);

                r = Math.min(Math.max(r, 0), 255);
                g = Math.min(Math.max(g, 0), 255);
                b = Math.min(Math.max(b, 0), 255);

                int newColor = (color & 0xFF000000) | (r << 16) | (g << 8) | b;
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private void applyGrayscale() {
        if (image == null) return;

        saveStateForUndo();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                int gray = (r + g + b) / 3;

                int newColor = (color & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private void applyHueShift() {
        if (image == null) return;

        saveStateForUndo();

        float hueShiftAmount = ConfigManager.getClientConfig().hueShiftAmount;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int a = (color >> 24) & 0xFF;
                if (a == 0) a = 255;

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                int newColor = getNewHueColor(r, g, b, a, hueShiftAmount);

                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private static int getNewHueColor(int r, int g, int b, int a, float hueShift) {
        float[] hsb = Color.RGBtoHSB(r, g, b, null);

        float newHue = (hsb[0] + hueShift) % 1.0f;
        if (newHue < 0) newHue += 1.0f;

        int rgb = Color.HSBtoRGB(newHue, hsb[1], hsb[2]);

        return (a << 24) | (rgb & 0x00FFFFFF);
    }


    private void applyBlur() {
        if (image == null || cropStartX < 0 || cropStartY < 0 || cropEndX < 0 || cropEndY < 0) return;

        saveStateForUndo();

        int x1 = Math.max(0, Math.min(Math.min(cropStartX, cropEndX), image.getWidth() - 1));
        int y1 = Math.max(0, Math.min(Math.min(cropStartY, cropEndY), image.getHeight() - 1));
        int x2 = Math.max(0, Math.min(Math.max(cropStartX, cropEndX), image.getWidth()));
        int y2 = Math.max(0, Math.min(Math.max(cropStartY, cropEndY), image.getHeight()));

        if (x2 <= x1 || y2 <= y1) {
            logger.warn("Invalid blur area: width={}, height={}", x2 - x1, y2 - y1);
            return;
        }

        int kernelSize = ConfigManager.getClientConfig().blurKernelSize;
        int iterations = ConfigManager.getClientConfig().blurIterations;

        for (int i = 0; i < iterations; i++) {
            NativeImage blurredImage = new NativeImage(image.getWidth(), image.getHeight(), false);

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    blurredImage.setColor(x, y, image.getColor(x, y));
                }
            }

            for (int y = y1; y < y2; y++) {
                for (int x = x1; x < x2; x++) {
                    int colorSumR = 0, colorSumG = 0, colorSumB = 0;
                    int count = 0;

                    for (int dy = -kernelSize / 2; dy <= kernelSize / 2; dy++) {
                        for (int dx = -kernelSize / 2; dx <= kernelSize / 2; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;

                            if (nx >= 0 && nx < image.getWidth() && ny >= 0 && ny < image.getHeight()) {
                                int color = image.getColor(nx, ny);
                                colorSumR += (color >> 16) & 0xFF;
                                colorSumG += (color >> 8) & 0xFF;
                                colorSumB += color & 0xFF;
                                count++;
                            }
                        }
                    }

                    int avgR = colorSumR / count;
                    int avgG = colorSumG / count;
                    int avgB = colorSumB / count;

                    int avgColor = (0xFF << 24) | (avgR << 16) | (avgG << 8) | avgB;
                    blurredImage.setColor(x, y, avgColor);
                }
            }

            image.close();
            image = blurredImage;
        }

        cropStartX = cropStartY = cropEndX = cropEndY = -1;

        saveImage();


        cropStartX = cropStartY = cropEndX = cropEndY = -1;

        saveImage();
    }


    private void applyPixelAveraging() {
        if (image == null) return;

        saveStateForUndo();

        NativeImage blurredImage = new NativeImage(image.getWidth(), image.getHeight(), false);

        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                int colorSum = 0;
                int count = 0;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        colorSum += image.getColor(x + dx, y + dy);
                        count++;
                    }
                }

                int avgColor = colorSum / count;
                blurredImage.setColor(x, y, avgColor);
            }
        }

        image.close();
        image = blurredImage;

        saveImage();
    }

    private void applyInvert() {
        if (image == null) return;

        saveStateForUndo();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int r = 255 - ((color >> 16) & 0xFF);
                int g = 255 - ((color >> 8) & 0xFF);
                int b = 255 - (color & 0xFF);

                int newColor = (color & 0xFF000000) | (r << 16) | (g << 8) | b;
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private void applyPosterize() {
        if (image == null) return;

        saveStateForUndo();

        int step = 255 / (ConfigManager.getClientConfig().posterizeLevels - 1);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                r = (r / step) * step;
                g = (g / step) * step;
                b = (b / step) * step;

                int newColor = (color & 0xFF000000) | (r << 16) | (g << 8) | b;
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private void applyVignette() {
        if (image == null) return;

        saveStateForUndo();

        int centerX = image.getWidth() / 2;
        int centerY = image.getHeight() / 2;
        int maxDistance = (int) Math.sqrt(centerX * centerX + centerY * centerY);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int dx = x - centerX;
                int dy = y - centerY;
                int distance = (int) Math.sqrt(dx * dx + dy * dy);
                int newColor = getNewVignetteColor(distance, (float) maxDistance, color);
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private static int getNewVignetteColor(int distance, float maxDistance, int color) {
        float intensity = ConfigManager.getClientConfig().vignetteIntensity - Math.min(distance / maxDistance, 1.0f);

        int r = (int) (((color >> 16) & 0xFF) * intensity);
        int g = (int) (((color >> 8) & 0xFF) * intensity);
        int b = (int) ((color & 0xFF) * intensity);

        r = Math.min(r, 255);
        g = Math.min(g, 255);
        b = Math.min(b, 255);

        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private void applyEmboss() {
        if (image == null) return;

        saveStateForUndo();

        NativeImage embossedImage = new NativeImage(image.getWidth(), image.getHeight(), false);

        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                int color1 = image.getColor(x, y);
                int color2 = image.getColor(x + 1, y + 1);

                int newColor = getNewColor(color1, color2);
                embossedImage.setColor(x, y, newColor);
            }
        }

        image.close();
        image = embossedImage;

        saveImage();
    }

    private static int getNewColor(int color1, int color2) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = Math.min(Math.max(r1 - r2 + ConfigManager.getClientConfig().embossEffect, 0), 255);
        int g = Math.min(Math.max(g1 - g2 + ConfigManager.getClientConfig().embossEffect, 0), 255);
        int b = Math.min(Math.max(b1 - b2 + ConfigManager.getClientConfig().embossEffect, 0), 255);

        return (color1 & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private void applySolarize() {
        if (image == null) return;

        saveStateForUndo();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                if (r > ConfigManager.getClientConfig().solarizeThreshold) r = 255 - r;
                if (g > ConfigManager.getClientConfig().solarizeThreshold) g = 255 - g;
                if (b > ConfigManager.getClientConfig().solarizeThreshold) b = 255 - b;

                int newColor = (color & 0xFF000000) | (r << 16) | (g << 8) | b;
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }

    private void applyNoise() {
        if (image == null) return;

        saveStateForUndo();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getColor(x, y);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                r += (int) (Math.random() * 2 * ConfigManager.getClientConfig().noiseIntensity - ConfigManager.getClientConfig().noiseIntensity);
                g += (int) (Math.random() * 2 * ConfigManager.getClientConfig().noiseIntensity - ConfigManager.getClientConfig().noiseIntensity);
                b += (int) (Math.random() * 2 * ConfigManager.getClientConfig().noiseIntensity - ConfigManager.getClientConfig().noiseIntensity);

                r = Math.min(Math.max(r, 0), 255);
                g = Math.min(Math.max(g, 0), 255);
                b = Math.min(Math.max(b, 0), 255);

                int newColor = (color & 0xFF000000) | (r << 16) | (g << 8) | b;
                image.setColor(x, y, newColor);
            }
        }

        saveImage();
    }


    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        textInputField.render(context, mouseX, mouseY, delta);

        if (textureId != null) {
            RenderSystem.setShaderTexture(0, textureId);
            context.drawTexture(textureId, width / 4, height / 4, 0, 0, width / 2, height / 2, width / 2, height / 2);
        } else {
            if (image != null) {
                if (client != null) {
                    textureId = client.getTextureManager().registerDynamicTexture("edit_image", new NativeImageBackedTexture(image));
                }
            }
        }

        if (isSelecting || (cropStartX >= 0 && cropStartY >= 0 && cropEndX >= 0 && cropEndY >= 0)) {
            float scaleX = 1;
            float scaleY = 1;
            if (image != null) {
                scaleX = (float) image.getWidth() / ((float) width / 2);
                scaleY = (float) image.getHeight() / ((float) height / 2);
            }

            int x1 = (int) (Math.min(cropStartX, cropEndX) / scaleX + ((float) width / 4));
            int y1 = (int) (Math.min(cropStartY, cropEndY) / scaleY + ((float) height / 4));
            int x2 = (int) (Math.max(cropStartX, cropEndX) / scaleX + ((float) width / 4));
            int y2 = (int) (Math.max(cropStartY, cropEndY) / scaleY + ((float) height / 4));
            context.fill(x1, y1, x2, y2, 0x80FFFFFF);
        }
        if (colorField != null) {
            String hexColor = colorField.getText();
            if (hexColor != null) {
                if (!hexColor.contains("#")) {
                    hexColor = "#" + hexColor;
                }
                if (hexColor.matches("#[0-9A-Fa-f]{6}")) {
                    int r = Integer.parseInt(hexColor.substring(1, 3), 16);
                    int g = Integer.parseInt(hexColor.substring(3, 5), 16);
                    int b = Integer.parseInt(hexColor.substring(5, 7), 16);
                    int color = new Color(r, g, b).getRGB();

                    context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, color);
                } else {
                    context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFFFFFFFF);
                }
            }
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Element buttonWidget : this.children()) {
                if (buttonWidget != null && buttonWidget.isMouseOver(mouseX, mouseY)) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }
            }
            isSelecting = true;

            if (image != null) {
                float scaleX = (float) image.getWidth() / ((float) width / 2);
                float scaleY = (float) image.getHeight() / ((float) height / 2);

                cropStartX = (int) ((mouseX - ((double) width / 4)) * scaleX);
                cropStartY = (int) ((mouseY - ((double) height / 4)) * scaleY);

                cropStartX = Math.max(0, Math.min(cropStartX, image.getWidth()));
                cropStartY = Math.max(0, Math.min(cropStartY, image.getHeight()));
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isSelecting) {
            if (image != null) {
                float scaleX = (float) image.getWidth() / ((float) width / 2);
                float scaleY = (float) image.getHeight() / ((float) height / 2);

                cropEndX = (int) ((mouseX - ((double) width / 4)) * scaleX);
                cropEndY = (int) ((mouseY - ((double) height / 4)) * scaleY);

                cropEndX = Math.max(0, Math.min(cropEndX, image.getWidth()));
                cropEndY = Math.max(0, Math.min(cropEndY, image.getHeight()));
            }
            isSelecting = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isSelecting && button == 0 && image != null) {
            float scaleX = (float) image.getWidth() / ((float) width / 2);
            float scaleY = (float) image.getHeight() / ((float) height / 2);

            cropEndX = (int) ((mouseX - ((double) width / 4)) * scaleX);
            cropEndY = (int) ((mouseY - ((double) height / 4)) * scaleY);

            cropEndX = Math.max(0, Math.min(cropEndX, image.getWidth()));
            cropEndY = Math.max(0, Math.min(cropEndY, image.getHeight()));
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }


    @Override
    public void close() {
        if (onClose != null) {
            onClose.accept(image);
        }
        if (image != null) image.close();
        if (client != null) client.setScreen(parent);

    }
}
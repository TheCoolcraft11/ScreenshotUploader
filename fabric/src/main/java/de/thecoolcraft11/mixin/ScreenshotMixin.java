package de.thecoolcraft11.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.event.KeyInputHandler;
import de.thecoolcraft11.screen.ConfirmScreenshotsScreen;
import de.thecoolcraft11.screen.EditScreen;
import de.thecoolcraft11.screen.GalleryScreen;
import de.thecoolcraft11.util.ErrorMessages;
import de.thecoolcraft11.util.ReceivePackets;
import de.thecoolcraft11.util.ScreenshotDataHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static de.thecoolcraft11.util.ScreenshotUploadHelper.uploadScreenshot;


@Mixin(ScreenshotRecorder.class)
@Environment(EnvType.CLIENT)
public abstract class ScreenshotMixin {

    @Shadow
    private static File getScreenshotFilename(File directory) {
        return null;
    }

    @Unique
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotMixin.class);


    @Inject(at = @At(value = "HEAD"), method = "method_22691")
    private static void screenshotCaptured(NativeImage nativeImage_1, File file_1, Consumer<Text> consumer_1, CallbackInfo ci) {
        if (ConfigManager.getClientConfig().enableMod) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (KeyInputHandler.editKey.wasPressed()) {
                client.send(() -> client.setScreen(new EditScreen(null, null, nativeImage_1, (image) -> {
                    if (image != null) {
                        NativeImage imageCopy = new NativeImage(image.getWidth(), image.getHeight(), false);
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                imageCopy.setColorArgb(x, y, image.getColorArgb(x, y));
                            }
                        }

                        saveEditedFile(file_1.getAbsolutePath(), imageCopy);
                        initializeUpload(imageCopy);
                    }
                })));
            } else {
                initializeUpload(nativeImage_1);
            }

            GalleryScreen.addNewScreenshot(file_1.getAbsoluteFile().toPath());
        }
    }

    @Unique
    private static void saveEditedFile(String absolutePath, NativeImage imageCopy) {
        StringBuilder template = new StringBuilder(ConfigManager.getClientConfig().editImageFilePath);

        template.replace(template.indexOf("{fileName}"), "{fileName}".length(), absolutePath);
        File outputFile = new File(template.toString());

        try {
            imageCopy.writeTo(outputFile);
            logger.info("Image saved to {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to save the image to disk", e);
        }
    }

    @Unique
    private static void initializeUpload(NativeImage image) {
        MinecraftClient client = MinecraftClient.getInstance();
        String jsonData = ScreenshotDataHelper.getJSONData(client);


        String filename = Objects.requireNonNull(getScreenshotFilename(Paths.get(client.runDirectory.getName(), "screenshots").toFile())).getName();

        if (ConfigManager.getClientConfig().saveJsonData) {
            File jsonFile = new File(client.runDirectory, "screenshots/" + filename.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));
            try {
                boolean wasCreated = jsonFile.createNewFile();
                if (!wasCreated) {
                    logger.info("JSON file already exists: {}", jsonFile.getAbsolutePath());
                }
                Files.writeString(jsonFile.toPath(), jsonData);
            } catch (Exception e) {
                logger.error("Failed to save the JSON data to disk", e);
            }
        }

        if (ConfigManager.getClientConfig().requireNoHud && !client.options.hudHidden ||
                ConfigManager.getClientConfig().limitToServer &&
                        !Objects.equals(Objects.requireNonNull(client.getCurrentServerEntry()).address, ConfigManager.getClientConfig().limitedServerAddr)) {
            return;
        }

        if (ConfigManager.getClientConfig().askBeforeUpload) {
            client.send(() -> client.setScreen(new ConfirmScreenshotsScreen(client.currentScreen, image, jsonData, filename, (doSave, doUpload, image1, jsonData1) -> {
                if (!doSave) {
                    client.setScreen(client.currentScreen);
                    try {
                        File screenshotFile = new File(client.runDirectory, "screenshots/" + filename);
                        boolean wasDeleted = screenshotFile.delete();
                        if (wasDeleted) {
                            client.inGameHud.getChatHud().addMessage(Text.translatable("message.screenshot_uploader.screenshot_deleted", filename).styled(style -> style.withColor(Formatting.RED)));
                        } else {
                            logger.error("Failed to delete the screenshot file: {}", screenshotFile.getAbsolutePath());
                        }

                        Path screenshotPath = Path.of(screenshotFile.getAbsolutePath());
                        if(GalleryScreen.getNewScreenshots().contains(screenshotPath))
                        {
                            GalleryScreen.removeNewScreenshot(screenshotPath);
                        }

                    } catch (Exception e) {
                        logger.error("Failed to delete the image file", e);
                    }
                    return;
                }
                if (image1 != null) {
                    if (doUpload) {
                        NativeImage imageCopy = new NativeImage(image1.getWidth(), image1.getHeight(), false);
                        for (int y = 0; y < image1.getHeight(); y++) {
                            for (int x = 0; x < image1.getWidth(); x++) {
                                imageCopy.setColorArgb(x, y, image1.getColorArgb(x, y));
                            }
                        }
                        startUpload(client, imageCopy, jsonData1);

                    } else {
                        client.setScreen(client.currentScreen);
                    }
                }
            })));

        }

        if (ConfigManager.getClientConfig().askBeforeUpload) return;

        startUpload(client, image, jsonData);
    }

    @Unique
    private static void startUpload(MinecraftClient client, NativeImage image, String jsonData) {
        new Thread(() -> {
            List<String> targets = new ArrayList<>();
            if (ConfigManager.getClientConfig().uploadScreenshotsToUrl) {
                for (Map<String, String> value : ConfigManager.getClientConfig().upload_urls.values()) {
                    targets.add(value.get("upload"));
                }

            }
            if (ReceivePackets.serverSiteAddress != null) {
                targets.add(ReceivePackets.serverSiteAddress);
            }
            StringBuilder messageBuilder = getStringBuilder(targets);

            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.screenshot_uploader.uploading_to", Text.literal(messageBuilder.toString()).styled(style -> style.withColor(Formatting.AQUA))));

            List<JsonObject> uploadResults = uploadScreenshot(image, jsonData, targets);

            client.execute(() ->
                    uploadResults.forEach(uploadResult -> {
                        String statusMessage = uploadResult.get("status").getAsString();

                        if ("success".equals(statusMessage)) {
                            JsonObject responseBody = null;
                            try {
                                responseBody = JsonParser.parseString(uploadResult.get("responseBody").getAsString()).getAsJsonObject();
                            } catch (Exception e) {
                                logger.error("Failed to parse responseBody", e);
                            }

                            String baseMessage = "message.screenshot_uploader.upload_success";
                            Text clickableLink = Text.empty();
                            Text clickableLink2 = Text.empty();
                            Text clickableLink3 = Text.empty();
                            Text clickableLink4 = Text.empty();
                            Text clickableLink5 = Text.empty();

                            if (responseBody != null) {
                                String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
                                String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;


                                if (screenshotUrl != null && galleryUrl != null) {
                                    clickableLink = Text.translatable("message.screenshot_uploader.open_screenshot")
                                            .styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/open-screenshot \"" + screenshotUrl + "\""))
                                                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.see_screenshot"))).withColor(Formatting.AQUA));
                                }
                                if (screenshotUrl != null) {
                                    clickableLink2 = Text.translatable("message.screenshot_uploader.open_link")
                                            .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(screenshotUrl)))
                                                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.open_website"))).withColor(Formatting.BLUE));
                                }

                                if (galleryUrl != null) {
                                    clickableLink3 = Text.translatable("message.screenshot_uploader.open_all")
                                            .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(galleryUrl)))
                                                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.see_screenshots"))).withColor(Formatting.YELLOW));
                                }
                                if (screenshotUrl != null) {
                                    clickableLink4 = Text.translatable("message.screenshot_uploader.copy")
                                            .styled(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(screenshotUrl))
                                                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.copy_url"))).withColor(Formatting.GRAY));
                                }
                                if (screenshotUrl != null) {
                                    clickableLink5 = Text.translatable("message.screenshot_uploader.share")
                                            .styled(style -> style.withClickEvent(new ClickEvent.SuggestCommand(screenshotUrl))
                                                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.share_screenshot"))).withColor(Formatting.DARK_GREEN));
                                }

                                if (screenshotUrl == null && galleryUrl == null) {
                                    baseMessage = "message.screenshot_uploader_no_return_url";
                                }

                                Text finalMessage = Text.translatable(baseMessage, clickableLink, clickableLink2, clickableLink3, clickableLink4, clickableLink5);

                                client.inGameHud.getChatHud().addMessage(finalMessage);
                            }
                        } else {
                            String errorMessage = uploadResult.has("message") ? uploadResult.get("message").getAsString() : "Unknown error";
                            Text errorText = Text.translatable("message.screenshot_uploader.upload_failed", errorMessage.split(":")[0])
                                    .styled(style -> style.withHoverEvent(new HoverEvent.ShowText(Text.translatable(ErrorMessages.getErrorDescription(errorMessage.split(":")[0])))));

                            client.inGameHud.getChatHud().addMessage(errorText);
                        }
                    })
            );
        }).start();
    }

    @Unique
    private static @NotNull StringBuilder getStringBuilder(List<String> targets) {
        StringBuilder messageBuilder = new StringBuilder();

        for (int i = 0; i < targets.size(); i++) {
            String target = targets.get(i);
            String targetText = target.equals("mcserver://this") || target.equals("mcserver://chunked") ? "This Server" : target;
            messageBuilder.append(targetText);

            if (i < targets.size() - 1) {
                messageBuilder.append(", ");
            }
        }
        return messageBuilder;
    }
}



package de.thecoolcraft11.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.thecoolcraft11.ScreenshotUploaderServer.getServerIp;


public class ReceivePackets {
    private static final Logger logger = LoggerFactory.getLogger(ReceivePackets.class);
    public static String serverSiteAddress = null;
    public static String gallerySiteAddress = null;
    public static String homeSiteAddress = null;
    public static String commentSiteAddress = null;
    public static String deletionSiteAddress = null;
    public static String tagSiteAddress = null;
    public static boolean allowDelete = false;
    public static boolean allowDeleteOwn = false;

    public static void receiveAddress(MinecraftClient client, String message) {
        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
        String uploadAddr = jsonObject.get("upload").getAsString();
        String homeAddr = jsonObject.get("home").getAsString();
        String uploadDir = uploadAddr.equals("mcserver://this") || uploadAddr.equals("mcserver://chunked") ? "This Server" : uploadAddr;
        client.inGameHud.getChatHud().addMessage(Text.translatable("message.screenshot_uploader.next_upload", uploadDir).styled(style ->
                style.withClickEvent(new ClickEvent.OpenUrl(URI.create(homeAddr)))
                        .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.next_upload_description", Text.literal(homeAddr).styled(style2 -> style2.withColor(Formatting.AQUA)), Text.keybind("key.screenshot_uploader.gallery"))))))
        ;
        serverSiteAddress = uploadAddr;

        gallerySiteAddress = jsonObject.get("gallery").getAsString();

        homeSiteAddress = homeAddr;

        commentSiteAddress = jsonObject.has("comment") && !jsonObject.get("comment").isJsonNull() ? jsonObject.get("comment").getAsString() : null;

        deletionSiteAddress = jsonObject.has("delete") && !jsonObject.get("delete").isJsonNull() ? jsonObject.get("delete").getAsString() : null;

        tagSiteAddress = jsonObject.has("tag") && !jsonObject.get("tag").isJsonNull() ? jsonObject.get("tag").getAsString() : null;

        if (jsonObject.has("allowDelete")) allowDelete = jsonObject.get("allowDelete").getAsBoolean();
        if (jsonObject.has("allowDeleteOwn")) allowDeleteOwn = jsonObject.get("allowDeleteOwn").getAsBoolean();

    }

    public static void receiveScreenshotRes(JsonObject responseBody, MinecraftClient client) {
        String statusMessage = responseBody.get("status").getAsString();
        if ("success".equals(statusMessage)) {

            String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
            String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;


            String baseMessage = "message.screenshot_uploader.upload_success";
            Text clickableLink = Text.empty();
            Text clickableLink2 = Text.empty();
            Text clickableLink3 = Text.empty();
            Text clickableLink4 = Text.empty();
            Text clickableLink5 = Text.empty();
            Text clickableLink6 = Text.empty();


            if (screenshotUrl != null && galleryUrl != null) {
                clickableLink = Text.translatable("message.screenshot_uploader.open_screenshot")
                        .styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/open-screenshot \"" + screenshotUrl + "\""))
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.see_screenshot"))).withColor(Formatting.AQUA));
            }
            if (screenshotUrl != null) {
                if (getGalleryByHome(homeSiteAddress) != null) {
                    clickableLink2 = Text.translatable("message.screenshot_uploader.open_gallery")
                            .styled(style -> style.withClickEvent(new ClickEvent.RunCommand("/open-gallery \"" + getGalleryByHome(homeSiteAddress) + "\" \"" + screenshotUrl + "\""))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.open_game_gallery"))).withColor(Formatting.RED));
                }
            }
            if (screenshotUrl != null) {
                clickableLink3 = Text.translatable("message.screenshot_uploader.open_link")
                        .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(screenshotUrl)))
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.open_website"))).withColor(Formatting.BLUE));
            }
            if (galleryUrl != null) {
                clickableLink4 = Text.translatable("message.screenshot_uploader.open_all")
                        .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(galleryUrl)))
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.see_screenshots"))).withColor(Formatting.YELLOW));
            }
            if (screenshotUrl != null) {
                clickableLink5 = Text.translatable("message.screenshot_uploader.copy")
                        .styled(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(screenshotUrl))
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.copy_url"))).withColor(Formatting.GREEN));
            }
            if (screenshotUrl != null) {
                clickableLink6 = Text.translatable("message.screenshot_uploader.share")
                        .styled(style -> style.withClickEvent(new ClickEvent.SuggestCommand(screenshotUrl))
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("message.screenshot_uploader.share_screenshot"))).withColor(Formatting.DARK_GREEN));
            }

            if (screenshotUrl == null && galleryUrl == null) {
                baseMessage = "message.screenshot_uploader_no_return_url";
            }

            Text finalMessage = Text.translatable(baseMessage, clickableLink, clickableLink2, clickableLink3, clickableLink4, clickableLink5, clickableLink6);

            client.inGameHud.getChatHud().addMessage(finalMessage);
        }
    }

    public static String getGalleryByHome(String homeUrl) {
        for (Map<String, String> value : ConfigManager.getClientConfig().upload_urls.values()) {
            if (!value.containsKey("home")) continue;
            try {
                URI savedEntry = new URI(value.get("home"));
                URI messageEntry = new URI(Objects.requireNonNull(homeUrl));

                if (savedEntry.getHost().equals(messageEntry.getHost())) {
                    return value.get("gallery");
                }
            } catch (URISyntaxException ignored) {
            }
        }
        return null;
    }

    public static void handleReceivedScreenshot(byte[] screenshotData, String jsonData, ServerPlayerEntity player) {
        try {
            String playerName = player.getName().getString();
            String baseFileName = "screenshot-" + playerName + "_" + System.currentTimeMillis();
            String screenshotFileName = baseFileName + ".png";
            String outputFilePath = "screenshotUploader/screenshots/" + formatFileName(screenshotFileName, playerName);
            File outputFile = new File(outputFilePath);
            Files.write(outputFile.toPath(), screenshotData);
            logger.info("Screenshot received from {} and saved as {}", player.getName().getString(), screenshotFileName);
            String jsonFileName = "screenshotUploader/screenshots/" + baseFileName + ".json";
            try (FileWriter fileWriter = new FileWriter(jsonFileName)) {
                fileWriter.write(jsonData);
                logger.info("JSON data saved as {}", jsonFileName);
            } catch (IOException e) {
                logger.error("Error saving JSON data: {}", e.getMessage());
            }


            String urlString = getServerIp();
            if (!urlString.matches("^https?://.*")) {
                urlString = "http://" + urlString;
            }

            if (!urlString.matches(".*:\\d+.*")) {
                urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + ConfigManager.getServerConfig().port);
            }

            BufferedImage image = ImageIO.read(new File(outputFile.getAbsolutePath()));

            JsonObject jsonObject = getJsonObject(urlString, outputFilePath);
            try {
                JsonObject webhookJson = JsonParser.parseString(jsonData).getAsJsonObject();
                if (ConfigManager.getServerConfig().sendDiscordWebhook) {
                    DiscordWebhook.sendMessage(
                            ConfigManager.getServerConfig().webhookUrl,
                            playerName,
                            image,
                            webhookJson.get("server_address").getAsString(),
                            webhookJson.get("dimension").getAsString(),
                            webhookJson.get("coordinates").getAsString(),
                            webhookJson.get("facing_direction").getAsString(),
                            webhookJson.get("biome").getAsString(),
                            jsonObject.get("url").getAsString(),
                            webhookJson.get("world_info").getAsString()
                    );
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ServerPlayNetworking.send(player, new ScreenshotResponsePayload(jsonObject.toString()));
        } catch (IOException e) {
            logger.error("Error handling uploaded screenshot: {}", e.getMessage());
        }
    }


    private static @NotNull JsonObject getJsonObject(String websiteAddress, String outputFilePath) {

        String screenshotUrl = websiteAddress + outputFilePath.replace("screenshotUploader", "");
        String galleryUrl = websiteAddress + "/";

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("message", "File uploaded successfully");
        jsonObject.addProperty("url", screenshotUrl);
        jsonObject.addProperty("gallery", galleryUrl);
        jsonObject.addProperty("status", "success");
        return jsonObject;
    }

    private static String formatFileName(String originalFilename, String username) {

        String regex = "(screenshot)(\\d+)(\\.png)$";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(originalFilename);

        if (matcher.find()) {
            String baseName = matcher.group(1);
            String digits = matcher.group(2);

            return baseName + "-" + username + "_" + digits + ".jpg";
        } else {
            return originalFilename;
        }
    }

}

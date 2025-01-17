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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.thecoolcraft11.ScreenshotUploaderServer.getServerIp;


public class ReceivePackets {
    private static final Logger logger = LoggerFactory.getLogger(ReceivePackets.class);
    public static String serverSiteAddress = null;
    public static String gallerySiteAddress = null;
    public static String homeSiteAddress = null;

    public static void receiveAddress(MinecraftClient client, String message) {
        JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();
        String uploadAddr = jsonObject.get("upload").getAsString();
        String homeAddr = jsonObject.get("home").getAsString();
        String uploadDir = uploadAddr.equals("mcserver://this") ? "This Server" : uploadAddr;
        client.inGameHud.getChatHud().addMessage(Text.literal("Next screenshots will be uploaded to ").append(Text.literal("[" + uploadDir + "]").styled(style ->
                style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, homeAddr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Your screenshots will be uploaded to this server, you can see those screenshots at [" + homeAddr + "] or in your ingame Gallery(press [").append(Text.keybind("key.screenshot_uploader.gallery")).append("])"))).withColor(Formatting.AQUA))));
        serverSiteAddress = uploadAddr;

        gallerySiteAddress = jsonObject.get("gallery").getAsString();

        homeSiteAddress = homeAddr;
    }

    public static void receiveScreenshotRes(JsonObject responseBody, MinecraftClient client) {
        String statusMessage = responseBody.get("status").getAsString();
        if ("success".equals(statusMessage)) {

            String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
            String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;

            Text fullMessage = Text.literal("Screenshot uploaded successfully! ");


            if (screenshotUrl != null) {
                String linkText = "[OPEN]";
                Text clickableLink = Text.literal(linkText)
                        .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, screenshotUrl))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To See Screenshot"))).withColor(Formatting.AQUA));
                fullMessage = fullMessage.copy().append(clickableLink);
            }

            if (galleryUrl != null) {
                String galleryText = "[ALL]";
                Text clickableLink2 = Text.literal(galleryText)
                        .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, galleryUrl))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To See All Screenshots"))).withColor(Formatting.YELLOW));
                fullMessage = fullMessage.copy().append(" ").append(clickableLink2);
            }

            if (screenshotUrl == null && galleryUrl == null) {
                fullMessage = Text.literal("Screenshot upload failed: The server did not return valid URLs.");
            }

            client.inGameHud.getChatHud().addMessage(fullMessage);
        } else {
            String errorMessage = responseBody.has("message") ? responseBody.get("message").getAsString() : "Unknown error";
            Text errorText = Text.literal("Screenshot upload failed: " + errorMessage);


            client.inGameHud.getChatHud().addMessage(errorText);
        }
    }

    public static void handleReceivedScreenshot(byte[] screenshotData, String jsonData, ServerPlayerEntity player) {
        try {
            String playerName = player.getName().getString();
            String fileName = "screenshot-" + playerName + "_" + System.currentTimeMillis() + ".png";


            File screenshotFile = new File("screenshotUploader/" + fileName);
            Files.write(screenshotFile.toPath(), screenshotData);
            logger.info("Screenshot received from {} and saved as {}", player.getName().getString(), fileName);
            String uploadedFilePath = "screenshotUploader/" + fileName;
            String outputFilePath = "screenshotUploader/screenshots/" + formatFileName(fileName, playerName);
            convertToJpeg(uploadedFilePath, outputFilePath);


            Files.delete(Paths.get(uploadedFilePath));

            String urlString = getServerIp();
            if (!urlString.matches("^https?://.*")) {
                urlString = "http://" + urlString;
            }

            if (!urlString.matches(".*:\\d+.*")) {
                urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + ConfigManager.getServerConfig().port);
            }

            JsonObject jsonObject = getJsonObject(urlString, outputFilePath);
            try {
                JsonObject webhookJson = JsonParser.parseString(jsonData).getAsJsonObject();
                System.out.println(webhookJson);
                if (ConfigManager.getServerConfig().sendDiscordWebhook) {
                    DiscordWebhook.sendMessage(ConfigManager.getServerConfig().webhookUrl, playerName, webhookJson.get("server_address").getAsString(), webhookJson.get("dimension").getAsString(), webhookJson.get("coordinates").getAsString(), webhookJson.get("facing_direction").getAsString(), webhookJson.get("biome").getAsString(), jsonObject.get("url").getAsString(), webhookJson.get("world_info").getAsString());
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


    private static void convertToJpeg(String uploadedFilePath, String outputFilePath) throws IOException {
        File outputFile = new File(outputFilePath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (parentDir.mkdirs()) {
                logger.info("Created Upload Folder");
            }
        }

        BufferedImage originalImage = ImageIO.read(new File(uploadedFilePath));
        if (originalImage == null) {
            throw new IOException("Unable to read the image from the file: " + uploadedFilePath);
        }

        BufferedImage resizedImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        graphics.drawImage(originalImage, 0, 0, null);
        graphics.dispose();

        boolean success = ImageIO.write(resizedImage, "jpg", outputFile);
        if (!success) {
            throw new IOException("Failed to save the image to: " + outputFilePath);
        }

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

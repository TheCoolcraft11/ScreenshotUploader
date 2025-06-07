package de.thecoolcraft11.screenshotUploader.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.thecoolcraft11.screenshotUploader.ScreenshotUploader;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.thecoolcraft11.screenshotUploader.ScreenshotUploader.getServerIp;

public class ReceiveScreenshotPacket {
    static Logger logger = LoggerFactory.getLogger(ReceiveScreenshotPacket.class);

    public static void handleReceivedScreenshot(byte[] screenshotData, String jsonData, Player player) {
        try {
            String playerName = player.getName();
            String baseFileName = "screenshot-" + playerName + "_" + System.currentTimeMillis();
            String screenshotFileName = baseFileName + ".png";
            File screenshotFile = new File("screenshotUploader/" + screenshotFileName);
            Files.write(screenshotFile.toPath(), screenshotData);
            String jsonFileName = "screenshotUploader/screenshots/" + baseFileName + ".json";
            try (FileWriter fileWriter = new FileWriter(jsonFileName)) {
                fileWriter.write(jsonData);
            } catch (IOException e) {
                logger.error("Error saving JSON data: {}", e.getMessage());
            }

            String uploadedFilePath = "screenshotUploader/" + screenshotFileName;
            String outputFilePath = "screenshotUploader/screenshots/" + formatFileName(screenshotFileName, playerName);
            convertToJpeg(uploadedFilePath, outputFilePath);

            Files.delete(Paths.get(uploadedFilePath));

            String urlString = getServerIp();
            if (!urlString.matches("^https?://.*")) {
                urlString = "http://" + urlString;
            }

            if (!urlString.matches(".*:\\d+.*")) {
                urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + ScreenshotUploader.config.getFileConfiguration().getInt("port"));
            }

            JsonObject jsonObject = getJsonObject(urlString, outputFilePath);
            try {
                JsonObject webhookJson = JsonParser.parseString(jsonData).getAsJsonObject();
                if (ScreenshotUploader.config.getFileConfiguration().getBoolean("sendDiscordWebhook")) {
                    DiscordWebhook.sendMessage(
                            ScreenshotUploader.config.getFileConfiguration().getString("webhookUrl"),
                            playerName,
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
            byte[] responseData = encodeString(jsonObject.toString());
            player.sendPluginMessage(ScreenshotUploader.instance, "screenshot-uploader:screenshot_response_packet", responseData);
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


    public static void applyCommentToScreenshot(String comment, String filename, String author, UUID uuid) {
        Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
        Path targetFile = BASE_DIR.resolve(filename);
        Path commentFile = BASE_DIR.resolve(filename.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));

        try {
            if (!Files.exists(targetFile)) {
                return;
            }

            JsonObject newComment = new JsonObject();
            newComment.add("comment", new JsonPrimitive(comment));
            newComment.add("author", new JsonPrimitive(author));
            newComment.add("timestamp", new JsonPrimitive(Instant.now().toString()));
            newComment.add("authorUUID", new JsonPrimitive(uuid.toString()));

            JsonObject existingJson = new JsonObject();
            if (Files.exists(commentFile)) {
                String existingContent = new String(Files.readAllBytes(commentFile));
                existingJson = JsonParser.parseString(existingContent).getAsJsonObject();
            }

            JsonArray commentsArray = existingJson.has("comments") ? existingJson.getAsJsonArray("comments") : new JsonArray();
            commentsArray.add(newComment);
            existingJson.add("comments", commentsArray);

            Files.createDirectories(commentFile.getParent());
            Files.write(commentFile, existingJson.toString().getBytes());

        } catch (IOException ignored) {
        }
    }

    private static byte[] encodeString(String s) {
        byte[] stringBytes = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeVarInt(out, stringBytes.length);
            out.write(stringBytes);
        } catch (IOException e) {
            logger.error("Error encoding string: {}", e.getMessage());
        }
        return out.toByteArray();
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}

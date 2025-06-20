package de.thecoolcraft11.screenshotUploader.packet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static de.thecoolcraft11.screenshotUploader.ScreenshotUploader.getServerIp;

public class TagPacketListener implements PluginMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(TagPacketListener.class);

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals("screenshot-uploader:tag_packet")) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String tagsJson = readString(in);
            String screenshot = readString(in);

            applyTagToScreenshot(tagsJson, screenshot, player.getUniqueId());
        } catch (IOException e) {
            logger.error("Error processing tag packet: {}", e.getMessage(), e);
        }
    }

    private void applyTagToScreenshot(String tagsJson, String screenshot, java.util.UUID uuid) {
        Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
        String url = getServerIp();
        String filename = screenshot.replace(url + "/screenshots/", "");
        Path targetFile = BASE_DIR.resolve(filename);
        Path tagFile = BASE_DIR.resolve(filename.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));

        try {
            if (!Files.exists(targetFile)) {
                logger.warn("Unable to apply tags: Screenshot file does not exist: {}", filename);
                return;
            }

            JsonElement incomingJsonElement = JsonParser.parseString(tagsJson);
            JsonArray incomingTags;

            if (incomingJsonElement.isJsonObject() && incomingJsonElement.getAsJsonObject().has("tags")) {
                incomingTags = incomingJsonElement.getAsJsonObject().getAsJsonArray("tags");
            } else if (incomingJsonElement.isJsonArray()) {
                incomingTags = incomingJsonElement.getAsJsonArray();
            } else {
                logger.error("Invalid tags format: {}", tagsJson);
                return;
            }

            JsonObject existingJson = new JsonObject();
            if (Files.exists(tagFile)) {
                String existingContent = new String(Files.readAllBytes(tagFile));
                existingJson = JsonParser.parseString(existingContent).getAsJsonObject();
            }

            if (existingJson.has("uuid") && !existingJson.get("uuid").getAsString().equals(uuid.toString())) {
                logger.warn("Player {} tried to add tags to screenshot {}, but is not the author",
                        uuid, filename);
                return;
            }


            existingJson.add("tags", incomingTags);

            if (!existingJson.has("uuid")) {
                existingJson.addProperty("uuid", uuid.toString());
            }

            Files.createDirectories(tagFile.getParent());
            Files.write(tagFile, existingJson.toString().getBytes());
            logger.info("Tags added to screenshot {} by player {}", filename, uuid);

        } catch (Exception e) {
            logger.error("Error applying tags: ", e);
        }
    }

    private String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;

        do {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;

            if (position >= 32) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((currentByte & 0x80) != 0);

        return value;
    }
}
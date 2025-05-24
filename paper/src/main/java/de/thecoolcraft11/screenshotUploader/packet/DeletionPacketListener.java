package de.thecoolcraft11.screenshotUploader.packet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.screenshotUploader.ScreenshotUploader;
import de.thecoolcraft11.screenshotUploader.util.Config;
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

public class DeletionPacketListener implements PluginMessageListener {
    Logger logger = LoggerFactory.getLogger(DeletionPacketListener.class);
    private final static Config config = ScreenshotUploader.config;

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals("screenshot-uploader:deletion_packet")) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String screenshot = readString(in);

            deleteScreenshot(screenshot, player);
        } catch (
                IOException e) {
            logger.error("Error processing deletion packet: {}", e.getMessage(), e);
        }
    }

    private void deleteScreenshot(String screenshot, Player player) {
        String url = getServerIp();
        String filename = screenshot.replace(url + "/screenshots/", "");
        String deletionType = getDeletionType(player);


        final Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
        switch (deletionType) {
            case "deleteOP" -> {
                if (!player.isOp()) {
                    logger.error("Player {} tried to delete screenshot {}, but is not an OP", player.getName(), filename);
                    return;
                }
                Path targetFile = BASE_DIR.resolve(filename);
                Path jsonFile = BASE_DIR.resolve(filename.replaceFirst("\\.png$", ".json"));
                try {
                    if (Files.exists(targetFile)) {
                        Files.delete(targetFile);
                    }
                    if (Files.exists(jsonFile)) {
                        Files.delete(jsonFile);
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete screenshot by OP: {}", e.getMessage());
                }
            }
            case "deleteAll" -> {
                Path targetFile = BASE_DIR.resolve(filename);
                Path jsonFile = BASE_DIR.resolve(filename.replaceFirst("\\.png$", ".json"));
                try {
                    if (Files.exists(targetFile)) {
                        Files.delete(targetFile);
                    }
                    if (Files.exists(jsonFile)) {
                        Files.delete(jsonFile);
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete screenshot by Player: {}", e.getMessage());
                }
            }
            case "deleteOwn" -> {
                Path targetFile = BASE_DIR.resolve(filename);
                Path jsonFile = BASE_DIR.resolve(filename.replaceFirst("\\.png$", ".json"));
                try {
                    if (Files.exists(targetFile)) {
                        String jsonContent = new String(Files.readAllBytes(jsonFile));
                        JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
                        if (jsonObject.has("uuid") && jsonObject.get("uuid").getAsString().equals(player.getUniqueId().toString())) {
                            if (Files.exists(targetFile)) {
                                Files.delete(targetFile);
                            }
                            if (Files.exists(jsonFile)) {
                                Files.delete(jsonFile);
                            }
                        } else {
                            logger.error("Player {} tried to delete screenshot {}, but is not the author or an OP", player.getName(), filename);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete own screenshot: {}", e.getMessage());
                }
            }
            default ->
                    logger.error("Player {} tried to delete screenshot {}, but is not allowed to do so", player.getName(), filename);
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

    private static @NotNull String getDeletionType(Player player) {
        boolean allowOps = config.getFileConfiguration().getBoolean("allowOpsToDelete");
        boolean allowPlayers = config.getFileConfiguration().getBoolean("allowPlayersToDelete");
        boolean allowPlayersOwn = config.getFileConfiguration().getBoolean("allowPlayersToDeleteOwn");
        boolean isOP = player.isOp();

        String deletionType;

        if (allowOps && isOP) {
            deletionType = "deleteOP";
        } else if (allowPlayers) {
            deletionType = "deleteAll";
        } else if (allowPlayersOwn) {
            deletionType = "deleteOwn";
        } else {
            deletionType = "none";
        }
        return deletionType;
    }

}

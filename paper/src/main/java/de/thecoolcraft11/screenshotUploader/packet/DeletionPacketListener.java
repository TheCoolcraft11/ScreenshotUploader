package de.thecoolcraft11.screenshotUploader.packet;

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

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals("screenshot-uploader:deletion_packet")) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String screenshot = readString(in);

            if (player.isOp()) {
                deleteScreenshot(screenshot);
            } else {
                logger.error("Player {} does not have permission to delete screenshots.", player.getName());
            }
        } catch (IOException e) {
            logger.error("Error processing deletion packet: {}", e.getMessage(), e);
        }
    }

    private void deleteScreenshot(String screenshot) {
        String url = getServerIp();
        String filename = screenshot.replace(url + "/screenshots/", "");
        Path targetFile = Paths.get("./screenshotUploader/screenshots/" + filename);

        try {
            if (Files.exists(targetFile)) {
                Files.delete(targetFile);
            }
        } catch (IOException e) {
            logger.error("Error deleting file: {}", e.getMessage());
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

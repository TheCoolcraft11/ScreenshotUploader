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

import static de.thecoolcraft11.screenshotUploader.util.ReceiveScreenshotPacket.applyCommentToScreenshot;

public class CommentPacketListener implements PluginMessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommentPacketListener.class);

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals("screenshot-uploader:comment_packet")) {
            return;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String comment = readString(in);
            String screenshot = readString(in);

            applyCommentToScreenshot(comment, screenshot, player.getName(), player.getUniqueId());

        } catch (IOException e) {
            LOGGER.error("Error processing comment packet: {}", e.getMessage(), e);
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
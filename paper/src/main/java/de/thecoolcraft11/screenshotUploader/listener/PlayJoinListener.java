package de.thecoolcraft11.screenshotUploader.listener;

import com.google.gson.JsonObject;
import de.thecoolcraft11.screenshotUploader.ScreenshotUploader;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static de.thecoolcraft11.screenshotUploader.ScreenshotUploader.config;
import static de.thecoolcraft11.screenshotUploader.ScreenshotUploader.getServerIp;

public class PlayJoinListener implements Listener {
    private static final Logger logger = LoggerFactory.getLogger(PlayJoinListener.class);

    @SuppressWarnings("BusyWait")
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        new Thread(() -> {
            Player player = event.getPlayer();
            if (ScreenshotUploader.config.getFileConfiguration().getBoolean("sendUrlToClient")) {
                long startTime = System.currentTimeMillis();
                while (!player.getListeningPluginChannels().contains("screenshot-uploader:address_packet") &&
                        System.currentTimeMillis() - startTime < 20000) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }

                if (!player.getListeningPluginChannels().contains("screenshot-uploader:address_packet")) {
                    return;
                }

                JsonObject jsonObject = getJsonObject(player);
                byte[] data = encodeString(jsonObject.toString());
                player.sendPluginMessage(ScreenshotUploader.instance, "screenshot-uploader:address_packet", data);
            }
        }).start();
    }

    private static @NotNull JsonObject getJsonObject(Player player) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("upload", "mcserver://chunked");
        if (ScreenshotUploader.config.getFileConfiguration().getBoolean("sendGalleryUrlToClient")) {
            String urlString = getServerIp();
            if (!urlString.matches("^https?://.*")) {
                urlString = "http://" + urlString;
            }

            if (!urlString.matches(".*:\\d+.*")) {
                urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + ScreenshotUploader.config.getFileConfiguration().getInt("port"));
            }
            jsonObject.addProperty("home", urlString);
            urlString = urlString + "/screenshot-list";
            jsonObject.addProperty("gallery", urlString);

        }
        if (config.getFileConfiguration().getBoolean("useCustomWebURL")) {
            jsonObject.remove("upload");
            jsonObject.addProperty("upload", config.getFileConfiguration().getString("customWebURL"));
            jsonObject.remove("comment");
            jsonObject.addProperty("comment", config.getFileConfiguration().getString("customCommentURL"));
            jsonObject.remove("delete");
            jsonObject.addProperty("delete", config.getFileConfiguration().getString("customDeletionURL"));
            jsonObject.remove("tag");
            jsonObject.addProperty("tag", config.getFileConfiguration().getString("customTagURL"));
        }
        if ((config.getFileConfiguration().getBoolean("allowOpsToDelete") && player.isOp()) || config.getFileConfiguration().getBoolean("allowPlayersToDelete")) {
            jsonObject.addProperty("allowDelete", true);
        }
        if (config.getFileConfiguration().getBoolean("allowPlayersToDeleteOwn")) {
            jsonObject.addProperty("allowDeleteOwn", true);
        }
        return jsonObject;
    }

    public static byte[] encodeString(String s) {
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

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

}

package de.thecoolcraft11.util;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.thecoolcraft11.ScreenshotUploaderServer.MOD_ID;

public class DiscordWebhook {
    private static final Logger logger = LoggerFactory.getLogger(MOD_ID);

    public static void sendMessage(String webhookURL, String username, BufferedImage image, String server, String world, String coordinates, String direction, String biome, String screenshotUrl, String worldInfo) {
        Integer color = extractDominantColorHistogram(image);
        String jsonPayload = createPayload(username, server, world, coordinates, direction, biome, screenshotUrl, worldInfo, color);
        try {
            int responseCode = getResponseCode(webhookURL, jsonPayload);
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                logger.info("Webhook sent successfully");
            } else {
                logger.error("Failed to send message. Response Code: {}", responseCode);
            }

        } catch (IOException e) {
            logger.error("Error sending message: {}", e.getMessage());
        }
    }

    private static int getResponseCode(String webhookURL, String jsonPayload) throws IOException {
        URI url = URI.create(webhookURL);

        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection.getResponseCode();
    }

    private static String createPayload(String username, String server, String world, String coordinates, String direction, String biome, String screenshotUrl, String worldInfo, Integer color) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> embed = new HashMap<>();
        embed.put("description", "**" + (username == null ? "Unknown" : username) + "** has made a new screenshot:");
        embed.put("color", color == null ? 0xD2D2D2 : color);

        Map<String, String> field = new HashMap<>();
        field.put("name", "\u200B");
        field.put("value", String.format("__%s__\n**%s**\n%s - %s\n%s\n%s", server, world, coordinates, direction, biome, worldInfo));

        List<Map<String, String>> fields = new ArrayList<>();
        fields.add(field);
        embed.put("fields", fields);
        embed.put("timestamp", Instant.now().toString());
        embed.put("image", Map.of("url", screenshotUrl));

        List<Map<String, Object>> embeds = new ArrayList<>();
        embeds.add(embed);

        payload.put("embeds", embeds);
        payload.put("username", username == null ? "Unknown" : username);
        payload.put("avatar_url", username == null ? null : "https://minotar.net/avatar/" + username);
        payload.put("attachments", new Object[0]);
        payload.put("thread_name", username == null ? "Unknown" : username);

        Gson gson = new Gson();
        return gson.toJson(payload);
    }


    private static Integer extractDominantColorHistogram(BufferedImage image) {
        if (image == null) return null;

        Map<Integer, Integer> colorCount = new HashMap<>();

        for (int y = 0; y < image.getHeight(); y += 5) {
            for (int x = 0; x < image.getWidth(); x += 5) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;

                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                red = reduceColorChannel(red);
                green = reduceColorChannel(green);
                blue = reduceColorChannel(blue);

                if ((red + green + blue) < 60) continue;

                int reducedRgb = (red << 16) | (green << 8) | blue;

                colorCount.put(reducedRgb, colorCount.getOrDefault(reducedRgb, 0) + 1);
            }
        }

        return colorCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static int reduceColorChannel(int color) {
        int shift = 2;
        return (color >> shift) << shift;
    }
}

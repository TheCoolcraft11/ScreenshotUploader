package de.thecoolcraft11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.ScreenshotPayload;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import de.thecoolcraft11.util.DiscordWebhook;
import de.thecoolcraft11.util.WebServer;
import de.thecoolcraft11.util.config.ConfigManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ScreenshotUploaderServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "screenshot-uploader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);




    @Override
    public void onInitializeServer() {
        File configDir = new File("config/screenshotUploader");
        if(!configDir.exists()) {
            configDir.mkdir();
        }
        ConfigManager.initialize(configDir,false);

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarting);
        if (ConfigManager.getServerConfig().screenshotWebserver) {
            String ipAddress = "127.0.0.1";
            int port = ConfigManager.getServerConfig().port;
            LOGGER.info("Starting web server on {}:{} ...", ipAddress, port);
            startWebServer();
        }
        ServerPlayConnectionEvents.JOIN.register((player, packetSender, minecraftServer) -> {
            if(ConfigManager.getServerConfig().sendUrlToClient) {
                String serverAddress = "mcserver://this";
                if (ConfigManager.getServerConfig().useCustomWebURL) {
                    serverAddress = ConfigManager.getServerConfig().customWebURL;
                }

                ServerPlayNetworking.send(player.getPlayer(), new AddressPayload(serverAddress));
            }
            ServerPlayNetworking.registerGlobalReceiver(ScreenshotPayload.ID, (payload, context) -> {
                byte[] bytes = payload.bytes();
                String json = payload.json();
                context.server().execute(() ->
                        handleReceivedScreenshot(bytes, json, context.player())
                );
            });
        }
        );
    }

    private void onServerStarting(MinecraftServer server) {
        copyResourceToServerDir("/static/js/script.js", "screenshotUploader/static/js/script.js");
        copyResourceToServerDir("/static/css/style.css", "screenshotUploader/static/css/style.css");
    }

    private void copyResourceToServerDir(String resourcePath, String targetPath) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path targetFile = gameDir.resolve(targetPath);

        try (InputStream resourceStream = getClass().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                LOGGER.error("Resource not found: {}", resourcePath);
                return;
            }

            Files.createDirectories(targetFile.getParent());
            Files.copy(resourceStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Error while copying resource: {}", e.getMessage());
        }
    }


    private static void handleReceivedScreenshot(byte[] screenshotData, String jsonData, ServerPlayerEntity player) {
        try {
            String playerName = player.getName().getString();
            String fileName = "screenshot-" + playerName + "_" + System.currentTimeMillis() + ".png";

            File screenshotFile = new File("screenshotUploader/" + fileName);
            Files.write(screenshotFile.toPath(), screenshotData);
            System.out.println("Screenshot received from " + player.getName().getString() + " and saved as " + fileName);
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
                if(ConfigManager.getServerConfig().sendDiscordWebhook) {
                    DiscordWebhook.sendMessage(ConfigManager.getServerConfig().webhookUrl, playerName, webhookJson.get("server_address").getAsString(), webhookJson.get("dimension").getAsString(), webhookJson.get("coordinates").getAsString(), webhookJson.get("facing_direction").getAsString(), webhookJson.get("biome").getAsString(), jsonObject.get("url").getAsString());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ServerPlayNetworking.send(player, new ScreenshotResponsePayload(jsonObject.toString()));
        } catch (IOException e) {
            LOGGER.error("Error handling uploaded screenshot: {}", e.getMessage());
        }
    }

    private static @NotNull JsonObject getJsonObject(String websiteAddress, String outputFilePath) {

        String screenshotUrl = websiteAddress + outputFilePath.replace("screenshotUploader", "");
        String galleryUrl = websiteAddress + "/";

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("message", "File uploaded sucessfully");
        jsonObject.addProperty("url", screenshotUrl);
        jsonObject.addProperty("gallery", galleryUrl);
        jsonObject.addProperty("status", "success");
        return jsonObject;
    }

    private void startWebServer() {
        String ipAddress = "127.0.0.1";
        int port = ConfigManager.getServerConfig().port;
        try {
            WebServer.startWebServer(ipAddress, port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static void convertToJpeg(String uploadedFilePath, String outputFilePath) throws IOException {
        File outputFile = new File(outputFilePath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
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

    private static String getServerIp() {
        return ConfigManager.getServerConfig().websiteURL;
    }
}


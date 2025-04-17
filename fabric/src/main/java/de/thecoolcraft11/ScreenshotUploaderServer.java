package de.thecoolcraft11;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.CommentPayload;
import de.thecoolcraft11.packet.ScreenshotPayload;
import de.thecoolcraft11.util.WebServer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static de.thecoolcraft11.util.ReceivePackets.handleReceivedScreenshot;


public class ScreenshotUploaderServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "screenshot-uploader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    @Override
    public void onInitializeServer() {
        createConfig();
        createModFolder();
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarting);
        prepareWebServerStart();
        ServerPlayConnectionEvents.JOIN.register(this::registerJoinEvent);
        deleteOldScreenshots();
    }

    private void registerJoinEvent(ServerPlayNetworkHandler serverPlayNetworkHandler, PacketSender player, MinecraftServer minecraftServer) {
        {
            if (ConfigManager.getServerConfig().sendUrlToClient) {
                JsonObject jsonObject = getJsonObject();
                ServerPlayNetworking.send(serverPlayNetworkHandler.getPlayer(), new AddressPayload(jsonObject.toString()));
            }
            ServerPlayNetworking.registerGlobalReceiver(ScreenshotPayload.ID, (payload, context) -> {
                byte[] bytes = payload.bytes();
                String json = payload.json();
                context.server().execute(() ->
                        handleReceivedScreenshot(bytes, json, context.player())
                );
            });
            ServerPlayNetworking.registerGlobalReceiver(CommentPayload.ID, (payload, context) -> {

                String comment = payload.comment();
                String screenshot = payload.screenshot();
                context.server().execute(() -> applyCommentToScreenshot(comment, screenshot, String.valueOf(context.player().getName().getString()), context.player().getUuid())
                );
            });
        }
    }

    private static @NotNull JsonObject getJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("upload", "mcserver://this");
        if (ConfigManager.getServerConfig().sendGalleryUrlToClient) {
            String urlString = getServerIp();
            if (!urlString.matches("^https?://.*")) {
                urlString = "http://" + urlString;
            }

            if (!urlString.matches(".*:\\d+.*")) {
                urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + ConfigManager.getServerConfig().port);
            }
            jsonObject.addProperty("home", urlString);
            urlString = urlString + "/screenshot-list";
            jsonObject.addProperty("gallery", urlString);

        }
        if (ConfigManager.getServerConfig().useCustomWebURL) {
            jsonObject.remove("upload");
            jsonObject.addProperty("upload", ConfigManager.getServerConfig().customWebURL);
        }
        return jsonObject;
    }

    public void applyCommentToScreenshot(String comment, String filename, String author, UUID uuid) {
        Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
        Path targetFile = BASE_DIR.resolve(filename);
        Path commentFile = BASE_DIR.resolve(filename.replace(".png", ".json"));

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

    private void createConfig() {
        File configDir = new File("config/screenshotUploader");
        if (!configDir.exists()) {
            if (configDir.mkdir()) {
                LOGGER.info("Created Config Folder");
            }
        }
        ConfigManager.initialize(configDir, false);
    }

    private void prepareWebServerStart() {
        if (ConfigManager.getServerConfig().screenshotWebserver) {
            String ipAddress = "127.0.0.1";
            int port = ConfigManager.getServerConfig().port;
            LOGGER.info("Starting web server on {}:{} ...", ipAddress, port);
            startWebServer();
        }
    }

    private void startWebServer() {
        String ipAddress = "127.0.0.1";
        int port = ConfigManager.getServerConfig().port;
        String urlString = getServerIp();
        if (!urlString.matches("^https?://.*")) {
            urlString = "http://" + urlString;
        }

        if (!urlString.matches(".*:\\d+.*")) {
            urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + ConfigManager.getServerConfig().port);
        }
        try {
            WebServer.startWebServer(ipAddress, port, urlString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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


    public static String getServerIp() {
        return ConfigManager.getServerConfig().websiteURL;
    }

    private static void deleteOldScreenshots() {
        if (!ConfigManager.getServerConfig().deleteOldScreenshots) return;
        Path screenshotDir = Paths.get("./screenshotUploader/screenshots/");
        if (screenshotDir.toFile().listFiles() == null) return;
        for (File file : Objects.requireNonNull(screenshotDir.toFile().listFiles())) {
            if (file.isFile() && file.lastModified() < System.currentTimeMillis() - (ConfigManager.getServerConfig().deleteAfterDays * 24 * 60 * 60 * 1000L)) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    LOGGER.error("Failed to delete old screenshot: {}", e.getMessage());
                }
            }
        }
    }

    private void createModFolder() {
        Path screenshotDir = Paths.get("./screenshotUploader/screenshots/");
        Path staticDir = Paths.get("./screenshotUploader/static/");
        Path staticJsDir = Paths.get("./screenshotUploader/static/js/");
        Path staticCssDir = Paths.get("./screenshotUploader/static/css/");

        if (!Files.exists(screenshotDir)) {
            try {
                Files.createDirectories(screenshotDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create screenshot directory: {}", e.getMessage());
            }
        }
        if (!Files.exists(staticDir)) {
            try {
                Files.createDirectories(staticDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create static directory: {}", e.getMessage());
            }
        }
        if (!Files.exists(staticJsDir)) {
            try {
                Files.createDirectories(staticJsDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create static js directory: {}", e.getMessage());
            }
        }
        if (!Files.exists(staticCssDir)) {
            try {
                Files.createDirectories(staticCssDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create static css directory: {}", e.getMessage());
            }
        }
    }
}


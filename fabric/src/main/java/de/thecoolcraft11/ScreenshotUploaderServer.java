package de.thecoolcraft11;

import com.google.gson.*;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.packet.*;
import de.thecoolcraft11.util.WebServer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
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
        registerPacketReceivers();
    }

    private void registerJoinEvent(ServerPlayNetworkHandler serverPlayNetworkHandler, PacketSender player, MinecraftServer minecraftServer) {
        if (ConfigManager.getServerConfig().sendUrlToClient) {
            JsonObject jsonObject = getJsonObject(serverPlayNetworkHandler.getPlayer());
            ServerPlayNetworking.send(serverPlayNetworkHandler.getPlayer(), new AddressPayload(jsonObject.toString()));
        }
    }

    private void registerPacketReceivers() {
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
        ServerPlayNetworking.registerGlobalReceiver(DeletionPacket.ID, (payload, context) -> {
            String screenshot = payload.screenshot();
            String url = getServerIp();
            String filename = screenshot.replace(url + "/screenshots/", "");
            context.server().execute(() -> {
                String deletionType = getDeletionType(context.player());


                final Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
                switch (deletionType) {
                    case "deleteOP" -> {
                        if (!context.player().hasPermissionLevel(3)) {
                            LOGGER.error("Player {} tried to delete screenshot {}, but is not an OP", context.player().getName().getString(), filename);
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
                            LOGGER.error("Failed to delete screenshot by OP: {}", e.getMessage());
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
                            LOGGER.error("Failed to delete screenshot by Player: {}", e.getMessage());
                        }
                    }
                    case "deleteOwn" -> {
                        Path targetFile = BASE_DIR.resolve(filename);
                        Path jsonFile = BASE_DIR.resolve(filename.replaceFirst("\\.png$", ".json"));
                        try {
                            if (Files.exists(targetFile)) {
                                String jsonContent = new String(Files.readAllBytes(jsonFile));
                                JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
                                if (jsonObject.has("uuid") && jsonObject.get("uuid").getAsString().equals(context.player().getUuid().toString())) {
                                    if (Files.exists(targetFile)) {
                                        Files.delete(targetFile);
                                    }
                                    if (Files.exists(jsonFile)) {
                                        Files.delete(jsonFile);
                                    }
                                } else {
                                    LOGGER.error("Player {} tried to delete screenshot {}, but is not the author or an OP", context.player().getName().getString(), filename);
                                }
                            }
                        } catch (IOException e) {
                            LOGGER.error("Failed to delete own screenshot: {}", e.getMessage());
                        }
                    }
                    default ->
                            LOGGER.error("Player {} tried to delete screenshot {}, but is not allowed to do so", context.player().getName().getString(), filename);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(TagPayload.ID, (payload, context) -> {
            String tagsJson = payload.tagsJson();
            String screenshot = payload.screenshot();
            context.server().execute(() -> applyTagToScreenshot(tagsJson, screenshot, context.player().getUuid())
            );
        });
    }

    private void applyTagToScreenshot(String tagsJson, String screenshot, UUID uuid) {
        Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
        String url = getServerIp();
        String filename = screenshot.replace(url + "/screenshots/", "");
        Path targetFile = BASE_DIR.resolve(filename);
        Path tagFile = BASE_DIR.resolve(filename.replaceFirst("\\.png$", ".json"));

        try {
            if (!Files.exists(targetFile)) {
                return;
            }

            JsonElement incomingJsonElement = JsonParser.parseString(tagsJson);
            JsonArray incomingTags;

            if (incomingJsonElement.isJsonObject() && incomingJsonElement.getAsJsonObject().has("tags")) {
                incomingTags = incomingJsonElement.getAsJsonObject().getAsJsonArray("tags");
            } else if (incomingJsonElement.isJsonArray()) {
                incomingTags = incomingJsonElement.getAsJsonArray();
            } else {
                LOGGER.error("Invalid tags format: {}", tagsJson);
                return;
            }

            JsonObject existingJson = new JsonObject();
            if (Files.exists(tagFile)) {
                String existingContent = new String(Files.readAllBytes(tagFile));
                existingJson = JsonParser.parseString(existingContent).getAsJsonObject();
            }

            if (!existingJson.has("uuid") || !existingJson.get("uuid").getAsString().equals(uuid.toString())) {
                return;
            }

            existingJson.add("tags", incomingTags);

            Files.createDirectories(tagFile.getParent());
            Files.write(tagFile, existingJson.toString().getBytes());

        } catch (Exception e) {
            LOGGER.error("Error applying tags: ", e);
        }
    }

    private static @NotNull String getDeletionType(PlayerEntity player) {
        boolean allowOps = ConfigManager.getServerConfig().allowOpsToDelete;
        boolean allowPlayers = ConfigManager.getServerConfig().allowPlayersToDelete;
        boolean allowPlayersOwn = ConfigManager.getServerConfig().allowPlayersToDeleteOwn;
        boolean isOP = player.hasPermissionLevel(3);

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

    private static @NotNull JsonObject getJsonObject(PlayerEntity playerEntity) {
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
        if ((ConfigManager.getServerConfig().allowOpsToDelete && playerEntity.hasPermissionLevel(3)) || ConfigManager.getServerConfig().allowPlayersToDelete) {
            jsonObject.addProperty("allowDelete", true);
        }
        if (ConfigManager.getServerConfig().allowPlayersToDeleteOwn) {
            jsonObject.addProperty("allowDeleteOwn", true);
        }
        return jsonObject;
    }

    public void applyCommentToScreenshot(String comment, String filename, String author, UUID uuid) {
        Path BASE_DIR = Paths.get("./screenshotUploader/screenshots/");
        Path targetFile = BASE_DIR.resolve(filename);
        Path commentFile = BASE_DIR.resolve(filename.replaceFirst("\\.png$", ".json"));

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
        File configDir = new File("config");
        File screenshotUploaderDir = new File(configDir, "screenshotUploader");
        if (!configDir.exists()) {
            if (configDir.mkdir()) {
                LOGGER.info("Created Config Folder");
            }
        }
        if (!screenshotUploaderDir.exists()) {
            if (screenshotUploaderDir.mkdir()) {
                LOGGER.info("Created ScreenshotUploader Folder");
            }
        }
        ConfigManager.initialize(screenshotUploaderDir, false);
    }

    private void prepareWebServerStart() {
        if (ConfigManager.getServerConfig().screenshotWebserver) {
            String ipAddress = ConfigManager.getServerConfig().host == null || Objects.requireNonNull(ConfigManager.getServerConfig().host).isEmpty() ? "0.0.0.0" : ConfigManager.getServerConfig().host;
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

        boolean fileExists = Files.exists(targetFile);
        boolean shouldReplace = ConfigManager.getServerConfig().replaceStaticFilesOnStart;

        if (fileExists && !shouldReplace) {
            return;
        }

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


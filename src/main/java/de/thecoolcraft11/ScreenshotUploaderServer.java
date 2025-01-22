package de.thecoolcraft11;

import com.google.gson.JsonObject;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.packet.AddressPayload;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static de.thecoolcraft11.util.ReceivePackets.handleReceivedScreenshot;


public class ScreenshotUploaderServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "screenshot-uploader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    @Override
    public void onInitializeServer() {
        createConfig();
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarting);
        prepareWebServerStart();
        ServerPlayConnectionEvents.JOIN.register(this::registerJoinEvent);
    }

    private void registerJoinEvent(ServerPlayNetworkHandler serverPlayNetworkHandler, PacketSender player, MinecraftServer minecraftServer) {
        {
            if (ConfigManager.getServerConfig().sendUrlToClient) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("upload", "mcserver://this");
                if (ConfigManager.getServerConfig().senGalleryUrlToClient) {
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
                ServerPlayNetworking.send(serverPlayNetworkHandler.getPlayer(), new AddressPayload(jsonObject.toString()));
            }
            ServerPlayNetworking.registerGlobalReceiver(ScreenshotPayload.ID, (payload, context) -> {
                byte[] bytes = payload.bytes();
                String json = payload.json();
                context.server().execute(() ->
                        handleReceivedScreenshot(bytes, json, context.player())
                );
            });
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
}


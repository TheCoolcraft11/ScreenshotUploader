package de.thecoolcraft11;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.ScreenshotPayload;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import de.thecoolcraft11.util.WebServer;
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
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ScreenshotUploaderServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "screenshot-uploader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("./config/screenshotUploader/serverConfig.json");
    private static JsonObject config;

    private void createDefaultConfig() {
        try {
            File parentDir = CONFIG_FILE.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Failed to create config directory: " + parentDir.getAbsolutePath());
            }

            config = new JsonObject();
            config.addProperty("screenshotWebserver", true);
            config.addProperty("port", 4567);
            config.addProperty("allowDelete", false);
            config.addProperty("useCustomWebURL", false);
            config.addProperty("customWebURL", "");

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
                LOGGER.info("Default config created at: {}", CONFIG_FILE.getAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create default config: {}", e.getMessage());
        }
    }


    private void loadConfig() {
        if (!CONFIG_FILE.exists()) {
            createDefaultConfig();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            config = GSON.fromJson(reader, JsonObject.class);
            System.out.println("Config loaded successfully!");
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
            createDefaultConfig();
        }
    }


    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarting);
        loadConfig();
        if(config.get("screenshotWebserver").getAsBoolean()) {
            String ipAddress = "127.0.0.1";
            int port = config.get("port").getAsInt();
            LOGGER.info("Starting web server on {}:{} ...", ipAddress, port);
            startWebServer();
        }
        ServerPlayConnectionEvents.JOIN.register((player, packetSender, minecraftServer)-> {


            String serverAddress = "mcserver://this";
            if(config.get("useCustomWebURL").getAsBoolean()) {
                serverAddress = config.get("customWebURL").getAsString();
            }

            ServerPlayNetworking.send(player.getPlayer(), new AddressPayload(serverAddress));
        });

        ServerPlayNetworking.registerGlobalReceiver(ScreenshotPayload.ID, (payload, context) -> {
           byte[] bytes = payload.bytes();
           context.server().execute(() ->
               handleReceivedScreenshot(bytes, context.player())
           );
        });
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



    private static void handleReceivedScreenshot(byte[] screenshotData, ServerPlayerEntity player) {
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

            String ipAdress = getServerIp();
            int port = config.get("port").getAsInt();
            JsonObject jsonObject = getJsonObject(ipAdress, port, outputFilePath);
            ServerPlayNetworking.send(player, new ScreenshotResponsePayload(jsonObject.toString()));
        } catch (IOException e) {
            LOGGER.error("Error handling uploaded screenshot: {}", e.getMessage());
        }
    }

    private static @NotNull JsonObject getJsonObject(String ipAdress, int port, String outputFilePath) {
        String websiteAddress = "http://" + ipAdress + ":" + port;

        String screenshotUrl = websiteAddress + outputFilePath.replace("uploads", "");
        String galleryUrl = websiteAddress + "/screenshots";

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("message", "File uploaded sucessfully");
        jsonObject.addProperty("url", screenshotUrl);
        jsonObject.addProperty("gallery", galleryUrl);
        jsonObject.addProperty("status", "success");
        return jsonObject;
    }

    private void startWebServer() {
        String ipAddress = "127.0.0.1";
        int port = config.get("port").getAsInt();
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
    public static String getServerIp() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            return ip.getHostAddress();
        } catch (Exception e) {
            LOGGER.error("Exeption while getting server IP: {}", e.getMessage());
            return "localhost";
        }
    }
}



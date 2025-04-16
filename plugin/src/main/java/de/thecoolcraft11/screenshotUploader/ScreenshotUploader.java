package de.thecoolcraft11.screenshotUploader;

import de.thecoolcraft11.screenshotUploader.listener.PlayJoinListener;
import de.thecoolcraft11.screenshotUploader.util.Config;
import de.thecoolcraft11.screenshotUploader.util.ScreenshotPacketChunkListener;
import de.thecoolcraft11.screenshotUploader.util.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class ScreenshotUploader extends JavaPlugin {

    public static Config config;
    public static ScreenshotUploader instance;

    @Override
    public void onEnable() {
        instance = this;
        saveResource("static/js/script.js", false);
        saveResource("static/css/style.css", false);
        config = new Config("config.yml", ScreenshotUploader.getProvidingPlugin(ScreenshotUploader.class).getDataFolder());
        createModFolder();
        prepareWebServerStart();
        deleteOldScreenshots();

        getServer().getMessenger().registerIncomingPluginChannel(this, "screenshot-uploader:screenshot_packet", new ScreenshotPacketChunkListener());
        //getServer().getMessenger().registerIncomingPluginChannel(this, "screenshot-uploader:comment_packet", new CommentPacketListener());

        getServer().getMessenger().registerOutgoingPluginChannel(this, "screenshot-uploader:address_packet");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "screenshot-uploader:screenshot_response_packet");

        Bukkit.getPluginManager().registerEvents(new PlayJoinListener(), this);
    }


    public static @NotNull Path getPluginFolder() {
        return instance.getDataPath();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void prepareWebServerStart() {
        if (config.getFileConfiguration().getBoolean("screenshotWebserver")) {
            String ipAddress = "127.0.0.1";
            int port = config.getFileConfiguration().getInt("port");
            getLogger().info("Starting web server on" + ipAddress + ":" + port + "...");
            startWebServer();
        }
    }

    private void startWebServer() {
        String ipAddress = "127.0.0.1";
        int port = config.getFileConfiguration().getInt("port");
        String urlString = getServerIp();
        if (!urlString.matches("^https?://.*")) {
            urlString = "http://" + urlString;
        }

        if (!urlString.matches(".*:\\d+.*")) {
            urlString = urlString.replaceFirst("^(https?://[^/]+)", "$1:" + config.getFileConfiguration().getInt("port"));
        }
        try {
            WebServer.startWebServer(ipAddress, port, urlString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static String getServerIp() {
        return config.getFileConfiguration().getString("websiteURL");
    }

    private void deleteOldScreenshots() {
        if (!config.getFileConfiguration().getBoolean("deleteOldScreenshots")) return;
        Path screenshotDir = Paths.get("./screenshotUploader/screenshots/");
        if (screenshotDir.toFile().listFiles() == null) return;
        for (File file : Objects.requireNonNull(screenshotDir.toFile().listFiles())) {
            if (file.isFile() && file.lastModified() < System.currentTimeMillis() - (config.getFileConfiguration().getInt("deleteAfterDays") * 24 * 60 * 60 * 1000L)) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    getLogger().severe("Failed to delete old screenshot: " + e.getMessage());
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
                getLogger().severe("Failed to create screenshot directory: " + e.getMessage());
            }
        }
        if (!Files.exists(staticDir)) {
            try {
                Files.createDirectories(staticDir);
            } catch (IOException e) {
                getLogger().severe("Failed to create static directory: " + e.getMessage());
            }
        }
        if (!Files.exists(staticJsDir)) {
            try {
                Files.createDirectories(staticJsDir);
            } catch (IOException e) {
                getLogger().severe("Failed to create static js directory: " + e.getMessage());
            }
        }
        if (!Files.exists(staticCssDir)) {
            try {
                Files.createDirectories(staticCssDir);
            } catch (IOException e) {
                getLogger().severe("Failed to create static css directory: " + e.getMessage());
            }
        }
    }
}

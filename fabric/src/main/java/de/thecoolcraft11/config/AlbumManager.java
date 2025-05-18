package de.thecoolcraft11.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.thecoolcraft11.config.data.Album;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class AlbumManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlbumManager.class);
    private static final String CONFIG_DIR = "config/screenshotUploader/data";
    private static final String ALBUMS_FILE = "albums.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<UUID, Album> albums = new HashMap<>();

    public static void loadAlbums() {
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists() && !configDir.mkdirs()) {
            LOGGER.error("Failed to create config directory during load");
            return;
        }

        File albumsFile = new File(configDir, ALBUMS_FILE);
        if (!albumsFile.exists()) {
            albums = new HashMap<>();
            saveAlbums();
            return;
        }

        try (Reader reader = new FileReader(albumsFile)) {
            JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
            albums = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonObject albumObj = entry.getValue().getAsJsonObject();

                    String title = albumObj.get("title").getAsString();
                    String color = albumObj.get("color").getAsString();
                    String description = albumObj.get("description").getAsString();
                    String coverScreenshotName = albumObj.get("coverScreenshotName").getAsString();

                    Album album = new Album(uuid, title, color, description, coverScreenshotName);
                    albums.put(uuid, album);
                } catch (Exception e) {
                    LOGGER.error("Error parsing album with ID: {}", entry.getKey(), e);
                }
            }

            LOGGER.info("Loaded {} albums", albums.size());
        } catch (Exception e) {
            LOGGER.error("Error loading albums", e);
            albums = new HashMap<>();
        }
    }

    public static void saveAlbums() {
        try {
            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists() && !configDir.mkdirs()) {
                LOGGER.error("Failed to create config directory during save");
                return;
            }

            JsonObject root = getJsonObject();

            try (Writer writer = new FileWriter(new File(configDir, ALBUMS_FILE))) {
                GSON.toJson(root, writer);
            }

            LOGGER.info("Saved {} albums", albums.size());
        } catch (IOException e) {
            LOGGER.error("Error saving albums", e);
        }
    }

    private static @NotNull JsonObject getJsonObject() {
        JsonObject root = new JsonObject();

        for (Map.Entry<UUID, Album> entry : albums.entrySet()) {
            Album album = entry.getValue();
            JsonObject albumObj = new JsonObject();

            albumObj.addProperty("title", album.getTitle());
            albumObj.addProperty("color", album.getColor());
            albumObj.addProperty("description", album.getDescription());
            albumObj.addProperty("coverScreenshotName", album.getCoverScreenshotName());

            root.add(entry.getKey().toString(), albumObj);
        }
        return root;
    }

    public static void addAlbum(Album album) {
        albums.put(album.getUuid(), album);
        saveAlbums();
    }

    public static void updateAlbum(Album album) {
        if (albums.containsKey(album.getUuid())) {
            albums.put(album.getUuid(), album);
            saveAlbums();
        }
    }

    public static void removeAlbum(UUID uuid) {
        if (albums.remove(uuid) != null) {
            saveAlbums();
        }
    }

    public static List<Album> getAllAlbums() {
        return new ArrayList<>(albums.values());
    }

    public static Album getAlbum(UUID uuid) {
        return albums.get(uuid);
    }

}
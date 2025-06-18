package de.thecoolcraft11.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.thecoolcraft11.config.data.ClientConfig;
import de.thecoolcraft11.config.data.ServerConfig;
import de.thecoolcraft11.config.value.Comment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;

import static de.thecoolcraft11.ScreenshotUploaderServer.MOD_ID;


public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    private static ClientConfig clientConfig;
    private static ServerConfig serverConfig;


    public static void initialize(File configDir, boolean isClient) {
        if (isClient) {
            File clientConfigFile = new File(configDir, "config.json");
            if (clientConfigFile.exists()) {
                loadConfig(clientConfigFile, true);
            } else {
                clientConfig = new ClientConfig();
                saveClientConfig(configDir);
            }
        } else {
            File serverConfigFile = new File(configDir, "serverConfig.json");
            if (serverConfigFile.exists()) {
                loadConfig(serverConfigFile, false);
            } else {
                serverConfig = new ServerConfig();
                saveServerConfig(configDir);
            }
        }
    }


    private static void loadConfig(File configFile, boolean isClient) {
        try (FileReader reader = new FileReader(configFile)) {
            JsonElement jsonElement = GSON.fromJson(reader, JsonElement.class);

            if (isClient) {
                clientConfig = GSON.fromJson(jsonElement, ClientConfig.class);
                addMissingFields(clientConfig, jsonElement, configFile);
            } else {
                serverConfig = GSON.fromJson(jsonElement, ServerConfig.class);
                addMissingFields(serverConfig, jsonElement, configFile);
            }

        } catch (IOException e) {
            logger.error("Failed to load config: {}", e.getMessage());
            if (isClient) {
                clientConfig = new ClientConfig();
            } else {
                serverConfig = new ServerConfig();
            }
        }
    }

    private static void addMissingFields(Object config, JsonElement jsonElement, File configFile) {
        Field[] fields = config.getClass().getDeclaredFields();
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        boolean hasNewFields = false;

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                String commentFieldName = "_comment_" + fieldName;

                if (!jsonObject.has(fieldName)) {
                    Object defaultValue = field.get(config);

                    if (field.isAnnotationPresent(Comment.class)) {
                        Comment comment = field.getAnnotation(Comment.class);
                        jsonObject.addProperty(commentFieldName, comment.value());
                    }

                    jsonObject.add(fieldName, GSON.toJsonTree(defaultValue));
                    field.set(config, defaultValue);
                    logger.info("Added missing field: '{}' with default value.", fieldName);
                    hasNewFields = true;
                } else if (field.isAnnotationPresent(Comment.class) && !jsonObject.has(commentFieldName)) {
                    Comment comment = field.getAnnotation(Comment.class);
                    jsonObject.addProperty(commentFieldName, comment.value());
                    logger.info("Added missing comment for field: '{}'", fieldName);
                    hasNewFields = true;
                }

            } catch (IllegalAccessException e) {
                logger.error("Failed to add new config values: {}", e.getMessage());
            }
        }

        if (hasNewFields) {
            saveConfig(configFile, jsonObject);
        }
    }

    private static void saveConfig(File configFile, Object config) {
        try (FileWriter writer = new FileWriter(configFile)) {
            JsonObject jsonObject = new JsonObject();

            for (Field field : config.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    if (field.isAnnotationPresent(Comment.class)) {
                        Comment comment = field.getAnnotation(Comment.class);
                        jsonObject.addProperty("_comment_" + field.getName(), comment.value());
                    }

                    jsonObject.add(field.getName(), GSON.toJsonTree(field.get(config)));

                } catch (IllegalAccessException e) {
                    logger.error("Failed to access field '{}' while saving config: {}", field.getName(), e.getMessage());
                }
            }

            if (jsonObject.has("members")) {
                jsonObject = jsonObject.getAsJsonObject("members");
            }

            GSON.toJson(jsonObject, writer);
            logger.info("Config saved to {}", configFile.getName());

        } catch (IOException e) {
            logger.error("Failed to save config: {}", e.getMessage());
        }
    }

    public static ClientConfig getClientConfig() {
        return clientConfig;
    }

    public static ServerConfig getServerConfig() {
        return serverConfig;
    }


    public static void saveClientConfig(File configDir) {
        File clientConfigFile = new File(configDir, "config.json");
        saveConfig(clientConfigFile, clientConfig);
    }

    public static void saveServerConfig(File configDir) {
        File serverConfigFile = new File(configDir, "serverConfig.json");
        saveConfig(serverConfigFile, serverConfig);
    }


    public static void reloadConfig(File configDir, boolean isClient) {
        if (isClient) {
            File clientConfigFile = new File(configDir, "config.json");
            if (clientConfigFile.exists()) {
                loadConfig(clientConfigFile, true);
                logger.info("Client configuration reloaded successfully.");
            } else {
                logger.warn("Client config file does not exist. Reload ignored.");
            }
        } else {
            File serverConfigFile = new File(configDir, "serverConfig.json");
            if (serverConfigFile.exists()) {
                loadConfig(serverConfigFile, false);
                logger.info("Server configuration reloaded successfully.");
            } else {
                logger.warn("Server config file does not exist. Reload ignored.");
            }
        }
    }

}

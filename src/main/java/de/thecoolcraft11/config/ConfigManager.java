package de.thecoolcraft11.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.thecoolcraft11.config.data.ClientConfig;
import de.thecoolcraft11.config.data.ServerConfig;
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

        for (Field field : fields) {
            try {
                field.setAccessible(true);
                String fieldName = field.getName();
                if (!jsonObject.has(fieldName)) {
                    Object defaultValue = field.get(config);
                    jsonObject.add(fieldName, GSON.toJsonTree(defaultValue));

                    field.set(config, defaultValue);
                    logger.info("Added missing field: '{}' with default value.", fieldName);
                }
            } catch (IllegalAccessException e) {
                logger.error("Failed to add new config values: {}", e.getMessage());
            }
        }

        saveConfig(configFile, jsonObject);
    }

    private static void saveConfig(File configFile, JsonObject jsonObject) {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(jsonObject, writer);
            logger.info("Config saved to {}", configFile.getName());
        } catch (IOException e) {
            logger.error("Failed to save Client config: {}", e.getMessage());
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
        try (FileWriter writer = new FileWriter(clientConfigFile)) {
            GSON.toJson(clientConfig, writer);
        } catch (IOException e) {
            logger.error("Failed to save Config: {}", e.getMessage());
        }
    }

    public static void saveServerConfig(File configDir) {
        File serverConfigFile = new File(configDir, "serverConfig.json");
        try (FileWriter writer = new FileWriter(serverConfigFile)) {
            GSON.toJson(serverConfig, writer);
        } catch (IOException e) {
            logger.error("Failed to save Server config: {}", e.getMessage());
        }
    }

}

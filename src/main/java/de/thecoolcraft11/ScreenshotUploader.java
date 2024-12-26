package de.thecoolcraft11;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ScreenshotUploader implements ModInitializer {
	public static final String MOD_ID = "screenshot-uploader";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final File CONFIG_FILE = new File("./config/screenshotUploader/config.json");
	private static JsonObject config;

	@Override
	public void onInitialize() {
		loadConfig();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			LOGGER.info("Player joined a server. Reloading config...");
			reloadConfig();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LOGGER.info("Player left the server. Reloading config...");
			reloadConfig();
		});
	}

	private void createDefaultConfig() {
		try {
			File parentDir = CONFIG_FILE.getParentFile();
			if (!parentDir.exists() && !parentDir.mkdirs()) {
				throw new IOException("Failed to create config directory: " + parentDir.getAbsolutePath());
			}

			config = new JsonObject();
			config.addProperty("upload_url", "https://chipmunk-polite-loon.ngrok-free.app/api/upload-screenshot");
			config.addProperty("requireNoHud", true);
			config.addProperty("limitToServer", false);
			config.addProperty("limitedServerAddr", "");

			try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
				GSON.toJson(config, writer);
				LOGGER.info("Default config created at: " + CONFIG_FILE.getAbsolutePath());
			}
		} catch (IOException e) {
			LOGGER.error("Failed to create default config: " + e.getMessage());
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
	public static JsonObject getConfig() {
		return config;
	}

	public static void reloadConfig() {
		try (FileReader reader = new FileReader(CONFIG_FILE)) {
			config = GSON.fromJson(reader, JsonObject.class);
			LOGGER.info("Config reloaded successfully!");
		} catch (IOException e) {
			LOGGER.error("Failed to reload config: " + e.getMessage());
		}
	}

}

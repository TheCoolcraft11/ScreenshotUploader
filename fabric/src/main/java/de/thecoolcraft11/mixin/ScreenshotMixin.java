package de.thecoolcraft11.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.ScreenshotData;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.event.KeyInputHandler;
import de.thecoolcraft11.screen.EditScreen;
import de.thecoolcraft11.util.ErrorMessages;
import de.thecoolcraft11.util.ReceivePackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static de.thecoolcraft11.util.ScreenshotUploadHelper.uploadScreenshot;


@Mixin(ScreenshotRecorder.class)
@Environment(EnvType.CLIENT)
public abstract class ScreenshotMixin {

    @Shadow
    private static File getScreenshotFilename(File directory) {
        return null;
    }

    @Unique
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotMixin.class);


    @Inject(at = @At(value = "HEAD"), method = "method_1661")
    private static void screenshotCaptured(NativeImage nativeImage_1, File file_1, Consumer<Text> consumer_1, CallbackInfo ci) {
        if (ConfigManager.getClientConfig().enableMod) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (KeyInputHandler.editKey.wasPressed()) {
                client.send(() -> client.setScreen(new EditScreen(null, null, nativeImage_1, (image) -> {
                    if (image != null) {
                        NativeImage imageCopy = new NativeImage(image.getWidth(), image.getHeight(), false);
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                imageCopy.setColor(x, y, image.getColor(x, y));
                            }
                        }

                        saveEditedFile(file_1.getAbsolutePath(), imageCopy);
                        initializeUpload(imageCopy);
                    }
                })));
            } else {
                initializeUpload(nativeImage_1);
            }

        }
    }

    @Unique
    private static void saveEditedFile(String absolutePath, NativeImage imageCopy) {
        StringBuilder template = new StringBuilder(ConfigManager.getClientConfig().editImageFilePath);

        template.replace(template.indexOf("{fileName}"), "{fileName}".length(), absolutePath);
        File outputFile = new File(template.toString());

        try {
            imageCopy.writeTo(outputFile);
            logger.info("Image saved to {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to save the image to disk", e);
        }
    }

    @Unique
    private static void initializeUpload(NativeImage image) {
        MinecraftClient client = MinecraftClient.getInstance();

        boolean sendWorldData = ConfigManager.getClientConfig().sendWorldData;
        boolean sendSystemData = ConfigManager.getClientConfig().sendSystemInfo;

        String username = client.getSession().getUsername();
        String uuid = String.valueOf(client.getSession().getUuidOrNull());
        String accountType = String.valueOf(client.getSession().getAccountType());
        String worldName = sendWorldData ? getWorldName(client) : "N/A";
        String seed = sendWorldData ? getWorldSeed(client) : "N/A";
        String coordinates = sendWorldData ? getPlayerCoordinates(client) : "N/A";
        String biome = sendWorldData ? getPlayerBiome(client) : "N/A";
        String facingDirection = sendWorldData ? getPlayerFacingDirection(client) : "N/A";
        String dimension = sendWorldData ? getCurrentDimension(client) : "N/A";
        String playerState = sendWorldData ? getPlayerState(client) : "N/A";
        String chunkInfo = sendWorldData ? getChunkInfo(client) : "N/A";
        String entitiesInfo = sendWorldData ? getEntitiesInfo(client) : "N/A";
        String worldInfo = sendWorldData ? getWorldInfo(client) : "N/A";
        String serverAddress = sendWorldData ? getServerAddress(client) : "N/A";
        String clientSettings = sendWorldData ? getClientSettings(client) : "N/A";
        String systemInfo = sendSystemData ? getSystemInfo() : "N/A";
        String currentTime = System.currentTimeMillis() + "";


        ScreenshotData data = new ScreenshotData(username, uuid, accountType, worldName, seed, coordinates, biome, facingDirection, dimension, playerState, chunkInfo, entitiesInfo, worldInfo, serverAddress, clientSettings, systemInfo, currentTime);
        String jsonData = serializeToJson(data);

        logger.error("TST: {}", ConfigManager.getClientConfig().saveJsonData);

        if (ConfigManager.getClientConfig().saveJsonData) {
            logger.error("A");
            String filename = Objects.requireNonNull(getScreenshotFilename(Paths.get(client.runDirectory.getName(), "screenshots").toFile())).getName();
            File jsonFile = new File(client.runDirectory, "screenshots/" + filename.replace(".png", ".json"));
            try {
                boolean wasCreated = jsonFile.createNewFile();
                if (!wasCreated) {
                    logger.info("JSON file already exists: {}", jsonFile.getAbsolutePath());
                }
                Files.writeString(jsonFile.toPath(), jsonData);
            } catch (Exception e) {
                logger.error("Failed to save the JSON data to disk", e);
            }
        }

        if (ConfigManager.getClientConfig().requireNoHud && !client.options.hudHidden ||
                ConfigManager.getClientConfig().limitToServer &&
                        !Objects.equals(Objects.requireNonNull(client.getCurrentServerEntry()).address, ConfigManager.getClientConfig().limitedServerAddr)) {
            return;
        }


        new Thread(() -> {
            List<String> targets = new ArrayList<>();
            if (ConfigManager.getClientConfig().uploadScreenshotsToUrl) {
                for (Map<String, String> value : ConfigManager.getClientConfig().upload_urls.values()) {
                    targets.add(value.get("upload"));
                }

            }
            if (ReceivePackets.serverSiteAddress != null) {
                targets.add(ReceivePackets.serverSiteAddress);
            }
            StringBuilder messageBuilder = getStringBuilder(targets);

            client.inGameHud.getChatHud().addMessage(
                    Text.translatable("message.screenshot_uploader.uploading_to", Text.literal(messageBuilder.toString()).styled(style -> style.withColor(Formatting.AQUA))));

            List<JsonObject> uploadResults = uploadScreenshot(image, jsonData, targets);

            client.execute(() ->
                    uploadResults.forEach(uploadResult -> {
                        String statusMessage = uploadResult.get("status").getAsString();

                        if ("success".equals(statusMessage)) {
                            JsonObject responseBody = null;
                            try {
                                responseBody = JsonParser.parseString(uploadResult.get("responseBody").getAsString()).getAsJsonObject();
                            } catch (Exception e) {
                                logger.error("Failed to parse responseBody", e);
                            }

                            String baseMessage = "message.screenshot_uploader.upload_success";
                            Text clickableLink = Text.empty();
                            Text clickableLink2 = Text.empty();
                            Text clickableLink3 = Text.empty();
                            Text clickableLink4 = Text.empty();
                            Text clickableLink5 = Text.empty();

                            if (responseBody != null) {
                                String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
                                String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;


                                if (screenshotUrl != null && galleryUrl != null) {
                                    clickableLink = Text.translatable("message.screenshot_uploader.open_screenshot")
                                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/open-screenshot \"" + screenshotUrl + "\""))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.see_screenshot"))).withColor(Formatting.AQUA));
                                }
                                if (screenshotUrl != null) {
                                    clickableLink2 = Text.translatable("message.screenshot_uploader.open_link")
                                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, screenshotUrl))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.open_website"))).withColor(Formatting.BLUE));
                                }

                                if (galleryUrl != null) {
                                    clickableLink3 = Text.translatable("message.screenshot_uploader.open_all")
                                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, galleryUrl))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.see_screenshots"))).withColor(Formatting.YELLOW));
                                }
                                if (screenshotUrl != null) {
                                    clickableLink4 = Text.translatable("message.screenshot_uploader.copy")
                                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, screenshotUrl))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.copy_url"))).withColor(Formatting.GRAY));
                                }
                                if (screenshotUrl != null) {
                                    clickableLink5 = Text.translatable("message.screenshot_uploader.share")
                                            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, screenshotUrl))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.share_screenshot"))).withColor(Formatting.DARK_GREEN));
                                }

                                if (screenshotUrl == null && galleryUrl == null) {
                                    baseMessage = "message.screenshot_uploader_no_return_url";
                                }

                                Text finalMessage = Text.translatable(baseMessage, clickableLink, clickableLink2, clickableLink3, clickableLink4, clickableLink5);

                                client.inGameHud.getChatHud().addMessage(finalMessage);
                            }
                        } else {
                            String errorMessage = uploadResult.has("message") ? uploadResult.get("message").getAsString() : "Unknown error";
                            Text errorText = Text.translatable("message.screenshot_uploader.upload_failed", errorMessage.split(":")[0])
                                    .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(ErrorMessages.getErrorDescription(errorMessage.split(":")[0])))));

                            client.inGameHud.getChatHud().addMessage(errorText);
                        }
                    })
            );
        }).start();
    }

    @Unique
    private static @NotNull StringBuilder getStringBuilder(List<String> targets) {
        StringBuilder messageBuilder = new StringBuilder();

        for (int i = 0; i < targets.size(); i++) {
            String target = targets.get(i);
            String targetText = target.equals("mcserver://this") || target.equals("mcserver://chunked") ? "This Server" : target;
            messageBuilder.append(targetText);

            if (i < targets.size() - 1) {
                messageBuilder.append(", ");
            }
        }
        return messageBuilder;
    }

    @Unique
    private static String getWorldName(MinecraftClient client) {
        return client.world != null ? client.world.getRegistryKey().getValue().toString() : "Unknown World";
    }

    @Unique
    private static String getWorldSeed(MinecraftClient client) {
        if (client.getServer() != null) {
            return String.valueOf(client.getServer().getOverworld().getSeed());
        }
        return "Unknown Seed";
    }

    @Unique
    private static String getPlayerCoordinates(MinecraftClient client) {
        if (client.player != null) {
            BlockPos pos = client.player.getBlockPos();
            return String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ());
        }
        return "Unknown Coordinates";
    }

    @Unique
    private static String getPlayerBiome(MinecraftClient client) {
        if (client.world != null && client.player != null) {
            RegistryEntry<Biome> biomeEntry = client.world.getBiome(client.player.getBlockPos());

            String biomeName = biomeEntry.getIdAsString();
            String formattedName = biomeName.split(":")[1];
            formattedName = formattedName.replace("_", " ");
            formattedName = formattedName.substring(0, 1).toUpperCase() + formattedName.substring(1);

            return formattedName;
        }
        return "Unknown Biome";
    }

    @Unique
    private static String getPlayerFacingDirection(MinecraftClient client) {
        if (client.player != null) {
            Direction direction = client.player.getHorizontalFacing();
            return direction.asString();
        }
        return "Unknown Direction";
    }

    @Unique
    private static String getCurrentDimension(MinecraftClient client) {
        return client.world != null ? client.world.getRegistryKey().getValue().toString() : "Unknown Dimension";
    }

    @Unique
    private static String getPlayerState(MinecraftClient client) {
        if (client.player != null) {
            return String.format("Speed: %.2f b/s, Health: %.2f, Food: %d, Air: %d",
                    client.player.getVelocity().length() * 20,
                    client.player.getHealth(),
                    client.player.getHungerManager().getFoodLevel() / 2,
                    client.player.getAir());
        }
        return "Unknown State";
    }


    @Unique
    private static String getChunkInfo(MinecraftClient client) {
        if (client.player != null) {
            BlockPos pos = client.player.getBlockPos();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            return String.format("Chunk: [%d, %d]", chunkX, chunkZ);
        }
        return "Unknown Chunk Info";
    }

    @Unique
    private static String getEntitiesInfo(MinecraftClient client) {
        if (client.world != null) {
            Iterable<Entity> entities = client.world.getEntities();
            int entityCount = 0;

            for (Entity ignored : entities) {
                entityCount++;
            }

            return String.format("Loaded Entities: %d", entityCount);
        }
        return "No Entity Info";
    }

    @Unique
    private static String getWorldInfo(MinecraftClient client) {
        if (client.world != null) {
            WorldProperties properties = client.world.getLevelProperties();
            return String.format("Time: %s, Weather: %s, Difficulty: %s",
                    convertMinecraftTimeToHumanReadable(properties.getTimeOfDay()),
                    client.world.isRaining() ? "Raining" : "Clear",
                    client.world.getDifficulty().getName());
        }
        return "No World Info";
    }

    @Unique
    private static String convertMinecraftTimeToHumanReadable(long ticks) {
        int hours = (int) ((ticks / 1000 + 6) % 24);
        int minutes = (int) ((ticks % 1000) * 60 / 1000);

        return String.format("%02d:%02d", hours, minutes);
    }


    @Unique
    private static String getServerAddress(MinecraftClient client) {
        ServerInfo serverInfo = client.getCurrentServerEntry();
        String singlePlayerWorldName = client.getServer() != null ? client.getServer().getSaveProperties().getLevelName() : "Singleplayer";
        return !client.isInSingleplayer() ? serverInfo != null ? serverInfo.address : "Unknown Server" : singlePlayerWorldName;
    }

    @Unique
    private static String getClientSettings(MinecraftClient client) {
        return String.format("Graphics: %s, V-Sync: %b, Fullscreen: %b, Language: %s, FPS: %d / %d, Render Distance: %d",
                client.options.getGraphicsMode().getValue(),
                client.options.getEnableVsync().getValue(),
                client.options.getFullscreen(),
                client.getLanguageManager().getLanguage(),
                client.getCurrentFps(),
                client.options.getMaxFps().getValue(),
                client.options.getViewDistance().getValue()
        );
    }

    @Unique
    private static String getSystemInfo() {
        return String.format("OS: %s %s (%s), Java: %s, Version: %s",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"),
                FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString()
        );
    }


    @Unique
    private static String serializeToJson(ScreenshotData data) {
        Gson gson = new Gson();
        return gson.toJson(data);
    }
}



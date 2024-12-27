package de.thecoolcraft11.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.ScreenshotData;
import de.thecoolcraft11.ScreenshotUpload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldProperties;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.*;
import java.util.Objects;
import java.util.function.Consumer;

import static de.thecoolcraft11.ScreenshotUpload.uploadScreenshot;
import static de.thecoolcraft11.ScreenshotUploader.getConfig;


@Mixin(ScreenshotRecorder.class)
public class ScreenshotMixin {

    @Unique
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotMixin.class);

    @Unique
    private static final JsonObject config = getConfig();

    @Inject(at = @At(value = "HEAD"), method = "method_1661")
    private static void screenshotCaptured(NativeImage nativeImage_1, File file_1, Consumer<Text> consumer_1, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (config.get("requireNoHud").getAsBoolean() && !client.options.hudHidden ||
                config.get("limitToServer").getAsBoolean() &&
                        !Objects.equals(Objects.requireNonNull(client.getCurrentServerEntry()).address, config.get("limitedServerAddr").getAsString())) {
            return;
        }

        // Collect necessary data for the upload
        String username = client.getSession().getUsername();
        String uuid = String.valueOf(client.getSession().getUuidOrNull());
        String accountType = String.valueOf(client.getSession().getAccountType());
        String worldName = getWorldName(client);
        String coordinates = getPlayerCoordinates(client);
        String biome = getPlayerBiome(client);
        String facingDirection = getPlayerFacingDirection(client);
        String dimension = getCurrentDimension(client);
        String playerState = getPlayerState(client);
        String chunkInfo = getChunkInfo(client);
        String entitiesInfo = getEntitiesInfo(client);
        String worldInfo = getWorldInfo(client);
        String serverAddress = getServerAddress(client);
        String clientSettings = getClientSettings(client);
        String systemInfo = getSystemInfo();

        ScreenshotData data = new ScreenshotData(username, uuid, accountType, worldName, coordinates, biome, facingDirection, dimension, playerState, chunkInfo, entitiesInfo, worldInfo, serverAddress, clientSettings, systemInfo);

        String jsonData = serializeToJson(data);

        new Thread(() -> {
            JsonObject uploadResult = uploadScreenshot(nativeImage_1, jsonData);

            client.execute(() -> {
                String statusMessage = uploadResult.get("status").getAsString();

                if ("success".equals(statusMessage)) {
                    JsonObject responseBody = null;
                    try {
                        responseBody = JsonParser.parseString(uploadResult.get("responseBody").getAsString()).getAsJsonObject();
                    } catch (Exception e) {
                        logger.error("Failed to parse responseBody", e);
                    }

                    if (responseBody != null) {
                        String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
                        String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;

                        Text fullMessage = Text.literal("Screenshot uploaded successfully! ");

                        if (screenshotUrl != null) {
                            String linkText = "[OPEN]";
                            Text clickableLink = Text.literal(linkText)
                                    .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, screenshotUrl))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To See Screenshot"))).withColor(Formatting.AQUA));
                            fullMessage = fullMessage.copy().append(clickableLink);
                        }

                        if (galleryUrl != null) {
                            String galleryText = "[ALL]";
                            Text clickableLink2 = Text.literal(galleryText)
                                    .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, galleryUrl))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To See All Screenshots"))).withColor(Formatting.YELLOW));
                            fullMessage = fullMessage.copy().append(" ").append(clickableLink2);
                        }

                        if (screenshotUrl == null && galleryUrl == null) {
                            fullMessage = Text.literal("Screenshot upload failed: The server did not return valid URLs.");
                        }

                        client.inGameHud.getChatHud().addMessage(fullMessage);
                    }
                } else {
                    String errorMessage = uploadResult.has("message") ? uploadResult.get("message").getAsString() : "Unknown error";
                    Text errorText = Text.literal("Screenshot upload failed: " + errorMessage);
                    client.inGameHud.getChatHud().addMessage(errorText);
                }
            });
        }).start();
    }





    @Unique
    private static String getWorldName(MinecraftClient client) {
        return client.world != null ? client.world.getRegistryKey().getValue().toString() : "Unknown World";
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
            return String.format("Flying: %b, Sneaking: %b, Gliding: %b",
                    client.player.getAbilities().flying,
                    client.player.isSneaking(),
                    client.player.isFallFlying());
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
            return String.format("Time: %d, Weather: %s, Difficulty: %s",
                    properties.getTime(),
                    client.world.isRaining() ? "Raining" : "Clear",
                    client.world.getDifficulty().getName());
        }
        return "No World Info";
    }

    @Unique
    private static String getServerAddress(MinecraftClient client) {
        ServerInfo serverInfo = client.getCurrentServerEntry();
        return serverInfo != null ? serverInfo.address : "Singleplayer or Unknown Server";
    }

    @Unique
    private static String getClientSettings(MinecraftClient client) {
        return String.format("Graphics: %s, V-Sync: %b, Fullscreen: %b, Language: %s",
                client.options.getGraphicsMode().getValue(),
                client.options.getEnableVsync().getValue(),
                client.options.getFullscreen(),
                client.getLanguageManager().getLanguage());
    }

    @Unique
    private static String getSystemInfo() {
        return String.format("OS: %s %s (%s), Java: %s",
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                System.getProperty("java.version"));
    }

    @Unique
    private static String serializeToJson(ScreenshotData data) {
        Gson gson = new Gson();
        return gson.toJson(data);
    }
}


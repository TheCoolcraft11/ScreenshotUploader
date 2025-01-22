package de.thecoolcraft11.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.thecoolcraft11.ScreenshotData;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.util.ErrorMessages;
import de.thecoolcraft11.util.ReceivePackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static de.thecoolcraft11.util.ScreenshotUploadHelper.uploadScreenshot;


@Mixin(ScreenshotRecorder.class)
@Environment(EnvType.CLIENT)
public class ScreenshotMixin {

    @Unique
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotMixin.class);


    @Inject(at = @At(value = "HEAD"), method = "method_1661")
    private static void screenshotCaptured(NativeImage nativeImage_1, File file_1, Consumer<Text> consumer_1, CallbackInfo ci) {
        if (ConfigManager.getClientConfig().enableMod) {
            MinecraftClient client = MinecraftClient.getInstance();

            if (ConfigManager.getClientConfig().requireNoHud && !client.options.hudHidden ||
                    ConfigManager.getClientConfig().limitToServer &&
                            !Objects.equals(Objects.requireNonNull(client.getCurrentServerEntry()).address, ConfigManager.getClientConfig().limitedServerAddr)) {
                return;
            }

            boolean sendWorldData = ConfigManager.getClientConfig().sendWorldData;
            boolean sendSystemData = ConfigManager.getClientConfig().sendSystemInfo;

            String username = client.getSession().getUsername();
            String uuid = String.valueOf(client.getSession().getUuidOrNull());
            String accountType = String.valueOf(client.getSession().getAccountType());
            String worldName = sendWorldData ? getWorldName(client) : "N/A";
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

            ScreenshotData data = new ScreenshotData(username, uuid, accountType, worldName, coordinates, biome, facingDirection, dimension, playerState, chunkInfo, entitiesInfo, worldInfo, serverAddress, clientSettings, systemInfo, currentTime);

            String jsonData = serializeToJson(data);

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

                List<JsonObject> uploadResults = uploadScreenshot(nativeImage_1, jsonData, targets);

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

                                if (responseBody != null) {
                                    String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
                                    String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;


                                    if (screenshotUrl != null) {
                                        clickableLink = Text.translatable("message.screenshot_uploader.open_link")
                                                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, screenshotUrl))
                                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.see_screenshot"))).withColor(Formatting.AQUA));
                                    }

                                    if (galleryUrl != null) {
                                        clickableLink2 = Text.translatable("message.screenshot_uploader.open_all")
                                                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, galleryUrl))
                                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.see_screenshots"))).withColor(Formatting.YELLOW));
                                    }

                                    if (screenshotUrl == null && galleryUrl == null) {
                                        baseMessage = "message.screenshot_uploader_no_return_url";
                                    }

                                    Text finalMessage = Text.translatable(baseMessage, clickableLink, clickableLink2);

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
    }

    @Unique
    private static @NotNull StringBuilder getStringBuilder(List<String> targets) {
        StringBuilder messageBuilder = new StringBuilder();

        for (int i = 0; i < targets.size(); i++) {
            String target = targets.get(i);
            String targetText = target.equals("mcserver://this") ? "This Server" : target;
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



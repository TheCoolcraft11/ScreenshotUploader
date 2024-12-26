package de.thecoolcraft11.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.thecoolcraft11.ScreenshotData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldProperties;
import net.minecraft.util.math.Direction;
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
    private static final JsonObject config = getConfig();

    @Inject(at = @At(value = "HEAD"), method = "method_1661")
    private static void screenshotCaptured(NativeImage nativeImage_1, File file_1, Consumer<Text> consumer_1, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (config.get("requireNoHud").getAsBoolean() && !client.options.hudHidden || config.get("limitToServer").getAsBoolean() && !Objects.equals(Objects.requireNonNull(client.getCurrentServerEntry()).address, config.get("limitedServerAddr").getAsString())) {
            return;
        }

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

        uploadScreenshot(nativeImage_1, jsonData);
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

            for (Entity entity : entities) {
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

package de.thecoolcraft11.util;

import com.google.gson.Gson;
import de.thecoolcraft11.ScreenshotData;
import de.thecoolcraft11.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Unique;

public class ScreenshotDataHelper {
    public static String getJSONData(MinecraftClient client) {
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
        return serializeToJson(data);
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

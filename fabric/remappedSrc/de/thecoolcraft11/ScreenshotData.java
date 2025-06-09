package de.thecoolcraft11;

import com.google.gson.annotations.SerializedName;

public class ScreenshotData {
    @SerializedName("username")
    private final String username;

    @SerializedName("uuid")
    private final String uuid;

    @SerializedName("accountType")
    private final String accountType;

    @SerializedName("world_name")
    private final String worldName;
    @SerializedName("world_seed")
    private final String worldSeed;

    @SerializedName("coordinates")
    private final String coordinates;

    @SerializedName("biome")
    private final String biome;

    @SerializedName("facing_direction")
    private final String facingDirection;

    @SerializedName("dimension")
    private final String dimension;

    @SerializedName("player_state")
    private final String playerState;

    @SerializedName("chunk_info")
    private final String chunkInfo;

    @SerializedName("entities_info")
    private final String entitiesInfo;

    @SerializedName("world_info")
    private final String worldInfo;

    @SerializedName("server_address")
    private final String serverAddress;

    @SerializedName("client_settings")
    private final String clientSettings;

    @SerializedName("system_info")
    private final String systemInfo;

    @SerializedName("current_time")
    private final String currentTime;

    // Constructor
    public ScreenshotData(String username, String uuid, String accountType, String worldName, String worldSeed, String coordinates, String biome, String facingDirection, String dimension, String playerState, String chunkInfo, String entitiesInfo, String worldInfo, String serverAddress, String clientSettings, String systemInfo, String currentTime) {
        this.username = username;
        this.uuid = uuid;
        this.accountType = accountType;
        this.worldName = worldName;
        this.worldSeed = worldSeed;
        this.coordinates = coordinates;
        this.biome = biome;
        this.facingDirection = facingDirection;
        this.dimension = dimension;
        this.playerState = playerState;
        this.chunkInfo = chunkInfo;
        this.entitiesInfo = entitiesInfo;
        this.worldInfo = worldInfo;
        this.serverAddress = serverAddress;
        this.clientSettings = clientSettings;
        this.systemInfo = systemInfo;
        this.currentTime = currentTime;
    }

}



package de.thecoolcraft11;

import com.google.gson.annotations.SerializedName;

public class ScreenshotData {
    @SerializedName("username")
    private String username;

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("accountType")
    private String accountType;

    @SerializedName("world_name")
    private String worldName;

    @SerializedName("coordinates")
    private String coordinates;

    @SerializedName("biome")
    private String biome;

    @SerializedName("facing_direction")
    private String facingDirection;

    @SerializedName("dimension")
    private String dimension;

    @SerializedName("player_state")
    private String playerState;

    @SerializedName("chunk_info")
    private String chunkInfo;

    @SerializedName("entities_info")
    private String entitiesInfo;

    @SerializedName("world_info")
    private String worldInfo;

    @SerializedName("server_address")
    private String serverAddress;

    @SerializedName("client_settings")
    private String clientSettings;

    @SerializedName("system_info")
    private String systemInfo;

    // Constructor and getters/setters
    public ScreenshotData(String username, String uuid, String accountType, String worldName, String coordinates, String biome, String facingDirection, String dimension, String playerState, String chunkInfo, String entitiesInfo, String worldInfo, String serverAddress, String clientSettings, String systemInfo) {
        this.username = username;
        this.uuid = uuid;
        this.accountType = accountType;
        this.worldName = worldName;
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
    }

    // Getters and setters omitted for brevity
}

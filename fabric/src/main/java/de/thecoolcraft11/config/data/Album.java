package de.thecoolcraft11.config.data;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class Album {
    @SerializedName("title")
    private String title;

    @SerializedName("color")
    private String color;

    @SerializedName("description")
    private String description;

    @SerializedName("coverScreenshotName")
    private String coverScreenshotName;

    private transient final UUID uuid;

    public Album(String title, String color, String description, String coverScreenshotName) {
        this.title = title;
        this.color = color;
        this.description = description;
        this.coverScreenshotName = coverScreenshotName;
        this.uuid = UUID.randomUUID();
    }

    public Album(UUID uuid, String title, String color, String description, String coverScreenshotName) {
        this.uuid = uuid;
        this.title = title;
        this.color = color;
        this.description = description;
        this.coverScreenshotName = coverScreenshotName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverScreenshotName() {
        return coverScreenshotName;
    }

    public void setCoverScreenshotName(String coverScreenshotName) {
        this.coverScreenshotName = coverScreenshotName;
    }

    public UUID getUuid() {
        return uuid;
    }
}

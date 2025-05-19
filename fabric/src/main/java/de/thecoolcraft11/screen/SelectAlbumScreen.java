package de.thecoolcraft11.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.thecoolcraft11.config.AlbumManager;
import de.thecoolcraft11.config.data.Album;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SelectAlbumScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(SelectAlbumScreen.class);

    private final Screen parent;
    private final String screenshotPath;
    private final List<Album> albums = new ArrayList<>();
    private int selectedAlbumIndex = -1;
    private int scrollOffset = 0;
    private final int maxAlbumsVisible = 6;
    private boolean setAsCover = false;

    public SelectAlbumScreen(Screen parent, String screenshotPath) {
        super(Text.translatable("gui.screenshot_uploader.select_album.title"));
        this.parent = parent;
        this.screenshotPath = screenshotPath;
        if (parent instanceof GalleryScreen galleryScreen) {
            galleryScreen.cancelAllAsyncTasks();
        }
    }

    @Override
    protected void init() {
        super.init();

        albums.clear();
        albums.addAll(AlbumManager.getAllAlbums());

        if (parent instanceof GalleryScreen galleryScreen) {
            galleryScreen.cancelAllAsyncTasks();
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonWidth = 200;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▲"),
                button -> {
                    if (scrollOffset > 0) {
                        scrollOffset--;
                    }
                }
        ).position(centerX + buttonWidth / 2 + 10, centerY - 100).size(20, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▼"),
                button -> {
                    if (scrollOffset < Math.max(0, albums.size() - maxAlbumsVisible)) {
                        scrollOffset++;
                    }
                }
        ).position(centerX + buttonWidth / 2 + 10, centerY + 80).size(20, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                getSetAsCoverToggleText(),
                button -> {
                    setAsCover = !setAsCover;
                    button.setMessage(getSetAsCoverToggleText());
                }
        ).position(centerX - 100, centerY + 90).size(200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.select_album.assign"),
                button -> {
                    if (selectedAlbumIndex >= 0 && selectedAlbumIndex < albums.size()) {
                        UUID albumUuid = albums.get(selectedAlbumIndex).getUuid();
                        assignScreenshotToAlbum(albumUuid);

                        if (setAsCover) {
                            assignScreenshotAsAlbumCover(albumUuid);
                        }

                        if (this.client != null) {
                            if (parent instanceof GalleryScreen galleryScreen) {
                                galleryScreen.cancelAllAsyncTasks();
                                client.setScreen(new GalleryScreen());
                            } else {
                                client.setScreen(parent);
                            }
                        }
                    }
                }
        ).position(centerX - 100, centerY + 120).size(200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.cancel"),
                button -> close()
        ).position(centerX - 100, centerY + 150).size(200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.select_album.create"),
                button -> {
                    if (client != null) {
                        client.setScreen(new AlbumConfigScreen(this));
                    }
                }
        ).position(centerX - 100, height - 40).size(200, 20).build());
    }

    private Text getSetAsCoverToggleText() {
        return Text.translatable("gui.screenshot_uploader.select_album.set_as_cover")
                .append(": ")
                .append(setAsCover
                        ? Text.translatable("options.on")
                        : Text.translatable("options.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int centerY = height / 2;

        context.drawCenteredTextWithShadow(textRenderer, this.title, centerX, 20, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.select_album.album_prompt"), centerX - 100, 50, 0xAAAAAA);

        int y = centerY - 70;
        int visibleCount = Math.min(maxAlbumsVisible, albums.size() - scrollOffset);

        for (int i = 0; i < visibleCount; i++) {
            int albumIndex = i + scrollOffset;
            Album album = albums.get(albumIndex);

            boolean isSelected = albumIndex == selectedAlbumIndex;
            int bgColor = isSelected ? 0x80808080 : 0x20808080;

            context.fill(centerX - 100, y - 2, centerX + 100, y + 22, bgColor);


            context.drawTextWithShadow(textRenderer, Text.literal(album.getTitle()), centerX - 90, y + 3, 0xFFFFFF);

            String desc = album.getDescription();
            if (desc != null && !desc.isEmpty()) {
                if (desc.length() > 30) {
                    desc = desc.substring(0, 27) + "...";
                }
                context.drawTextWithShadow(textRenderer, Text.literal(desc), centerX - 90, y + 14, 0xAAAAAA);
            }

            try {
                String albumColor = album.getColor();
                if (albumColor != null && albumColor.startsWith("#")) {
                    int colorInt = Integer.parseInt(albumColor.substring(1), 16);
                    context.fill(centerX + 70, y, centerX + 95, y + 20, 0xFF000000 | colorInt);
                }
            } catch (Exception e) {
                context.fill(centerX + 70, y, centerX + 95, y + 20, 0xFFFF0000);
            }

            y += 30;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;
        int y = centerY - 70;

        if (mouseX >= centerX - 100 && mouseX <= centerX + 100) {
            int visibleCount = Math.min(maxAlbumsVisible, albums.size() - scrollOffset);

            for (int i = 0; i < visibleCount; i++) {
                if (mouseY >= y - 2 && mouseY <= y + 22) {
                    selectedAlbumIndex = i + scrollOffset;
                    return true;
                }
                y += 30;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount < 0) {
            if (scrollOffset < Math.max(0, albums.size() - maxAlbumsVisible)) {
                scrollOffset++;
                return true;
            }
        } else if (verticalAmount > 0) {
            if (scrollOffset > 0) {
                scrollOffset--;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void assignScreenshotToAlbum(UUID albumUuid) {
        try {
            String jsonPath = screenshotPath.substring(0, screenshotPath.lastIndexOf('.')) + ".json";
            File jsonFile = new File(jsonPath);

            JsonObject metaData;
            if (jsonFile.exists()) {
                try (Reader reader = new FileReader(jsonFile)) {
                    metaData = new Gson().fromJson(reader, JsonObject.class);
                }
            } else {
                metaData = new JsonObject();
            }

            metaData.addProperty("album", albumUuid.toString());

            try (Writer writer = new FileWriter(jsonFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(metaData, writer);
            }
        } catch (IOException e) {
            logger.error("Error updating screenshot metadata file", e);
        }
    }

    private void assignScreenshotAsAlbumCover(UUID albumUuid) {
        try {
            File screenshotFile = new File(screenshotPath);
            String screenshotName = screenshotFile.getName();

            Album album = AlbumManager.getAlbum(albumUuid);
            if (album != null) {
                album.setCoverScreenshotName(screenshotName);
                AlbumManager.updateAlbum(album);
                logger.info("Set screenshot {} as cover for album {}", screenshotName, album.getTitle());
            } else {
                logger.error("Could not find album with UUID: {}", albumUuid);
            }
        } catch (Exception e) {
            logger.error("Error setting screenshot as album cover", e);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            if (parent instanceof GalleryScreen galleryScreen) {
                galleryScreen.cancelAllAsyncTasks();
                client.setScreen(new GalleryScreen());
            } else {
                client.setScreen(parent);
            }
        }
    }
}

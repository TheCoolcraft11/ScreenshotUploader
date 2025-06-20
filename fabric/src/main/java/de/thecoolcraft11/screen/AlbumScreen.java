package de.thecoolcraft11.screen;

import de.thecoolcraft11.config.AlbumManager;
import de.thecoolcraft11.config.data.Album;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AlbumScreen extends Screen {
    private static final Logger logger = LoggerFactory.getLogger(AlbumScreen.class);
    private static final int ALBUMS_PER_ROW = 3;
    private static int ALBUM_WIDTH = 220;
    private static int ALBUM_HEIGHT = 150;
    private static int GAP = 20;
    private static int TOP_PADDING = 40;
    private static final Identifier FOLDER_ICON = Identifier.of("screenshot-uploader", "textures/gui/album_screen/folder_icon.png");
    private static final Identifier FOLDER_ICON_COVER = Identifier.of("screenshot-uploader", "textures/gui/album_screen/folder_icon_cover.png");


    private final List<Album> albums = new ArrayList<>();
    private final Map<String, Identifier> coverImageIds = new HashMap<>();
    private int scrollOffset = 0;
    private static Screen parent;

    public AlbumScreen(Screen passedParent) {
        super(Text.translatable("gui.screenshot_uploader.album_screen.title"));
        parent = passedParent;
    }

    @Override
    protected void init() {
        super.init();

        albums.clear();
        coverImageIds.clear();
        scrollOffset = 0;
        albums.addAll(AlbumManager.getAllAlbums());

        int scaledHeight = height / 6;
        int scaledWidth = (scaledHeight * 16) / 9;
        int scaledGap = scaledHeight / 10;

        TOP_PADDING = height / 20;
        ALBUM_WIDTH = scaledWidth;
        ALBUM_HEIGHT = scaledHeight;
        GAP = scaledGap;

        int buttonWidth = width / 8;
        int buttonHeight = height / 25;

        ButtonWidget backButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.album_screen.back"),
                button -> {
                    if (client != null) {
                        if (parent instanceof GalleryScreen galleryScreen) galleryScreen.cancelAllAsyncTasks();
                        client.setScreen(new GalleryScreen());
                    }
                }
        ).dimensions(5, 5, buttonWidth, buttonHeight).build();

        ButtonWidget configButton = ButtonWidget.builder(
                Text.translatable("gui.screenshot_uploader.album_screen.album_config"),
                button -> {
                    if (client != null) {
                        client.setScreen(new AlbumConfigScreen(this));
                    }
                }
        ).dimensions(5 + 5 + buttonWidth, 5, buttonWidth, buttonHeight).build();

        addDrawableChild(backButton);

        addDrawableChild(configButton);

        loadCoverImagesAsync();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        Text title = Text.translatable("gui.screenshot_uploader.album_screen.title");
        context.drawCenteredTextWithShadow(this.textRenderer, title, this.width / 2, 10, 0xFFFFFF);

        int startX = ((width - (ALBUMS_PER_ROW * ALBUM_WIDTH + (ALBUMS_PER_ROW - 1) * GAP)) / 2) + 25;
        int y = TOP_PADDING - scrollOffset + 15;

        int albumIndex = 0;
        for (Album album : albums) {
            int row = albumIndex / ALBUMS_PER_ROW;
            int col = albumIndex % ALBUMS_PER_ROW;

            int x = startX + col * (ALBUM_WIDTH + GAP);
            int albumY = y + row * (ALBUM_HEIGHT + GAP);

            if (albumY + ALBUM_HEIGHT >= 0 && albumY <= height) {
                renderAlbum(context, album, x, albumY, mouseX, mouseY);
            }

            albumIndex++;
        }

    }

    private void renderAlbum(DrawContext context, Album album, int x, int y, int mouseX, int mouseY) {
        int color = 0xFF333333;
        try {
            color = Integer.parseInt(album.getColor().replace("#", ""), 16) | 0xFF000000;
        } catch (NumberFormatException e) {
            logger.error("Invalid color format for album: {}", album.getTitle());
        }

        int iconSizeOffset = (int) (Math.min(ALBUM_WIDTH, ALBUM_HEIGHT) * 0.70);
        int iconSize = Math.min(ALBUM_WIDTH, ALBUM_HEIGHT) + iconSizeOffset;
        int iconX = x + (ALBUM_WIDTH - iconSize) / 2;
        int iconY = y + (ALBUM_HEIGHT - iconSize) / 2;

        context.drawTexture(RenderLayer::getGuiTextured, FOLDER_ICON, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize, color);
        Identifier coverId = coverImageIds.get(album.getCoverScreenshotName());
        if (coverId != null) {
            int coverWidth = (int) (ALBUM_WIDTH * 0.65);
            int coverHeight = (int) (ALBUM_HEIGHT * 0.65);
            int coverOffsetX = (int) (ALBUM_WIDTH * 0.15);
            int coverOffsetY = (int) (ALBUM_HEIGHT * 0.12);
            int coverX = (x + (ALBUM_WIDTH - coverWidth) / 2) + coverOffsetX;
            int coverY = (y + (ALBUM_HEIGHT - coverHeight) / 2) - coverOffsetY;

            context.drawTexture(RenderLayer::getGuiTextured, coverId, coverX, coverY, 0, 0, coverWidth, coverHeight, coverWidth, coverHeight);
        }
        boolean isHovering = mouseX >= x && mouseX <= x + ALBUM_WIDTH && mouseY >= y && mouseY <= y + ALBUM_HEIGHT;
        if (!isHovering) {
            context.drawTexture(RenderLayer::getGuiTextured, FOLDER_ICON_COVER, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize, color);
        }

        int titleX = x + 5;
        int titleY = y + ALBUM_HEIGHT - this.textRenderer.fontHeight - 5;
        context.fill(titleX - 2, titleY - 2, titleX + this.textRenderer.getWidth(album.getTitle()) + 2, titleY + this.textRenderer.fontHeight + 2, 0x80000000);
        context.drawText(this.textRenderer, album.getTitle(), titleX, titleY, 0xFFFFFF, false);

        if (isHovering) {
            context.fill(x, y, x + ALBUM_WIDTH, y + ALBUM_HEIGHT, 0x40FFFFFF);

            List<Text> tooltipLines = new ArrayList<>();
            tooltipLines.add(Text.literal(album.getTitle()).formatted(net.minecraft.util.Formatting.YELLOW, net.minecraft.util.Formatting.BOLD));

            if (album.getDescription() != null && !album.getDescription().isEmpty()) {
                tooltipLines.add(Text.literal(album.getDescription()).formatted(net.minecraft.util.Formatting.GRAY));
            }

            context.drawTooltip(this.textRenderer, tooltipLines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int startX = (width - (ALBUMS_PER_ROW * ALBUM_WIDTH + (ALBUMS_PER_ROW - 1) * GAP)) / 2;
        int y = TOP_PADDING - scrollOffset;

        int albumIndex = 0;
        for (Album album : albums) {
            int row = albumIndex / ALBUMS_PER_ROW;
            int col = albumIndex % ALBUMS_PER_ROW;

            int x = startX + col * (ALBUM_WIDTH + GAP);
            int albumY = y + row * (ALBUM_HEIGHT + GAP);

            if (mouseX >= x && mouseX <= x + ALBUM_WIDTH &&
                    mouseY >= albumY && mouseY <= albumY + ALBUM_HEIGHT) {
                if (this.client != null) {
                    if (parent instanceof GalleryScreen galleryScreen) galleryScreen.cancelAllAsyncTasks();
                    this.client.setScreen(new GalleryScreen(album.getUuid()));
                }
                return true;
            }

            albumIndex++;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) (verticalAmount * 20);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        int maxRows = (albums.size() + ALBUMS_PER_ROW - 1) / ALBUMS_PER_ROW;
        int maxScroll = maxRows * (ALBUM_HEIGHT + GAP) - (height - TOP_PADDING);
        if (scrollOffset > maxScroll && maxScroll > 0) {
            scrollOffset = maxScroll;
        }

        return true;
    }

    private void loadCoverImagesAsync() {
        if (client == null) return;

        for (Album album : albums) {
            String coverName = album.getCoverScreenshotName();
            if (coverName == null || coverName.isEmpty()) continue;

            Path screenshotPath = Paths.get(System.getProperty("user.dir"), "screenshots", coverName);
            File file = screenshotPath.toFile();

            if (!file.exists()) continue;

            CompletableFuture.runAsync(() -> {
                try {
                    NativeImage image = NativeImage.read(Files.newInputStream(file.toPath()));
                    MinecraftClient.getInstance().execute(() -> {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(String::new, image);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(
                                Identifier.of("album_cover/" + album.getUuid().toString()), texture);
                        Identifier id = Identifier.of("album_cover/" + album.getUuid().toString());
                        coverImageIds.put(coverName, id);
                    });
                } catch (IOException e) {
                    logger.error("Failed to load cover image for album: {}", album.getTitle(), e);
                }
            });
        }
    }
}

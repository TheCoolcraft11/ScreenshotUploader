package de.thecoolcraft11.screen;

import de.thecoolcraft11.config.AlbumManager;
import de.thecoolcraft11.config.data.Album;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AlbumConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget titleField;
    private TextFieldWidget colorField;
    private TextFieldWidget descriptionField;
    private TextFieldWidget coverScreenshotField;
    private ButtonWidget addButton;

    private int colorPreviewX;
    private int colorPreviewY;
    private int colorPreviewSize;

    private final Pattern colorPattern = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private final List<Album> albums = new ArrayList<>();
    private int selectedAlbumIndex = -1;
    private int scrollOffset = 0;

    public AlbumConfigScreen(Screen parent) {
        super(Text.translatable("gui.screenshot_uploader.album_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        albums.clear();
        albums.addAll(AlbumManager.getAllAlbums());

        titleField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 50, 200, 20, Text.literal("Title"));
        titleField.setMaxLength(32);
        titleField.setPlaceholder(Text.translatable("gui.screenshot_uploader.album_manager.album_title"));
        addDrawableChild(titleField);

        int centerX = this.width / 2;

        colorField = new TextFieldWidget(this.textRenderer, centerX - 100, 80, 180, 20, Text.literal("Color"));
        colorField.setMaxLength(7);
        colorField.setText("#");
        colorField.setPlaceholder(Text.literal("#RRGGBB"));
        addDrawableChild(colorField);


        colorPreviewX = centerX + 90;
        colorPreviewY = 80;
        colorPreviewSize = 20;


        descriptionField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 110, 200, 20, Text.literal("Description"));
        descriptionField.setMaxLength(128);
        descriptionField.setPlaceholder(Text.translatable("gui.screenshot_uploader.album_manager.album_description"));
        addDrawableChild(descriptionField);

        coverScreenshotField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 140, 200, 20, Text.literal("Cover"));
        coverScreenshotField.setMaxLength(64);
        coverScreenshotField.setPlaceholder(Text.translatable("gui.screenshot_uploader.album_manager.album_cover"));
        addDrawableChild(coverScreenshotField);

        addButton = ButtonWidget.builder(Text.literal("Add Album"), button -> {
            if (validateInputs()) {
                Album album = new Album(
                        titleField.getText(),
                        colorField.getText(),
                        descriptionField.getText(),
                        coverScreenshotField.getText()
                );
                AlbumManager.addAlbum(album);

                albums.clear();
                albums.addAll(AlbumManager.getAllAlbums());

                clearInputFields();
            }
        }).position(this.width / 2 - 100, 170).size(95, 20).build();
        addDrawableChild(addButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .position(this.width / 2 + 5, 170).size(95, 20).build();
        addDrawableChild(cancelButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("▲"), button -> {
            if (scrollOffset > 0) {
                scrollOffset--;
            }
        }).position(this.width / 2 + 110, 220).size(20, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("▼"), button -> {
            if (scrollOffset < Math.max(0, albums.size() - 5)) {
                scrollOffset++;
            }
        }).position(this.width / 2 + 110, 280).size(20, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.screenshot_uploader.album_manager.delete"), button -> {
            if (selectedAlbumIndex >= 0 && selectedAlbumIndex < albums.size()) {
                Album albumToDelete = albums.get(selectedAlbumIndex);
                AlbumManager.removeAlbum(albumToDelete.getUuid());

                albums.clear();
                albums.addAll(AlbumManager.getAllAlbums());

                selectedAlbumIndex = -1;
            }
        }).position(this.width / 2 - 100, this.height - 30).size(200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
//         this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.album_manager.title_header"), this.width / 2 - 100, 40, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.album_manager.color_header"), this.width / 2 - 100, 70, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.album_manager.description_header"), this.width / 2 - 100, 100, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.screenshot_uploader.album_manager.cover_header"), this.width / 2 - 100, 130, 0xAAAAAA);

        context.drawTextWithShadow(textRenderer, Text.literal("Existing Albums:"), this.width / 2 - 100, 200, 0xFFFFFF);

        String hexColor = colorField.getText();
        if (hexColor != null) {
            if (!hexColor.startsWith("#")) {
                hexColor = "#" + hexColor;
            }
            if (hexColor.matches("#[0-9A-Fa-f]{6}")) {
                try {
                    int r = Integer.parseInt(hexColor.substring(1, 3), 16);
                    int g = Integer.parseInt(hexColor.substring(3, 5), 16);
                    int b = Integer.parseInt(hexColor.substring(5, 7), 16);
                    int color = new Color(r, g, b).getRGB();


                    context.fill(colorPreviewX - 1, colorPreviewY - 1, colorPreviewX + colorPreviewSize + 1, colorPreviewY + colorPreviewSize + 1, 0xFF000000);
                    context.fill(colorPreviewX, colorPreviewY, colorPreviewX + colorPreviewSize, colorPreviewY + colorPreviewSize, color);
                } catch (Exception e) {
                    context.fill(colorPreviewX, colorPreviewY, colorPreviewX + colorPreviewSize, colorPreviewY + colorPreviewSize, 0xFFFF0000);
                }
            } else {
                context.fill(colorPreviewX, colorPreviewY, colorPreviewX + colorPreviewSize, colorPreviewY + colorPreviewSize, 0x77FFFFFF);
            }
        }

        int y = 220;
        int maxToShow = Math.min(5, albums.size() - scrollOffset);
        for (int i = 0; i < maxToShow; i++) {
            int albumIndex = i + scrollOffset;
            Album album = albums.get(albumIndex);

            boolean isSelected = albumIndex == selectedAlbumIndex;
            int bgColor = isSelected ? 0x80808080 : 0x20808080;

            context.fill(this.width / 2 - 100, y, this.width / 2 + 100, y + 20, bgColor);

            context.drawTextWithShadow(textRenderer, Text.literal(album.getTitle()), this.width / 2 - 95, y + 6, 0xFFFFFF);

            try {
                int colorInt = Integer.parseInt(album.getColor().substring(1), 16);
                context.fill(this.width / 2 + 70, y + 2, this.width / 2 + 95, y + 18, 0xFF000000 | colorInt);
            } catch (Exception e) {
                context.fill(this.width / 2 + 70, y + 2, this.width / 2 + 95, y + 18, 0xFFFF0000);
            }

            y += 25;
        }

    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int centerX = this.width / 2;
        int listStartY = 225;
        int albumListHeight = 200;
        int listEndY = listStartY + albumListHeight;

        if (mouseX >= centerX - 100 && mouseX <= centerX + 110 &&
                mouseY >= listStartY && mouseY <= listEndY) {
            scrollOffset = 0;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= (double) this.width / 2 - 100 && mouseX <= (double) this.width / 2 + 100) {
            int y = 220;
            int maxToShow = Math.min(5, albums.size() - scrollOffset);

            for (int i = 0; i < maxToShow; i++) {
                if (mouseY >= y && mouseY <= y + 20) {
                    selectedAlbumIndex = i + scrollOffset;

                    Album selected = albums.get(selectedAlbumIndex);
                    titleField.setText(selected.getTitle());
                    colorField.setText(selected.getColor());
                    descriptionField.setText(selected.getDescription());
                    coverScreenshotField.setText(selected.getCoverScreenshotName());

                    addButton.setMessage(Text.literal("Update Album"));

                    return true;
                }
                y += 25;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean validateInputs() {
        boolean valid = !titleField.getText().trim().isEmpty();

        if (!colorPattern.matcher(colorField.getText()).matches()) {
            valid = false;
        }
        return valid;
    }

    private void clearInputFields() {
        titleField.setText("");
        colorField.setText("#");
        descriptionField.setText("");
        coverScreenshotField.setText("");
        selectedAlbumIndex = -1;
        addButton.setMessage(Text.literal("Add Album"));
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}

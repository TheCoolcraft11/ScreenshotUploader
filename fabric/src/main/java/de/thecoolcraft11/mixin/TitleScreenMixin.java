package de.thecoolcraft11.mixin;

import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.screen.GalleryScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TitleScreen.class)
@Environment(EnvType.CLIENT)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (!ConfigManager.getClientConfig().addGalleryButtonToMainMenu) return;
        ButtonWidget galleryButton = this.addDrawableChild(
                TextIconButtonWidget.builder(
                                Text.translatable("gui.screenshot_uploader.album_manager.album_cover"),
                                btn -> MinecraftClient.getInstance().setScreen(new GalleryScreen()),
                                true)
                        .texture(Identifier.of("screenshot-uploader", "icon/gallery"), 20, 20)
                        .width(20)
                        .build()
        );

        List<? extends Element> widgets = this.children();

        int multiplayerButtonX = 0;
        int multiplayerButtonY = 0;

        for (Element drawable : widgets) {
            if (drawable instanceof ButtonWidget btn) {
                Text key = btn.getMessage();
                if (key.copy().getContent().equals(Text.translatable("menu.singleplayer").copy().getContent())) {
                    multiplayerButtonX = btn.getX() + btn.getWidth();
                    multiplayerButtonY = btn.getY();
                    break;
                }
            }
        }

        galleryButton.setPosition(multiplayerButtonX + 3, multiplayerButtonY);
    }
}

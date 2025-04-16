package de.thecoolcraft11.event;

import de.thecoolcraft11.screen.GalleryScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {
    public static final String KEY_CATEGORY = "key.category.screenshot_uploader.screenshots";
    public static final String KEY_OPEN_GALLERY = "key.screenshot_uploader.gallery";
    public static final String KEY_EDIT_IMAGE = "key.screenshot_uploader.edit_image";

    public static KeyBinding galleryKey;
    public static KeyBinding editKey;

    public static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (galleryKey.wasPressed()) {
                client.setScreen(new GalleryScreen());
            }
        });
    }

    public static void register() {
        galleryKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_OPEN_GALLERY,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KEY_CATEGORY
        ));
        editKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_EDIT_IMAGE,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KEY_CATEGORY
        ));
        registerKeyInputs();
    }
}

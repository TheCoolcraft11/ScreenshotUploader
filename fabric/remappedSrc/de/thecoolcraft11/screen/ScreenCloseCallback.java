package de.thecoolcraft11.screen;

import net.minecraft.client.texture.NativeImage;

@FunctionalInterface
public interface ScreenCloseCallback {
    void accept(Boolean doSave, Boolean doUpload, NativeImage image, String message);
}

package de.thecoolcraft11.mixin;

import de.thecoolcraft11.screen.CustomSignEditScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class SignEditorOpenMixin {
    @Inject(method = "onSignEditorOpen", at = @At("HEAD"), cancellable = true)
    private void onSignEditorOpen(SignEditorOpenS2CPacket packet, CallbackInfo ci) {
        if (CustomSignEditScreen.customEditsLeft > 0) {
            ci.cancel();
            CustomSignEditScreen.customEditsLeft--;
        }


    }
}

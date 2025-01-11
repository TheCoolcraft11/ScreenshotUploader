package de.thecoolcraft11;


import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.ScreenshotPayload;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;


public class ScreenshotUploader implements ModInitializer {
	public static final String MOD_ID = "screenshot-uploader";

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(AddressPayload.ID, AddressPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ScreenshotPayload.ID, ScreenshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ScreenshotResponsePayload.ID, ScreenshotResponsePayload.CODEC);

		System.out.println("Screenshot Uploader initialized.");
	}


}

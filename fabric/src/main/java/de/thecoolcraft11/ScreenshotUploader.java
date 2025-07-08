package de.thecoolcraft11;


import de.thecoolcraft11.packet.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ScreenshotUploader implements ModInitializer {
    public static final String MOD_ID = "screenshot-uploader";
    public static final String MOD_USER_AGENT = "ScreenshotUploader/2.0";
    private final Logger logger = LoggerFactory.getLogger(ScreenshotUploader.class);


    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(AddressPayload.ID, AddressPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScreenshotPayload.ID, ScreenshotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScreenshotChunkPayload.ID, ScreenshotChunkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScreenshotResponsePayload.ID, ScreenshotResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CommentPayload.ID, CommentPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeletionPacket.ID, DeletionPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(TagPayload.ID, TagPayload.CODEC);


        logger.info("Screenshot Uploader initialized.");
    }


}

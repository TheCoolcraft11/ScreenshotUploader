package de.thecoolcraft11.packet;

import de.thecoolcraft11.ScreenshotUploader;
import net.minecraft.util.Identifier;

public class ModMessages {
    public static final Identifier ADDRESS_PACKET_ID = Identifier.of(ScreenshotUploader.MOD_ID, "address_packet");
    public static final Identifier SCREENSHOT_PACKET_ID = Identifier.of(ScreenshotUploader.MOD_ID, "screenshot_packet");
    public static final Identifier SCREENSHOT_RESPONSE_PACKET_ID = Identifier.of(ScreenshotUploader.MOD_ID, "screenshot_response_packet");
    public static final Identifier COMMENT_PACKET_ID = Identifier.of(ScreenshotUploader.MOD_ID, "comment_packet");
}

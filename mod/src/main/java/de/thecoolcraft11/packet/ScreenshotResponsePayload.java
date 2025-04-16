package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ScreenshotResponsePayload(String json) implements CustomPayload {


    public static final CustomPayload.Id<ScreenshotResponsePayload> ID = new CustomPayload.Id<>(ModMessages.SCREENSHOT_RESPONSE_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, ScreenshotResponsePayload> CODEC = PacketCodec.tuple(PacketCodecs.STRING,ScreenshotResponsePayload::json , ScreenshotResponsePayload::new);


    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

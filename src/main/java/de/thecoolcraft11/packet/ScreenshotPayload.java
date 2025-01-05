package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ScreenshotPayload(byte[] bytes) implements CustomPayload {


    public static final CustomPayload.Id<ScreenshotPayload> ID = new CustomPayload.Id<>(ModMessages.SCREENSHOT_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, ScreenshotPayload> CODEC = PacketCodec.tuple(PacketCodecs.BYTE_ARRAY,ScreenshotPayload::bytes , ScreenshotPayload::new);


    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

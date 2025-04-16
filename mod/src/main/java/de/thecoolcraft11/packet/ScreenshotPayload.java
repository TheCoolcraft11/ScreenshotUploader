package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

public record ScreenshotPayload(String type, String transferId, int totalChunks, int chunkIndex, byte[] bytes,
                                String json) implements CustomPayload {

    public static final CustomPayload.Id<ScreenshotPayload> ID = new CustomPayload.Id<>(ModMessages.SCREENSHOT_PACKET_ID);

    public static final PacketCodec<RegistryByteBuf, ScreenshotPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ScreenshotPayload::type,
            PacketCodecs.STRING, ScreenshotPayload::transferId,
            PacketCodecs.VAR_INT, ScreenshotPayload::totalChunks,
            PacketCodecs.VAR_INT, ScreenshotPayload::chunkIndex,
            PacketCodecs.BYTE_ARRAY, ScreenshotPayload::bytes,
            PacketCodecs.STRING, ScreenshotPayload::json,
            ScreenshotPayload::new
    );

    // Constructor for init packet
    public ScreenshotPayload(String transferId, int totalChunks, String json) {
        this("INIT", transferId, totalChunks, 0, new byte[0], json);
    }

    // Constructor for chunk packet
    public ScreenshotPayload(String transferId, int totalChunks, int chunkIndex, byte[] bytes) {
        this("CHUNK", transferId, totalChunks, chunkIndex, bytes, "");
    }

    // Constructor for final packet
    public ScreenshotPayload(String transferId) {
        this("FINAL", transferId, 0, 0, new byte[0], "");
    }

    // Legacy constructor (for Fabric servers)
    public ScreenshotPayload(byte[] bytes, String json) {
        this("LEGACY", UUID.randomUUID().toString(), 1, 0, bytes, json);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
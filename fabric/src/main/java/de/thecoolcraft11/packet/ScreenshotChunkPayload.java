package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ScreenshotChunkPayload(String type, String transferId, int totalChunks, int chunkIndex, byte[] bytes,
                                     String json) implements CustomPayload {

    public static final CustomPayload.Id<ScreenshotChunkPayload> ID = new CustomPayload.Id<>(ModMessages.SCREENSHOT_CHUNK_PACKET_ID);

    public static final PacketCodec<RegistryByteBuf, ScreenshotChunkPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ScreenshotChunkPayload::type,
            PacketCodecs.STRING, ScreenshotChunkPayload::transferId,
            PacketCodecs.VAR_INT, ScreenshotChunkPayload::totalChunks,
            PacketCodecs.VAR_INT, ScreenshotChunkPayload::chunkIndex,
            PacketCodecs.BYTE_ARRAY, ScreenshotChunkPayload::bytes,
            PacketCodecs.STRING, ScreenshotChunkPayload::json,
            ScreenshotChunkPayload::new
    );

    public ScreenshotChunkPayload(String transferId, int totalChunks, String json) {
        this("INIT", transferId, totalChunks, 0, new byte[0], json);
    }

    public ScreenshotChunkPayload(String transferId, int totalChunks, int chunkIndex, byte[] bytes) {
        this("CHUNK", transferId, totalChunks, chunkIndex, bytes, "");
    }

    public ScreenshotChunkPayload(String transferId) {
        this("FINAL", transferId, 0, 0, new byte[0], "");
    }


    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record CommentPayload(String comment, String screenshot) implements CustomPayload {
    public static final CustomPayload.Id<CommentPayload> ID = new CustomPayload.Id<>(ModMessages.COMMENT_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, CommentPayload> CODEC = PacketCodec.tuple(PacketCodecs.STRING, CommentPayload::comment, PacketCodecs.STRING, CommentPayload::screenshot, CommentPayload::new);


    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}

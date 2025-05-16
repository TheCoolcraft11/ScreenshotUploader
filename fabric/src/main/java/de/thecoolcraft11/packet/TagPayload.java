package de.thecoolcraft11.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record TagPayload(String tagsJson, String screenshot) implements CustomPayload {
    public static final Id<TagPayload> ID = new Id<>(ModMessages.TAG_PACKET_ID);
    public static final PacketCodec<RegistryByteBuf, TagPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, TagPayload::tagsJson,
            PacketCodecs.STRING, TagPayload::screenshot,
            TagPayload::new
    );


    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
